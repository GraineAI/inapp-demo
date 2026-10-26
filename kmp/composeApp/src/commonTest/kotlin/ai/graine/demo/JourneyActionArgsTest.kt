package ai.graine.demo

import ai.graine.demo.journey.Args
import ai.graine.demo.journey.JourneyAgent
import ai.graine.demo.journey.JourneyAnswer
import ai.graine.demo.journey.JourneyTime
import ai.graine.demo.journey.Plan
import ai.graine.demo.journey.Weekday
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What each of the journey's actions reads from its arguments — maps and lists, as either platform hands them over. */
class JourneyActionArgsTest {
    private val ok = JourneyAnswer.Status.OK
    private val refused = JourneyAnswer.Status.REFUSED

    private fun week(vararg days: Pair<String, List<Map<String, Any?>>>) = mapOf(
        "title" to "Week",
        "days" to days.map { (d, items) -> mapOf("day" to d, "items" to items) },
    )

    private fun lesson(time: String, title: String = "Lesson", minutes: Any? = 20) = mapOf("time" to time, "kind" to "lesson", "title" to title, "minutes" to minutes)

    private fun TestScope.signedIn(): Pair<ai.graine.demo.journey.JourneyModel, FakeJourneySdk> {
        val h = JourneyHarness(this)
        val m = h.model()
        m.start()
        return m to h.sdk
    }

    @Test
    fun argumentsAreReadWhateverTypeTheyArriveAs() {
        assertEquals(2.5, Args.number("2.5"))
        assertEquals(3.0, Args.number(3))
        assertEquals(3.0, Args.number(3L))
        assertNull(Args.number(Double.NaN))
        assertNull(Args.number("lots"))
        assertEquals(true, Args.bool("yes"))
        assertEquals(false, Args.bool(0))
        assertEquals(true, Args.bool(" TRUE "))
        assertNull(Args.bool("maybe"))
        assertEquals("1800", Args.string(1800.0), "a whole number has no decimal point")
        assertEquals(listOf("a", "2"), Args.strings(listOf("a", 2, null, " ")))
        assertEquals(listOf("one"), Args.strings("one"))
        assertEquals(Weekday.TUE, Args.weekday("Tue"))
        assertEquals(Weekday.THU, Args.weekday("thursday"))
        assertEquals(Weekday.WED, Args.weekday("WED"))
        assertNull(Args.weekday("t"))
        assertNull(Args.weekday("someday"))
        assertEquals(mapOf("k" to 1), Args.map(mapOf("k" to 1)))
    }

    @Test
    fun timesAreKeptAsHhMm() {
        assertEquals("09:30", JourneyTime.normalise("9:30"))
        assertEquals("21:30", JourneyTime.normalise("9:30 pm"))
        assertEquals("00:15", JourneyTime.normalise("12:15 am"))
        assertEquals("12:00", JourneyTime.normalise("12 pm"))
        assertEquals("21:30", JourneyTime.normalise("21.30"))
        assertEquals("07:00", JourneyTime.normalise("7 a.m."))
        assertNull(JourneyTime.normalise("9"), "an hour alone is not a time")
        assertNull(JourneyTime.normalise("24:00"))
        assertNull(JourneyTime.normalise("13:00 pm"))
        assertNull(JourneyTime.normalise("08:61"))
        assertTrue(JourneyTime.quiet("23:00", "22:00", "07:00"))
        assertTrue(JourneyTime.quiet("06:59", "22:00", "07:00"))
        assertFalse(JourneyTime.quiet("07:00", "22:00", "07:00"), "the end is not quiet")
        assertFalse(JourneyTime.quiet("12:00", "22:00", "22:00"), "equal start and end: no quiet hours")
        assertTrue(JourneyTime.quiet("13:30", "13:00", "14:00"))
    }

    @Test
    fun thePlanActionRefusesWhatCannotBeAPlanAndSaysWhatToSend() {
        assertTrue((Plan.parse(mapOf("days" to emptyList<Any>()), 0) as Plan.Parsed.Refused).message.contains("title"))
        assertTrue((Plan.parse(mapOf("title" to "W"), 0) as Plan.Parsed.Refused).message.contains("days"))
        assertTrue((Plan.parse(mapOf("title" to "W", "days" to "mon"), 0) as Plan.Parsed.Refused).message.contains("days"))
        val nothing = Plan.parse(week("mon" to listOf(mapOf("time" to "soon", "kind" to "lesson", "title" to "x"), mapOf("time" to "08:00", "title" to "no kind"))), 0)
        assertTrue((nothing as Plan.Parsed.Refused).message.contains("time (HH:MM), a kind and a title"))
    }

    @Test
    fun aPlanWithSomeBadPartsKeepsTheRestAndSaysWhatWasLeftOut() {
        val r = Plan.parse(
            mapOf(
                "title" to "W",
                "days" to listOf(
                    mapOf("day" to "funday", "items" to listOf(lesson("08:00"))),
                    mapOf("day" to "mon", "items" to listOf(lesson("08:00"), mapOf("kind" to "lesson", "title" to "no time"), "not an item")),
                    mapOf("day" to "tue", "items" to (0 until 20).map { lesson("${(6 + it / 2).toString().padStart(2, '0')}:${if (it % 2 == 0) "00" else "30"}") }),
                    mapOf("day" to "mon", "items" to listOf(lesson("09:00", "Again"))),
                ),
            ),
            0,
        ) as Plan.Parsed.Ok
        assertEquals(listOf("Again"), r.plan.items(Weekday.MON).map { it.title }, "a day sent twice: the last one is kept")
        assertEquals(16, r.plan.items(Weekday.TUE).size, "at most 16 items a day")
        val notes = r.notes.joinToString(" ")
        assertTrue(notes.contains("1 day entry without a valid day"), notes)
        assertTrue(notes.contains("2 items without a valid time"), notes)
        assertTrue(notes.contains("4 items over 16 a day"), notes)
        assertTrue(notes.contains("Monday was sent twice"), notes)
    }

    @Test
    fun thePlanActionSavesAndAnswersWithWhatItSaved() = runTest {
        val (m, sdk) = signedIn()
        val answer = sdk.run(JourneyAgent.COACH, "save_week", week("mon" to listOf(lesson("08:00"), lesson("19:00")), "tue" to listOf(lesson("08:00"))))
        assertEquals(ok, answer.status)
        assertEquals(mapOf("days" to 2, "items" to 3), answer.data)
        assertTrue(answer.message.contains("2 days, 3 items"), answer.message)
        assertEquals(3, m.plan?.itemCount)
        m.close()
    }

    @Test
    fun thePlanReaderAnswersOneDayOrTheWeekAndNeverGuesses() = runTest {
        val (m, sdk) = signedIn()
        assertEquals(mapOf("has_plan" to false), sdk.run(JourneyAgent.COACH, "get_week").data, "no plan yet: it says so")
        sdk.run(JourneyAgent.COACH, "save_week", week("mon" to listOf(lesson("08:00")), "wed" to listOf(lesson("08:00", "Lesson 3"))))
        val wed = sdk.run(JourneyAgent.COACH, "get_week", mapOf("day" to "Wed"))
        assertEquals(ok, wed.status)
        assertEquals(listOf("wed"), (wed.data["days"] as List<*>).map { (it as Map<*, *>)["day"] })
        assertEquals("wed", wed.data["today"], "14 Oct 2026 is a Wednesday")
        val all = sdk.run(JourneyAgent.COACH, "get_week")
        assertEquals(2, (all.data["days"] as List<*>).size)
        assertEquals(refused, sdk.run(JourneyAgent.COACH, "get_week", mapOf("day" to "someday")).status)
        m.close()
    }

    @Test
    fun theSwapActionNeedsTheDayTheTimeAndTheNewItem() = runTest {
        val (m, sdk) = signedIn()
        assertEquals(refused, sdk.run(JourneyAgent.COACH, "change_item", mapOf("day" to "mon", "time" to "08:00", "title" to "X")).status, "no plan yet")
        sdk.run(JourneyAgent.COACH, "save_week", week("mon" to listOf(lesson("08:00", "Lesson 1", minutes = 20), lesson("19:00", "Practice"))))
        assertTrue(sdk.run(JourneyAgent.COACH, "change_item", mapOf("time" to "08:00", "title" to "X")).message.contains("which day"))
        assertTrue(sdk.run(JourneyAgent.COACH, "change_item", mapOf("day" to "mon", "title" to "X")).message.contains("time"))
        assertTrue(sdk.run(JourneyAgent.COACH, "change_item", mapOf("day" to "mon", "time" to "08:00")).message.contains("title"))
        val missing = sdk.run(JourneyAgent.COACH, "change_item", mapOf("day" to "mon", "time" to "10:00", "title" to "X"))
        assertEquals(refused, missing.status)
        assertTrue(missing.message.contains("08:00 Lesson 1, 19:00 Practice"), "the day's items, so the agent can pick: ${missing.message}")
        val done = sdk.run(JourneyAgent.COACH, "change_item", mapOf("day" to "Monday", "time" to "8:00 am", "title" to "Lesson 1 video", "details" to "Watch it", "minutes" to "15", "reason" to "Prefers video"))
        assertEquals(ok, done.status)
        val item = m.plan!!.item(Weekday.MON, "08:00")!!
        assertEquals("Lesson 1 video", item.title)
        assertEquals("Watch it", item.details)
        assertEquals(mapOf("minutes" to 15.0), item.figures, "the figures sent with the new item")
        assertEquals(listOf("Lesson 1"), item.swaps)
        assertEquals("Prefers video", item.swapReason)
        m.close()
    }

    @Test
    fun theCheckInActionKeepsHowAnItemWentForTheDayItMeans() = runTest {
        val (m, sdk) = signedIn()
        sdk.run(JourneyAgent.COACH, "save_week", week("mon" to listOf(lesson("08:00", "Lesson 1"))))
        assertTrue(sdk.run(JourneyAgent.COACH, "log_progress", mapOf("followed" to true)).message.contains("which day"))
        assertTrue(sdk.run(JourneyAgent.COACH, "log_progress", mapOf("day" to "mon")).message.contains("followed"))
        assertEquals(refused, sdk.run(JourneyAgent.COACH, "log_progress", mapOf("day" to "mon", "followed" to true, "time" to "breakfast")).status)
        val logged = sdk.run(JourneyAgent.COACH, "log_progress", mapOf("day" to "mon", "time" to "8:00", "followed" to "yes", "note" to "x".repeat(300)))
        assertEquals(ok, logged.status)
        assertTrue(logged.message.contains("Lesson 1"), logged.message)
        val c = m.activity.checkIns.single()
        assertEquals(kotlinx.datetime.LocalDate(2026, 10, 12), c.date, "Monday: the most recent one")
        assertEquals("08:00", c.time)
        assertTrue(c.followed)
        assertEquals(200, c.note?.length, "a note is at most 200 characters")
        val today = sdk.run(JourneyAgent.COACH, "log_progress", mapOf("day" to "wed", "followed" to false, "time" to "13:00"))
        assertTrue(today.message.contains("no plan item at 13:00"), today.message)
        assertEquals(kotlinx.datetime.LocalDate(2026, 10, 14), m.activity.checkIns.last().date, "today's weekday is today")
        m.close()
    }

    @Test
    fun theIntakeActionNeedsWhatThePackRequiresAndKeepsItBounded() = runTest {
        val (m, sdk) = signedIn()
        assertEquals(refused, sdk.run(JourneyAgent.FIRST_CALL, "save_kickoff", emptyMap()).status)
        val missing = sdk.run(JourneyAgent.FIRST_CALL, "save_kickoff", mapOf("goals" to "Pass the exam"))
        assertEquals(refused, missing.status)
        assertTrue(missing.message.contains("Missing: level"), missing.message)
        val saved = sdk.run(
            JourneyAgent.FIRST_CALL,
            "save_kickoff",
            mapOf(
                "level" to "beginner",
                "goals" to "g".repeat(900),
                "topics" to (1..30).map { "topic $it" },
                "hours" to 6L,
                "ok" to true,
                "blank" to "  ",
                "nested" to mapOf("a" to "b", "n" to 2),
                "nothing" to null,
            ),
        )
        assertEquals(ok, saved.status)
        val values = m.intake!!.values
        assertEquals(500, (values["goals"] as String).length, "text is at most 500 characters")
        assertEquals(20, (values["topics"] as List<*>).size, "lists hold at most 20")
        assertEquals(6.0, values["hours"])
        assertEquals(true, values["ok"])
        assertEquals(mapOf("a" to "b", "n" to "2"), values["nested"])
        assertFalse("blank" in values || "nothing" in values, "nothing empty is kept")
        val read = sdk.run(JourneyAgent.COACH, "get_kickoff")
        assertEquals(true, read.data["has_consultation"])
        assertEquals("beginner", read.data["level"])
        m.close()
    }

    @Test
    fun everyActionRefusesWhileNobodyIsSignedIn() = runTest {
        val h = JourneyHarness(this)
        val m = h.model()
        for ((agent, name) in listOf(
            JourneyAgent.FIRST_CALL to "save_kickoff", JourneyAgent.COACH to "get_kickoff", JourneyAgent.COACH to "save_week",
            JourneyAgent.COACH to "get_week", JourneyAgent.COACH to "change_item", JourneyAgent.COACH to "log_progress",
        )) {
            val answer = h.sdk.run(agent, name, mapOf("level" to "x", "title" to "t", "days" to emptyList<Any>(), "day" to "mon", "followed" to true))
            assertEquals(refused, answer.status, name)
            assertTrue(answer.message.contains("sign in"), answer.message)
        }
        assertEquals(setOf("save_kickoff"), h.sdk.actions[JourneyAgent.FIRST_CALL]?.keys?.toSet(), "the intake on the first-call agent")
        assertEquals(setOf("get_kickoff", "save_week", "get_week", "change_item", "log_progress"), h.sdk.actions[JourneyAgent.COACH]?.keys?.toSet(), "the plan's on the coach")
        m.close()
    }
}
