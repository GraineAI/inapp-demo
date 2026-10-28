package ai.graine.demo.graine

import ai.graine.demo.domain.AppConfig
import ai.graine.demo.domain.DemoActionResult
import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.DemoScreenContext
import ai.graine.demo.domain.DemoSignIn
import ai.graine.demo.domain.DemoTokens
import ai.graine.demo.domain.PastSessionsState
import ai.graine.demo.domain.ServerTokenStore
import ai.graine.demo.domain.SessionTranscript
import ai.graine.demo.journey.JourneyAgent
import ai.graine.demo.platform.Platform
import ai.graine.demo.security.AgentSecurity
import ai.graine.demo.security.GraineSecurity
import ai.graine.inapp.kmp.Graine
import ai.graine.inapp.kmp.GraineCallTransport
import ai.graine.inapp.kmp.GraineActionResult
import ai.graine.inapp.kmp.GraineAgent
import ai.graine.inapp.kmp.GraineBookingState
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

    // Who the user is, and memory (shared by every agent) — through security/GraineSecurity.kt
    /**
     * Sign-in (and every launch while someone is signed in): who the user is — [userId] rides in [traits] under the
     * pack's field and under user_id — proven by their identity token (GraineSecurity.signIn), with their memory choice.
     */
    fun signIn(userId: String, traits: Map<String, String>, memory: Boolean?)
    /** Sign out everywhere (GraineSecurity.signOut): this device stops being the user's; later conversations are anonymous. */
    fun signOut()
    /**
     * Waits until the sign-in has identified the user with their token — at most 10 s after signIn — and returns at once
     * when nothing waits (GraineSecurity.awaitSignIn): a call or the voice bar starts after it. A fake never waits.
     */
    suspend fun awaitSignIn() {}

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
    /**
     * Whether [agent] can be booked from the app now, and why not, AS IT CHANGES — the SDK's `bookingState` (0.14.4):
     * read again by the SDK when the agent's session arrives, after identify and setMemory, and when the session
     * could not load. Collect it, and draw every booking button, hint and time list from it. Never read the answer
     * once at launch: until the session has arrived it is `not_loaded`, which means "not yet", not "no".
     */
    fun bookingState(agent: DemoAgent): StateFlow<GraineBookingState>
    /** The SDK's booking sheet, in the agent's design, with [reason] filled in. The booked call, or null when closed without booking. */
    suspend fun presentBooking(reason: String?, agent: DemoAgent): GraineScheduledCall?

    // Booking drawn by the app itself ("Your own design"): the SDK's rules and times, and its scheduleCall
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

    // Your app and your users, and server tokens: what Graine has proven, per agent (security/GraineSecurity.kt)
    /** Every configured agent's security, as it changes — Profile's Security card, the Help screen's note. */
    val security: StateFlow<List<AgentSecurity>> get() = NO_AGENTS
    /** This install's debug token — in a debug build only (GraineSecurity.debugToken); null in a release build. */
    val debugToken: String? get() = null

    // What the SDK reports
    var onSessionReady: (() -> Unit)?
    var onCallEvent: ((CallEvent) -> Unit)?
}

/** No agent configured: what a client without the SDK (the tests' fake) reports. */
private val NO_AGENTS: StateFlow<List<AgentSecurity>> = MutableStateFlow(emptyList())

/**
 * THE ONE FILE THAT CALLS THE GRAINE SDK — `ai.graine:inapp-kmp`, from shared
 * code, on Android and iOS alike — except security: who the user is and their
 * proof, the app's proof, server tokens and refusals are all in
 * security/GraineSecurity.kt, which this file calls.
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
                // A call that could not start for the app, the user, the server token or the key says why with the code first.
                if (o.outcome == GraineCallOutcome.Kind.FAILED) GraineSecurity.noteRefusalText(o.agent ?: Graine.DEFAULT_AGENT, o.reason)
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

    /**
     * One of [agent]'s requests, once the sign-in has identified the user: a refusal about security is noted — the
     * Security card, the log — and thrown on as it came, for the screen to say in plain words (CallText.message). No
     * retry of the app's own: the SDK did its one.
     */
    private suspend fun <T> guarded(agent: DemoAgent, request: suspend (GraineAgent) -> T): T {
        val graine = sdk(agent) ?: throw noAgent()
        // Never before the sign-in's identify: the request carries who the user is, and their token.
        GraineSecurity.awaitSignIn()
        return try {
            request(graine).also { GraineSecurity.noteAnswered(agent.sdkName) }
        } catch (e: GraineException) {
            GraineSecurity.noteRefusal(agent.sdkName, e)
            throw e
        }
    }

    // ── who the user is, and memory (the SDK shares them with every agent) ──

    // Identified WITH the identity token — fetched before identify when none is in hand, 10 s at most (security/GraineSecurity.kt).
    override fun signIn(userId: String, traits: Map<String, String>, memory: Boolean?) = GraineSecurity.signIn(userId, traits, memory)

    // Every agent: the device stops being this user's, and later conversations are anonymous.
    override fun signOut() = GraineSecurity.signOut()

    override suspend fun awaitSignIn() = GraineSecurity.awaitSignIn()

    override fun setVariables(values: Map<String, String?>) = agents.forEach { sdk(it)?.setVariables(values) }
    override fun currentVariables(): Map<String, String> = Graine.currentVariables()

    override suspend fun requestCallPermissions() {
        Graine.calls.requestCallPermissions()
    }

    override fun setMemory(enabled: Boolean) = Graine.setMemory(enabled)

    override val installId: String get() = Graine.installId

    override suspend fun forgetMemory(): Int = guarded(DemoAgent.MAIN) { it.forgetMemory() }

    override suspend fun memories(): List<GraineMemory> = guarded(DemoAgent.MAIN) { it.memories() }

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

    /** An agent with no key never loads: "not yet" for as long as the app runs (the screens say how to add it). */
    private val notConfigured = MutableStateFlow(GraineBookingState(available = false, reason = "not_loaded"))

    override fun bookingState(agent: DemoAgent): StateFlow<GraineBookingState> = sdk(agent)?.bookingState ?: notConfigured

    // The day, the time, the time zone, "What's it about?" and its refusals are the sheet's own.
    override suspend fun presentBooking(reason: String?, agent: DemoAgent): GraineScheduledCall? =
        guarded(agent) { it.presentBooking(reason) }

    override suspend fun scheduledCalls(agent: DemoAgent): List<GraineScheduledCall> = guarded(agent) { it.scheduledCalls() }

    override suspend fun cancelScheduledCall(id: String, agent: DemoAgent): GraineCallStatus =
        guarded(agent) { it.cancelScheduledCall(id) }

    // Your own design: the same rules and times the SDK's sheet uses, read from its public state.
    override fun schedulingRules(agent: DemoAgent): GraineSchedulingRules = sdk(agent)?.schedulingRules ?: GraineSchedulingRules()

    override fun bookingSlots(day: LocalDate, agent: DemoAgent): List<Instant> = sdk(agent)?.bookingSlots(day) ?: emptyList()

    override suspend fun scheduleCall(at: Instant, reason: String?, agent: DemoAgent): GraineScheduledCall =
        guarded(agent) { it.scheduleCall(at, reason) }

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
        val state = sdk(agent)?.let { PastSessionsSdk.state(it, scope) } ?: return@getOrPut MutableStateFlow(PastSessionsState(error = "unavailable"))
        // The list's refusal is a word ("identity_unverified"): one about security is noted like any other request's.
        scope.launch { state.collect { s -> s.error?.let { GraineSecurity.noteRefusalText(agent.sdkName, it) } } }
        state
    }

    override fun refreshPastSessions(agent: DemoAgent) {
        sdk(agent)?.let { PastSessionsSdk.refresh(it) }
    }

    override fun loadMorePastSessions(agent: DemoAgent) {
        sdk(agent)?.let { PastSessionsSdk.loadMore(it) }
    }

    override suspend fun sessionTranscript(id: String, agent: DemoAgent): SessionTranscript =
        guarded(agent) { PastSessionsSdk.transcript(it, id) }

    override fun chatCloseDelaySeconds(agent: DemoAgent): Int = sdk(agent)?.chat?.closeDelaySeconds ?: CHAT_CLOSE_DELAY_SECONDS

    // ── your app and your users, and server tokens (security/GraineSecurity.kt) ──

    override val security: StateFlow<List<AgentSecurity>> get() = GraineSecurity.statuses

    override val debugToken: String? get() = GraineSecurity.debugToken

    override fun startNativeCall(reason: String, variables: Map<String, String>, agent: DemoAgent): String? {
        if (sdk(agent) == null) return null
        return Graine.calls.startCall(reason = reason, variables = variables, agent = agent.sdkName)
    }

    companion object {
        private var instance: GraineService? = null

        /** How long a chat session stays open after the chat screen closes, in seconds (GraineConfig.chatCloseDelaySeconds). */
        const val CHAT_CLOSE_DELAY_SECONDS = 15

        /**
         * Configure the agents and the calls. Once per launch. The help desk and the Journey's first-call agent are
         * optional: without a key — or if one cannot be started, which the log says — its screens say so and
         * everything else works. [debugBuild]: a debug build, which shows its debug token on Profile.
         */
        fun start(config: AppConfig, callName: String, appVersion: String, debug: Boolean, debugBuild: Boolean): GraineService {
            instance?.let { return it }
            // DEMO ONLY — server tokens from a stand-in for your server (Profile → Server token (demo), domain/ServerToken.kt).
            // Off by default: no agent gets a tokenProvider, and no token is ever sent.
            val tokens = ServerTokenStore.load(Platform.store())
            fun demoTokens(key: String, main: Boolean): (suspend () -> String?)? =
                if (!main && !tokens.mode.everyAgent) null
                else DemoTokens.provider(
                    tokens, config.baseUrl, key, user = { GraineSecurity.userId },
                    get = { Platform.httpGet(it) }, post = { url, json -> Platform.httpPost(url, json) },
                )
            // Each agent: its own publishable key — and, when server tokens are on, its own tokenProvider. A chat session
            // closes this long after its screen closes (the SDK's default, set here so it is plain to see).
            fun agent(name: String, key: String, main: Boolean = false) = GraineSecurity.Agent(
                name,
                GraineConfig(
                    baseUrl = config.baseUrl,
                    publishableKey = key,
                    appVersion = appVersion,
                    debug = debug,
                    chatCloseDelaySeconds = CHAT_CLOSE_DELAY_SECONDS,
                    tokenProvider = demoTokens(key, main),
                ),
            )
            GraineSecurity.start(
                agents = buildList {
                    add(agent(Graine.DEFAULT_AGENT, config.publishableKey, main = true))
                    if (config.hasHelpKey) add(agent(DemoAgent.HELP.sdkName, config.helpPublishableKey))
                    // The Journey's first-call agent (a flow agent): its first call, booked or now, and its chat version.
                    if (config.hasConsultationKey) add(agent(JourneyAgent.FIRST_CALL.sdkName, config.consultationPublishableKey))
                },
                // DEMO ONLY — your sign-in hands you the user's ID token (Firebase: user.getIdToken(false)). This demo has no
                // sign-in, so Graine's demo sign-in server signs one for the person picked (domain/DemoSignIn.kt).
                identityTokens = { userId ->
                    DemoSignIn.fetch(config.baseUrl, config.publishableKey, userId) { url, json -> Platform.httpPost(url, json) }
                },
                debugBuild = debugBuild,
                log = { Platform.log(it) },
            )
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
