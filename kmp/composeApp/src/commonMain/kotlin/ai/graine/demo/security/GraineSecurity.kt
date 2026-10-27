package ai.graine.demo.security

import ai.graine.inapp.kmp.Graine
import ai.graine.inapp.kmp.GraineAgent
import ai.graine.inapp.kmp.GraineConfig
import ai.graine.inapp.kmp.GraineEvent
import ai.graine.inapp.kmp.GraineException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/*
 * ═══ GRAINE SECURITY — EVERY LINE OF IT IN THIS APP. COPY THIS FILE (and SecurityText.kt for the words). ═══
 *
 * Set up per agent in the dashboard: Agent → Embed & Widgets → Install → "Your app and your users" (six steps)
 * and "Server tokens". What each step needs from the app, and where it is:
 *
 *   1 Your apps .............. Nothing in code: the SDK says which app it is on every request. List the iOS bundle ID
 *                              and Team ID, and the Android package with the SHA-256 of EVERY certificate that signs
 *                              it (debug, upload, Google Play app signing).
 *   2 What we've seen ........ Nothing in code: the dashboard counts what the SDK sends.
 *   3 Prove it's your app .... iOS: the App Attest capability (iosApp/project.yml). Android: the Play Integrity
 *                              dependency (composeApp/build.gradle.kts). Here: start() proves the app at launch, and
 *                              debugToken is what a debug build sends instead (register it under Development).
 *   4 Prove who the user is .. Here: signIn() at sign-in AND at every launch — identify WITH the identity token: pass
 *                              the one you hold, or it is fetched before identify (2 s at most); awaitSignIn() before
 *                              a call or the voice bar; the refresher start() sets; signOut().
 *   5 Turn it on ............. Nothing new in code: a refusal arrives as the SDK's GraineErrorCode. noteRefusal()
 *                              keeps it for the Security card and logs it; SecurityRefusal says it to the user.
 *   6 Send setup to your developer — this file.
 *   Server tokens ............ start(): each agent's GraineConfig.tokenProvider asks YOUR server, which mints the
 *                              token with your secret API key (POST /v2/client-tokens). Never a secret in the app.
 *
 * EVERY AGENT WHOSE PUBLISHABLE KEY SHIPS IN THE APP has its own dashboard page — its own Your apps, proofs and
 * switches — and its own server tokens. [statuses] says, per agent, what Graine has proven.
 *
 * Main thread: start() from the app's launch, signIn()/signOut() from the UI; the SDK calls the refresher there too.
 */
object GraineSecurity {
    /** One agent the app talks to: the name it is configured under ([Graine.DEFAULT_AGENT] for the main one) and its configuration. */
    class Agent(val name: String, val config: GraineConfig)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var log: (String) -> Unit = {}
    private var debugBuild = false
    private var user = UserIdentity(scope, fetch = { null }, identify = { _, _, _ -> }, setIdentityToken = {}, log = {})

    private val tokens = MutableStateFlow<Map<String, ServerTokenState>>(emptyMap())
    private val refusals = MutableStateFlow<Map<String, SecurityRefusal>>(emptyMap())
    private val errors = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Agents whose last request met a refusal no retry can lift; cleared by the next request that is answered. */
    private val forGood = MutableStateFlow<Set<String>>(emptySet())
    private val all = MutableStateFlow<List<AgentSecurity>>(emptyList())

    /** What Graine has proven for every configured agent, as it changes: the Security card. */
    val statuses: StateFlow<List<AgentSecurity>> = all.asStateFlow()

    /** Who is signed in now — the user id [signIn] named — or null. */
    val userId: String? get() = user.current

    /**
     * AT LAUNCH, ONCE, before anything else uses Graine — Application.onCreate on Android, the app's start on iOS (a
     * push that wakes a closed app rings only once the agents are configured). Returns the names configured.
     *
     * [identityTokens] is YOUR SIGN-IN: the signed-in user's ID token (a JWT), fresh enough to use, or null when none
     * can be had right now — Firebase `user.getIdToken(false)`, or your Auth0, Cognito or Okta session's ID token.
     * The SDK keeps it in memory only, beside the identity it proves; never in variables or logs.
     */
    fun start(
        agents: List<Agent>,
        identityTokens: suspend (userId: String) -> String?,
        debugBuild: Boolean,
        log: (String) -> Unit = { println(it) },
    ): List<String> {
        this.debugBuild = debugBuild
        this.log = log
        user = UserIdentity(
            scope,
            fetch = identityTokens,
            identify = { traits, memory, token -> Graine.identify(traits, memory, identityToken = token) },
            setIdentityToken = { Graine.setIdentityToken(it) },
            log = log,
        )
        // STEP 4 — the SDK asks for a fresh identity token by itself: when one expired, when a request was refused
        // identity_unverified (that request is then sent once more), before a conversation whose JWT has less than a
        // minute left, and when a call rings. One for the whole app, set before configure so every agent has it.
        Graine.identityTokenRefresher = { user.fresh() }
        // STEP 3, Development — Graine.debugTokenMode stays AUTOMATIC: a debug build sends this install's debug token
        // instead of App Attest or Play Integrity; a release build never makes, logs or sends one.
        val configured = agents.mapNotNull { configure(it) }
        configured.forEach { watch(it) }
        publish(configured)
        // STEP 4 — a call that rings while signIn still waits for the token identifies the user at once: it knows who it
        // is ringing; the token follows when it comes.
        scope.launch { Graine.calls.incoming.collect { user.identifyPendingNow() } }
        return configured
    }

    private fun configure(agent: Agent): String? {
        // SERVER TOKENS — a tokenProvider only on an agent you mint tokens for (each token is for one agent and one
        // user); wrapped here only so the Security card can say whether the last ask brought one.
        val provider = agent.config.tokenProvider
        tokens.update { it + (agent.name to if (provider == null) ServerTokenState.OFF else ServerTokenState.NOT_ASKED) }
        return try {
            Graine.configure(agent.config.copy(tokenProvider = provider?.let { watched(agent.name, it) }), name = agent.name)
            agent.name
        } catch (e: Exception) {
            // The main agent is the app's own: without it nothing works, so it fails loudly. Another agent's screen says it is not available.
            if (agent.name == Graine.DEFAULT_AGENT) throw e
            log("Graine: the agent \"${agent.name}\" could not be configured: ${e.message}")
            null
        }
    }

    private fun watch(name: String) {
        val agent = Graine.agent(name) ?: return
        // STEP 3 — prove the app now, so the first conversation never waits for it. Nothing is sent when the agent does
        // not ask for a verified app, while a pass with hours left is held, or in a debug build (its debug token goes with
        // the next request). A proof that failed is UNVERIFIED, never an exception, and the SDK tries again by itself.
        scope.launch { log("Graine: app verification ($name): ${agent.prepareAppVerification().word}") }
        // What Graine has proven, as it changes — for the log; the Security card reads the same state.
        scope.launch { agent.verification.collect { log("Graine: $name — ${SecurityText.logLine(it)}") } }
        // An error the SDK reports with no code (a chat that could not connect): its sentence, for the card and the log.
        scope.launch {
            agent.events.collect { event ->
                if (event is GraineEvent.Error && event.message.isNotBlank()) {
                    errors.update { it + (name to event.message) }
                    log("Graine: $name error: ${event.message}")
                }
            }
        }
        // A voice-bar call that could not start says why with the code first ("key_revoked: …").
        scope.launch { agent.voice.state.map { it.error }.distinctUntilChanged().collect { noteRefusalText(name, it) } }
        // The agent's "Who is the user?" must be among the traits signIn sends, or the user is anonymous to that agent.
        scope.launch { agent.session.filterNotNull().collect { user.checkField(name, it.identityField) } }
    }

    private fun publish(names: List<String>) {
        val each = names.mapNotNull { name -> Graine.agent(name)?.let { statusOf(name, it) } }
        if (each.isEmpty()) return
        scope.launch { combine(each) { it.toList() }.collect { all.value = it } }
    }

    private fun statusOf(name: String, agent: GraineAgent): Flow<AgentSecurity> =
        combine(agent.session, agent.verification, tokens, refusals, errors) { session, verification, t, r, e ->
            AgentSecurity(name, session?.agentId, session?.identityField, verification, t[name] ?: ServerTokenState.OFF, r[name], e[name])
        }

    // ── step 4: who the user is ───────────────────────────────────────────────

    /**
     * STEP 4 — AT SIGN-IN, AND AT EVERY LAUNCH while someone is signed in: the SDK keeps who the user is, and their
     * identity token, in memory only. [userId] is the value your sign-in proves — the same value [traits] carries under
     * the field each agent's "Who is the user?" names (this app sends it under its own field and under `user_id`).
     *
     * IDENTIFY WITH THE TOKEN. Pass the [identityToken] you already hold (your sign-in hands you one; Firebase keeps it
     * cached), and the user is identified with it at once. If you must fetch it, it is fetched BEFORE identify, from
     * [start]'s identityTokens: identify waits for it, at most 2 seconds, then goes without it and hands the token over
     * when it comes. A conversation started before the token is answered anonymously while the agent requires verified
     * users — the SDK asks the refresher before a conversation only for a token about to expire — so start calls after
     * [awaitSignIn].
     */
    fun signIn(userId: String, traits: Map<String, Any?>, memory: Boolean? = null, identityToken: String? = null) =
        user.signIn(userId, traits, memory, identityToken)

    /**
     * Waits until [signIn] has identified the user — with their token, or after its 2-second wait — and returns at once
     * when nothing waits. Await it before starting a call or the voice bar right after signIn: a call takes who the user
     * is at its start. Requests wait for it too (GraineService.guarded). A chat needs nothing: when the identity arrives,
     * the SDK reconnects an open chat as the user.
     */
    suspend fun awaitSignIn() = user.awaitReady()

    /** Sign-out: the SDK forgets the user, their token and pass, and lets this device go; later conversations are anonymous. */
    fun signOut() = user.signOut()

    /**
     * STEP 3, Development — this install's debug token, in a debug build only (never in a release build). Register it
     * on EACH agent (Prove it's your app → Development → Debug tokens) to use this build while Require a verified app is on.
     */
    val debugToken: String? get() = if (debugBuild) Graine.debugToken else null

    // ── step 5: refusals ──────────────────────────────────────────────────────

    /**
     * STEP 5 — ONE OF [agent]'S REQUESTS WAS REFUSED: call it with what a suspending Graine call threw. A refusal about
     * security is kept for the Security card, logged with its fix and returned — show its [SecurityRefusal.message].
     * The SDK already did its one retry where one can help; add none. Null for any other error.
     */
    fun noteRefusal(agent: String, error: Throwable?): SecurityRefusal? {
        val e = error as? GraineException ?: return null
        return SecurityRefusal.of(e.code, e.reason)?.let { note(agent, it) }
    }

    /** The same, for a refusal written as text: a call's failure reason ("key_revoked: …"), or a list's error word. */
    fun noteRefusalText(agent: String, text: String?): SecurityRefusal? = SecurityRefusal.ofText(text)?.let { note(agent, it) }

    /** One of [agent]'s requests was answered: a refusal that seemed final no longer holds (the switch was turned off, say). */
    fun noteAnswered(agent: String) {
        if (agent in forGood.value) forGood.update { it - agent }
    }

    /** True while [agent]'s last request met a refusal no retry can lift (app_not_allowed, key_revoked, token_scope): retry nothing. */
    fun refusedForGood(agent: String): Boolean = agent in forGood.value

    private fun note(agent: String, refusal: SecurityRefusal): SecurityRefusal {
        if (refusals.value[agent] != refusal) log("Graine: $agent refused — ${refusal.log}")
        refusals.update { it + (agent to refusal) }
        forGood.update { if (refusal.final) it + agent else it - agent }
        return refusal
    }

    /** [provider], noting whether each ask brought a token. Throwing is no token, as the SDK takes it; cancellation goes on. */
    private fun watched(agent: String, provider: suspend () -> String?): suspend () -> String? = {
        val token = try {
            provider()?.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        tokens.update { it + (agent to if (token != null) ServerTokenState.OK else ServerTokenState.MISSING) }
        if (token == null && userId != null) log("Graine: $agent — your server gave no server token for the signed-in user")
        token
    }
}

/**
 * WHO IS SIGNED IN, AND THEIR IDENTITY TOKEN, KEPT STRAIGHT (step 4).
 *
 * Identify WITH the token. The SDK holds no token across launches, and a conversation that starts while it holds none
 * goes out unproven — answered anonymously while the agent requires verified users — because the SDK asks the refresher
 * before a conversation only for a token about to expire, never for a missing one. So, with no token in hand, signIn
 * fetches it FIRST and identifies with it, waiting at most [tokenWaitMs]; if it has not come by then (or the fetch
 * failed), it identifies without it — a call that rings still knows the user — keeps fetching, and hands the token over
 * with setIdentityToken when it comes. Meanwhile nobody is identified, never the last user: a call or the voice bar
 * waits for this step ([awaitReady]), a ring identifies at once ([identifyPendingNow]). A token fetched for one user is
 * never handed over for the next, and sign-out cancels everything on its way.
 *
 * The SDK calls are passed in, so these rules are tested without the SDK. Main thread.
 */
internal class UserIdentity(
    private val scope: CoroutineScope,
    private val fetch: suspend (userId: String) -> String?,
    private val identify: (traits: Map<String, Any?>?, memory: Boolean?, identityToken: String?) -> Unit,
    private val setIdentityToken: (String) -> Unit,
    private val log: (String) -> Unit,
    /** How long identify waits for the token it fetches before it goes without it (tests shorten it). */
    private val tokenWaitMs: Long = TOKEN_WAIT_MS,
) {
    /** The signed-in user, by the value the identity token proves; null when nobody is. */
    var current: String? = null
        private set

    /** What identify will send once the token comes, or once the wait is over; null when nothing waits. */
    private var pending: Pending? = null
    /** identify has named [current] — with or without a token. */
    private var identified = false
    /** A token was handed over for [current]: the SDK keeps it while the same user is identified again. */
    private var tokened = false
    /** The token on its way for [current]: one fetch, joined by every wait and by the SDK's refresher. */
    private var fetching: Deferred<String?>? = null
    private var waiting: Job? = null
    private var handover: Job? = null
    /** False while an identify waits for its token. */
    private val ready = MutableStateFlow(true)

    /** The traits identify was last given: each agent's "Who is the user?" field is checked against them. */
    private var traits: Map<String, Any?>? = null

    private class Pending(val traits: Map<String, Any?>, val memory: Boolean?)

    fun signIn(userId: String, traits: Map<String, Any?>, memory: Boolean?, identityToken: String?) {
        if (userId != current) {
            // Someone else: nothing on its way for the last user is ever this user's.
            val someoneBefore = current != null && identified
            reset()
            // While this user's token is fetched, nobody is identified — never the last user.
            if (someoneBefore && identityToken == null) identify(null, null, null)
        }
        current = userId
        this.traits = traits
        when {
            // The token in hand goes WITH identify: the very first request proves the user.
            identityToken != null -> {
                waiting?.cancel()
                fetching?.cancel()
                fetching = null
                pending = null
                identifyNow(traits, memory, identityToken)
            }
            // The same user again (new traits, a new launch of the same screen): at once. The SDK keeps the token it
            // holds; one still on its way follows.
            identified -> {
                identify(traits, memory, null)
                if (!tokened) handOverLater(userId)
            }
            // No token in hand: fetch it FIRST and identify WITH it, waiting at most tokenWaitMs.
            else -> {
                pending = Pending(traits, memory ?: pending?.memory)
                ready.value = false
                val token = tokenFor(userId)
                if (waiting?.isActive != true) {
                    waiting = scope.launch {
                        val inTime = withTimeoutOrNull(tokenWaitMs) { token.await() }
                        if (current != userId) return@launch
                        flush(inTime)
                        when {
                            inTime != null -> Unit
                            // Not in time: identified without it — the token follows when it comes.
                            token.isActive -> handOverLater(userId)
                            // The fetch gave none: the SDK asks the refresher when it needs one.
                            else -> log("Graine: no identity token for the signed-in user yet — the SDK asks again when it needs one")
                        }
                    }
                }
            }
        }
    }

    fun signOut() {
        reset()
        current = null
        traits = null
        identify(null, null, null)
    }

    /** Waits until signIn's identify has gone out — with the token, or after the wait — and returns at once when none waits. */
    suspend fun awaitReady() {
        // The wait above always ends within tokenWaitMs; the margin only guards against a wait that never ends.
        withTimeoutOrNull(tokenWaitMs + READY_MARGIN_MS) { ready.first { it } }
    }

    /** A call rings before the token came: identify at once with what is in hand — it knows the user; the token follows. */
    fun identifyPendingNow() {
        val userId = current ?: return
        if (pending == null) return
        waiting?.cancel()
        flush(null)
        handOverLater(userId)
    }

    /** What the SDK's refresher answers: a fresh token for whoever is signed in now (joining one on its way); null keeps the one it holds. */
    suspend fun fresh(): String? {
        val asked = current ?: return null
        val token = tokenFor(asked).await() ?: return null
        if (current != asked) return null
        tokened = true
        return token
    }

    /** [agent]'s "Who is the user?" names [field]: without it among the traits, the user is anonymous to that agent. */
    fun checkField(agent: String, field: String) {
        val sent = traits ?: return
        if (sent[field] == null) {
            log("Graine: the agent \"$agent\" identifies users by \"$field\", which signIn does not send — the user is anonymous to it. Send the user's id under \"$field\" too.")
        }
    }

    private fun identifyNow(traits: Map<String, Any?>, memory: Boolean?, token: String?) {
        identify(traits, memory, token)
        identified = true
        if (token != null) tokened = true
        ready.value = true
    }

    private fun flush(token: String?) {
        val p = pending ?: return
        pending = null
        identifyNow(p.traits, p.memory, token)
    }

    /** The token, when it comes, handed over for [userId] — if they are still the one signed in, and have none yet. */
    private fun handOverLater(userId: String) {
        if (handover?.isActive == true) return
        val token = tokenFor(userId)
        handover = scope.launch {
            val late = token.await()
            if (current != userId || tokened) return@launch
            if (late == null) {
                log("Graine: no identity token for the signed-in user yet — the SDK asks again when it needs one")
                return@launch
            }
            setIdentityToken(late)
            tokened = true
        }
    }

    /** The fetch on its way for [userId], or a new one. */
    private fun tokenFor(userId: String): Deferred<String?> =
        fetching?.takeIf { it.isActive } ?: scope.async { fetchOrNull(userId) }.also { fetching = it }

    private fun reset() {
        waiting?.cancel()
        waiting = null
        handover?.cancel()
        handover = null
        fetching?.cancel()
        fetching = null
        pending = null
        identified = false
        tokened = false
        ready.value = true
    }

    private suspend fun fetchOrNull(userId: String): String? = try {
        fetch(userId)?.takeIf { it.isNotBlank() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        /**
         * How long identify waits for the token it fetches, in milliseconds: long enough for a sign-in's token on a phone
         * network, short enough that a call placed at launch never waits noticeably. After it, the user is identified
         * without the token, and the token follows.
         */
        const val TOKEN_WAIT_MS = 2_000L

        /** The most awaitReady adds to the wait, should it ever not end by itself. */
        private const val READY_MARGIN_MS = 500L
    }
}
