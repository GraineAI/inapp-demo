package ai.graine.demo.security

import ai.graine.inapp.kmp.Graine
import ai.graine.inapp.kmp.GraineAgent
import ai.graine.inapp.kmp.GraineConfig
import ai.graine.inapp.kmp.GraineEvent
import ai.graine.inapp.kmp.GraineException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
 *   4 Prove who the user is .. Here: signIn() at sign-in AND at every launch, with your sign-in's ID token; the
 *                              refresher start() sets; signOut().
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
     * the field each agent's "Who is the user?" names (this app sends it under its own field and under `user_id`). Pass
     * [identityToken] when your sign-in just gave you one; without it, one is fetched now from [start]'s identityTokens
     * and handed to the SDK when it comes. Identifies at once either way, so a call that rings now knows the user.
     */
    fun signIn(userId: String, traits: Map<String, Any?>, memory: Boolean? = null, identityToken: String? = null) =
        user.signIn(userId, traits, memory, identityToken)

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
 * WHO IS SIGNED IN, AND THEIR IDENTITY TOKEN, KEPT STRAIGHT (step 4). Identifies at once — a call that rings now knows
 * the user — with the token in hand, or fetches one and hands it over when it comes; a token fetched for one user is
 * never handed over for the next. The SDK calls are passed in, so these rules are tested without the SDK. Main thread.
 */
internal class UserIdentity(
    private val scope: CoroutineScope,
    private val fetch: suspend (userId: String) -> String?,
    private val identify: (traits: Map<String, Any?>?, memory: Boolean?, identityToken: String?) -> Unit,
    private val setIdentityToken: (String) -> Unit,
    private val log: (String) -> Unit,
) {
    /** The signed-in user, by the value the identity token proves; null when nobody is. */
    var current: String? = null
        private set

    /** The user a token was handed over for: the SDK keeps it while the same user is identified again. */
    private var tokened: String? = null
    private var fetching: Job? = null

    /** The traits identify was last given: each agent's "Who is the user?" field is checked against them. */
    private var traits: Map<String, Any?>? = null

    fun signIn(userId: String, traits: Map<String, Any?>, memory: Boolean?, identityToken: String?) {
        if (userId != current) {
            // Someone else: the last user's token, and a fetch still on its way for them, are never this user's.
            fetching?.cancel()
            fetching = null
            tokened = null
        }
        current = userId
        this.traits = traits
        // The same user without a token keeps the one the SDK holds; another user starts with this one, or none.
        identify(traits, memory, identityToken)
        if (identityToken != null) {
            tokened = userId
            fetching?.cancel()
            fetching = null
            return
        }
        if (tokened == userId || fetching?.isActive == true) return
        fetching = scope.launch {
            val token = fetchOrNull(userId)
            if (current != userId) return@launch
            if (token == null) {
                log("Graine: no identity token for the signed-in user yet — the SDK asks again when it needs one")
            } else {
                setIdentityToken(token)
                tokened = userId
            }
        }
    }

    fun signOut() {
        fetching?.cancel()
        fetching = null
        current = null
        tokened = null
        traits = null
        identify(null, null, null)
    }

    /** What the SDK's refresher answers: a fresh token for whoever is signed in now; null keeps the one it holds. */
    suspend fun fresh(): String? {
        val asked = current ?: return null
        val token = fetchOrNull(asked) ?: return null
        if (current != asked) return null
        tokened = asked
        return token
    }

    /** [agent]'s "Who is the user?" names [field]: without it among the traits, the user is anonymous to that agent. */
    fun checkField(agent: String, field: String) {
        val sent = traits ?: return
        if (sent[field] == null) {
            log("Graine: the agent \"$agent\" identifies users by \"$field\", which signIn does not send — the user is anonymous to it. Send the user's id under \"$field\" too.")
        }
    }

    private suspend fun fetchOrNull(userId: String): String? = try {
        fetch(userId)?.takeIf { it.isNotBlank() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
