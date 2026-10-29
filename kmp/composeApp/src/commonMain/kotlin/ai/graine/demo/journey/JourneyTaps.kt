package ai.graine.demo.journey

import ai.graine.inapp.kmp.GraineComponentTap
import ai.graine.inapp.kmp.GraineComponentTapCall

/** What a component's tap does in the journey ("When they tap", SDK 0.14.8), decided before it runs ([JourneyTaps]). */
sealed class JourneyTap {
    /** Open one of the journey's screens: `open_screen` with a path naming it. */
    data class Open(val route: JourneyRoute) : JourneyTap()

    /** Run one of the journey's own actions, by the pack's name for it. */
    data class Run(val action: String, val args: Map<String, String>) : JourneyTap()

    /** Nothing in the journey does it: the notice to show. The agent is answered anyway. */
    data class Unknown(val notice: String) : JourneyTap()
}

/** The journey's handler for a component's tap. Pure: [JourneyModel.runTap] does what it decides. */
object JourneyTaps {
    /** [call] in a journey whose own actions are [actions] (the pack's names). */
    fun decide(call: GraineComponentTapCall, actions: Collection<String>): JourneyTap = when (call.action) {
        GraineComponentTap.NAVIGATE_ACTION -> {
            val path = call.args["path"].orEmpty().trim()
            route(path)?.let { JourneyTap.Open(it) }
                ?: JourneyTap.Unknown(if (path.isEmpty()) "The tap names no screen to open." else "There is no screen called \"$path\" here.")
        }
        in actions -> JourneyTap.Run(call.action, call.args)
        else -> JourneyTap.Unknown("This app has no action called ${call.action}.")
    }

    /**
     * The journey's screen a path names, by its last part ("week", "/week/", "app://journey/week?from=chat") and as
     * the agent hears the screens: a route's own name, or `plan_week` for the week.
     */
    fun route(path: String): JourneyRoute? {
        val word = path.substringBefore('?').substringBefore('#').trim().trimEnd('/').substringAfterLast('/').trim().lowercase()
        if (word == "plan_week") return JourneyRoute.WEEK
        return JourneyRoute.entries.firstOrNull { it.name.lowercase() == word }
    }
}
