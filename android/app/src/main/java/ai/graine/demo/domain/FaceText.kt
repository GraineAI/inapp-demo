package ai.graine.demo.domain

import ai.graine.inapp.GraineAvatarState

/** The agent's face, as the demo words it: the line under "Show the agent's face" for every state the SDK reports. */
object FaceText {
    fun caption(switchedOn: Boolean, face: GraineAvatarState): String {
        if (!switchedOn) return "Calls are voice only."
        return when (face.phase) {
            GraineAvatarState.Phase.LIVE -> "The face is speaking."
            GraineAvatarState.Phase.AVAILABLE -> "Connecting the face…"
            GraineAvatarState.Phase.OFF -> when (face.reason) {
                "busy" -> "Every face is in use right now. This call is voice only."
                "off" -> "This agent has no face yet: turn on Avatar for it in the dashboard."
                "unsupported" -> "This call can't show a face. It continues with voice."
                "failed", "stalled", "no_voice" -> "The face isn't available right now. The call continues with voice."
                else -> "Shown on your next call with the agent."
            }
        }
    }
}
