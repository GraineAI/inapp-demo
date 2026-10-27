package ai.graine.demo

import ai.graine.demo.journey.CheckInSchedule
import ai.graine.demo.journey.JourneyAgent
import ai.graine.demo.journey.JourneyAnswer
import ai.graine.demo.journey.JourneyModel
import ai.graine.demo.journey.JourneyRoute
import ai.graine.demo.journey.JourneyStage
import ai.graine.demo.journey.JourneyStats
import ai.graine.demo.journey.NoConnect
import ai.graine.demo.journey.Weekday
import ai.graine.inapp.kmp.GraineCallInfo
import ai.graine.inapp.kmp.GraineCallOutcome
import ai.graine.inapp.kmp.GraineCallSource
import ai.graine.inapp.kmp.GraineCallState
import ai.graine.inapp.kmp.GraineCallStatus
import ai.graine.inapp.kmp.GraineChatStatus
import ai.graine.inapp.kmp.GraineErrorCode
import ai.graine.inapp.kmp.GraineException
import ai.graine.inapp.kmp.GraineMissedCall
import ai.graine.inapp.kmp.GraineScheduledCall
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The small cases that break a journey in the field: duplicates, drops, the app closed, nothing arriving, overlaps. */
@OptIn(ExperimentalCoroutinesApi::class)
class JourneyEdgeCasesTest {
    private val firstCallAgent = JourneyAgent.FIRST_CALL.sdkName
    private val coach get() = JourneyAgent.COACH

    private fun TestScope.started(h: JourneyHarness = JourneyHarness(this)): Pair<JourneyModel, JourneyHarness> {
        h.sdk.slots = { day ->
            (9 * 12 until 21 * 12).map { i -> LocalDateTime(day.year, day.monthNumber, day.dayOfMonth, i / 12, (i % 12) * 5).toInstant(TestClock.zone) }
                .filter { it.toEpochMilliseconds() >= h.now() + 5 * 60_000 }
        }
        val m = h.model()
        m.start()
        m.onboardingNext()
        m.saveProfile(TestJourney.profile)
        m.chooseLanguage("en")
        m.allowMicrophone()
        m.allowNotifications()
        return m to h
    }

    private fun outcome(kind: GraineCallOutcome.Kind, reason: String? = null, endCause: String? = null) =
        GraineCallOutcome("call_1", kind, reason = reason, agent = firstCallAgent, endCause = endCause)

    private fun week(vararg days: String) = mapOf(
        "title" to "W",
        "days" to days.map { d -> mapOf("day" to d, "items" to listOf(mapOf("time" to "08:00", "kind" to "lesson", "title" to "Lesson $d"))) },
    )

    private suspend fun TestScope.building(): Pair<JourneyModel, JourneyHarness> {
        val (m, h) = started()
        m.startFirstCall()
        h.sdk.run(JourneyAgent.FIRST_CALL, "save_kickoff", mapOf("level" to "beginner"))
        h.sdk.outcomes.tryEmit(outcome(GraineCallOutcome.Kind.ENDED, "remote"))
        assertEquals(JourneyStage.BUILDING_PLAN, m.stage)
        return m to h
    }

    // ── the plan ────────────────────────────────────────────────────────────

    @Test
    fun aPlanSavedTwiceKeepsTheLastAndMovesOnOnce() = runTest {
        val (m, h) = building()
        h.sdk.chats.getValue(coach).value = GraineChatStatus.CONNECTED
        h.sdk.run(coach, "save_week", week("wed"))
        val shown = h.sdk.shown.toList()
        h.sdk.run(coach, "save_week", week("thu", "fri"))
        assertEquals(JourneyStage.READY, m.stage)
        assertEquals(listOf(Weekday.THU, Weekday.FRI), m.plan!!.days.map { it.day }, "the last one saved")
        assertEquals(shown, h.sdk.shown, "the chat is let go once")
        assertEquals(listOf("2026-10-15T08:00", "2026-10-16T08:00"), h.reminders.scheduled.map { it.id }, "the reminders follow the last plan")
        m.close()
    }

    @Test
    fun aPlanAskedForInAConversationThatEndedIsAskedForAgain() = runTest {
        val (m, h) = building()
        val chat = h.sdk.chats.getValue(coach)
        chat.value = GraineChatStatus.CONNECTED
        assertEquals(1, h.sdk.reported.size)
        // A blip on the same conversation: not asked twice.
        chat.value = GraineChatStatus.RECONNECTING
        chat.value = GraineChatStatus.CONNECTED
        assertEquals(1, h.sdk.reported.size)
        // The app went to the background: the conversation ended. Back in front, the chat comes back and is asked again.
        chat.value = GraineChatStatus.IDLE
        m.onForeground()
        assertEquals(1, h.sdk.retries, "the chat connects again")
        chat.value = GraineChatStatus.CONNECTED
        assertEquals(listOf("kickoff_done", "kickoff_done"), h.sdk.reported.map { it.first })
        h.sdk.run(coach, "save_week", week("wed"))
        assertEquals(JourneyStage.READY, m.stage)
        chat.value = GraineChatStatus.IDLE
        chat.value = GraineChatStatus.CONNECTED
        assertEquals(2, h.sdk.reported.size, "once the plan is in, nothing more")
        m.close()
    }

    @Test
    fun aJourneyKilledWhileThePlanWasOnItsWayAsksAgainAtTheNextLaunch() = runTest {
        val (m, h) = building()
        m.close()
        val sdk = FakeJourneySdk()
        val again = JourneyHarness(this, sdk = sdk, files = h.files).model()
        assertEquals(JourneyStage.BUILDING_PLAN, again.stage)
        assertEquals(listOf(coach to true), sdk.shown)
        sdk.chats.getValue(coach).value = GraineChatStatus.CONNECTED
        assertEquals("kickoff_done", sdk.reported.single().first)
        again.close()
    }

    @Test
    fun aPlanWithDaysMissingShowsWhatItHasAndNothingForTheRest() = runTest {
        val (m, h) = building()
        h.sdk.run(coach, "save_week", week("mon"))
        val today = m.todayDate() // a Wednesday
        assertEquals(JourneyStats.Count(0, 0), JourneyStats.today(m.plan, m.activity, today), "nothing planned today")
        assertEquals(LocalDate(2026, 10, 19), JourneyStats.upNext(m.plan, today, m.timeNow())?.first, "next Monday")
        assertEquals(listOf("2026-10-19T08:00"), h.reminders.scheduled.map { it.id })
        assertEquals(1, (h.sdk.run(coach, "get_week").data["days"] as List<*>).size)
        assertEquals(JourneyAnswer.Status.REFUSED, h.sdk.run(coach, "change_item", mapOf("day" to "tue", "time" to "08:00", "title" to "x")).status)
        m.close()
    }

    @Test
    fun aPlanThatNeverComesNeverSpinsForever() = runTest {
        val (m, h) = building()
        h.sdk.chats.getValue(coach).value = GraineChatStatus.CONNECTED
        advanceTimeBy(JourneyModel.PLAN_SLOW_MS + 1)
        assertTrue(m.planSlow, "the screen offers Ask again and the chat")
        m.askForPlanAgain()
        assertFalse(m.planSlow)
        advanceTimeBy(JourneyModel.PLAN_SLOW_MS + 1)
        assertTrue(m.planSlow, "slow again: the offer comes back")
        m.skipToChat()
        assertEquals(JourneyStage.READY to JourneyRoute.CHAT, m.stage to m.route)
        m.close()
    }

    // ── the first call ──────────────────────────────────────────────────────

    @Test
    fun aCallThatDroppedIsEndedEarlyUnlessItHadSaved() = runTest {
        val (m, h) = started()
        m.startFirstCall()
        h.sdk.outcomes.tryEmit(outcome(GraineCallOutcome.Kind.ENDED, "failed", endCause = "media_drop"))
        advanceTimeBy(JourneyModel.INTAKE_GRACE_MS + 1)
        assertEquals(NoConnect.ENDED_EARLY, m.noConnect, "a drop before the save")
        m.close()

        val (m2, h2) = started()
        m2.startFirstCall()
        h2.sdk.run(JourneyAgent.FIRST_CALL, "save_kickoff", mapOf("level" to "advanced"))
        h2.sdk.outcomes.tryEmit(outcome(GraineCallOutcome.Kind.ENDED, "failed", endCause = "media_drop"))
        assertEquals(JourneyStage.BUILDING_PLAN, m2.stage, "a drop after the save: the plan is next")
        m2.close()
    }

    @Test
    fun continueInChatAfterAPartialCallFinishesTheConsultationThere() = runTest {
        val (m, h) = started()
        m.startFirstCall()
        h.sdk.outcomes.tryEmit(outcome(GraineCallOutcome.Kind.ENDED, "local"))
        advanceTimeBy(JourneyModel.INTAKE_GRACE_MS + 1)
        m.continueInChat()
        assertEquals(JourneyStage.FIRST_CALL_CHAT, m.stage)
        val partial = h.sdk.run(JourneyAgent.FIRST_CALL, "save_kickoff", mapOf("goals" to "Exam in June"))
        assertEquals(JourneyAnswer.Status.REFUSED, partial.status, "missing what the pack requires: the agent asks for it")
        assertEquals(JourneyStage.FIRST_CALL_CHAT, m.stage)
        h.sdk.run(JourneyAgent.FIRST_CALL, "save_kickoff", mapOf("goals" to "Exam in June", "level" to "beginner"))
        assertEquals(JourneyStage.BUILDING_PLAN, m.stage)
        m.close()
    }

    @Test
    fun aSecondStartNowWhileTheFirstCallIsOnPlacesNoSecondCall() = runTest {
        val (m, h) = started()
        m.startFirstCall()
        m.startFirstCall()
        m.callMeNow()
        assertEquals(1, h.sdk.started.size)
        m.close()
    }

    @Test
    fun twoBookingsNeverOverlapAndNoneRingsOnceThePlanIsBeingBuilt() = runTest {
        val (m, h) = started()
        // A stray booking from before (another phone, a cancel that failed).
        h.sdk.bookedCalls += GraineScheduledCall("stray", Instant.fromEpochMilliseconds(h.now() + 86_400_000), GraineCallStatus.Scheduled, GraineCallSource.App)
        m.openPicker()
        m.pickTime(m.picker!!.times[0])
        m.confirmPicker()
        assertEquals(listOf("stray"), h.sdk.cancelled, "the new time is safe: every other booking goes")
        m.openPicker()
        m.pickTime(m.picker!!.times[1])
        m.confirmPicker()
        assertEquals(listOf("stray", "booked_1"), h.sdk.cancelled)
        // The second time rings and the consultation happens: nothing booked is left to ring.
        h.sdk.callState.value = GraineCallInfo(state = GraineCallState.RINGING, callId = "call_r", incoming = true, hosted = true, agent = firstCallAgent)
        h.sdk.run(JourneyAgent.FIRST_CALL, "save_kickoff", mapOf("level" to "beginner"))
        h.sdk.outcomes.tryEmit(outcome(GraineCallOutcome.Kind.ENDED))
        assertEquals(listOf("stray", "booked_1", "booked_2"), h.sdk.cancelled)
        m.close()
    }

    @Test
    fun theMissedCallScreenOnAColdStart() = runTest {
        // Woken by the ring and still running when the user opens the app: the missed ring is on the call state.
        val (m, h) = started()
        m.openPicker()
        m.pickTime(m.picker!!.times[0])
        m.confirmPicker()
        m.close()
        val woken = FakeJourneySdk().apply {
            callState.value = GraineCallInfo(missedCall = GraineMissedCall("call_m", "Ari", null, hosted = true, at = Instant.fromEpochMilliseconds(h.now())))
        }
        val first = JourneyHarness(this, sdk = woken, files = h.files).model()
        assertEquals(JourneyStage.COULD_NOT_CONNECT to NoConnect.MISSED, first.stage to first.noConnect)
        first.close()
    }

    @Test
    fun aBookedCallWhoseFateCannotBeReadWaitsUntilItCanOrUntilItIsLongPast() = runTest {
        val (m, h) = started()
        m.openPicker()
        m.pickTime(m.picker!!.times[0])
        m.confirmPicker()
        m.close()
        val offline = Result.failure<List<GraineScheduledCall>>(GraineException(GraineErrorCode.Network, "No connection."))
        val soon = FakeJourneySdk().apply { listResult = offline }
        val again = JourneyHarness(this, sdk = soon, files = h.files).model()
        assertEquals(JourneyStage.BOOKED, again.stage, "offline before the time: still booked")
        soon.listResult = Result.success(listOf(GraineScheduledCall(again.booked!!.id, null, GraineCallStatus.Missed)))
        again.onForeground()
        assertEquals(JourneyStage.COULD_NOT_CONNECT to NoConnect.MISSED, again.stage to again.noConnect, "read again on return")
        again.close()

        val (m3, h3) = started()
        m3.openPicker()
        m3.pickTime(m3.picker!!.times[0])
        m3.confirmPicker()
        m3.close()
        val late = JourneyHarness(this, sdk = FakeJourneySdk().apply { listResult = offline }, files = h3.files, start = TestClock.at(23)).model()
        assertEquals(JourneyStage.COULD_NOT_CONNECT to NoConnect.NOT_REACHED, late.stage to late.noConnect, "offline, hours past: decided")
        late.close()
    }

    // ── check-ins ───────────────────────────────────────────────────────────

    private suspend fun TestScope.ready(): Pair<JourneyModel, JourneyHarness> {
        val (m, h) = building()
        h.sdk.chats.getValue(coach).value = GraineChatStatus.CONNECTED
        h.sdk.run(coach, "save_week", week("wed", "thu"))
        h.sdk.chats.getValue(coach).value = GraineChatStatus.IDLE
        h.sdk.reported.clear()
        return m to h
    }

    @Test
    fun aReminderOpenedDuringACallWaitsForTheChatAfterIt() = runTest {
        val (m, h) = ready()
        // A call with the coach holds the chat; a reminder tapped meanwhile is told once the chat is back.
        h.sdk.callState.value = GraineCallInfo(state = GraineCallState.ACTIVE, callId = "call_c", agent = "default")
        m.openCheckIn(CheckInSchedule.id(LocalDate(2026, 10, 15), "08:00"))
        assertEquals(JourneyRoute.CHAT, m.route)
        assertTrue(h.sdk.reported.isEmpty())
        assertEquals(JourneyStage.READY, m.stage, "a call with the coach changes nothing of the journey")
        h.sdk.callState.value = GraineCallInfo()
        h.sdk.chats.getValue(coach).value = GraineChatStatus.CONNECTED
        assertEquals("lesson_check_in", h.sdk.reported.single().first)
        m.close()
    }

    @Test
    fun aCheckInWithNoPlanDoesNothing() = runTest {
        val (m, h) = started()
        m.openCheckIn(CheckInSchedule.id(LocalDate(2026, 10, 15), "08:00"))
        m.simulateReminder()
        assertTrue(h.sdk.reported.isEmpty())
        assertTrue(h.reminders.scheduled.isEmpty(), "no plan, no reminders")
        assertEquals(JourneyStage.FIRST_CALL, m.stage)
        m.close()
    }

    @Test
    fun aLanguageChangedMidJourneyReachesTheAgentsFromTheirNextConversation() = runTest {
        val (m, h) = ready()
        m.changeLanguage("es")
        assertEquals(mapOf("agent_language" to "es", "language_style" to "Spanish, informal"), h.sdk.variables.last().filterKeys { it == "agent_language" || it == "language_style" })
        assertEquals(JourneyStage.READY, m.stage, "the journey stays where it is")
        m.changeLanguage("en")
        assertEquals("en", h.sdk.held["agent_language"])
        assertNull(h.sdk.held["language_style"], "a style the new language does not have is removed")
        m.changeLanguage("klingon")
        assertEquals("en", h.sdk.held["agent_language"], "a language the pack does not offer is ignored")
        m.close()
    }
}
