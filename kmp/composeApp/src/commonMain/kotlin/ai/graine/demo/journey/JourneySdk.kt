package ai.graine.demo.journey

import ai.graine.demo.security.GraineSecurity
import ai.graine.inapp.kmp.Graine
import ai.graine.inapp.kmp.GraineActionResult
import ai.graine.inapp.kmp.GraineAgent
import ai.graine.inapp.kmp.GraineBookingState
import ai.graine.inapp.kmp.GraineCallInfo
import ai.graine.inapp.kmp.GraineCallOutcome
import ai.graine.inapp.kmp.GraineCallStatus
import ai.graine.inapp.kmp.GraineChatStatus
import ai.graine.inapp.kmp.GraineErrorCode
import ai.graine.inapp.kmp.GraineException
import ai.graine.inapp.kmp.GraineMemory
import ai.graine.inapp.kmp.GraineScheduledCall
import ai.graine.inapp.kmp.GraineSchedulingRules
import ai.graine.inapp.kmp.GraineScreen
import ai.graine.inapp.kmp.GraineSessionSummary
import ai.graine.inapp.kmp.GraineSessionTranscript
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/** What an action handler answers: the agent speaks about exactly this. [data] is the structured result it reads. */
data class JourneyAnswer(val status: Status, val message: String, val data: Map<String, Any?> = emptyMap()) {
    enum class Status { OK, REFUSED, ERROR }

    companion object {
        fun ok(message: String, data: Map<String, Any?> = emptyMap()) = JourneyAnswer(Status.OK, message, data)
        fun refused(message: String) = JourneyAnswer(Status.REFUSED, message)
    }
}

/** The call permissions after asking: whether each is allowed (or not needed on this phone). */
data class CallPermissionAnswer(val microphone: Boolean, val notifications: Boolean)

/**
 * EVERYTHING THE JOURNEY ASKS OF THE GRAINE SDK, step by step — the interface the journey's model uses, so its rules
 * run in unit tests against a fake. [GraineJourneySdk] is the real one: every SDK call of the journey is in it.
 */
interface JourneySdk {
    fun isConfigured(agent: JourneyAgent): Boolean

    // 1. Onboarding: who the user is (and their proof), what the agents know about them, the permissions a call needs
    /** Who the user is, proven by their identity token — at sign-up and at every launch while signed in. */
    fun signIn(userId: String, traits: Map<String, String>)
    /** Nobody: every agent lets the user go. */
    fun signOut()
    fun setVariables(values: Map<String, String?>)
    suspend fun requestCallPermissions(): CallPermissionAnswer

    // 2. The first call: now, or booked from the first-call agent's times; the call itself
    val callState: StateFlow<GraineCallInfo>
    val callOutcomes: Flow<GraineCallOutcome>
    fun startCall(reason: String, agent: JourneyAgent): String?
    fun answer()
    fun decline()
    fun end()
    fun setMuted(muted: Boolean)
    fun setSpeaker(on: Boolean)
    fun schedulingRules(agent: JourneyAgent): GraineSchedulingRules
    fun bookingState(agent: JourneyAgent): StateFlow<GraineBookingState>
    fun bookingSlots(day: LocalDate, agent: JourneyAgent): List<Instant>
    suspend fun scheduleCall(at: Instant, reason: String, agent: JourneyAgent): GraineScheduledCall
    suspend fun scheduledCalls(agent: JourneyAgent): List<GraineScheduledCall>
    suspend fun cancelScheduledCall(id: String, agent: JourneyAgent): GraineCallStatus

    // 3. "We couldn't connect": call back the missed ring, or let it go
    fun callBack(): String?
    fun dismissMissedCall()

    // The chats: the first call's chat version, the coach's (the main screen), support's
    fun chatStatus(agent: JourneyAgent): StateFlow<GraineChatStatus>
    fun chatShown(agent: JourneyAgent, visible: Boolean)
    fun chatRetry(agent: JourneyAgent)

    // 4–5. Telling the coach: an event it may speak up about, things that happened, what is on screen
    fun reportEvent(name: String, detail: String?)
    fun track(name: String, data: Map<String, Any?>)
    fun setScreen(screen: GraineScreen?)

    // The plan's actions: the app is the store
    fun registerAction(agent: JourneyAgent, name: String, handler: (Map<String, Any?>) -> JourneyAnswer)
    fun unregisterActions()

    // 6. Privacy: what the agents remember, the user's conversations, and forgetting
    suspend fun memories(): List<GraineMemory>
    suspend fun forgetMemory(agent: JourneyAgent): Int
    suspend fun pastSessions(agent: JourneyAgent, max: Int): List<GraineSessionSummary>
    suspend fun sessionTranscript(agent: JourneyAgent, id: String): GraineSessionTranscript
}

/**
 * THE JOURNEY'S SDK CALLS — `ai.graine:inapp-kmp`, from shared code, on Android and iOS alike. The coach is the default
 * agent (`Graine`), the first-call agent `Graine.agent("consultation")`, support `Graine.agent("help")`; calls are
 * one engine for the app (`Graine.calls`). Callbacks arrive on the main thread.
 */
class GraineJourneySdk : JourneySdk {
    private val registered = mutableListOf<() -> Unit>()

    private fun sdk(agent: JourneyAgent): GraineAgent? = Graine.agent(agent.sdkName)

    private fun need(agent: JourneyAgent): GraineAgent =
        sdk(agent) ?: throw GraineException(GraineErrorCode.InvalidKey, "No publishable key is set for this agent.")

    /**
     * One of [agent]'s requests: a refusal about the app, the user, the server token or the key is noted
     * (security/GraineSecurity.kt: the Security card, the log) and thrown on as it came. No retry of the app's own.
     */
    private suspend fun <T> guarded(agent: JourneyAgent, request: suspend (GraineAgent) -> T): T {
        val graine = need(agent)
        return try {
            request(graine).also { GraineSecurity.noteAnswered(agent.sdkName) }
        } catch (e: GraineException) {
            GraineSecurity.noteRefusal(agent.sdkName, e)
            throw e
        }
    }

    override fun isConfigured(agent: JourneyAgent): Boolean = sdk(agent) != null

    // ── 1. onboarding ───────────────────────────────────────────────────────

    /**
     * `Graine.identify(mapOf("user_id" to id, "name" to name), identityToken = …)` through GraineSecurity — every agent
     * hears who it is, proven by the identity token.
     */
    override fun signIn(userId: String, traits: Map<String, String>) = GraineSecurity.signIn(userId, traits)

    /** `Graine.identify(null)` through GraineSecurity: signed out everywhere, the token dropped with the identity. */
    override fun signOut() = GraineSecurity.signOut()

    /** The profile as prompt variables, on every agent the journey talks to (each agent keeps its own); null removes one. */
    override fun setVariables(values: Map<String, String?>) {
        JourneyAgent.entries.forEach { sdk(it)?.setVariables(values) }
    }

    /** `Graine.calls.requestCallPermissions()`: the microphone, and notifications on Android 13+. */
    override suspend fun requestCallPermissions(): CallPermissionAnswer {
        val p = Graine.calls.requestCallPermissions()
        return CallPermissionAnswer(p.microphone.isSatisfied, p.notifications.isSatisfied)
    }

    // ── 2. the first call ───────────────────────────────────────────────────

    override val callState: StateFlow<GraineCallInfo> get() = Graine.calls.state
    override val callOutcomes: Flow<GraineCallOutcome> get() = Graine.calls.outcomes

    /** `Graine.calls.startCall(reason, agent = "consultation")` — the call's id, or null when another call is live. */
    override fun startCall(reason: String, agent: JourneyAgent): String? {
        if (sdk(agent) == null) return null
        return Graine.calls.startCall(reason = reason, agent = agent.sdkName)
    }

    override fun answer() = Graine.calls.answer()
    override fun decline() = Graine.calls.decline()
    override fun end() = Graine.calls.end()
    override fun setMuted(muted: Boolean) = Graine.calls.setMuted(muted)
    override fun setSpeaker(on: Boolean) = Graine.calls.setSpeaker(on)

    override fun schedulingRules(agent: JourneyAgent): GraineSchedulingRules = sdk(agent)?.schedulingRules ?: GraineSchedulingRules()

    private val notLoaded = MutableStateFlow(GraineBookingState(available = false, reason = "not_loaded"))

    /** Whether the agent can be booked now, and why not, as it changes (0.14.4). */
    override fun bookingState(agent: JourneyAgent): StateFlow<GraineBookingState> = sdk(agent)?.bookingState ?: notLoaded

    /** The times the agent's rules allow on [day], every 5 minutes: the app's picker shows a few of them. */
    override fun bookingSlots(day: LocalDate, agent: JourneyAgent): List<Instant> = sdk(agent)?.bookingSlots(day) ?: emptyList()

    /** `agent.scheduleCall(at, reason)`: Graine rings this phone then. Every refusal is a GraineException. */
    override suspend fun scheduleCall(at: Instant, reason: String, agent: JourneyAgent): GraineScheduledCall =
        guarded(agent) { it.scheduleCall(at, reason) }

    /** The agent's calls for this user, with their status: a ring missed while the app was closed reads Missed. */
    override suspend fun scheduledCalls(agent: JourneyAgent): List<GraineScheduledCall> = guarded(agent) { it.scheduledCalls() }

    override suspend fun cancelScheduledCall(id: String, agent: JourneyAgent): GraineCallStatus = guarded(agent) { it.cancelScheduledCall(id) }

    // ── 3. we couldn't connect ──────────────────────────────────────────────

    /** `Graine.calls.callBack()`: the user calls back the ring they missed. The new call's id, or null. */
    override fun callBack(): String? = Graine.calls.callBack()

    override fun dismissMissedCall() = Graine.calls.dismissMissedCall()

    // ── the chats ───────────────────────────────────────────────────────────

    private val idle = MutableStateFlow(GraineChatStatus.IDLE)

    override fun chatStatus(agent: JourneyAgent): StateFlow<GraineChatStatus> = sdk(agent)?.chat?.status ?: idle

    /** `chat.shown(true)` / `chat.shown(false)`, in pairs: the chat is connected while something shows it. */
    override fun chatShown(agent: JourneyAgent, visible: Boolean) {
        sdk(agent)?.chat?.shown(visible)
    }

    /** `chat.retry()` — never after a refusal no retry can lift (app_not_allowed, key_revoked, token_scope). */
    override fun chatRetry(agent: JourneyAgent) {
        if (GraineSecurity.refusedForGood(agent.sdkName)) return
        sdk(agent)?.chat?.retry()
    }

    // ── telling the coach ───────────────────────────────────────────────────

    /** `Graine.reportEvent(name, detail)` (0.14.4): the coach's live chat hears it and may speak first. Dropped with none live. */
    override fun reportEvent(name: String, detail: String?) {
        if (sdk(JourneyAgent.COACH) == null) return
        Graine.reportEvent(name, detail)
    }

    /** `Graine.track(name, data)`: informs without interrupting; the agent knows it the next time the user speaks. */
    override fun track(name: String, data: Map<String, Any?>) = Graine.track(name, data)

    /** `Graine.setScreen(...)`: what the user is looking at, for every agent. */
    override fun setScreen(screen: GraineScreen?) = Graine.setScreen(screen)

    // ── the plan's actions ──────────────────────────────────────────────────

    /** `agent.registerAction(name) { args -> … }`: arguments arrive as maps and lists; each answers at once (within 6 s). */
    override fun registerAction(agent: JourneyAgent, name: String, handler: (Map<String, Any?>) -> JourneyAnswer) {
        val graine = sdk(agent) ?: return
        registered += graine.registerAction(name) { args ->
            val answer = handler(args)
            when (answer.status) {
                JourneyAnswer.Status.OK -> GraineActionResult.ok(answer.message, answer.data)
                JourneyAnswer.Status.REFUSED -> GraineActionResult.refused(answer.message)
                JourneyAnswer.Status.ERROR -> GraineActionResult.error(answer.message)
            }
        }
    }

    override fun unregisterActions() {
        registered.forEach { it() }
        registered.clear()
    }

    // ── 6. privacy ──────────────────────────────────────────────────────────

    /** `Graine.memories()`: what the coach remembers about the user, newest first. */
    override suspend fun memories(): List<GraineMemory> = guarded(JourneyAgent.COACH) { it.memories() }

    /** `agent.forgetMemory()`: what that agent remembers about the user, and the conversation it kept, gone. */
    override suspend fun forgetMemory(agent: JourneyAgent): Int = guarded(agent) { it.forgetMemory() }

    /**
     * `chat.pastSessions`, loaded page by page (`refreshPastSessions`, then `loadMorePastSessions`) up to [max]: the
     * user's earlier chats and calls with the agent, newest first. Each page waits at most 10 seconds.
     */
    override suspend fun pastSessions(agent: JourneyAgent, max: Int): List<GraineSessionSummary> {
        val chat = need(agent).chat
        chat.refreshPastSessions()
        var state = withTimeoutOrNull(PAGE_WAIT_MS) { chat.pastSessions.first { !it.loading } }
            ?: throw GraineException(GraineErrorCode.Network, "The conversations did not load in time.")
        state.error?.let {
            if (state.sessions.isEmpty()) {
                val refused = GraineException(GraineErrorCode.from(it), "The conversations could not be read ($it).")
                GraineSecurity.noteRefusal(agent.sdkName, refused)
                throw refused
            }
        }
        while (state.hasMore && state.sessions.size < max) {
            val before = state.sessions.size
            chat.loadMorePastSessions()
            state = withTimeoutOrNull(PAGE_WAIT_MS) { chat.pastSessions.first { !it.loading && (it.sessions.size > before || !it.hasMore || it.error != null) } } ?: break
            if (state.error != null || state.sessions.size == before) break
        }
        return state.sessions.take(max)
    }

    /** `chat.sessionTranscript(id)`: one past session's messages, oldest first. */
    override suspend fun sessionTranscript(agent: JourneyAgent, id: String): GraineSessionTranscript =
        guarded(agent) { it.chat.sessionTranscript(id) }

    private companion object {
        const val PAGE_WAIT_MS = 10_000L
    }
}
