package ai.graine.demo

import ai.graine.demo.domain.UseCasePacks
import ai.graine.demo.journey.CheckInSchedule
import ai.graine.demo.journey.JourneyPack
import ai.graine.demo.journey.JourneyProfile
import ai.graine.demo.journey.JourneySettings
import ai.graine.demo.journey.JourneyStats
import ai.graine.demo.journey.JourneyText
import ai.graine.demo.journey.Picker
import ai.graine.demo.journey.Plan
import ai.graine.demo.journey.PlanItem
import ai.graine.demo.journey.ProfileRules
import ai.graine.demo.journey.Weekday
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The shipped packs' journeys, and the journey's pure rules: profile, picker, reminders, figures. */
class JourneyPackTest {
    private val health get() = UseCasePacks.load().first { it.id == "health-coach" }

    @Test
    fun theDefaultPackCarriesTheWholeJourneyAndTheOthersNone() {
        val j = assertNotNull(health.journey, "health-coach.json has a journey")
        assertEquals("user_id", j.identifierField)
        assertEquals(
            listOf("save_consultation", "get_consultation", "save_plan", "get_plan", "swap_item", "log_check_in"),
            with(j.actions) { listOf(saveIntake, getIntake, savePlan, getPlan, swapItem, logCheckIn) },
            "the names the agents declare (agent-setup/health-coach/journey)",
        )
        assertEquals("consultation_finished", j.events.firstCallFinished)
        assertEquals("meal_check_in", j.events.checkIn)
        assertEquals(listOf("10:30", "13:00", "17:30", "20:00"), j.firstCall.preferredTimes)
        assertEquals(listOf("en", "hi", "hi"), j.onboarding.language.options.map { it.code }, "Hinglish on the Hindi voice, its style a variable")
        assertEquals("22:00" to "07:00", j.checkIns.quietStart to j.checkIns.quietEnd)
        for (id in listOf("banking-support", "retail-shopping")) assertNull(UseCasePacks.load().first { it.id == id }.journey, id)
    }

    @Test
    fun theSamplePlanReadsAsTheCoachWouldSaveIt() {
        val j = health.journey!!
        val r = Plan.parse(j.plan.sample, 0) as Plan.Parsed.Ok
        assertTrue(r.notes.isEmpty(), r.notes.toString())
        assertEquals(Weekday.entries, r.plan.days.map { it.day }, "all seven days")
        assertEquals(35, r.plan.itemCount)
        val kinds = j.plan.kinds.map { it.id }.toSet()
        assertTrue(r.plan.days.flatMap { it.items }.all { it.kind in kinds }, "every item's kind is one the pack names")
        assertTrue(j.checkIns.kinds.all { it in kinds })
        assertEquals(setOf("kcal", "protein_g", "water_l"), r.plan.targets.keys)
        assertEquals("water_l", j.today.tracker?.target)
    }

    @Test
    fun theJourneysVariablesFitTheSdksLimitsAndNoActionNameIsTheTours() {
        for (pack in listOf(health, TestJourney.pack)) {
            val j = pack.journey!!
            assertTrue(j.variableNames.size <= 12, "${pack.id}: at most 12 variables (${j.variableNames})")
            assertTrue(j.variableNames.none { it.startsWith("graine_") || it == j.identifierField })
            val names = with(j.actions) { listOf(saveIntake, getIntake, savePlan, getPlan, swapItem, logCheckIn) }
            assertEquals(names.size, names.toSet().size)
            assertTrue(names.none { n -> pack.actions.any { it.name == n } }, "${pack.id}: a journey action never replaces a tour action")
            assertNotNull(j.nameField)
        }
    }

    @Test
    fun aProfileIsCheckedAndBecomesTheAgentsVariables() {
        val j = health.journey!!
        val today = LocalDate(2026, 10, 14)
        val answers = mapOf(
            "name" to "Riya", "gender" to "female", "birth_date" to "1992-10-15", "weight_kg" to "64.5", "height_cm" to "163",
            "goal" to "Lose weight", "diet" to "vegetarian",
        )
        assertTrue(ProfileRules.problems(j.onboarding.fields, answers, today).isEmpty())
        val v = ProfileRules.variables(j, JourneyProfile(answers, "hinglish"), today)
        assertEquals("33", v["age"], "a day before the 34th birthday")
        assertEquals("64.5", v["weight_kg"])
        assertEquals("163", v["height_cm"])
        assertEquals("Lose weight", v["goal"])
        assertEquals(null, v["health_notes"], "not answered: removed")
        assertEquals("hi", v["agent_language"])
        assertEquals("Hinglish: Hindi and English mixed, in Latin script", v["language_style"])
        assertEquals(mapOf("user_id" to "u-1", "name" to "Riya"), ProfileRules.traits(j, "u-1", JourneyProfile(answers)))

        val bad = ProfileRules.problems(j.onboarding.fields, answers + mapOf("weight_kg" to "12", "birth_date" to "2015-01-01", "gender" to "x", "name" to ""), today)
        assertEquals(setOf("weight_kg", "birth_date", "gender", "name"), bad.keys)
        assertEquals("Between 30 and 250 kg.", bad["weight_kg"])
        assertEquals(17, ProfileRules.age(LocalDate(2009, 2, 28), LocalDate(2026, 2, 28)))
    }

    @Test
    fun thePickerOffersThePreferredTimesElseAFewOfTheDaysOwn() {
        val zone = TestClock.zone
        fun at(day: LocalDate, h: Int, m: Int) = LocalDateTime(day.year, day.monthNumber, day.dayOfMonth, h, m).toInstant(zone)
        val today = LocalDate(2026, 10, 14)
        // A narrow calling window, 14:00–16:00: no preferred time fits, so a few of the window's own are offered.
        val narrow: (LocalDate) -> List<Instant> = { d -> (0 until 24).map { at(d, 14 + it / 12, (it % 12) * 5) } }
        val days = Picker.days(today, 2, 14, listOf("10:30", "13:00"), "22:00", "07:00", zone, narrow)
        assertEquals(listOf(at(today, 14, 0), at(today, 15, 30)), days[0].times, "on the hour or half hour, 90 minutes apart")
        assertEquals(1, Picker.days(today, 5, 0, listOf("10:30"), "22:00", "07:00", zone, narrow).size, "never past the agent's days ahead")
        assertEquals(today, Picker.open(days).day)
    }

    @Test
    fun checkInIdsNameTheItemAndTheDate() {
        val id = CheckInSchedule.id(LocalDate(2026, 10, 27), "08:00")
        assertEquals("2026-10-27T08:00", id)
        assertEquals(LocalDate(2026, 10, 27) to "08:00", CheckInSchedule.parse(id))
        assertNull(CheckInSchedule.parse("2026-10-27"))
        assertNull(CheckInSchedule.parse("tomorrowT08:00"))
    }

    @Test
    fun remindersNeverExceedWhatThePhoneKeeps() {
        val j = TestJourney.pack.journey!!
        val busy = Plan("W", emptyMap(), Weekday.entries.map { d -> ai.graine.demo.journey.PlanDay(d, (8..20).map { h -> PlanItem("${h.toString().padStart(2, '0')}:00", "lesson", "L$h") }) }, 0, Plan.Source.AGENT)
        val list = CheckInSchedule.build(busy, j, JourneySettings(quietStart = "00:00", quietEnd = "00:00"), Instant.fromEpochMilliseconds(TestClock.at(0)), TestClock.zone)
        assertEquals(CheckInSchedule.MAX, list.size)
        assertEquals(list.sortedBy { it.at }, list)
    }

    @Test
    fun upNextIsTheNextItemTodayElseTheNextDaysFirst() {
        val plan = Plan(
            "W", emptyMap(),
            listOf(
                ai.graine.demo.journey.PlanDay(Weekday.WED, listOf(PlanItem("08:00", "lesson", "A"), PlanItem("18:00", "lesson", "B"))),
                ai.graine.demo.journey.PlanDay(Weekday.FRI, listOf(PlanItem("09:00", "lesson", "C"))),
            ),
            0, Plan.Source.AGENT,
        )
        val wed = LocalDate(2026, 10, 14)
        assertEquals("B", JourneyStats.upNext(plan, wed, "12:00")?.second?.title)
        assertEquals(LocalDate(2026, 10, 16) to "C", JourneyStats.upNext(plan, wed, "19:00")?.let { it.first to it.second.title })
        assertEquals("1700 kcal", JourneyText.figure(1700.0, "kcal"))
        assertEquals("2.5 L", JourneyText.figure(2.5, "L"))
        assertEquals("7 days · 35 actions", JourneyText.planLine("{days} days · {actions} actions", Plan.parse(health.journey!!.plan.sample, 0).let { (it as Plan.Parsed.Ok).plan }))
    }

    @Test
    fun aJourneyBlockMissingWhatItNeedsLeavesThePackWithoutOne() {
        val broken = TestJourney.pack.let { ai.graine.demo.domain.UseCasePack.parse(TestPack.json.trimEnd().removeSuffix("}") + ",\n\"journey\": {\"title\": \"x\"}\n}") }
        assertNull(broken.journey)
        assertEquals("test-pack", broken.id, "the tour still reads")
        val j: JourneyPack = TestJourney.pack.journey!!
        assertEquals("Ari", j.agents.coach.name)
    }
}
