package ai.graine.demo

import ai.graine.demo.journey.JourneyRoute
import ai.graine.demo.journey.JourneyTap
import ai.graine.demo.journey.JourneyTaps
import ai.graine.inapp.kmp.GraineComponentTapCall
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A component's tap in the journey's chats ("When they tap"): a screen of the journey, one of its actions, or a notice. */
class JourneyTapsTest {
    /** The test journey's actions, by the pack's names. */
    private val actions = listOf("save_kickoff", "get_kickoff", "save_week", "get_week", "change_item", "log_progress")

    private fun call(action: String, vararg args: Pair<String, String>) = GraineComponentTapCall(action, mapOf(*args), null)

    @Test
    fun aScreenPathOpensTheJourneysScreenByTheNameTheAgentHears() {
        fun open(path: String) = JourneyTaps.decide(call("open_screen", "path" to path), actions)
        assertEquals(JourneyTap.Open(JourneyRoute.WEEK), open("week"))
        assertEquals(JourneyTap.Open(JourneyRoute.WEEK), open("plan_week"), "the week's name in the screen the agent hears")
        assertEquals(JourneyTap.Open(JourneyRoute.PROGRESS), open("/Progress/"))
        assertEquals(JourneyTap.Open(JourneyRoute.TODAY), open("app://journey/today?from=chat"))
        assertEquals(JourneyTap.Unknown("There is no screen called \"billing\" here."), open("billing"))
        assertEquals(JourneyTap.Unknown("The tap names no screen to open."), JourneyTaps.decide(call("open_screen"), actions))
    }

    @Test
    fun aJourneyActionRunsByItsNameAndAnythingElseIsANotice() {
        assertEquals(JourneyTap.Run("log_progress", mapOf("item" to "Lesson 1")), JourneyTaps.decide(call("log_progress", "item" to "Lesson 1"), actions))
        assertEquals(JourneyTap.Unknown("This app has no action called open_cart."), JourneyTaps.decide(call("open_cart"), actions))
    }

    @Test
    fun theJourneyRunsATapAndSaysWhatItCannotDo() = runTest {
        val m = JourneyHarness(this).model()
        m.start()
        m.runTap(call("open_screen", "path" to "progress"))
        assertEquals(JourneyRoute.PROGRESS, m.route)
        m.runTap(call("open_cart"))
        assertEquals("This app has no action called open_cart.", m.notice)
        m.notice = null
        m.runTap(call("get_kickoff"))
        assertNull(m.notice, "the journey's own action ran")
        m.runTap(call("save_kickoff"))
        assertEquals("Nothing was sent. Call it once more with everything the user told you.", m.notice, "its refusal, on screen")
    }
}
