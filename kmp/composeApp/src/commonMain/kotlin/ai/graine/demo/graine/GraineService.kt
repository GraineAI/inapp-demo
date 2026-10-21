package ai.graine.demo.graine

import ai.graine.demo.domain.AppConfig
import ai.graine.demo.domain.DemoActionResult
import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.DemoScreenContext
import ai.graine.demo.domain.PastSessionsState
import ai.graine.demo.domain.SessionTranscript
import ai.graine.demo.platform.Platform
import ai.graine.inapp.kmp.Graine
import ai.graine.inapp.kmp.GraineCallTransport
import ai.graine.inapp.kmp.GraineActionResult
import ai.graine.inapp.kmp.GraineAgent
import ai.graine.inapp.kmp.GraineCallConnection
import ai.graine.inapp.kmp.GraineCallOutcome
import ai.graine.inapp.kmp.GraineCallStatus
import ai.graine.inapp.kmp.GraineCallsConfig
import ai.graine.inapp.kmp.GraineConfig
import ai.graine.inapp.kmp.GraineErrorCode
import ai.graine.inapp.kmp.GraineException
import ai.graine.inapp.kmp.GraineField
import ai.graine.inapp.kmp.GraineFieldStatus
import ai.graine.inapp.kmp.GraineMemory
import ai.graine.inapp.kmp.GraineScheduledCall
import ai.graine.inapp.kmp.GraineSchedulingRules
import ai.graine.inapp.kmp.GraineScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * What the SDK reported about a native call: placed, incoming, answered, declined, missed, ended or failed. [agent]:
 * whose call, when known. An ended or failed call's outcome also carries how long it was connected and how it
 * connected ([connection]: timings and network figures only), which the app's own call screen shows once it is over.
 */
data class CallEvent(
    val callId: String,
    val event: String,
    val detail: String?,
    val agent: DemoAgent? = null,
    val durationSeconds: Int? = null,
    val connection: GraineCallConnection? = null,
    /** Who or what ended it: local_end, remote_bye, media_drop, audio_watch, failed, provider_reset. */
    val endCause: String? = null,
)

/**
 * EVERYTHING THE APP ASKS OF THE GRAINE SDK. The scheduling and memory types
 * are the SDK's own (GraineScheduledCall, GraineMemory, GraineException): the
 * booking sheet, its rules and refusals, and status logic live in the SDK, and
 * the app only presents what it returns.
 *
 * Two agents: what belongs to one agent (its chat, voice, actions and
 * scheduled calls) takes [DemoAgent]; what the SDK shares between them (who
 * the person is, the memory choice, the screen) does not. The real
 * implementation is [GraineService]; the tests use a fake. Callbacks arrive on
 * the main thread.
 */
interface GraineClient {
    /** The agents configured: the main one always, the help desk when its key is set. */
    val agents: List<DemoAgent>

    // Who the user is, and memory (shared by every agent)
    fun identify(traits: Map<String, String>?, memory: Boolean?)

    /**
     * The agents' prompt {variables} — what an API call sends as `variables` — set through the SDK, for
     * every agent: merged into each chat, voice session and call from now on; null under a key removes it.
     */
    fun setVariables(values: Map<String, String?>)

    /** Every variable the main agent will receive with the next conversation, as the SDK holds them (currentVariables). */
    fun currentVariables(): Map<String, String>

    /** The SDK asks for what calls need — the microphone, and notifications on Android 13+ — at sign-in. */
    suspend fun requestCallPermissions() {}
    fun setMemory(enabled: Boolean)
    val installId: String
    /** How many memories were deleted (the main agent's memory). */
    suspend fun forgetMemory(): Int
    /** What the main agent remembers about the signed-in user, newest first. */
    suspend fun memories(): List<GraineMemory>
    /** The main agent's memory switch, from its session. Null until the session has said. */
    val agentMemoryEnabled: Boolean?

    // Screen context (shared) and actions (per agent)
    fun setScreen(context: DemoScreenContext?)
    fun registerAction(name: String, agent: DemoAgent, handler: (Map<String, Any?>) -> DemoActionResult)
    fun unregisterActions()

    // Booking and scheduled calls, per agent
    /** The SDK's own rule: Graine rings this phone, the agent lets users book, and the user is identified. Read as it is now. */
    fun bookingAvailable(agent: DemoAgent): Boolean
    /** The SDK's booking sheet, in the agent's design, with [reason] filled in. The booked call, or null when closed without booking. */
    suspend fun presentBooking(reason: String?, agent: DemoAgent): GraineScheduledCall?

    // Booking drawn by the app itself ("Your own design"): the SDK's rule, rules and times, and its scheduleCall
    /** Why [agent] cannot be booked now, as the SDK's word (`not_loaded`, `rings_not_set_up`, …); null when it can. */
    fun bookingUnavailableReason(agent: DemoAgent): String?
    /** What [agent] allows when a user books: lead time, days ahead, calls pending. */
    fun schedulingRules(agent: DemoAgent): GraineSchedulingRules
    /** The times the SDK's own sheet would offer on [day] (every 5 minutes inside the rules), for a picker of the app's own. */
    fun bookingSlots(day: LocalDate, agent: DemoAgent): List<Instant>
    /** Book [at] with [agent]; the SDK checks the agent's rules first, then Graine. Every refusal is a GraineException. */
    suspend fun scheduleCall(at: Instant, reason: String?, agent: DemoAgent): GraineScheduledCall
    suspend fun scheduledCalls(agent: DemoAgent): List<GraineScheduledCall>
    /** The call's status afterwards (cancelled). */
    suspend fun cancelScheduledCall(id: String, agent: DemoAgent): GraineCallStatus

    // Voice in the bar, chat, and native calls, per agent
    fun startVoice(agent: DemoAgent)
    fun endVoice()
    /** Send a message in that agent's chat (it connects if it is not connected). */
    fun sendChatMessage(text: String, agent: DemoAgent)
    /** The name a ringing call shows when the push names none. */
    fun setCallName(name: String)
    /** The call's id, or null when another call is live. */
    fun startNativeCall(reason: String, variables: Map<String, String>, agent: DemoAgent): String?

    /**
     * "Your own design": the app draws the call screen itself. iOS draws it in place; on Android the SDK opens a
     * call screen for every call, so the app names its own (GraineCallsConfig.androidCallActivity).
     */
    fun useOwnCallScreen(own: Boolean)

    // Past sessions: the signed-in user's earlier sessions with an agent, newest first, and one session's transcript
    /** The list as the SDK holds it (chat.pastSessions): the sessions loaded so far, loading, more to load, the last error. */
    fun pastSessions(agent: DemoAgent): StateFlow<PastSessionsState>
    /** Load the first page again (chat.refreshPastSessions). */
    fun refreshPastSessions(agent: DemoAgent)
    /** Load the next page (chat.loadMorePastSessions). */
    fun loadMorePastSessions(agent: DemoAgent)
    /** One session's lines, oldest first (chat.sessionTranscript); `not_found` for one that is not this user's. */
    suspend fun sessionTranscript(id: String, agent: DemoAgent): SessionTranscript
    /** How long [agent]'s chat stays open once no screen shows it (chat.closeDelaySeconds; set at launch with GraineConfig.chatCloseDelaySeconds). */
    fun chatCloseDelaySeconds(agent: DemoAgent): Int

    // What the SDK reports
    var onSessionReady: (() -> Unit)?
    var onCallEvent: ((CallEvent) -> Unit)?
}

/**
 * THE ONE FILE THAT CALLS THE GRAINE SDK — `ai.graine:inapp-kmp`, from shared
 * code, on Android and iOS alike.
 *
 * Two agents, each with its own publishable key: the main one is the SDK's
 * default agent, the help desk is configured under the name "help" when its
 * key is set. Calls are one engine for the whole app. [start] runs at every
 * launch (Application.onCreate on Android, the app's start on iOS): a push
 * that wakes a closed app can ring only once the SDK and its calls are set up.
 */
class GraineService private constructor() : GraineClient {
    private val registered = mutableListOf<() -> Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    override var onSessionReady: (() -> Unit)? = null
    override var onCallEvent: ((CallEvent) -> Unit)? = null

    init {
        for (agent in agents) {
            val sdk = sdk(agent) ?: continue
            scope.launch { sdk.session.filterNotNull().collect { onSessionReady?.invoke() } }
        }
        // Each ring and outcome says which agent the call is with (`agent`: the name it was configured under).
        scope.launch {
            Graine.calls.incoming.collect { call ->
                onCallEvent?.invoke(CallEvent(call.callId, "incoming", if (call.hosted) "scheduled" else null, DemoAgent.ofSdkName(call.agent)))
            }
        }
        // The audio path each call took — WebRTC, or the standard connection with the SDK's reason — once a call.
        scope.launch {
            var reported: String? = null
            Graine.calls.state.collect { info ->
                val id = info.callId ?: return@collect
                val transport = info.transport ?: return@collect
                if (reported == id) return@collect
                reported = id
                val detail = transport.wire + (info.transportFallbackReason?.let { ":$it" } ?: "")
                onCallEvent?.invoke(CallEvent(id, "audio", detail, DemoAgent.ofSdkName(info.agent)))
                // And into the conversation itself: Call History shows it in this call's timeline, as it shows
                // the voice bar's own "transport" line. A tracked event is listed by its name, so the name says it.
                Graine.track(
                    if (transport == GraineCallTransport.WEBRTC) "call audio: WebRTC"
                    else "call audio: standard connection" + (info.transportFallbackReason?.let { " ($it)" } ?: ""),
                )
            }
        }
        scope.launch {
            Graine.calls.outcomes.collect { o ->
                // The ring itself is the "incoming" line already.
                if (o.outcome != GraineCallOutcome.Kind.RINGING) {
                    onCallEvent?.invoke(CallEvent(o.callId, o.outcome.wire, o.reason, DemoAgent.ofSdkName(o.agent), o.durationSeconds, o.connection, o.endCause))
                }
            }
        }
    }

    override val agents: List<DemoAgent> get() = if (Graine.agent(DemoAgent.HELP.sdkName) != null) listOf(DemoAgent.MAIN, DemoAgent.HELP) else listOf(DemoAgent.MAIN)

    private fun sdk(agent: DemoAgent): GraineAgent? = Graine.agent(agent.sdkName)

    private fun noAgent() = GraineException(GraineErrorCode.InvalidKey, "No publishable key is set for this agent.")

    // ── who the user is, and memory (the SDK shares them with every agent) ──

    // null signs out everywhere: the device stops being this user's, and later conversations are anonymous.
    override fun identify(traits: Map<String, String>?, memory: Boolean?) = Graine.identify(traits, memory)
    override fun setVariables(values: Map<String, String?>) = agents.forEach { sdk(it)?.setVariables(values) }
    override fun currentVariables(): Map<String, String> = Graine.currentVariables()

    override suspend fun requestCallPermissions() {
        Graine.calls.requestCallPermissions()
    }

    override fun setMemory(enabled: Boolean) = Graine.setMemory(enabled)

    override val installId: String get() = Graine.installId

    override suspend fun forgetMemory(): Int = Graine.forgetMemory()

    override suspend fun memories(): List<GraineMemory> = Graine.memories()

    override val agentMemoryEnabled: Boolean? get() = Graine.session.value?.memoryEnabled

    // ── screen context and actions ──────────────────────────────────────────

    override fun setScreen(context: DemoScreenContext?) {
        // One screen for the whole app: the SDK hands it to every agent.
        Graine.setScreen(context?.let { c ->
            GraineScreen(
                screen = c.screen,
                title = c.title,
                fields = c.fields.map { f ->
                    GraineField(f.name, f.label, f.value, f.status?.let { s -> GraineFieldStatus.entries.firstOrNull { it.wire == s } })
                },
            )
        })
    }

    override fun registerAction(name: String, agent: DemoAgent, handler: (Map<String, Any?>) -> DemoActionResult) {
        val graine = sdk(agent) ?: return
        // The SDK runs handlers on the main thread; these answer at once.
        registered += graine.registerAction(name) { args ->
            val result = handler(args)
            when (result.status) {
                DemoActionResult.Status.OK -> GraineActionResult.ok(result.message)
                DemoActionResult.Status.REFUSED -> GraineActionResult.refused(result.message)
                DemoActionResult.Status.ERROR -> GraineActionResult.error(result.message)
            }
        }
    }

    override fun unregisterActions() {
        registered.forEach { it() }
        registered.clear()
    }

    // ── booking and scheduled calls ─────────────────────────────────────────

    override fun bookingAvailable(agent: DemoAgent): Boolean = sdk(agent)?.bookingAvailable ?: false

    // The day, the time, the time zone, "What's it about?" and its refusals are the sheet's own.
    override suspend fun presentBooking(reason: String?, agent: DemoAgent): GraineScheduledCall? =
        (sdk(agent) ?: throw noAgent()).presentBooking(reason)

    override suspend fun scheduledCalls(agent: DemoAgent): List<GraineScheduledCall> = (sdk(agent) ?: throw noAgent()).scheduledCalls()

    override suspend fun cancelScheduledCall(id: String, agent: DemoAgent): GraineCallStatus =
        (sdk(agent) ?: throw noAgent()).cancelScheduledCall(id)

    // Your own design: the same rule, rules and times the SDK's sheet uses, read from its public state.
    // Null is the SDK's "it can be booked": only an agent that is not configured has no answer of its own.
    override fun bookingUnavailableReason(agent: DemoAgent): String? {
        val graine = sdk(agent) ?: return "not_loaded"
        return graine.bookingUnavailableReason
    }

    override fun schedulingRules(agent: DemoAgent): GraineSchedulingRules = sdk(agent)?.schedulingRules ?: GraineSchedulingRules()

    override fun bookingSlots(day: LocalDate, agent: DemoAgent): List<Instant> = sdk(agent)?.bookingSlots(day) ?: emptyList()

    override suspend fun scheduleCall(at: Instant, reason: String?, agent: DemoAgent): GraineScheduledCall =
        (sdk(agent) ?: throw noAgent()).scheduleCall(at, reason)

    // ── voice in the bar, chat, and native calls ────────────────────────────

    override fun startVoice(agent: DemoAgent) {
        sdk(agent)?.voice?.start()
    }

    override fun endVoice() {
        agents.forEach { sdk(it)?.voice?.end() }
    }

    override fun sendChatMessage(text: String, agent: DemoAgent) {
        sdk(agent)?.chat?.send(text)
    }

    private var callName: String = ""
    private var ownCallScreen = false

    /** How calls ring and show: the pack's call name; with "Your own design", the app's own call screen (Android). */
    private fun callsConfig() = GraineCallsConfig(
        displayName = callName,
        ringTimeoutSeconds = 45,
        smallIcon = Platform.callIcon,
        androidCallActivity = Platform.ownCallActivity.takeIf { ownCallScreen },
    )

    override fun setCallName(name: String) {
        callName = name
        Graine.calls.configure(callsConfig())
    }

    override fun useOwnCallScreen(own: Boolean) {
        if (own == ownCallScreen) return
        ownCallScreen = own
        Graine.calls.configure(callsConfig())
    }

    // ── past sessions (PastSessionsSdk reads the SDK's state) ──────────────

    private val pastSessionFlows = mutableMapOf<DemoAgent, StateFlow<PastSessionsState>>()

    override fun pastSessions(agent: DemoAgent): StateFlow<PastSessionsState> = pastSessionFlows.getOrPut(agent) {
        sdk(agent)?.let { PastSessionsSdk.state(it, scope) } ?: MutableStateFlow(PastSessionsState(error = "unavailable"))
    }

    override fun refreshPastSessions(agent: DemoAgent) {
        sdk(agent)?.let { PastSessionsSdk.refresh(it) }
    }

    override fun loadMorePastSessions(agent: DemoAgent) {
        sdk(agent)?.let { PastSessionsSdk.loadMore(it) }
    }

    override suspend fun sessionTranscript(id: String, agent: DemoAgent): SessionTranscript =
        PastSessionsSdk.transcript(sdk(agent) ?: throw noAgent(), id)

    override fun chatCloseDelaySeconds(agent: DemoAgent): Int = sdk(agent)?.chat?.closeDelaySeconds ?: CHAT_CLOSE_DELAY_SECONDS

    override fun startNativeCall(reason: String, variables: Map<String, String>, agent: DemoAgent): String? {
        if (sdk(agent) == null) return null
        return Graine.calls.startCall(reason = reason, variables = variables, agent = agent.sdkName)
    }

    companion object {
        private var instance: GraineService? = null

        /** How long a chat session stays open after the chat screen closes, in seconds (GraineConfig.chatCloseDelaySeconds). */
        const val CHAT_CLOSE_DELAY_SECONDS = 15

        /**
         * Configure the agents and the calls. Once per launch. The help desk is
         * optional: without its key — or if it cannot be started, which the log
         * says — the Help screen says so and everything else works.
         */
        fun start(config: AppConfig, callName: String, appVersion: String, debug: Boolean): GraineService {
            instance?.let { return it }
            // A chat session closes this long after its screen closes (the SDK's default, set here so it is plain to see).
            fun agentConfig(key: String) = GraineConfig(
                baseUrl = config.baseUrl,
                publishableKey = key,
                appVersion = appVersion,
                debug = debug,
                chatCloseDelaySeconds = CHAT_CLOSE_DELAY_SECONDS,
            )
            Graine.configure(agentConfig(config.publishableKey))
            if (config.hasHelpKey) {
                runCatching { Graine.configure(agentConfig(config.helpPublishableKey), name = DemoAgent.HELP.sdkName) }
                    .onFailure { Platform.log("The help agent is not available here: ${it.message}") }
            }
            // iOS: receive the agents' calls as VoIP pushes (Android: the SDK's own messaging service rings them).
            Graine.calls.enableVoIPPushes()
            return GraineService().also {
                it.setCallName(callName)
                instance = it
            }
        }

        fun get(): GraineService = instance ?: error("GraineService.start has not run")
    }
}
