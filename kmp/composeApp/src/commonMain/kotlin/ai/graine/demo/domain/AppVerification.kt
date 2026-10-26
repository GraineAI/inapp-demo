package ai.graine.demo.domain

import ai.graine.inapp.kmp.GraineAppVerdict
import ai.graine.inapp.kmp.GraineUserVerdict
import ai.graine.inapp.kmp.GraineVerification

/*
 * YOUR APP AND YOUR USERS (SDK 0.14.4): what Graine has proven about this build
 * and about the person signed in, as the SDK reports it (Graine.verification),
 * in the words Profile shows. Presentation only — the verdicts are Graine's.
 *
 * This demo has no server of its own, so it never sends an identity token: the
 * person stays unproven, and the agent's "Verified users" is kept report-only
 * for it (agent-setup/YOUR-APPS-AND-USERS.md). A real app's server would sign
 * the token; no app ever carries the secret that signs it.
 */
object VerificationText {
    /** "App": whether Graine knows this build is the agent's app. */
    fun app(v: GraineVerification): String = when (v.app) {
        GraineAppVerdict.OFF -> "not checked"
        GraineAppVerdict.CHECKING -> "checking…"
        GraineAppVerdict.VERIFIED -> "verified ✓"
        GraineAppVerdict.TEST_BUILD -> "development build ✓"
        GraineAppVerdict.DEBUG_TOKEN -> "debug token ✓"
        GraineAppVerdict.UNVERIFIED -> "not verified" + (appReason(v.appReason)?.let { " ($it)" } ?: "")
    }

    /** "User": whether Graine knows the person is who the app says. */
    fun user(v: GraineVerification): String = when (v.user) {
        GraineUserVerdict.OFF -> "not checked"
        GraineUserVerdict.VERIFIED -> "verified ✓"
        GraineUserVerdict.TEST_USER -> "test user ✓"
        GraineUserVerdict.UNVERIFIED -> "unverified (report only)"
        GraineUserVerdict.INVALID -> "token refused" + (v.userReason?.let { " (${it.replace('_', ' ')})" } ?: "")
    }

    /** Graine's reason an app is not verified, in plain words; null when it gave none. */
    fun appReason(reason: String?): String? = when (reason) {
        null, "" -> null
        "missing" -> "no proof yet"
        "expired" -> "proof expired"
        "invalid" -> "proof refused"
        "wrong_app" -> "another app's proof"
        "test_build" -> "development build"
        "not_configured" -> "Play Integrity not set up"
        "unsupported" -> "not supported on this device"
        "debug_token_unknown" -> "debug token not registered"
        "debug_token_expired" -> "debug token expired"
        else -> reason.replace('_', ' ')
    }
}
