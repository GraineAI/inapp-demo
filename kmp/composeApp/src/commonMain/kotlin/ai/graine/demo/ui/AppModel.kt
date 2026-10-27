package ai.graine.demo.ui

import ai.graine.demo.domain.ServerTokenSettings
import ai.graine.demo.domain.ServerTokenStore
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
import ai.graine.demo.domain.EndedCall
import ai.graine.demo.domain.ItemsState
import ai.graine.demo.domain.OwnBooking
import ai.graine.demo.domain.OwnText
import ai.graine.demo.domain.PastSessionsText
import ai.graine.demo.domain.SessionTranscript
import ai.graine.demo.domain.RowMatch
import ai.graine.demo.domain.UseCasePack
import ai.graine.demo.domain.UseCasePacks
import ai.graine.demo.graine.CallEvent
import ai.graine.demo.graine.GraineClient
import ai.graine.demo.journey.JourneyAgent
import ai.graine.demo.journey.JourneyModel
import ai.graine.demo.platform.KeyValueStore
import ai.graine.demo.security.SecurityRefusal
import ai.graine.inapp.kmp.GraineBookingState
import ai.graine.inapp.kmp.GraineCallStatus
import ai.graine.inapp.kmp.GraineException
import ai.graine.inapp.kmp.GraineMemory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** The tabs along the bottom. Profile opens from Home. */
enum class DemoTab { HOME, ITEMS, CALLS, HELP, MEMORY }

/**
 * THE APP'S STATE: the use-case pack, who is signed in, where they are, the
 * Items list, the scheduled calls of both agents, what the main agent
 * remembers and the call activity log. Every agent feature goes through
 * [graine] (a [GraineClient]), so this whole class runs in unit tests against
 * a fake. Main thread.
 */
class AppModel(
    val graine: GraineClient,
    val config: AppConfig,
    /** Every bundled pack, in the app's order. */
    val packs: List<UseCasePack>,
    private val store: KeyValueStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /** The user's time zone: the days and times the app's own booking screen offers. */
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    /**
     * Makes the Journey for a pack that has one (null for a pack without): the journey hears [onOpenChanged] when it
     * comes on screen or leaves it. Null — the tour's tests — for no journey at all.
     */
    private val journeys: ((pack: UseCasePack, onOpenChanged: (Boolean) -> Unit) -> JourneyModel?)? = null,
    /** Where the app's long-lived collectors run (each agent's bookingState): [scope] in the app; the test's background scope in tests. */
    private val collectors: CoroutineScope = scope,
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

    // ── the agent's variables (Profile) ─────────────────────────────────────

    /** What the agent receives with the next chat, voice session or call — the SDK's own read-back. */
    var sentVariables by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /** Variables added on Profile — any name, any value — beyond the pack's mapping. */
    var addedVariables by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /**
     * Add (or change) one prompt variable, exactly as an API call's `variables` carries it: the agent's
     * prompt and welcome use it as {name}. Returns why it was refused, in plain words, or null when sent.
     */
    fun addVariable(name: String, value: String): String? {
        val pack = pack ?: return "Sign in first."
        val n = name.trim()
        val v = value.trim()
        if (!VARIABLE_NAME.matches(n)) return "A name is letters, digits and underscores, starting with a letter (up to 40)."
        if (n.lowercase().startsWith("graine_")) return "Names starting with graine_ are set by Graine."
        if (n == pack.identifierField) return "${pack.identifierLabel} identifies the user; it is never a variable."
        if (v.isEmpty()) return "Give the variable a value."
        if (v.length > VARIABLE_VALUE_MAX) return "A value is at most $VARIABLE_VALUE_MAX characters."
        graine.setVariables(mapOf(n to v))
        addedVariables = addedVariables + (n to v)
        refreshVariables()
        return null
    }

    /** Remove a variable added on Profile (null under its name, as the SDK takes a removal). */
    fun removeVariable(name: String) {
        if (name !in addedVariables) return
        graine.setVariables(mapOf(name to null))
        addedVariables = addedVariables - name
        refreshVariables()
    }

    fun refreshVariables() {
        sentVariables = runCatching { graine.currentVariables() }.getOrDefault(emptyMap())
    }

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
    /**
     * Whether each agent can be booked from the app now, and why not — each agent's `bookingState`, collected from
     * launch, so a button drawn before the agent's session arrived turns on by itself when it does.
     */
    var booking by mutableStateOf<Map<DemoAgent, GraineBookingState>>(emptyMap())
        private set
    /** The SDK's booking sheet is on screen: a second tap opens no other. */
    var bookingOpen by mutableStateOf(false)
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

    // ── "Your own design" ───────────────────────────────────────────────────

    /**
     * "Your own design": every surface — the chat, voice, the call screen, booking and the agent's components —
     * drawn by this app from the SDK's public state, with no SDK screen at all. Off (the default): the SDK's own
     * screens, in the brand's Garage design, exactly as before. Kept on the device.
     */
    var ownDesign by mutableStateOf(store.getString(OWN_DESIGN_KEY) == "on")
        private set
    /** Profile → Server token (demo): how this app gets a server token, kept on the device (domain/ServerToken.kt). */
    var serverToken by mutableStateOf(ServerTokenStore.load(store))
        private set

    /** Keep a new server-token choice; it takes effect the next time the app starts. */
    fun saveServerToken(settings: ServerTokenSettings) {
        ServerTokenStore.save(store, settings)
        serverToken = settings
    }
    /** The app's own booking screen, while it is open: its agent, the day and time picked, and how booking went. */
    var ownBooking by mutableStateOf<OwnBooking?>(null)
        private set
    /** The Public state screen is open: every public state value, live. */
    var publicStateOpen by mutableStateOf(false)
        private set
    /** The last call that ended while the app draws its own call screen, with its final connection report, until closed. */
    var endedCall by mutableStateOf<EndedCall?>(null)
        private set
    /** The Past sessions screen is open, for this agent's sessions (from the own chat). */
    var pastSessionsAgent by mutableStateOf<DemoAgent?>(null)
        private set
    /** The past session whose transcript is open on it. */
    var transcript by mutableStateOf<SessionTranscript?>(null)
        private set
    /** The id of the transcript on its way. */
    var transcriptLoading by mutableStateOf<String?>(null)
        private set
    /** Why the last transcript could not be opened, in plain words. */
    var transcriptError by mutableStateOf<String?>(null)
        private set

    /**
     * THE JOURNEY of the chosen pack — onboarding, a first call, the plan, check-ins — when the pack has one. Made at
     * launch, so its actions are registered and its signed-in user identified before any call can ring; while it is
     * open it is the whole app, and the tour's screens wait behind it.
     */
    var journey by mutableStateOf<JourneyModel?>(null)
        private set

    /** What the SDK was last told about the call screen: its own (false, the SDK's default) or the app's. Before init: init sets it. */
    private var ownCallScreenNamed = false

    init {
        graine.onSessionReady = { sessionArrived() }
        graine.onCallEvent = { record(it) }
        registerActions()
        collectBooking()
        agentMemoryEnabled = graine.agentMemoryEnabled
        journey = makeJourney()
        syncCallScreen()
        describeScreen()
    }

    private fun makeJourney(): JourneyModel? = pack?.let { p ->
        journeys?.invoke(p) { open ->
            syncCallScreen(open)
            if (!open) describeScreen()
        }
    }

    /**
     * The app draws its own call screen while the Journey is open or "Your own design" is on (on Android the SDK then
     * opens the app's call activity); otherwise the SDK's.
     */
    private fun syncCallScreen(journeyOpen: Boolean = journey?.open == true) {
        val want = ownDesign || journeyOpen
        if (want == ownCallScreenNamed) return
        ownCallScreenNamed = want
        graine.useOwnCallScreen(want)
    }

    /** The Home switch: "Graine's screens" or "Your own design". What is open closes; the next opens in the design chosen. */
    fun chooseOwnDesign(on: Boolean) {
        if (on == ownDesign) return
        if (voiceAgent != null) graine.endVoice()
        voiceAgent = null
        chatAgent = null
        ownBooking = null
        publicStateOpen = false
        endedCall = null
        closePastSessions()
        ownDesign = on
        store.putString(OWN_DESIGN_KEY, if (on) "on" else null)
        syncCallScreen()
        describeScreen()
    }

    fun openPublicState() {
        if (user == null) return
        publicStateOpen = true
    }

    fun closePublicState() {
        publicStateOpen = false
    }

    /** The own call screen's Close, once a call is over. */
    fun closeEndedCall() {
        endedCall = null
    }

    // ── past sessions ───────────────────────────────────────────────────────

    /** The Past sessions screen for [agent]: the SDK loads the first page again, newest first. */
    fun openPastSessions(agent: DemoAgent) {
        if (user == null || !isAvailable(agent)) return
        pastSessionsAgent = agent
        closeTranscript()
        graine.refreshPastSessions(agent)
    }

    fun closePastSessions() {
        pastSessionsAgent = null
        closeTranscript()
    }

    fun refreshPastSessions() {
        pastSessionsAgent?.let { graine.refreshPastSessions(it) }
    }

    /** The next page, at the end of the list: only while there is more and nothing is on its way. */
    fun loadMorePastSessions() {
        val agent = pastSessionsAgent ?: return
        val state = graine.pastSessions(agent).value
        if (state.loading || !state.hasMore) return
        graine.loadMorePastSessions(agent)
    }

    /** A past session's transcript, read only: what was said, oldest first. */
    fun openTranscript(id: String) {
        val agent = pastSessionsAgent ?: return
        transcriptLoading = id
        transcriptError = null
        scope.launch {
            val result = attempt { graine.sessionTranscript(id, agent) }
            if (pastSessionsAgent != agent || transcriptLoading != id) return@launch
            transcriptLoading = null
            result.onSuccess { transcript = it }
                .onFailure { transcriptError = PastSessionsText.transcriptError((it as? GraineException)?.code?.wire) ?: words(it) }
        }
    }

    fun closeTranscript() {
        transcript = null
        transcriptLoading = null
        transcriptError = null
    }

    /** An agent's name, role and starters, from the pack. */
    fun profile(agent: DemoAgent): AgentProfile? = pack?.profile(agent)

    /** The main assistant's name in the copy: the pack's ("Asha"). */
    val agentName: String get() = profile(DemoAgent.MAIN)?.name ?: pack?.agentName ?: "Assistant"

    /** The agent is configured (its key is set) — always true of the main one. */
    fun isAvailable(agent: DemoAgent): Boolean = agent in graine.agents

    /** The agent's name as the app shows it. */
    fun nameOf(agent: DemoAgent): String = profile(agent)?.name ?: if (agent == DemoAgent.MAIN) agentName else "Help"

    /** An agent's name on the Security card, by the name the SDK knows it by: the pack's words, else that name. */
    fun securityName(sdkName: String): String = DemoAgent.ofSdkName(sdkName)?.let { nameOf(it) }
        ?: (if (sdkName == JourneyAgent.FIRST_CALL.sdkName) pack?.journey?.agents?.firstCall?.name else null)
        ?: sdkName

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
        journey?.close()
        journey = makeJourney()
        syncCallScreen()
        describeScreen()
    }

    // ── signing in and out ──────────────────────────────────────────────────

    /**
     * The demo's sign-in: the user identified once, with their memory consent — and proven by their identity token
     * (security/GraineSecurity.kt). The SDK identifies them to every agent.
     */
    fun signIn(user: UseCasePack.User, memory: Boolean) {
        val pack = pack ?: return
        graine.signIn(user.id, pack.traitsFor(user), memory)
        graine.setVariables(pack.variablesFor(user))
        refreshVariables()
        // The microphone (and notifications on Android 13+) now, so a call or a ring never waits on a prompt.
        scope.launch { runCatching { graine.requestCallPermissions() } }
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
        describeScreen()
        refreshCalls()
    }

    /** Sign out everywhere: the SDK forgets the user and releases this device; later conversations are anonymous. */
    fun signOut() {
        if (voiceAgent != null) graine.endVoice()
        val leaving = user
        val leavingPack = pack
        if (leaving != null && leavingPack != null) {
            graine.setVariables((leavingPack.variables.keys + addedVariables.keys).associateWith { null })
        }
        addedVariables = emptyMap()
        sentVariables = emptyMap()
        graine.signOut()
        user = null
        items = null
        expandedRowId = null
        calls = emptyList()
        memories = emptyList()
        chatAgent = null
        voiceAgent = null
        ownBooking = null
        publicStateOpen = false
        endedCall = null
        closePastSessions()
        profileOpen = false
        callLogOpen = false
        tab = DemoTab.HOME
        notice = null
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
        scope.launch {
            val result = attempt { graine.memories() }
            if (user != asked) return@launch // signed out or switched while it was on its way
            memoriesLoading = false
            result.onSuccess {
                memories = it
                memoriesError = null
            }.onFailure { memoriesError = words(it) }
            describeScreen()
        }
    }

    /** Erase everything the assistant remembers about this user. [done] hears whether it is gone. */
    fun forgetMemory(done: (Boolean) -> Unit = {}) {
        scope.launch {
            attempt { graine.forgetMemory() }.onSuccess { deleted ->
                memories = emptyList()
                memoriesError = null
                notice = when {
                    deleted == 0 -> "There was nothing to erase."
                    deleted == 1 -> "Erased the 1 thing $agentName remembered about you."
                    deleted > 1 -> "Erased the $deleted things $agentName remembered about you."
                    else -> "Erased what $agentName remembered about you."
                }
                describeScreen()
                done(true)
            }.onFailure {
                notice = words(it)
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
        ownBooking = null
        publicStateOpen = false
        closePastSessions()
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

    /**
     * "Talk": that agent's voice bar, and the call in it. In your own design the app's own voice panel opens ready,
     * and the conversation — the microphone with it — starts only when its Start is tapped ([startTalk]).
     */
    fun talk(agent: DemoAgent = DemoAgent.MAIN) {
        if (!isAvailable(agent)) return
        if (voiceAgent != null && voiceAgent != agent) graine.endVoice()
        voiceAgent = agent
        if (!ownDesign) graine.startVoice(agent)
    }

    fun voiceBarClosed() {
        voiceAgent = null
    }

    /** The own voice panel's Start. */
    fun startTalk() {
        val agent = voiceAgent ?: return
        graine.startVoice(agent)
    }

    /** The own voice panel's End: the conversation ends, the panel stays to start another. */
    fun endTalk() {
        if (voiceAgent != null) graine.endVoice()
    }

    /** The own voice panel's close: the conversation ends and the panel goes. */
    fun closeTalk() {
        if (voiceAgent != null) graine.endVoice()
        voiceAgent = null
    }

    /** "Call": a native call with that agent on the phone's call screen, carrying the pack's placeReason. */
    fun callAgent(agent: DemoAgent = DemoAgent.MAIN) {
        val pack = pack ?: return
        if (!isAvailable(agent)) return
        val entry = if (agent == DemoAgent.MAIN) "home" else "help"
        val id = graine.startNativeCall(pack.placeReason(agent), mapOf("entry_point" to entry), agent)
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

    /** The next call that will ring, from any agent. */
    val nextCall: AgentCall? get() = calls.filter { it.call.isUpcoming && it.call.at != null }.minByOrNull { it.call.at!! }

    /** Upcoming calls of every agent, soonest first; then the rest, most recent first. */
    val upcomingCalls: List<AgentCall> get() = calls.filter { it.call.isUpcoming }.sortedBy { it.call.at ?: Instant.DISTANT_FUTURE }
    val pastCalls: List<AgentCall> get() = calls.filter { !it.call.isUpcoming }.sortedByDescending { it.call.at ?: Instant.DISTANT_PAST }

    /** Every configured agent's calls, each marked with its agent. One agent failing does not hide the other's. */
    fun refreshCalls() {
        if (user == null) return
        val asked = user
        callsLoading = true
        scope.launch {
            val merged = mutableListOf<AgentCall>()
            var failure: Throwable? = null
            for (agent in graine.agents) {
                attempt { graine.scheduledCalls(agent) }
                    .onSuccess { list -> merged += list.map { AgentCall(agent, it) } }
                    .onFailure { if (failure == null) failure = it }
            }
            if (user != asked) return@launch
            calls = merged.toList()
            callsError = failure?.let { words(it) }
            callsLoading = false
            describeScreen()
        }
    }

    /** Whether [agent]'s booking button is on: its bookingState, as it stands. */
    fun canBook(agent: DemoAgent): Boolean = booking[agent]?.available == true

    /**
     * Why [agent] cannot be booked now, in plain words — from its bookingState's `reason` (`not_loaded` reads "not
     * yet"); null when it can.
     */
    fun bookingHint(agent: DemoAgent): String? {
        val state = booking[agent] ?: return OwnText.bookingUnavailable("not_loaded")
        return if (state.available) null else OwnText.bookingUnavailable(state.reason ?: "not_loaded")
    }

    /**
     * Each agent's bookingState, from now on: the SDK reads it again when the agent's session arrives, after identify
     * and setMemory, and when the session could not load — the app never asks again itself. Its current value first,
     * so the first frame is right; then every change, and the app's own booking screen follows.
     */
    private fun collectBooking() {
        for (agent in graine.agents) {
            val flow = graine.bookingState(agent)
            booking = booking + (agent to flow.value)
            collectors.launch {
                flow.collect { state ->
                    if (booking[agent] != state) booking = booking + (agent to state)
                    if (ownBooking?.agent == agent) refreshOwnBooking()
                }
            }
        }
    }

    /**
     * The booking button: the SDK's own sheet for [agent] — the day, the time, the time zone and "What's it
     * about?", with the pack's first reason filled in; its refusals stay in the sheet. A booked call joins the
     * list at once and the list is read again; a sheet closed without booking changes nothing.
     */
    fun book(agent: DemoAgent = DemoAgent.MAIN) {
        val pack = pack ?: return
        if (user == null || bookingOpen) return
        // Your own design: the app's own booking screen instead of the SDK's sheet.
        if (ownDesign) {
            openOwnBooking(agent)
            return
        }
        // Always the SDK's own sheet: while booking is off for this phone, the sheet itself says why.
        bookingOpen = true
        scope.launch {
            val result = attempt { graine.presentBooking(pack.reasons(agent).firstOrNull(), agent) }
            bookingOpen = false
            result.onSuccess { booked ->
                if (booked == null) return@onSuccess
                calls = calls.filter { !(it.agent == agent && it.call.id == booked.id) } + AgentCall(agent, booked)
                describeScreen()
                refreshCalls()
            }.onFailure { notice = words(it) }
        }
    }

    fun cancelCall(call: AgentCall) {
        scope.launch {
            attempt { graine.cancelScheduledCall(call.call.id, call.agent) }.onSuccess { status ->
                notice = if (status == GraineCallStatus.Cancelled) "Call cancelled." else "The call is ${CallText.label(status).lowercase()}."
                refreshCalls()
                ownBooking?.let { refreshOwnBooking() }
            }.onFailure { notice = words(it) }
        }
    }

    // ── booking drawn by the app ("Your own design") ────────────────────────

    /** The app's own booking screen for [agent]: the SDK's rule and its times, the pack's first reason filled in. */
    fun openOwnBooking(agent: DemoAgent = DemoAgent.MAIN) {
        if (user == null || !isAvailable(agent)) return
        ownBooking = readBooking(agent, day = null, reason = pack?.reasons(agent)?.firstOrNull().orEmpty(), slot = null)
        refreshCalls()
        describeScreen()
    }

    fun closeOwnBooking() {
        ownBooking = null
        describeScreen()
    }

    /** Read the SDK again — whether booking is on and why not, the rules, each day's times — keeping the choices that still fit. */
    fun refreshOwnBooking() {
        val b = ownBooking ?: return
        ownBooking = readBooking(b.agent, b.day, b.reason, b.slot).copy(booked = b.booked)
    }

    fun pickBookingDay(day: LocalDate) {
        val b = ownBooking ?: return
        if (b.busy) return
        ownBooking = b.copy(day = day, slots = graine.bookingSlots(day, b.agent), slot = null, refusal = null, booked = null)
    }

    fun pickBookingSlot(at: Instant) {
        val b = ownBooking ?: return
        if (b.busy || at !in b.slots) return
        ownBooking = b.copy(slot = at, refusal = null, booked = null)
    }

    fun setBookingReason(text: String) {
        val b = ownBooking ?: return
        ownBooking = b.copy(reason = text.take(OwnBooking.REASON_MAX))
    }

    /**
     * Book the time picked, with what it is about — the SDK's scheduleCall, which checks the agent's rules first and
     * then Graine's (calling hours, calls already booked). A refusal shows the SDK's own sentence; a booked call joins
     * the list at once.
     */
    fun bookOwn() {
        val b = ownBooking ?: return
        val at = b.slot ?: return
        if (b.busy || !b.available) return
        ownBooking = b.copy(busy = true, refusal = null, booked = null)
        scope.launch {
            val result = attempt { graine.scheduleCall(at, b.reason.trim().ifEmpty { null }, b.agent) }
            result.onSuccess { call ->
                calls = calls.filter { !(it.agent == b.agent && it.call.id == call.id) } + AgentCall(b.agent, call)
                if (ownBooking?.agent == b.agent) ownBooking = readBooking(b.agent, b.day, b.reason, slot = null).copy(booked = call)
                describeScreen()
                refreshCalls()
            }.onFailure { e ->
                ownBooking?.takeIf { it.agent == b.agent }?.let { ownBooking = it.copy(busy = false, refusal = refusal(e)) }
            }
        }
    }

    private fun readBooking(agent: DemoAgent, day: LocalDate?, reason: String, slot: Instant?): OwnBooking {
        val rules = graine.schedulingRules(agent)
        val days = OwnText.bookingDays(rules, Instant.fromEpochMilliseconds(now()), zone).map { it to graine.bookingSlots(it, agent) }
        val chosen = day?.takeIf { d -> days.any { it.first == d && it.second.isNotEmpty() } }
            ?: days.firstOrNull { it.second.isNotEmpty() }?.first
        val slots = days.firstOrNull { it.first == chosen }?.second.orEmpty()
        val state = booking[agent]
        return OwnBooking(
            agent = agent,
            unavailableReason = if (state?.available == true) null else state?.reason ?: "not_loaded",
            rules = rules,
            days = days.map { OwnBooking.Day(it.first, it.second.size) },
            day = chosen,
            slots = slots,
            slot = slot?.takeIf { it in slots },
            reason = reason,
        )
    }

    /**
     * A booking refused: a refusal about the app, the user, the server token or the key in SecurityRefusal's words;
     * else the SDK's sentence (its booking sheet's words, or the server's), else the app's words.
     */
    private fun refusal(e: Throwable): String {
        val g = e as? GraineException
        g?.let { SecurityRefusal.of(it.code, it.reason) }?.let { return it.message }
        return g?.message?.trim()?.takeIf { it.isNotEmpty() } ?: words(e)
    }

    // ── call activity ───────────────────────────────────────────────────────

    fun record(event: CallEvent) {
        callLog = callLog.record(CallLogEntry(callId = event.callId, event = event.event, detail = event.detail, at = now(), agent = event.agent?.key))
        store.putString(CallLog.STORAGE_KEY, callLog.toJson())
        // Your own design's call screen: a call that is over stays on it with its final connection report, until closed.
        when (event.event) {
            "placed", "incoming" -> endedCall = null
            "ended", "failed" -> if (ownDesign) {
                endedCall = EndedCall(event.callId, event.agent, event.event, event.detail, event.durationSeconds, event.connection, event.endCause)
            }
        }
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
            ownBooking != null -> DemoScreen.CALLS
            else -> when (tab) {
                DemoTab.HOME -> if (profileOpen) DemoScreen.PROFILE else DemoScreen.HOME
                DemoTab.ITEMS -> DemoScreen.ITEMS
                DemoTab.CALLS -> if (callLogOpen) DemoScreen.CALL_LOG else DemoScreen.CALLS
                DemoTab.HELP -> DemoScreen.HELP
                DemoTab.MEMORY -> DemoScreen.MEMORY
            }
        }

    /** Tell the agents what is on screen (the SDK shares it with every agent). Called on every change; the open Journey describes its own. */
    fun describeScreen() {
        if (journey?.open == true) return
        graine.setScreen(screenContext())
    }

    /** The same keys on every platform (usecases/README.md, "Screen context"). */
    fun screenContext(): DemoScreenContext {
        val screen = visibleScreen
        val fields = when (screen) {
            DemoScreen.HOME -> listOf(
                DemoField("memory", "Memory", if (memoryOn) "on" else "off", "filled"),
                DemoField("next_call", "Next call", nextCall?.call?.at?.let { DemoFormat.iso(it.toEpochMilliseconds()) } ?: "none", "filled"),
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
        // Booking follows its own state (collectBooking); the session brings the agent's rules, which the own booking screen reads.
        ownBooking?.let { refreshOwnBooking() }
        agentMemoryEnabled = graine.agentMemoryEnabled
        describeScreen()
    }

    private fun sessionKey(pack: UseCasePack, user: UseCasePack.User) = "${pack.id}/${user.id}"

    private fun words(error: Throwable?): String = CallText.message(error)

    /** A suspending SDK call as a Result — never swallowing cancellation. */
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

/** Where the Home switch is kept on the device. */
private const val OWN_DESIGN_KEY = "demo.ownDesign"

/** A prompt variable's name, as the agent's {placeholders} are written: a letter, then letters, digits or _. */
private val VARIABLE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]{0,39}$")
private const val VARIABLE_VALUE_MAX = 200
