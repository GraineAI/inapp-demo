package ai.graine.demo

import ai.graine.demo.domain.FaceText
import ai.graine.inapp.kmp.GraineAvatarState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The line under "Show the agent's face", for every state the SDK reports. */
class FaceTextTest {
    private fun state(phase: GraineAvatarState.Phase, reason: String? = null) = GraineAvatarState(phase, reason)

    @Test fun offSaysVoiceOnly() {
        assertEquals("Calls are voice only.", FaceText.caption(false, state(GraineAvatarState.Phase.LIVE)))
    }

    @Test fun eachStateSaysWhatTheCustomerSees() {
        assertEquals("The face is speaking.", FaceText.caption(true, state(GraineAvatarState.Phase.LIVE)))
        assertEquals("Connecting the face…", FaceText.caption(true, state(GraineAvatarState.Phase.AVAILABLE)))
        assertEquals("Shown on your next call with the agent.", FaceText.caption(true, GraineAvatarState.OFF))
        assertTrue(FaceText.caption(true, state(GraineAvatarState.Phase.OFF, "busy")).startsWith("Every face is in use"))
        assertTrue("turn on Avatar" in FaceText.caption(true, state(GraineAvatarState.Phase.OFF, "off")))
        assertTrue("continues with voice" in FaceText.caption(true, state(GraineAvatarState.Phase.OFF, "unsupported")))
    }
}
