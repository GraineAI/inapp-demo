package ai.graine.demo.graine

import ai.graine.demo.R
import ai.graine.demo.domain.AppConfig
import ai.graine.demo.domain.DemoActionResult
import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.DemoScreenContext
import ai.graine.inapp.ActionResult
import ai.graine.inapp.GraineAgentBarView
import ai.graine.inapp.GraineCall
import ai.graine.inapp.GraineCallListener
import ai.graine.inapp.GraineCallOutcome
import ai.graine.inapp.GraineCallStatus
import ai.graine.inapp.GraineCallsConfig
import ai.graine.inapp.GraineChatView
import ai.graine.inapp.GraineConfig
import ai.graine.inapp.GraineError
import ai.graine.inapp.GraineErrorCode
import ai.graine.inapp.GraineField
import ai.graine.inapp.GraineInApp
import ai.graine.inapp.GraineListener
import ai.graine.inapp.GraineMemory
import ai.graine.inapp.GraineScheduledCall
import ai.graine.inapp.GraineScreen
import ai.graine.inapp.SessionConfig
import android.app.Activity
import android.app.Application
import android.content.Context
import android.view.View
import org.json.JSONObject

/** What the SDK reported about a native call: incoming, answered, declined, missed, ended or failed. [agent]: whose call, when known. */
data class CallEvent(val callId: String, val event: String, val detail: String?, val agent: DemoAgent? = null)

/**
 * EVERYTHING THE APP ASKS OF THE GRAINE SDK. The scheduling and memory types
 * are the SDK's own (GraineScheduledCall, GraineMemory, GraineError): booking
 * — the sheet, its rules and its checks — and status logic live in the SDK,
 * and the app only presents them.
 *
 * Two agents: what belongs to one agent (its chat, voice, calls, actions and
 * scheduled calls) takes [DemoAgent]; what the SDK shares between them (who the
 * person is, the memory choice, the screen) does not. The real implementation
 * is [GraineService]; the tests use a fake. Main thread; every callback
 * arrives on the main thread.
 */
interface GraineClient {
    /** The agents configured: the main one always, the help desk when its key is set. */
    val agents: List<DemoAgent>

    // Who the user is, and memory (shared by every agent)
    fun identify(traits: Map<String, String>?, memory: Boolean?)
    fun setMemory(enabled: Boolean)
    val installId: String
    /** [done] hears how many memories were deleted (the main agent's memory), or why not. */
    fun forgetMemory(done: (Int?, GraineError?) -> Unit)
    /** What the main agent remembers about the signed-in user, newest first. */
    fun memories(done: (List<GraineMemory>?, GraineError?) -> Unit)
    /** The main agent's memory switch, from its session. Null until the session has said. */
    val agentMemoryEnabled: Boolean?

    // Screen context (shared) and actions (per agent)
    fun setScreen(context: DemoScreenContext?)
    fun registerAction(name: String, agent: DemoAgent, handler: (Map<String, Any?>) -> DemoActionResult)
    fun unregisterActions()

    // Scheduled calls, per agent
    /** The agent can be booked from the app now (the SDK's bookingAvailable). Read again after sign-in and when a session arrives. */
    fun bookingAvailable(agent: DemoAgent): Boolean
    /**
     * The SDK's booking sheet for the agent over [activity], its "What's it about?" starting with [reason].
     * [done] hears the call booked, or null when it was closed without booking.
     */
    fun presentBooking(activity: Activity, reason: String?, agent: DemoAgent, done: (GraineScheduledCall?) -> Unit)
    fun scheduledCalls(agent: DemoAgent, done: (List<GraineScheduledCall>?, GraineError?) -> Unit)
    /** [done] hears the call's status afterwards (cancelled), or why not. */
    fun cancelScheduledCall(id: String, agent: DemoAgent, done: (GraineCallStatus?, GraineError?) -> Unit)

    // Voice in the bar, chat, and native calls, per agent
    fun startVoice(agent: DemoAgent)
    fun endVoice()
    /** Send a message in that agent's chat (it connects if it is not connected). */
    fun sendChatMessage(text: String, agent: DemoAgent)
    /** The name a ringing call shows when the push names none. */
    fun setCallName(name: String)
    /** The call's id, or null when another call is live. Needs the activity in front. */
    fun startNativeCall(activity: Activity, reason: String, variables: Map<String, String>, agent: DemoAgent): String?

    // What the SDK reports
    var onSessionReady: (() -> Unit)?
    var onCallEvent: ((CallEvent) -> Unit)?
}

/**
 * THE ONE FILE THAT CALLS THE GRAINE SDK.
 *
 * Every SDK call the app makes goes through here, behind [GraineClient], so
 * the tests run against a fake and moving to a new SDK version means reading
 * one file. Scheduling and memory values are the SDK's own types, passed
 * through unchanged: their rules live in the SDK, not in the app.
 *
 * Two agents, each with its own publishable key: the main one is the SDK's
 * default agent, the help desk is initialised under the name "help" when its
 * key is set. Calls are one engine for the whole app.
 *
 * [start] runs in Application.onCreate on every launch: a push that wakes a
 * closed app can ring only once the SDK and its calls are set up.
 */
class GraineService private constructor(private val main: GraineInApp, private val help: GraineInApp?) : GraineClient {
    private val registered = mutableListOf<() -> Unit>()
    override var onSessionReady: (() -> Unit)? = null
    override var onCallEvent: ((CallEvent) -> Unit)? = null

    // Held here: the SDK keeps listeners in a set, and these live as long as the app.
    private val sessionListener = object : GraineListener {
        override fun onSessionReady(session: SessionConfig) {
            onSessionReady?.invoke()
        }
    }

    private val callListener = object : GraineCallListener {
        override fun onIncomingCall(call: GraineCall) {
            onCallEvent?.invoke(CallEvent(call.callId, "incoming", if (call.hosted) "scheduled" else null, agentOf(call.callId)))
        }

        override fun onCallOutcome(callId: String, outcome: GraineCallOutcome) {
            val detail = outcome.endReason?.wire ?: outcome.error
            onCallEvent?.invoke(CallEvent(callId, outcome.wire, detail, agentOf(callId)))
        }
    }

    /**
     * The agent a call is with, as the SDK says: `calls.callAgent`, the agent it routed a ring to (by the
     * push's agent) or placed the call with. Read for the call in progress only — an outcome reaches the app
     * before the call is let go of; a ring refused while another call is live is not that call.
     */
    private fun agentOf(callId: String): DemoAgent? =
        main.calls.callAgent?.takeIf { main.calls.call?.callId == callId }?.let { DemoAgent.ofSdkName(it.name) }

    init {
        main.addListener(sessionListener)
        help?.addListener(sessionListener)
        main.calls.addListener(callListener)
    }

    override val agents: List<DemoAgent> get() = if (help == null) listOf(DemoAgent.MAIN) else listOf(DemoAgent.MAIN, DemoAgent.HELP)

    private fun sdk(agent: DemoAgent): GraineInApp? = if (agent == DemoAgent.MAIN) main else help

    private val noAgent = GraineError(GraineErrorCode.INVALID_KEY, "No publishable key is set for this agent.")

    // ── who the user is, and memory (the SDK shares them with every agent) ──

    override fun identify(traits: Map<String, String>?, memory: Boolean?) {
        // null signs out everywhere: the device stops being this user's, and later conversations are anonymous.
        main.identify(traits, memory)
    }

    override fun setMemory(enabled: Boolean) = main.setMemory(enabled)

    override val installId: String get() = main.installId

    override fun forgetMemory(done: (Int?, GraineError?) -> Unit) {
        main.forgetMemory { deleted, error -> done(deleted, error) }
    }

    override fun memories(done: (List<GraineMemory>?, GraineError?) -> Unit) {
        main.memories { list, error -> done(list, error) }
    }

    override val agentMemoryEnabled: Boolean? get() = main.session?.memoryEnabled

    // ── screen context and actions ──────────────────────────────────────────

    override fun setScreen(context: DemoScreenContext?) {
        // One screen for the whole app: the SDK hands it to every agent.
        main.setScreen(context?.let { c ->
            GraineScreen(
                screen = c.screen,
                title = c.title,
                fields = c.fields.map { f ->
                    GraineField(f.name, f.label, f.value, f.status?.let { s -> GraineField.Status.values().firstOrNull { it.wire == s } })
                },
            )
        })
    }

    override fun registerAction(name: String, agent: DemoAgent, handler: (Map<String, Any?>) -> DemoActionResult) {
        val graine = sdk(agent) ?: return
        // The SDK runs handlers on the main thread.
        registered += graine.registerAction(name) { args ->
            val result = handler(args.toMap())
            when (result.status) {
                DemoActionResult.Status.OK -> ActionResult.ok(result.message)
                DemoActionResult.Status.REFUSED -> ActionResult.refused(result.message)
                DemoActionResult.Status.ERROR -> ActionResult.error(result.message)
            }
        }
    }

    override fun unregisterActions() {
        registered.forEach { it() }
        registered.clear()
    }

    // ── scheduled calls ─────────────────────────────────────────────────────

    override fun bookingAvailable(agent: DemoAgent): Boolean = sdk(agent)?.bookingAvailable ?: false

    override fun presentBooking(activity: Activity, reason: String?, agent: DemoAgent, done: (GraineScheduledCall?) -> Unit) {
        val graine = sdk(agent) ?: return done(null)
        // The SDK draws the sheet over the activity, in the agent's design, and books it.
        graine.presentBooking(activity, reason) { call, _ -> done(call) }
    }

    override fun scheduledCalls(agent: DemoAgent, done: (List<GraineScheduledCall>?, GraineError?) -> Unit) {
        val graine = sdk(agent) ?: return done(null, noAgent)
        graine.scheduledCalls { calls, error -> done(calls, error) }
    }

    override fun cancelScheduledCall(id: String, agent: DemoAgent, done: (GraineCallStatus?, GraineError?) -> Unit) {
        val graine = sdk(agent) ?: return done(null, noAgent)
        graine.cancelScheduledCall(id) { status, error -> done(status, error) }
    }

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

    override fun setCallName(name: String) {
        main.calls.configure(GraineCallsConfig(displayName = name, smallIcon = R.drawable.ic_stat_call, ringTimeoutMs = 45_000))
    }

    override fun startNativeCall(activity: Activity, reason: String, variables: Map<String, String>, agent: DemoAgent): String? {
        if (sdk(agent) == null) return null
        return main.calls.startCall(activity, reason = reason, variables = variables, agent = agent.sdkName)
    }

    // ── the SDK's own views, and the activity's plumbing ────────────────────

    /** Mount the voice engine on the first activity (it follows the user from activity to activity). */
    fun attachVoice(activity: Activity) = main.voice.attach(activity)

    /** That agent's chat screen, drawn in its design, with its components. */
    fun chatView(context: Context, agent: DemoAgent, onClose: () -> Unit): View = GraineChatView(context).apply {
        sdk(agent)?.let { source = it.chat }
        this.onClose = onClose
    }

    /** That agent's voice bar: voice only here (the chat has its own screen). */
    fun voiceBar(context: Context, agent: DemoAgent, name: String, onClose: () -> Unit): View = GraineAgentBarView(context).apply {
        sdk(agent)?.let { graine = it }
        this.name = name
        chat = null
        this.onClose = onClose
    }

    /** The microphone prompts the voice and the calls asked for. */
    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
        val voiceTookIt = agents.any { sdk(it)?.voice?.onRequestPermissionsResult(requestCode, grantResults) == true }
        if (!voiceTookIt) main.calls.onRequestPermissionsResult(requestCode, grantResults)
    }

    companion object {
        @Volatile private var instance: GraineService? = null

        /** Configure the agents and the calls. Once, in Application.onCreate. */
        fun start(app: Application, config: AppConfig, callName: String, appVersion: String, debug: Boolean): GraineService {
            instance?.let { return it }
            fun agentConfig(key: String) = GraineConfig(baseUrl = config.baseUrl, publishableKey = key, appVersion = appVersion, debug = debug)
            val main = GraineInApp.init(app, agentConfig(config.publishableKey))
            val help = if (config.hasHelpKey) GraineInApp.init(app, agentConfig(config.helpPublishableKey), DemoAgent.HELP.sdkName) else null
            return GraineService(main, help).also {
                it.setCallName(callName)
                instance = it
            }
        }

        fun get(): GraineService = instance ?: error("GraineService.start has not run (Application.onCreate)")

        private fun JSONObject.toMap(): Map<String, Any?> =
            keys().asSequence().associateWith { k -> opt(k).takeUnless { it == JSONObject.NULL } }
    }
}
