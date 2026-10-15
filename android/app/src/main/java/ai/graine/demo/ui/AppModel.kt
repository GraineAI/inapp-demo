package ai.graine.demo.ui

import ai.graine.demo.domain.AgentCall
import ai.graine.demo.domain.AgentProfile
import ai.graine.demo.domain.AppConfig
import ai.graine.demo.domain.CallLog
import ai.graine.demo.domain.CallLogEntry
import ai.graine.demo.domain.CallText
import ai.graine.demo.domain.DemoActionResult
import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.DemoField
import ai.graine.demo.domain.DemoFormat
import ai.graine.demo.domain.DemoScreen
import ai.graine.demo.domain.DemoScreenContext
import ai.graine.demo.domain.ItemsState
import ai.graine.demo.domain.RowMatch
import ai.graine.demo.domain.UseCasePack
import ai.graine.demo.domain.UseCasePacks
import ai.graine.demo.graine.CallEvent
import ai.graine.demo.graine.GraineClient
import ai.graine.inapp.GraineCallStatus
import ai.graine.inapp.GraineError
import ai.graine.inapp.GraineMemory
import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The tabs along the bottom. Profile opens from Home. */
enum class DemoTab { HOME, ITEMS, CALLS, HELP, MEMORY }

/** Where the app keeps its few on-device choices (the pack, the call log). SharedPreferences in the app. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
}

/**
 * THE APP'S STATE: the use-case pack, who is signed in, where they are, the
 * Items list, the scheduled calls of both agents, what the main agent
 * remembers and the call activity log. Every agent feature goes through
 * [graine] (a [GraineClient]), so this whole class runs in JVM unit tests
 * against a fake. Main thread.
 */
class AppModel(
    val graine: GraineClient,
    val config: AppConfig,
    /** Every bundled pack, in the app's order. */
    val packs: List<UseCasePack>,
    private val store: KeyValueStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Row statuses for the app session, per pack and user: signing out and back in keeps them; a restart does not. */
    private val sessionStatuses = mutableMapOf<String, ItemsState>()

    // Which pack, and who
    var pack by mutableStateOf(UseCasePacks.chosen(packs, store.getString(UseCasePacks.CHOICE_KEY)))
        private set
    var user by mutableStateOf<UseCasePack.User?>(null)
        private set
    /** The user's consent: their conversations are remembered. */
    var memoryOn by mutableStateOf(true)
        private set

    // Where
    var tab by mutableStateOf(DemoTab.HOME)
        private set
    var profileOpen by mutableStateOf(false)
        private set
    var callLogOpen by mutableStateOf(false)
        private set
    /** The agent whose chat is open full screen; null when no chat is open. */
    var chatAgent by mutableStateOf<DemoAgent?>(null)
        private set
    /** The agent whose voice bar is up; null when none is. */
    var voiceAgent by mutableStateOf<DemoAgent?>(null)
        private set
    /** The row shown expanded on the Items screen. */
    var expandedRowId by mutableStateOf<String?>(null)
        private set

    // What
    var items by mutableStateOf<ItemsState?>(null)
        private set
    /** Every agent's scheduled calls, each with its agent. */
    var calls by mutableStateOf<List<AgentCall>>(emptyList())
        private set
    var callsLoading by mutableStateOf(false)
        private set
    var callsError by mutableStateOf<String?>(null)
        private set
    /** The agents that can be booked from the app now (the SDK's bookingAvailable): their booking buttons are enabled. */
    var bookable by mutableStateOf<Set<DemoAgent>>(emptySet())
        private set
    var memories by mutableStateOf<List<GraineMemory>>(emptyList())
        private set
    var memoriesLoading by mutableStateOf(false)
        private set
    var memoriesError by mutableStateOf<String?>(null)
        private set
    var agentMemoryEnabled by mutableStateOf<Boolean?>(null)
        private set
    var callLog by mutableStateOf(CallLog.fromJson(store.getString(CallLog.STORAGE_KEY)))
        private set
    /** A short message for the user: what just happened, or why it did not. */
    var notice by mutableStateOf<String?>(null)

    /** The activity in front: a native call is placed from it, and the booking sheet shown over it. Set by MainActivity. */
    var activity: Activity? = null

    init {
        graine.onSessionReady = { sessionArrived() }
        graine.onCallEvent = { record(it) }
        registerActions()
        refreshBookable()
        agentMemoryEnabled = graine.agentMemoryEnabled
        describeScreen()
    }

    /** An agent's name, role and starters, from the pack. */
    fun profile(agent: DemoAgent): AgentProfile? = pack?.profile(agent)

    /** The main assistant's name in the copy: the pack's ("Asha"). */
    val agentName: String get() = profile(DemoAgent.MAIN)?.name ?: pack?.agentName ?: "Assistant"

    /** The agent is configured (its key is set) — always true of the main one. */
    fun isAvailable(agent: DemoAgent): Boolean = agent in graine.agents

    /** The agent's name as the app shows it. */
    fun nameOf(agent: DemoAgent): String = profile(agent)?.name ?: if (agent == DemoAgent.MAIN) agentName else "Help"

    val chatOpen: Boolean get() = chatAgent != null
    val voiceBarVisible: Boolean get() = voiceAgent != null

    // ── the pack ────────────────────────────────────────────────────────────

    /** Switch use case on the sign-in screen. Remembered on the device; whoever was signed in is signed out first. */
    fun choosePack(id: String) {
        val next = packs.firstOrNull { it.id == id } ?: return
        if (next == pack) return
        if (user != null) signOut()
        pack = next
        store.putString(UseCasePacks.CHOICE_KEY, next.id)
        graine.setCallName(next.call.callName)
        registerActions()
        describeScreen()
    }

    // ── signing in and out ──────────────────────────────────────────────────

    /** The demo's sign-in: identify the user once, with their memory consent. The SDK identifies them to every agent. */
    fun signIn(user: UseCasePack.User, memory: Boolean) {
        val pack = pack ?: return
        graine.identify(pack.traitsFor(user), memory)
        this.user = user
        memoryOn = memory
        items = sessionStatuses[sessionKey(pack, user)] ?: ItemsState(pack.rowsFor(user))
        expandedRowId = null
        tab = DemoTab.HOME
        profileOpen = false
        callLogOpen = false
        calls = emptyList()
        memories = emptyList()
        callsError = null
        memoriesError = null
        refreshBookable()
        describeScreen()
        refreshCalls()
    }

    /** Sign out everywhere: the SDK forgets the user and releases this device; later conversations are anonymous. */
    fun signOut() {
        if (voiceAgent != null) graine.endVoice()
        graine.identify(null, null)
        user = null
        items = null
        expandedRowId = null
        calls = emptyList()
        memories = emptyList()
        chatAgent = null
        voiceAgent = null
        profileOpen = false
        callLogOpen = false
        tab = DemoTab.HOME
        notice = null
        refreshBookable()
        describeScreen()
    }

    // ── memory ──────────────────────────────────────────────────────────────

    /** Home's chip: memory works only when the agent has it on AND the user agreed. */
    val memoryStatus: Pair<String, Boolean>
        get() = when {
            agentMemoryEnabled == false -> "Memory: off for this assistant" to false
            memoryOn -> "Memory: on" to true
            else -> "Memory: off" to false
        }

    fun setMemory(enabled: Boolean) {
        graine.setMemory(enabled)
        memoryOn = enabled
        notice = if (enabled) "$agentName will remember your conversations from the next one on."
        else "$agentName will not remember your conversations from the next one on."
        describeScreen()
    }

    fun refreshMemories() {
        if (user == null) return
        memoriesLoading = true
        val asked = user
        graine.memories { list, error ->
            if (user != asked) return@memories // signed out or switched while it was on its way
            memoriesLoading = false
            if (list != null) {
                memories = list
                memoriesError = null
            } else {
                memoriesError = words(error)
            }
            describeScreen()
        }
    }

    /** Erase everything the assistant remembers about this user. [done] hears whether it is gone. */
    fun forgetMemory(done: (Boolean) -> Unit = {}) {
        graine.forgetMemory { deleted, error ->
            if (deleted != null) {
                memories = emptyList()
                memoriesError = null
                notice = when (deleted) {
                    0 -> "There was nothing to erase."
                    1 -> "Erased the 1 thing $agentName remembered about you."
                    else -> "Erased the $deleted things $agentName remembered about you."
                }
                describeScreen()
                done(true)
            } else {
                notice = words(error)
                done(false)
            }
        }
    }

    // ── navigation ──────────────────────────────────────────────────────────

    /** Show a screen: from a button, a home card, or the agent's open_screen. */
    fun open(screen: DemoScreen) {
        if (user == null) return
        if (screen == DemoScreen.CHAT) {
            openChat(DemoAgent.MAIN)
            return
        }
        when (screen) {
            DemoScreen.SIGN_IN, DemoScreen.CHAT -> return
            DemoScreen.HOME -> select(DemoTab.HOME)
            DemoScreen.ITEMS -> select(DemoTab.ITEMS)
            DemoScreen.CALLS -> select(DemoTab.CALLS)
            DemoScreen.MEMORY -> select(DemoTab.MEMORY)
            DemoScreen.HELP -> select(DemoTab.HELP)
            DemoScreen.PROFILE -> { select(DemoTab.HOME); profileOpen = true }
            DemoScreen.CALL_LOG -> { select(DemoTab.CALLS); callLogOpen = true }
        }
        describeScreen()
    }

    /** A tab tapped, or a screen opened. */
    fun select(next: DemoTab) {
        tab = next
        chatAgent = null
        profileOpen = false
        callLogOpen = false
        describeScreen()
    }

    /** A home card's target. */
    fun openTarget(target: String) {
        DemoScreen.parse(target, pack)?.let { open(it) }
    }

    /** Open an agent's chat full screen. One conversation at a time: a voice call with either agent ends. */
    fun openChat(agent: DemoAgent) {
        if (user == null || !isAvailable(agent)) return
        if (voiceAgent != null) {
            graine.endVoice()
            voiceAgent = null
        }
        chatAgent = agent
        describeScreen()
    }

    fun closeChat() {
        chatAgent = null
        describeScreen()
    }

    fun closeProfile() {
        profileOpen = false
        describeScreen()
    }

    fun closeCallLog() {
        callLogOpen = false
        describeScreen()
    }

    /** A starter chip: send it in that agent's chat, and show the chat. */
    fun startChat(text: String, agent: DemoAgent = DemoAgent.MAIN) {
        if (!isAvailable(agent)) return
        graine.sendChatMessage(text, agent)
        openChat(agent)
    }

    /** "Talk": that agent's voice bar, and the call in it. */
    fun talk(agent: DemoAgent = DemoAgent.MAIN) {
        if (!isAvailable(agent)) return
        if (voiceAgent != null && voiceAgent != agent) graine.endVoice()
        voiceAgent = agent
        graine.startVoice(agent)
    }

    fun voiceBarClosed() {
        voiceAgent = null
    }

    /** "Call": a native call with that agent on the phone's call screen, carrying the pack's placeReason. */
    fun callAgent(agent: DemoAgent = DemoAgent.MAIN) {
        val pack = pack ?: return
        if (!isAvailable(agent)) return
        val front = activity ?: return
        val entry = if (agent == DemoAgent.MAIN) "home" else "help"
        val id = graine.startNativeCall(front, pack.placeReason(agent), mapOf("entry_point" to entry), agent)
        if (id != null) record(CallEvent(id, "placed", null, agent)) else notice = "Another call is in progress."
    }

    // ── items ───────────────────────────────────────────────────────────────

    fun toggleExpanded(row: UseCasePack.Row) {
        expandedRowId = if (expandedRowId == row.id) null else row.id
    }

    /** A row's button: the same set_status the agent runs. */
    fun press(action: UseCasePack.Action, row: UseCasePack.Row) {
        val result = perform(action.name, mapOf("item" to row.id))
        if (result.status != DemoActionResult.Status.OK) notice = result.message
    }

    // ── scheduled calls ─────────────────────────────────────────────────────

    /** The agent's booking button is enabled: the SDK says it can be booked from the app now. */
    fun canBook(agent: DemoAgent): Boolean = agent in bookable

    /** The next call that will ring, from any agent. */
    val nextCall: AgentCall? get() = calls.filter { it.call.isUpcoming && it.call.at != null }.minByOrNull { it.call.at!!.time }

    /** Upcoming calls of every agent, soonest first; then the rest, most recent first. */
    val upcomingCalls: List<AgentCall> get() = calls.filter { it.call.isUpcoming }.sortedBy { it.call.at?.time ?: Long.MAX_VALUE }
    val pastCalls: List<AgentCall> get() = calls.filter { !it.call.isUpcoming }.sortedByDescending { it.call.at?.time ?: Long.MIN_VALUE }

    /** Every configured agent's calls, each marked with its agent. One agent failing does not hide the other's. */
    fun refreshCalls() {
        if (user == null) return
        val asked = user
        val agents = graine.agents
        callsLoading = true
        val merged = mutableListOf<AgentCall>()
        var failure: GraineError? = null
        var pending = agents.size
        for (agent in agents) {
            graine.scheduledCalls(agent) { list, error ->
                if (list != null) merged += list.map { AgentCall(agent, it) } else if (failure == null) failure = error
                pending--
                if (pending == 0 && user == asked) {
                    calls = merged.toList()
                    callsError = failure?.let { words(it) }
                    callsLoading = false
                    describeScreen()
                }
            }
        }
    }

    /**
     * The pack's booking button: the SDK's booking sheet for [agent], its "What's it about?" starting with the
     * pack's reason. The day, the time, the rules and any refusal are the sheet's; a call booked in it joins
     * the list at once, and the list is read again.
     */
    fun book(agent: DemoAgent = DemoAgent.MAIN) {
        val front = activity ?: return
        if (user == null) return
        // Always the SDK's own sheet: while booking is off for this phone, the sheet itself says why.
        graine.presentBooking(front, pack?.bookingReason(agent), agent) { booked ->
            if (booked == null) return@presentBooking
            calls = calls.filter { !(it.agent == agent && it.call.id == booked.id) } + AgentCall(agent, booked)
            notice = "Call with ${nameOf(agent)} booked for ${DemoFormat.time(booked.at, now())}."
            describeScreen()
            refreshCalls()
        }
    }

    fun cancelCall(call: AgentCall) {
        graine.cancelScheduledCall(call.call.id, call.agent) { status, error ->
            if (status != null) {
                notice = if (status == GraineCallStatus.CANCELLED) "Call cancelled." else "The call is ${CallText.label(status).lowercase()}."
                refreshCalls()
            } else {
                notice = CallText.message(error)
            }
        }
    }

    // ── call activity ───────────────────────────────────────────────────────

    fun record(event: CallEvent) {
        callLog = callLog.record(CallLogEntry(callId = event.callId, event = event.event, detail = event.detail, at = now(), agent = event.agent?.key))
        store.putString(CallLog.STORAGE_KEY, callLog.toJson())
        // A call that ended may have been a scheduled one: its status changed.
        if (event.event in setOf("answered", "declined", "missed", "ended")) refreshCalls()
    }

    fun clearCallLog() {
        callLog = callLog.cleared()
        store.putString(CallLog.STORAGE_KEY, callLog.toJson())
    }

    // ── the agent's actions ─────────────────────────────────────────────────

    /** Every action in the pack, registered under its name with every configured agent (each runs the ones its dashboard declares). */
    private fun registerActions() {
        graine.unregisterActions()
        for (agent in graine.agents) {
            pack?.actions?.forEach { action ->
                val name = action.name
                graine.registerAction(name, agent) { args -> perform(name, args) }
            }
        }
    }

    /** Run one of the pack's actions by its effect, answering in the pack's words (usecases/README.md). */
    fun perform(name: String, args: Map<String, Any?>): DemoActionResult {
        val pack = pack
        val action = pack?.action(name) ?: return DemoActionResult.refused("This app has no action called $name.")
        val state = items
        if (user == null || state == null) return DemoActionResult.refused("Nobody is signed in yet. Ask the user to sign in first.")
        return when (action.knownEffect) {
            UseCasePack.Effect.OPEN_SCREEN -> {
                val raw = (args["screen"] as? String) ?: (args["path"] as? String) ?: ""
                val screen = DemoScreen.parse(raw, pack)
                if (screen == null) {
                    val names = DemoScreen.openable.joinToString(", ") { it.contextName(pack) }
                    DemoActionResult.refused("There is no screen called \"$raw\". Screens: $names.")
                } else {
                    open(screen)
                    DemoActionResult.ok("Opened ${screen.title(pack)}.")
                }
            }
            UseCasePack.Effect.OPEN_ITEM -> findRow(args["item"], state, pack).fold({ it }) { row ->
                open(DemoScreen.ITEMS)
                expandedRowId = row.id
                describeScreen()
                DemoActionResult.ok("Showing ${row.title}.")
            }
            UseCasePack.Effect.SET_STATUS -> findRow(args["item"], state, pack).fold({ it }) { row ->
                val status = action.status
                when {
                    status == null || action.name !in row.actions ->
                        DemoActionResult.refused("${action.label} is not available for ${row.title}.")
                    state.statusOf(row) == status -> DemoActionResult.ok("${row.title} is already $status.")
                    else -> {
                        val next = state.withStatus(status, row)
                        items = next
                        user?.let { sessionStatuses[sessionKey(pack, it)] = next }
                        describeScreen()
                        DemoActionResult.ok("${row.title}: $status.")
                    }
                }
            }
            null -> DemoActionResult.refused("This version of the app cannot do \"${action.label}\".")
        }
    }

    /** The row named, or the refusal the agent hears. */
    private sealed class Lookup {
        data class Row(val row: UseCasePack.Row) : Lookup()
        data class Refusal(val result: DemoActionResult) : Lookup()

        fun fold(onRefusal: (DemoActionResult) -> DemoActionResult, onRow: (UseCasePack.Row) -> DemoActionResult) = when (this) {
            is Row -> onRow(row)
            is Refusal -> onRefusal(result)
        }
    }

    private fun findRow(raw: Any?, state: ItemsState, pack: UseCasePack): Lookup {
        val asked = (raw as? String) ?: ""
        return when (val m = state.find(asked)) {
            is RowMatch.Found -> Lookup.Row(m.row)
            is RowMatch.Ambiguous -> Lookup.Refusal(DemoActionResult.refused("Which one? ${m.rows.joinToString(", ") { it.title }}."))
            RowMatch.NoMatch -> Lookup.Refusal(DemoActionResult.refused(
                "No ${pack.items.itemNoun} called \"$asked\". ${pack.items.title}: ${state.rows.joinToString(", ") { it.title }}.",
            ))
        }
    }

    // ── screen context ──────────────────────────────────────────────────────

    /** The screen the user is looking at now. */
    val visibleScreen: DemoScreen
        get() = when {
            user == null -> DemoScreen.SIGN_IN
            chatAgent == DemoAgent.MAIN -> DemoScreen.CHAT
            chatAgent == DemoAgent.HELP -> DemoScreen.HELP
            else -> when (tab) {
                DemoTab.HOME -> if (profileOpen) DemoScreen.PROFILE else DemoScreen.HOME
                DemoTab.ITEMS -> DemoScreen.ITEMS
                DemoTab.CALLS -> if (callLogOpen) DemoScreen.CALL_LOG else DemoScreen.CALLS
                DemoTab.HELP -> DemoScreen.HELP
                DemoTab.MEMORY -> DemoScreen.MEMORY
            }
        }

    /** Tell the agents what is on screen (the SDK shares it with every agent). Called on every change. */
    fun describeScreen() = graine.setScreen(screenContext())

    /** The same keys on every platform (usecases/README.md, "Screen context"). */
    fun screenContext(): DemoScreenContext {
        val screen = visibleScreen
        val fields = when (screen) {
            DemoScreen.HOME -> listOf(
                DemoField("memory", "Memory", if (memoryOn) "on" else "off", "filled"),
                DemoField("next_call", "Next call", nextCall?.call?.at?.let { DemoFormat.iso(it.time) } ?: "none", "filled"),
            )
            DemoScreen.ITEMS -> items?.let { s -> s.rows.map { DemoField(it.id, it.title, s.valueOf(it), "filled") } } ?: emptyList()
            DemoScreen.CALLS -> listOf(DemoField("upcoming", "Upcoming calls", upcomingCalls.size.toString(), "filled"))
            DemoScreen.MEMORY -> listOf(
                DemoField("memory", "Memory", if (memoryOn) "on" else "off", "filled"),
                DemoField("remembered", "Remembered", memories.size.toString(), "filled"),
            )
            DemoScreen.SIGN_IN, DemoScreen.CHAT, DemoScreen.PROFILE, DemoScreen.HELP, DemoScreen.CALL_LOG -> emptyList()
        }
        return DemoScreenContext(screen.contextName(pack), screen.title(pack), fields)
    }

    // ── private ─────────────────────────────────────────────────────────────

    private fun sessionArrived() {
        refreshBookable()
        agentMemoryEnabled = graine.agentMemoryEnabled
        describeScreen()
    }

    /** Whether each agent can be booked changes with its session and with who is signed in: read it again then. */
    private fun refreshBookable() {
        bookable = graine.agents.filter { graine.bookingAvailable(it) }.toSet()
    }

    private fun sessionKey(pack: UseCasePack, user: UseCasePack.User) = "${pack.id}/${user.id}"

    private fun words(error: GraineError?): String = CallText.message(error)
}
