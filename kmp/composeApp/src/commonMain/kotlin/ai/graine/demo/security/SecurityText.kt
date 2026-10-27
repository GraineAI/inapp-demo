package ai.graine.demo.security

import ai.graine.inapp.kmp.GraineAppVerdict
import ai.graine.inapp.kmp.GraineErrorCode
import ai.graine.inapp.kmp.GraineUserVerdict
import ai.graine.inapp.kmp.GraineVerification

/*
 * GRAINE SECURITY, IN WORDS — what the user is told when a request is refused, what the log says, and how the
 * Security card reads what Graine has proven. Pure Kotlin, no SDK calls: copy it with GraineSecurity.kt, or write
 * your own words from the same codes.
 */

/**
 * A REFUSAL ABOUT YOUR APP, YOUR USER, YOUR SERVER TOKEN OR YOUR KEY — read from the SDK's own code
 * ([GraineErrorCode]), never from a message.
 *
 * The SDK has already done its one retry before it reports any of them: `app_not_verified` after proving the app and
 * sending the request once more, `identity_unverified` after asking `Graine.identityTokenRefresher`, the `token_*`
 * codes after asking the agent's `tokenProvider`. `app_not_allowed`, `token_scope` and `key_revoked` it never
 * retries: a retry cannot help ([final]). So the app adds no retry of its own — it shows [message] and logs [log].
 */
data class SecurityRefusal(
    val code: GraineErrorCode,
    /** Graine's one-word reason beside the code (`signature`, `scope`, …), when it gave one. */
    val reason: String?,
    /** For the user: plain words that never blame them and never show a code. */
    val message: String,
    /** For the developer and the tester: what was refused, and why. */
    val summary: String,
    /** What fixes it — in the dashboard, the build, or your server. */
    val fix: String,
    /** True: trying again cannot help until the build or the agent's settings change. Never retry it. */
    val final: Boolean,
) {
    /** One line for your logs: the code, the reason, what it means, the fix. Never a token, never a user id. */
    val log: String
        get() = code.wire + (reason?.let { " ($it)" } ?: "") + ": " + summary + ". " + fix +
            if (final) " Final: never retried." else " The SDK already tried once more."

    companion object {
        private const val UPDATE = "This version of the app can't connect to the assistant. Please update the app."
        private const val LATER = "The assistant isn't available right now. Please try again later."

        /** The refusal for [code]; null for a code that is not about security (booking rules, memory, the network…). */
        fun of(code: GraineErrorCode, reason: String? = null): SecurityRefusal? {
            val why = reason?.trim()?.takeIf { it.isNotEmpty() }
            fun refusal(message: String, summary: String, fix: String, final: Boolean = false) =
                SecurityRefusal(code, why, message, summary, fix, final)
            return when (code) {
                GraineErrorCode.AppNotAllowed -> refusal(
                    UPDATE,
                    "this build is not one of the agent's apps (Only my apps is on)",
                    "List it under the agent's Your apps: the iOS bundle ID and Team ID, or the Android package and the " +
                        "SHA-256 of every certificate that signs it (debug, upload, Google Play app signing).",
                    final = true,
                )
                GraineErrorCode.KeyRevoked -> refusal(
                    "This version of the app can't connect to the assistant any more. Please update the app.",
                    "this publishable key was revoked",
                    "Ship the agent's new key (Embed & Widgets → Install → Keys).",
                    final = true,
                )
                GraineErrorCode.AppNotVerified -> refusal(
                    "We couldn't confirm this copy of the app. Please try again later, or update the app.",
                    "this install could not prove it is your app (Require a verified app is on)",
                    "Release build: check App Attest (iOS) or Play Integrity (Android). Debug build: register this " +
                        "install's debug token under Prove it's your app → Development.",
                )
                GraineErrorCode.IdentityUnverified -> refusal(
                    "Please sign in again to continue.",
                    "the signed-in user is not proven (Require verified users is on)",
                    "Check that signIn hands an identity token, and that its issuer, audience and user-ID claim match " +
                        "the agent's Prove who the user is.",
                )
                GraineErrorCode.TokenRequired -> refusal(
                    LATER,
                    "the agent requires a server token and none was sent",
                    "This agent's tokenProvider returned none: check that your server mints one for the signed-in user.",
                )
                GraineErrorCode.TokenInvalid -> refusal(
                    LATER,
                    "the server token could not be checked",
                    "Mint it with POST /v2/client-tokens for this agent, and pass it to the SDK exactly as it came.",
                )
                GraineErrorCode.TokenExpired -> refusal(
                    LATER,
                    "the server token expired and a fresh one did not come in time",
                    "Check that your server answers the tokenProvider within a few seconds.",
                )
                GraineErrorCode.TokenScope -> refusal(
                    "This isn't available in the app right now.",
                    "the server token is for another agent or user, or lacks the scope this needs",
                    "Mint it for this agent, the user signIn names, and every scope the app uses (chat, call, memory, booking).",
                    final = true,
                )
                else -> null
            }
        }

        /**
         * The refusal a text starts with — `"key_revoked: …"`, as a call that could not start says why, or a bare word,
         * as a list's `error` is (`"identity_unverified"`). Null when the text does not start with a security code.
         */
        fun ofText(text: String?): SecurityRefusal? {
            val word = text?.trim()?.substringBefore(':')?.trim()?.takeIf { it.isNotEmpty() && ' ' !in it } ?: return null
            return of(GraineErrorCode.from(word))
        }
    }
}

/** Whether the agent's requests carry a server token (its `GraineConfig.tokenProvider`), as the app last saw it. */
enum class ServerTokenState {
    /** This agent has no tokenProvider: it is sent no server token. */
    OFF,

    /** The SDK has not asked for one yet. */
    NOT_ASKED,

    /** The last ask brought a token. */
    OK,

    /** The last ask brought none (nobody signed in, or your server gave none). */
    MISSING,
}

/**
 * ONE AGENT'S SECURITY, AS THE APP SEES IT — the Security card's lines. [verification] is the SDK's own state
 * (`agent.verification`); the rest is what this app noted.
 */
data class AgentSecurity(
    /** The name the agent is configured under: "default" for the main agent. */
    val name: String,
    /** The agent's id, once its settings have loaded. */
    val agentId: String? = null,
    /** The trait the agent's "Who is the user?" names, once its settings have loaded. */
    val identityField: String? = null,
    val verification: GraineVerification = GraineVerification(),
    val serverToken: ServerTokenState = ServerTokenState.OFF,
    /** The last refusal about security one of this agent's requests met, read from its code. */
    val refusal: SecurityRefusal? = null,
    /** The last error the SDK reported for this agent with no code (a chat that could not connect), as its sentence. */
    val lastError: String? = null,
)

/** How the Security card and the log read what Graine has proven. */
object SecurityText {
    /** One line of the card: [ok] true is proven, false is a problem, null is neither (off, not yet). */
    data class Line(val label: String, val value: String, val ok: Boolean?, val detail: String? = null)

    /** Every line for one agent, in the card's order. Until its settings load, its verdicts are only "not yet". */
    fun lines(s: AgentSecurity, signedIn: Boolean): List<Line> = buildList {
        if (s.agentId == null) add(Line("Settings", "not loaded yet", null, "The lines below read off until they arrive. Still so? The last error says why."))
        add(app(s.verification))
        add(user(s.verification, signedIn))
        add(serverToken(s.serverToken, s.refusal, signedIn))
        s.refusal?.takeUnless { isTokenCode(it.code) }?.let {
            add(Line("Last refusal", it.code.wire + (it.reason?.let { r -> " ($r)" } ?: ""), false, it.summary + ". " + it.fix))
        }
        s.lastError?.let { add(Line("Last error", it, false)) }
    }

    /** "App": whether Graine knows this build is the agent's app. */
    fun app(v: GraineVerification): Line = when (v.app) {
        GraineAppVerdict.OFF -> Line("App", "not checked", null, "This agent doesn't check app proofs.")
        GraineAppVerdict.CHECKING -> Line("App", "checking…", null)
        GraineAppVerdict.VERIFIED -> Line("App", "verified", true, "App Attest or Play Integrity")
        GraineAppVerdict.TEST_BUILD -> Line("App", "verified", true, "development build, while Allow development builds is on")
        GraineAppVerdict.DEBUG_TOKEN -> Line("App", "verified", true, "registered debug token")
        GraineAppVerdict.UNVERIFIED -> Line("App", "not verified", false, appReason(v.appReason))
    }

    /** "User": whether Graine knows the signed-in user is who the app says. */
    fun user(v: GraineVerification, signedIn: Boolean): Line = when {
        !signedIn -> Line("User", "nobody signed in", null)
        v.user == GraineUserVerdict.OFF -> Line("User", "not checked", null, "This agent doesn't verify its users.")
        v.user == GraineUserVerdict.VERIFIED -> Line("User", "verified", true, "identity token")
        v.user == GraineUserVerdict.TEST_USER -> Line("User", "verified", true, "test user, from an install with a registered debug token")
        else -> Line(
            "User",
            "not proven",
            false,
            (userReason(v.userReason) ?: if (v.user == GraineUserVerdict.INVALID) "token refused" else "no identity token") +
                " — answered anonymously while Require verified users is on",
        )
    }

    /** "Server token": whether this agent's requests carry one. */
    fun serverToken(state: ServerTokenState, refusal: SecurityRefusal?, signedIn: Boolean): Line {
        if (refusal != null && isTokenCode(refusal.code)) {
            return Line("Server token", "refused", false, refusal.code.wire + (refusal.reason?.let { " ($it)" } ?: "") + ": " + refusal.fix)
        }
        return when (state) {
            ServerTokenState.OFF -> Line("Server token", "off", null, "No tokenProvider for this agent.")
            ServerTokenState.NOT_ASKED -> Line("Server token", "not asked yet", null)
            ServerTokenState.OK -> Line("Server token", "ok", true)
            ServerTokenState.MISSING ->
                if (signedIn) Line("Server token", "missing", false, "Your server gave none for the signed-in user.")
                else Line("Server token", "missing", null, "Nobody is signed in.")
        }
    }

    /**
     * True when an agent's settings have loaded and it checks neither the app nor its users — every agent whose key
     * ships in the app needs its own Your apps and switches.
     */
    fun checksNothing(s: AgentSecurity): Boolean =
        s.agentId != null && s.verification.app == GraineAppVerdict.OFF && s.verification.user == GraineUserVerdict.OFF

    /** The agent's id as the card shows it: its first characters. */
    fun idPrefix(agentId: String): String = if (agentId.length <= 12) agentId else agentId.take(12) + "…"

    /** What Graine has proven, in one line for the log: "app verified, user unverified (missing)". */
    fun logLine(v: GraineVerification): String =
        "app " + v.app.word + (v.appReason?.let { " ($it)" } ?: "") + ", user " + v.user.word + (v.userReason?.let { " ($it)" } ?: "")

    /** Graine's reason an app is not verified, in plain words. */
    fun appReason(reason: String?): String = when (reason) {
        null, "" -> "no reason given"
        "missing" -> "no proof yet"
        "expired" -> "proof expired"
        "invalid" -> "proof refused"
        "wrong_app" -> "another app's proof"
        "test_build" -> "a development build, while Allow development builds is off"
        "not_configured" -> "proofs are not set up for this platform on the agent"
        "unsupported" -> "this device cannot prove the app"
        "debug_token_unknown" -> "debug token not registered"
        "debug_token_expired" -> "debug token expired"
        else -> reason.replace('_', ' ')
    }

    /** Graine's reason a user is not proven, in plain words; null when it gave none. */
    fun userReason(reason: String?): String? = when (reason) {
        null, "" -> null
        "missing" -> "no identity token"
        "expired" -> "identity token expired"
        "not_yet_valid" -> "identity token not valid yet"
        "signature" -> "identity token signature refused"
        "audience" -> "identity token for another audience"
        "issuer" -> "identity token from another issuer"
        "claim" -> "identity token lacks the user-ID claim"
        "mismatch" -> "identity token is for another user"
        "malformed" -> "identity token malformed"
        "unknown_key" -> "identity token signed with an unknown key"
        "unavailable" -> "the sign-in's keys could not be read"
        else -> reason.replace('_', ' ')
    }

    private fun isTokenCode(code: GraineErrorCode): Boolean =
        code == GraineErrorCode.TokenRequired || code == GraineErrorCode.TokenInvalid ||
            code == GraineErrorCode.TokenExpired || code == GraineErrorCode.TokenScope
}
