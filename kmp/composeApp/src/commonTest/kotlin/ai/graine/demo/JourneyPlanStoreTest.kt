package ai.graine.demo

import ai.graine.demo.journey.JourneyAgent
import ai.graine.demo.journey.JourneyAnswer
import ai.graine.demo.journey.Plan
import ai.graine.demo.journey.PlanItem
import ai.graine.demo.journey.Weekday
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The plan the coach saves through the app's actions, kept on the phone: read, stored, survived, changed, erased. */
class JourneyPlanStoreTest {
    /** A week as the model sends it on either platform: numbers as Int, Long, Double or text, days out of order. */
    private val week: Map<String, Any?> = mapOf(
        "title" to "A first week",
        "daily_targets" to mapOf("minutes" to 45, "sessions" to 2L, "focus" to "1.5"),
        "days" to listOf(
            mapOf("day" to "tue", "items" to listOf(mapOf("time" to "8:00", "kind" to "lesson", "title" to "Lesson 2", "minutes" to 20.0))),
            mapOf(
                "day" to "Monday",
                "items" to listOf(
                    mapOf("time" to "19:00", "kind" to "Practice", "title" to "Practice set A", "minutes" to "25", "why" to "Repetition sticks."),
                    mapOf("time" to "08:00", "kind" to "lesson", "title" to "Lesson 1", "minutes" to 20, "swaps" to listOf("Lesson 1 video", "Lesson 1 notes", "x", "y")),
                ),
            ),
        ),
    )

    @Test
    fun theWholeWeekIsReadFromMapsAndListsMondayFirstEachDayInTimeOrder() {
        val r = Plan.parse(week, savedAt = 7L)
        val plan = (r as Plan.Parsed.Ok).plan
        assertTrue(r.notes.isEmpty(), r.notes.toString())
        assertEquals("A first week", plan.title)
        assertEquals(mapOf("minutes" to 45.0, "sessions" to 2.0, "focus" to 1.5), plan.targets)
        assertEquals(listOf(Weekday.MON, Weekday.TUE), plan.days.map { it.day })
        assertEquals(listOf("08:00", "19:00"), plan.items(Weekday.MON).map { it.time })
        val practice = plan.item(Weekday.MON, "19:00")!!
        assertEquals("practice", practice.kind, "kinds are kept as the pack writes them: lower case")
        assertEquals(mapOf("minutes" to 25.0), practice.figures, "a number sent as text is a figure too")
        assertEquals(listOf("Lesson 1 video", "Lesson 1 notes", "x"), plan.item(Weekday.MON, "08:00")!!.swaps, "at most three swaps")
        assertEquals(3, plan.itemCount)
        assertEquals(Plan.Source.AGENT, plan.source)
    }

    @Test
    fun aStoredPlanReadsBackExactly() {
        val plan = (Plan.parse(week, savedAt = 99L, source = Plan.Source.SAMPLE) as Plan.Parsed.Ok).plan
        val again = Plan.fromStored(plan.toWire(withMeta = true))
        assertEquals(plan, again)
        assertEquals(listOf("tue"), (plan.toWire(Weekday.TUE)["days"] as List<*>).map { (it as Map<*, *>)["day"] }, "one day alone")
    }

    @Test
    fun thePlanItsCheckInsAndSettingsSurviveARestartOfTheApp() = runTest {
        val h = JourneyHarness(this)
        val m = h.model()
        m.start()
        assertEquals(JourneyAnswer.Status.OK, h.sdk.run(JourneyAgent.COACH, "save_week", week).status)
        m.setQuietHours("21:30", "06:30")
        m.markDone(ai.graine.demo.journey.ItemRef(Weekday.WED, "08:00"), true) // no item on Wednesday: nothing kept
        h.sdk.run(JourneyAgent.COACH, "log_progress", mapOf("day" to "mon", "time" to "08:00", "followed" to true, "note" to "Done before work"))
        m.close()

        val again = JourneyHarness(this, files = h.files).model()
        assertEquals(m.plan, again.plan, "the same plan, after a restart")
        assertEquals("21:30", again.settings.quietStart)
        assertEquals("06:30", again.settings.quietEnd)
        val checkIn = again.activity.checkIns.single()
        assertEquals("08:00", checkIn.time)
        assertEquals("Done before work", checkIn.note)
        assertEquals("agent", checkIn.source)
        assertEquals(kotlinx.datetime.LocalDate(2026, 10, 12), checkIn.date, "Monday of the test's week (Wednesday 14 Oct)")
        assertEquals("user-test-1", again.account?.userId)
        again.close()
    }

    @Test
    fun aDamagedFileReadsAsNothingNeverAsACrash() = runTest {
        val files = MemoryTextStore()
        files.files["test-pack.plan.json"] = "{\"title\": \"half a fi"
        files.files["test-pack.account.json"] = "[1, 2"
        files.files["test-pack.activity.json"] = "{\"check_ins\": [{\"date\": \"not a date\", \"followed\": true}], \"tracker\": {\"2026-10-14\": \"x\"}}"
        val m = JourneyHarness(this, files = files).model()
        assertNull(m.plan)
        assertNull(m.account)
        assertTrue(m.activity.checkIns.isEmpty() && m.activity.tracker.isEmpty())
        assertTrue(!m.open)
        m.close()
    }

    @Test
    fun aSwapReplacesOneItemAndKeepsTheOldOneAsASwapToGoBackTo() {
        val item = PlanItem("13:00", "lesson", "Lesson 3", details = "Chapter 3", figures = mapOf("minutes" to 30.0), swaps = listOf("Lesson 3 video"))
        val swapped = item.swappedFor("Lesson 3 video", null, emptyMap(), "Prefers video")
        assertEquals("Lesson 3 video", swapped.title)
        assertNull(swapped.details, "what described the old item gives way")
        assertTrue(swapped.figures.isEmpty())
        assertEquals(listOf("Lesson 3"), swapped.swaps, "the chosen swap leaves the list; the old title joins it")
        assertEquals("Lesson 3", swapped.swappedFrom)
        assertEquals("Prefers video", swapped.swapReason)
    }

    @Test
    fun startingOverErasesEverythingAndNoEarlierWriteBringsItBack() = runTest {
        val h = JourneyHarness(this)
        val m = h.model()
        m.start()
        m.saveProfile(TestJourney.profile)
        h.sdk.run(JourneyAgent.COACH, "save_week", week)
        assertTrue(h.files.files.keys.any { it.endsWith("plan.json") })
        m.logOut()
        m.eraseThisPhone()
        assertTrue(h.files.files.isEmpty(), "nothing of the journey left: ${h.files.files.keys}")
        m.close()
        val again = JourneyHarness(this, files = h.files).model()
        assertNull(again.account)
        assertNull(again.plan)
        again.close()
    }

    @Test
    fun checkInsAreKeptBoundedNewestFirstToGo() = runTest {
        val h = JourneyHarness(this)
        val m = h.model()
        m.start()
        h.sdk.run(JourneyAgent.COACH, "save_week", week)
        repeat(320) { h.sdk.run(JourneyAgent.COACH, "log_progress", mapOf("day" to "mon", "followed" to (it % 2 == 0))) }
        assertEquals(300, m.activity.checkIns.size, "at most 300 kept")
        assertNotNull(m.activity.checkIns.last())
        m.close()
    }
}
