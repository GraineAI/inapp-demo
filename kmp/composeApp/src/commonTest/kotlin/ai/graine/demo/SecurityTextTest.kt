package ai.graine.demo

import ai.graine.demo.domain.CallText
import ai.graine.demo.domain.PastSessionsText
import ai.graine.demo.security.AgentSecurity
import ai.graine.demo.security.SecurityRefusal
import ai.graine.demo.security.SecurityText
import ai.graine.demo.security.ServerTokenState
import ai.graine.inapp.kmp.GraineAppVerdict
import ai.graine.inapp.kmp.GraineErrorCode
import ai.graine.inapp.kmp.GraineException
import ai.graine.inapp.kmp.GraineUserVerdict
import ai.graine.inapp.kmp.GraineVerification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** security/SecurityText.kt: refusals read from the SDK's codes, and the Security card's lines. */
class SecurityTextTest {
    private val securityCodes = listOf(
        GraineErrorCode.AppNotAllowed, GraineErrorCode.AppNotVerified, GraineErrorCode.IdentityUnverified,
        GraineErrorCode.TokenRequired, GraineErrorCode.TokenInvalid, GraineErrorCode.TokenExpired,
        GraineErrorCode.TokenScope, GraineErrorCode.KeyRevoked,
    )

    @Test
    fun everySecurityCodeHasPlainWordsALogLineAndARetryRule() {
        for (code in securityCodes) {
            val r = assertNotNull(SecurityRefusal.of(code), code.wire)
            assertTrue(r.message.isNotBlank() && code.wire !in r.message && "_" !in r.message, "the user never sees a code: ${r.message}")
            assertTrue(r.log.startsWith(code.wire + ": "), "the log line starts with the code: ${r.log}")
        }
        // A retry cannot help these: the SDK never retries them, and neither does the app.
        assertEquals(
            setOf(GraineErrorCode.AppNotAllowed, GraineErrorCode.KeyRevoked, GraineErrorCode.TokenScope),
            securityCodes.filter { SecurityRefusal.of(it)!!.final }.toSet(),
        )
        assertTrue(SecurityRefusal.of(GraineErrorCode.AppNotAllowed)!!.log.endsWith("Final: never retried."))
        assertTrue(SecurityRefusal.of(GraineErrorCode.IdentityUnverified)!!.log.endsWith("The SDK already tried once more."))
    }

    @Test
    fun otherCodesAreNotAboutSecurity() {
        listOf(
            GraineErrorCode.IdentityRequired, GraineErrorCode.DeviceLimit, GraineErrorCode.Network, GraineErrorCode.RateLimited,
            GraineErrorCode.InvalidKey, GraineErrorCode.OriginRefused, GraineErrorCode.Unknown("something_new"),
        ).forEach { assertNull(SecurityRefusal.of(it), it.wire) }
    }

    @Test
    fun theReasonRidesInTheLogLine() {
        val r = SecurityRefusal.of(GraineErrorCode.TokenScope, " scope ")!!
        assertEquals("scope", r.reason)
        assertTrue(r.log.startsWith("token_scope (scope): "))
    }

    @Test
    fun aRefusalWrittenAsText() {
        // A call that could not start: the code first, then the sentence.
        assertEquals(GraineErrorCode.KeyRevoked, SecurityRefusal.ofText("key_revoked: This publishable key was revoked.")?.code)
        assertEquals(GraineErrorCode.AppNotAllowed, SecurityRefusal.ofText("app_not_allowed: not one of the agent's apps")?.code)
        // A list's error is the bare word.
        assertEquals(GraineErrorCode.IdentityUnverified, SecurityRefusal.ofText("identity_unverified")?.code)
        assertNull(SecurityRefusal.ofText("The call could not be connected."), "a sentence is no code")
        assertNull(SecurityRefusal.ofText("network"), "not about security")
        assertNull(SecurityRefusal.ofText(null))
        assertNull(SecurityRefusal.ofText("  "))
    }

    @Test
    fun theAppsScreensSayItInTheSameWords() {
        val refused = GraineException(GraineErrorCode.AppNotAllowed, "app_not_allowed: the server's own sentence")
        assertEquals(SecurityRefusal.of(GraineErrorCode.AppNotAllowed)!!.message, CallText.message(refused))
        assertEquals("Please sign in again to continue.", CallText.message(GraineException(GraineErrorCode.IdentityUnverified, "x")))
        assertEquals("Please sign in again to continue.", PastSessionsText.error("identity_unverified"))
        assertEquals("Sign in to see past sessions.", PastSessionsText.error("identity_required"), "the list's own words first")
        assertEquals("Past sessions could not be loaded. Try again.", PastSessionsText.error("something_else"))
    }

    @Test
    fun theAppLine() {
        fun app(v: GraineAppVerdict, reason: String? = null) = SecurityText.app(GraineVerification(app = v, appReason = reason))
        assertEquals(SecurityText.Line("App", "not checked", null, "This agent doesn't check app proofs."), app(GraineAppVerdict.OFF))
        assertEquals("checking…", app(GraineAppVerdict.CHECKING).value)
        assertEquals(true, app(GraineAppVerdict.VERIFIED).ok)
        assertEquals("registered debug token", app(GraineAppVerdict.DEBUG_TOKEN).detail)
        assertEquals("verified", app(GraineAppVerdict.TEST_BUILD).value)
        assertEquals(SecurityText.Line("App", "not verified", false, "debug token not registered"), app(GraineAppVerdict.UNVERIFIED, "debug_token_unknown"))
        assertEquals("debug token expired", app(GraineAppVerdict.UNVERIFIED, "debug_token_expired").detail)
        assertEquals("brand new reason", app(GraineAppVerdict.UNVERIFIED, "brand_new_reason").detail, "a reason this build does not know, as Graine said it")
    }

    @Test
    fun theUserLine() {
        fun user(v: GraineUserVerdict, reason: String? = null, signedIn: Boolean = true) =
            SecurityText.user(GraineVerification(user = v, userReason = reason), signedIn)
        assertEquals("nobody signed in", user(GraineUserVerdict.VERIFIED, signedIn = false).value)
        assertEquals("not checked", user(GraineUserVerdict.OFF).value)
        assertEquals(SecurityText.Line("User", "verified", true, "identity token"), user(GraineUserVerdict.VERIFIED))
        assertEquals(true, user(GraineUserVerdict.TEST_USER).ok)
        val missing = user(GraineUserVerdict.UNVERIFIED, "missing")
        assertEquals("not proven", missing.value)
        assertEquals("no identity token — answered anonymously while Require verified users is on", missing.detail)
        assertEquals(
            "identity token expired — answered anonymously while Require verified users is on",
            user(GraineUserVerdict.INVALID, "expired").detail,
        )
        assertEquals("token refused — answered anonymously while Require verified users is on", user(GraineUserVerdict.INVALID).detail)
    }

    @Test
    fun theServerTokenLine() {
        assertEquals("off", SecurityText.serverToken(ServerTokenState.OFF, null, signedIn = true).value)
        assertEquals("not asked yet", SecurityText.serverToken(ServerTokenState.NOT_ASKED, null, signedIn = true).value)
        assertEquals(SecurityText.Line("Server token", "ok", true), SecurityText.serverToken(ServerTokenState.OK, null, signedIn = true))
        assertEquals(false, SecurityText.serverToken(ServerTokenState.MISSING, null, signedIn = true).ok, "signed in, and the server gave none")
        assertEquals(null, SecurityText.serverToken(ServerTokenState.MISSING, null, signedIn = false).ok, "nobody to mint for")
        val refused = SecurityText.serverToken(ServerTokenState.OK, SecurityRefusal.of(GraineErrorCode.TokenInvalid, "signature"), signedIn = true)
        assertEquals("refused", refused.value)
        assertTrue(refused.detail!!.startsWith("token_invalid (signature): "))
        // A refusal about something else leaves the token's own line alone.
        assertEquals("ok", SecurityText.serverToken(ServerTokenState.OK, SecurityRefusal.of(GraineErrorCode.AppNotAllowed), signedIn = true).value)
    }

    @Test
    fun theCardListsTheLastRefusalAndError() {
        val s = AgentSecurity(
            name = "help",
            agentId = "agt_0123456789abcdef",
            verification = GraineVerification(app = GraineAppVerdict.VERIFIED, user = GraineUserVerdict.VERIFIED),
            refusal = SecurityRefusal.of(GraineErrorCode.AppNotAllowed),
            lastError = "The chat could not connect.",
        )
        val lines = SecurityText.lines(s, signedIn = true)
        assertEquals(listOf("App", "User", "Server token", "Last refusal", "Last error"), lines.map { it.label })
        assertEquals("app_not_allowed", lines[3].value)
        assertEquals("agt_01234567…", SecurityText.idPrefix(s.agentId!!))
        // A token refusal shows on the token's line, not twice.
        val token = s.copy(refusal = SecurityRefusal.of(GraineErrorCode.TokenRequired), lastError = null)
        assertEquals(listOf("App", "User", "Server token"), SecurityText.lines(token, signedIn = true).map { it.label })
        // Before its settings load (a wrong key, no network), the card says so first.
        assertEquals("Settings", SecurityText.lines(token.copy(agentId = null), signedIn = true).first().label)
    }

    @Test
    fun anAgentThatChecksNothing() {
        val off = AgentSecurity(name = "help", agentId = "agt_1")
        assertTrue(SecurityText.checksNothing(off), "settings loaded, neither the app nor the user checked")
        assertFalse(SecurityText.checksNothing(off.copy(agentId = null)), "not before its settings have loaded")
        assertFalse(SecurityText.checksNothing(off.copy(verification = GraineVerification(app = GraineAppVerdict.UNVERIFIED))))
        assertFalse(SecurityText.checksNothing(off.copy(verification = GraineVerification(user = GraineUserVerdict.UNVERIFIED))))
    }

    @Test
    fun aClientWithoutTheSdkHasNoAgentsAndNoDebugToken() {
        assertTrue(FakeGraineClient().security.value.isEmpty())
        assertNull(FakeGraineClient().debugToken)
    }

    @Test
    fun theLogLine() {
        assertEquals(
            "app verified, user unverified (missing)",
            SecurityText.logLine(GraineVerification(app = GraineAppVerdict.VERIFIED, user = GraineUserVerdict.UNVERIFIED, userReason = "missing")),
        )
    }
}
