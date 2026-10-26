package ai.graine.demo

import ai.graine.demo.domain.VerificationText
import ai.graine.inapp.kmp.GraineAppVerdict
import ai.graine.inapp.kmp.GraineUserVerdict
import ai.graine.inapp.kmp.GraineVerification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Profile's "What Graine has proven" lines, from the SDK's verification state. */
class VerificationTextTest {
    @Test
    fun nothingIsCheckedUntilTheAgentAsks() {
        val v = FakeGraineClient().verification.value
        assertEquals(GraineVerification(), v)
        assertEquals("not checked", VerificationText.app(v))
        assertEquals("not checked", VerificationText.user(v))
        assertNull(FakeGraineClient().debugToken)
    }

    @Test
    fun theAppLine() {
        assertEquals("debug token ✓", VerificationText.app(GraineVerification(app = GraineAppVerdict.DEBUG_TOKEN)))
        assertEquals("verified ✓", VerificationText.app(GraineVerification(app = GraineAppVerdict.VERIFIED)))
        assertEquals("development build ✓", VerificationText.app(GraineVerification(app = GraineAppVerdict.TEST_BUILD)))
        assertEquals("checking…", VerificationText.app(GraineVerification(app = GraineAppVerdict.CHECKING)))
        assertEquals("not verified", VerificationText.app(GraineVerification(app = GraineAppVerdict.UNVERIFIED)))
        assertEquals(
            "not verified (debug token not registered)",
            VerificationText.app(GraineVerification(app = GraineAppVerdict.UNVERIFIED, appReason = "debug_token_unknown")),
        )
        assertEquals(
            "not verified (debug token expired)",
            VerificationText.app(GraineVerification(app = GraineAppVerdict.UNVERIFIED, appReason = "debug_token_expired")),
        )
        // A reason this build does not know reads as Graine said it.
        assertEquals("not verified (new reason)", VerificationText.app(GraineVerification(app = GraineAppVerdict.UNVERIFIED, appReason = "new_reason")))
    }

    @Test
    fun theUserLine() {
        // The demo sends no identity token: the person stays unproven, and the agent keeps its users on report only.
        assertEquals("unverified (report only)", VerificationText.user(GraineVerification(user = GraineUserVerdict.UNVERIFIED, userReason = "missing")))
        assertEquals("verified ✓", VerificationText.user(GraineVerification(user = GraineUserVerdict.VERIFIED)))
        assertEquals("test user ✓", VerificationText.user(GraineVerification(user = GraineUserVerdict.TEST_USER)))
        assertEquals("token refused (not yet valid)", VerificationText.user(GraineVerification(user = GraineUserVerdict.INVALID, userReason = "not_yet_valid")))
    }
}
