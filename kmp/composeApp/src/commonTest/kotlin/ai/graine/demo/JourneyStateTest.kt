package ai.graine.demo

import ai.graine.demo.journey.CheckInSchedule
import ai.graine.demo.journey.ItemRef
import ai.graine.demo.journey.JourneyAgent
import ai.graine.demo.journey.JourneyModel
import ai.graine.demo.journey.JourneyRoute
import ai.graine.demo.journey.JourneyStage
import ai.graine.demo.journey.NoConnect
import ai.graine.demo.journey.OnboardingStep
import ai.graine.demo.journey.Plan
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
import ai.graine.inapp.kmp.GraineSessionChannel
import ai.graine.inapp.kmp.GraineSessionSummary
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The journey's state machine over a fake SDK: every step, and every way the first call can fail to happen. */
@OptIn(ExperimentalCoroutinesApi::class)
class JourneyStateTest {
    private val agent = JourneyAgent.FIRST_CALL.sdkName

    /** The SDK's times on a day: every 5 minutes from 09:00 (or 5 minutes from now) to 21:00, as booking rules would allow. */
    private fun slotsFrom(h: JourneyHarness): (LocalDate) -> List<Instant> = { day ->
        (9 * 12 until 21 * 12).map { i -> LocalDateTime(day.year, day.monthNumber, day.dayOfMonth, i / 12, (i % 12) * 5).toInstant(TestClock.zone) }
            .filter { it.toEpochMilliseconds() >= h.now() + 5 * 60_000 }
    }

    private val week = mapOf(
        "title" to "A first week",
        "days" to listOf(
            mapOf("day" to "wed", "items" to listOf(
                mapOf("time" to "08:00", "kind" to "lesson", "title" to "Lesson 1"),
                mapOf("time" to "14:00", "kind" to "lesson", "title" to "Lesson 2", "swaps" to listOf("Lesson 2 video")),
                mapOf("time" to "19:00", "kind" to "practice", "title" to "Practice"),
            )),
            mapOf("day" to "thu", "items" to listOf(mapOf("time" to "08:00", "kind" to "lesson", "title" to "Lesson 3"))),
        ),
    )

    /** A journey through onboarding, at the first-call screen. */
    private fun TestScope.atFirstCall(h: JourneyHarness = JourneyHarness(this)): Pair<JourneyModel, JourneyHarness> {
        val m = h.model()
        m.start()
        m.onboardingNext()
        assertTrue(m.saveProfile(TestJourney.profile).isEmpty())
        m.chooseLanguage("en")
        m.allowMicrophone()
        m.allowNotifications()
        assertEquals(JourneyStage.FIRST_CALL, m.stage)
        return m to h
    }

    private fun outcome(kind: GraineCallOutcome.Kind, reason: String? = null, by: String? = agent, id: String = "call_1") =
        GraineCallOutcome(callId = id, outcome = kind, reason = reason, agent = by)

    // ── 1. onboarding ───────────────────────────────────────────────────────

    @Test
    fun onboardingIdentifiesTheUserAndSendsTheProfileAsVariables() = runTest {
        val h = JourneyHarness(this)
        val m = h.model()
        assertFalse(m.open)
        m.start()
        assertTrue(m.open)
        assertEquals(listOf(true), h.opens, "the app's own call screen follows the journey")
        assertEquals(JourneyStage.ONBOARDING to OnboardingStep.WELCOME, m.stage to m.onboardingStep)
        m.onboardingNext()
        assertEquals(OnboardingStep.PROFILE, m.onboardingStep)

        val problems = m.saveProfile(mapOf("name" to "Sam", "level" to "expert", "born" to "2020-01-01", "hours" to "90"))
        assertEquals(setOf("level", "born", "hours"), problems.keys)
        assertTrue(h.sdk.identified.isEmpty(), "nothing is sent until the profile can be kept")
        assertEquals(OnboardingStep.PROFILE, m.onboardingStep)

        assertTrue(m.saveProfile(TestJourney.profile + ("notes" to "  ")).isEmpty())
        assertEquals(mapOf("user_id" to "user-test-1", "name" to "Sam"), h.sdk.identified.last(), "identify(user_id, name)")
        assertEquals(
            mapOf("level" to "beginner", "age" to "36", "hours_week" to "6", "notes" to null, "agent_language" to null, "language_style" to null),
            h.sdk.variables.last(),
            "setVariables: the age from the date of birth, a blank answer removed",
        )
        assertEquals(OnboardingStep.LANGUAGE, m.onboardingStep)

        m.chooseLanguage("es")
        assertEquals("es", h.sdk.held["agent_language"], "the language's code: the agent starts in it")
        assertEquals("Spanish, informal", h.sdk.held["language_style"])
        assertEquals(OnboardingStep.MICROPHONE, m.onboardingStep)

        m.allowMicrophone()
        assertEquals(1, h.sdk.permissionAsks, "Graine.calls.requestCallPermissions()")
        assertEquals(true, m.microphoneAllowed)
        assertEquals(OnboardingStep.NOTIFICATIONS, m.onboardingStep)
        h.reminders.allow = false
        m.allowNotifications()
        assertEquals(false, m.notificationsAllowed)
        assertEquals(JourneyStage.FIRST_CALL, m.stage, "either answer moves on")
        assertEquals("first_call", h.sdk.lastScreen?.screen)
        m.close()
    }

    // ── 2. the first call ───────────────────────────────────────────────────

    @Test
    fun startNowCallsTheFirstCallAgentAndItsEndBuildsThePlan() = runTest {
        val (m, h) = atFirstCall()
        m.startFirstCall()
        assertEquals(listOf("kickoff_call" to JourneyAgent.FIRST_CALL), h.sdk.started, "startCall(reason, agent = \"consultation\")")
        assertEquals(JourneyStage.IN_CALL, m.stage)

        // The flow's last step saves what it learned, then the call ends.
        h.sdk.run(JourneyAgent.FIRST_CALL, "save_kickoff", mapOf("level" to "beginner", "goals" to "Pass the exam"))
        assertEquals(JourneyStage.IN_CALL, m.stage, "the call is still on")
        h.sdk.outcomes.tryEmit(outcome(GraineCallOutcome.Kind.ENDED, "remote"))
        assertEquals(JourneyStage.BUILDING_PLAN, m.stage)
        assertEquals(listOf(JourneyAgent.COACH to true), h.sdk.shown, "the coach's chat connected: chat.shown(true)")
        assertTrue(h.sdk.reported.isEmpty(), "nothing reported until the chat is ONLINE")
        assertEquals(listOf("kickoff_done"), m.waitingEvents)

        h.sdk.chats.getValue(JourneyAgent.COACH).value = GraineChatStatus.CONNECTING
        assertTrue(h.sdk.reported.isEmpty())
        h.sdk.chats.getValue(JourneyAgent.COACH).value = GraineChatStatus.CONNECTED
        assertEquals(listOf<Pair<String, String?>>("kickoff_done" to "Build the first study week"), h.sdk.reported, "Graine.reportEvent, once")
        h.sdk.chats.getValue(JourneyAgent.COACH).value = GraineChatStatus.RECONNECTING
        h.sdk.chats.getValue(JourneyAgent.COACH).value = GraineChatStatus.CONNECTED
        assertEquals(1, h.sdk.reported.size, "never twice")

        // The coach reads what the first call learned and saves the week.
        assertEquals("Pass the exam", h.sdk.run(JourneyAgent.COACH, "get_kickoff").data["goals"])
        h.sdk.run(JourneyAgent.COACH, "save_week", week)
        assertEquals(JourneyStage.READY, m.stage)
        assertTrue(m.planFresh, "the card says the plan is ready")
        assertEquals(JourneyRoute.CHAT, m.route)
        assertEquals(listOf(JourneyAgent.COACH to true, JourneyAgent.COACH to false), h.sdk.shown, "held in pairs")
        assertTrue(h.reminders.scheduled.isNotEmpty(), "check-in reminders follow the plan")
        m.go(JourneyRoute.WEEK)
        assertFalse(m.planFresh)
        m.close()
    }

    @Test
    fun startNowWhileAnotherCallIsLiveSaysSoAndStays() = runTest {
        val (m, h) = atFirstCall()
        h.sdk.startResult = null
        m.startFirstCall()
        assertEquals(JourneyStage.FIRST_CALL, m.stage)
        assertTrue(m.notice!!.contains("Another call"))
        m.close()
    }

    @Test
    fun withoutTheFirstCallAgentsKeyTheStepSaysWhatToAdd() = runTest {
        val h = JourneyHarness(this)
        h.sdk.configured -= JourneyAgent.FIRST_CALL
        val (m, _) = atFirstCall(h)
        m.startFirstCall()
        assertTrue(h.sdk.started.isEmpty())
        assertTrue(m.notice!!.contains("graine.consultationPublishableKey"))
        m.openPicker()
        assertNull(m.picker)
        m.close()
    }

    @Test
    fun thePickerOffersAFewTimesADayAndBooksTheOneChosen() = runTest {
        val (m, h) = atFirstCall()
        h.sdk.slots = slotsFrom(h)
        m.setQuietHours("19:30", "07:00")
        m.openPicker()
        val p = m.picker!!
        assertEquals(3, p.days.size, "the pack's dayCount")
        // Today at 12:00: 10:30 has passed; 20:00 is inside the user's quiet hours.
        assertEquals(listOf("13:00", "17:30"), p.days[0].times.map { ai.graine.demo.journey.JourneyTime.of(it, TestClock.zone) })
        assertEquals(listOf("10:30", "13:00", "17:30"), p.days[1].times.map { ai.graine.demo.journey.JourneyTime.of(it, TestClock.zone) })
        assertEquals(LocalDate(2026, 10, 14), p.day, "the first day with a time")

        m.pickDay(LocalDate(2026, 10, 15))
        val at = m.picker!!.times[0]
        m.pickTime(at)
        h.sdk.scheduleResult = Result.failure(GraineException(GraineErrorCode.OutsideWindow, "That time is outside the hours we can call — pick another"))
        m.confirmPicker()
        assertEquals("That time is outside the hours we can call — pick another", m.picker!!.error, "the SDK's own sentence")
        assertEquals(JourneyStage.FIRST_CALL, m.stage)

        h.sdk.scheduleResult = null
        m.confirmPicker()
        assertEquals(Triple(at, "Your kickoff call", JourneyAgent.FIRST_CALL), h.sdk.scheduled.last(), "scheduleCall(at, reason)")
        assertNull(m.picker)
        assertEquals(JourneyStage.BOOKED, m.stage)
        assertEquals(at.toEpochMilliseconds(), m.booked!!.at)

        // Another time in its place: the new one is booked first, then the old one called off.
        m.openPicker()
        m.pickDay(LocalDate(2026, 10, 16))
        m.pickTime(m.picker!!.times[1])
        m.confirmPicker()
        assertEquals(listOf("booked_2"), h.sdk.cancelled, "the new time booked first, then the old one called off")
        assertEquals("booked_3", m.booked!!.id)
        m.close()
    }

    @Test
    fun startNowInsteadCallsOffTheBookedCall() = runTest {
        val (m, h) = atFirstCall()
        h.sdk.slots = slotsFrom(h)
        m.openPicker()
        m.pickTime(m.picker!!.times[0])
        m.confirmPicker()
        val booked = m.booked!!.id
        m.startFirstCall()
        assertEquals(JourneyStage.IN_CALL, m.stage)
        assertEquals(listOf(booked), h.sdk.cancelled, "the call booked for later would ring again")
        m.close()
    }

    @Test
    fun theBookedCallRingingIsTheFirstCall() = runTest {
        val (m, h) = atFirstCall()
        h.sdk.slots = slotsFrom(h)
        m.openPicker()
        m.pickTime(m.picker!!.times[0])
        m.confirmPicker()
        h.sdk.callState.value = GraineCallInfo(state = GraineCallState.RINGING, callId = "call_ring", incoming = true, hosted = true, agent = agent)
        assertEquals(JourneyStage.IN_CALL, m.stage)
        h.sdk.run(JourneyAgent.FIRST_CALL, "save_kickoff", mapOf("level" to "advanced"))
        h.sdk.outcomes.tryEmit(outcome(GraineCallOutcome.Kind.ENDED, id = "call_ring"))
        assertEquals(JourneyStage.BUILDING_PLAN, m.stage)
        m.close()
    }

    // ── 3. we couldn't connect: every way in ────────────────────────────────

    private suspend fun TestScope.booked(): Pair<JourneyModel, JourneyHarness> {
        val (m, h) = atFirstCall()
        h.sdk.slots = slotsFrom(h)
        m.openPicker()
        m.pickTime(m.picker!!.times[0])
        m.confirmPicker()
        assertEquals(JourneyStage.BOOKED, m.stage)
        return m to h
    }

    @Test
    fun aMissedRingIsWeCouldntConnect() = runTest {
        val (m, h) = booked()
        h.sdk.outcomes.tryEmit(outcome(GraineCallOutcome.Kind.MISSED, "no_answer"))
        assertEquals(JourneyStage.COULD_NOT_CONNECT to NoConnect.MISSED, m.stage to m.noConnect)
        assertNull(m.booked, "it rang: nothing is booked any more")
        assertEquals("could_not_connect", h.sdk.lastScreen?.fields?.firstOrNull { it.name == "could_not_connect" }?.name)
        m.close()
    }

    @Test
    fun aDeclinedRingIsWeCouldntConnect() = runTest {
        val (m, h) = booked()
        h.sdk.outcomes.tryEmit(outcome(GraineCallOutcome.Kind.DECLINED))
        assertEquals(NoConnect.DECLINED, m.noConnect)
        m.close()
    }

    @Test
    fun aRingMissedBecauseAnotherCallWasLiveSaysBusy() = runTest {
        val (m, h) = booked()
        h.sdk.outcomes.tryEmit(outcome(GraineCallOutcome.Kind.MISSED, "busy"))
        assertEquals(NoConnect.BUSY, m.noConnect)
        m.close()
    }

    @Test
    fun aCallThatNeverConnectedIsWeCouldntConnect() = runTest {
        val (m, h) = atFirstCall()
        m.startFirstCall()
        h.sdk.outcomes.tryEmit(outcome(GraineCallOutcome.Kind.FAILED, "network"))
        assertEquals(JourneyStage.COULD_NOT_CONNECT to NoConnect.FAILED, m.stage to m.noConnect)
        m.close()
    }

    @Test
    fun aMissedCallOnTheCallStateIsWeCouldntConnect() = runTest {
        val (m, h) = booked()
        h.sdk.callState.value = GraineCallInfo(missedCall = GraineMissedCall("call_9", "Ari", null, hosted = true, at = Instant.fromEpochMilliseconds(h.now())))
        assertEquals(JourneyStage.COULD_NOT_CONNECT to NoConnect.MISSED, m.stage to m.noConnect)
        m.close()
    }

    @Test
    fun aCallThatEndedBeforeItsSaveIsEndedEarly() = runTest {
        val (m, h) = atFirstCall()
        m.startFirstCall()
        h.sdk.outcomes.tryEmit(outcome(GraineCallOutcome.Kind.ENDED, "local"))
        assertEquals(JourneyStage.IN_CALL, m.stage, "the save may still be on its way")
        advanceTimeBy(JourneyModel.INTAKE_GRACE_MS + 1)
        assertEquals(JourneyStage.COULD_NOT_CONNECT to NoConnect.ENDED_EARLY, m.stage to m.noConnect)
        // A save that comes even later still moves on.
        h.sdk.run(JourneyAgent.FIRST_CALL, "save_kickoff", mapOf("level" to "beginner"))
        assertEquals(JourneyStage.BUILDING_PLAN, m.stage)
        m.close()
    }

    @Test
    fun aSaveThatArrivesJustAfterTheEndIsNotEndedEarly() = runTest {
        val (m, h) = atFirstCall()
        m.startFirstCall()
        h.sdk.outcomes.tryEmit(outcome(GraineCallOutcome.Kind.ENDED))
        advanceTimeBy(1_000)
        h.sdk.run(JourneyAgent.FIRST_CALL, "save_kickoff", mapOf("level" to "beginner"))
        assertEquals(JourneyStage.BUILDING_PLAN, m.stage, "the call was over and waiting for it")
        advanceTimeBy(JourneyModel.INTAKE_GRACE_MS)
        assertNull(m.noConnect)
        m.close()
    }

    @Test
    fun anotherAgentsCallsChangeNothing() = runTest {
        val (m, h) = booked()
        h.sdk.outcomes.tryEmit(outcome(GraineCallOutcome.Kind.MISSED, by = "default", id = "call_coach"))
        h.sdk.outcomes.tryEmit(outcome(GraineCallOutcome.Kind.FAILED, by = "help", id = "call_help"))
        assertEquals(JourneyStage.BOOKED, m.stage)
        m.close()
    }

    @Test
    fun aRingMissedWhileTheAppWasClosedReadsMissedAtTheNextLaunch() = runTest {
        for ((status, expected) in listOf(
            GraineCallStatus.Missed to NoConnect.MISSED,
            GraineCallStatus.Declined to NoConnect.DECLINED,
            GraineCallStatus.NoDevice to NoConnect.NOT_REACHED,
            GraineCallStatus.Expired to NoConnect.NOT_REACHED,
            GraineCallStatus.Failed to NoConnect.FAILED,
        )) {
            val (m, h) = booked()
            val id = m.booked!!.id
            m.close()
            h.sdk.listResult = Result.success(listOf(GraineScheduledCall(id, Instant.fromEpochMilliseconds(h.now()), status, GraineCallSource.App)))
            val again = JourneyHarness(this, sdk = h.sdk, files = h.files).model()
            assertTrue(again.open, "signed in: back at launch")
            assertEquals(JourneyStage.COULD_NOT_CONNECT to expected, again.stage to again.noConnect, status.wire)
            again.close()
        }
    }

    @Test
    fun aBookedCallStillToComeOrCancelledReadsSoAtTheNextLaunch() = runTest {
        val (m, h) = booked()
        val id = m.booked!!.id
        m.close()
        h.sdk.listResult = Result.success(listOf(GraineScheduledCall(id, Instant.fromEpochMilliseconds(h.now()), GraineCallStatus.Scheduled)))
        val still = JourneyHarness(this, sdk = h.sdk, files = h.files).model()
        assertEquals(JourneyStage.BOOKED, still.stage)
        still.close()
        h.sdk.listResult = Result.success(listOf(GraineScheduledCall(id, Instant.fromEpochMilliseconds(h.now()), GraineCallStatus.Cancelled)))
        val cancelled = JourneyHarness(this, sdk = h.sdk, files = h.files).model()
        assertEquals(JourneyStage.FIRST_CALL, cancelled.stage)
        cancelled.close()
    }

    @Test
    fun aBookedCallGraineNoLongerListsLongAfterItsTimeDidNotReachThePhone() = runTest {
        val (m, h) = booked()
        m.close()
        h.sdk.listResult = Result.success(emptyList())
        val later = JourneyHarness(this, sdk = h.sdk, files = h.files, start = TestClock.at(23)).model()
        assertEquals(JourneyStage.COULD_NOT_CONNECT to NoConnect.NOT_REACHED, later.stage to later.noConnect)
        later.close()
    }

    @Test
    fun aFirstCallCutOffByTheAppClosingIsEndedEarlyAtTheNextLaunch() = runTest {
        val (m, h) = atFirstCall()
        m.startFirstCall()
        m.close()
        val again = JourneyHarness(this, sdk = FakeJourneySdk(), files = h.files).model()
        assertEquals(JourneyStage.COULD_NOT_CONNECT to NoConnect.ENDED_EARLY, again.stage to again.noConnect)
        again.close()
    }

    // ── 3. we couldn't connect: every way out ───────────────────────────────

    @Test
    fun callMeNowCallsBackTheMissedRing() = runTest {
        val (m, h) = booked()
        h.sdk.callState.value = GraineCallInfo(missedCall = GraineMissedCall("call_9", "Ari", null, hosted = true, at = Instant.fromEpochMilliseconds(h.now())))
        assertEquals(JourneyStage.COULD_NOT_CONNECT, m.stage)
        m.callMeNow()
        assertEquals(1, h.sdk.callBacks, "Graine.calls.callBack()")
        assertTrue(h.sdk.started.isEmpty())
        assertEquals(JourneyStage.IN_CALL, m.stage)
        m.close()
    }

    @Test
    fun callMeNowWithNoMissedRingPlacesANewCall() = runTest {
        val (m, h) = atFirstCall()
        m.startFirstCall()
        h.sdk.outcomes.tryEmit(outcome(GraineCallOutcome.Kind.FAILED))
        m.callMeNow()
        assertEquals(0, h.sdk.callBacks)
        assertEquals(2, h.sdk.started.size)
        assertEquals(JourneyStage.IN_CALL, m.stage)
        m.close()
    }

    @Test
    fun callMeNowThatCannotCallSaysSo() = runTest {
        val (m, h) = booked()
        h.sdk.callState.value = GraineCallInfo(missedCall = GraineMissedCall("call_9", null, null, hosted = true, at = Instant.fromEpochMilliseconds(h.now())))
        h.sdk.callBackResult = null
        h.sdk.startResult = null
        m.callMeNow()
        assertEquals(JourneyStage.COULD_NOT_CONNECT, m.stage)
        assertNotNull(m.notice)
        m.close()
    }

    @Test
    fun chooseAnotherTimeBooksAgainAndLetsTheMissedRingGo() = runTest {
        val (m, h) = booked()
        h.sdk.outcomes.tryEmit(outcome(GraineCallOutcome.Kind.MISSED))
        val dismissed = h.sdk.dismissed
        m.openPicker()
        m.pickDay(LocalDate(2026, 10, 15))
        m.pickTime(m.picker!!.times.last())
        m.confirmPicker()
        assertEquals(JourneyStage.BOOKED, m.stage)
        assertNull(m.noConnect)
        assertEquals(dismissed + 1, h.sdk.dismissed, "dismissMissedCall")
        m.close()
    }

    @Test
    fun continueInChatAsksTheSameQuestionsAndItsSaveBuildsThePlan() = runTest {
        val (m, h) = booked()
        h.sdk.outcomes.tryEmit(outcome(GraineCallOutcome.Kind.DECLINED))
        val dismissed = h.sdk.dismissed
        m.continueInChat()
        assertEquals(JourneyStage.FIRST_CALL_CHAT, m.stage)
        assertEquals(dismissed + 1, h.sdk.dismissed, "the missed ring let go")
        h.sdk.run(JourneyAgent.FIRST_CALL, "save_kickoff", mapOf("level" to "beginner"))
        assertEquals(JourneyStage.BUILDING_PLAN, m.stage)
        assertEquals(listOf(JourneyAgent.COACH to true), h.sdk.shown)
        m.close()
    }

    @Test
    fun graineRingingAgainWhileOnWeCouldntConnectIsTheFirstCall() = runTest {
        val (m, h) = booked()
        h.sdk.outcomes.tryEmit(outcome(GraineCallOutcome.Kind.MISSED))
        h.sdk.callState.value = GraineCallInfo(state = GraineCallState.RINGING, callId = "call_again", incoming = true, hosted = true, agent = agent)
        assertEquals(JourneyStage.IN_CALL, m.stage)
        m.close()
    }

    // ── 4. the plan ─────────────────────────────────────────────────────────

    private suspend fun TestScope.buildingPlan(): Pair<JourneyModel, JourneyHarness> {
        val (m, h) = atFirstCall()
        m.startFirstCall()
        h.sdk.run(JourneyAgent.FIRST_CALL, "save_kickoff", mapOf("level" to "beginner"))
        h.sdk.outcomes.tryEmit(outcome(GraineCallOutcome.Kind.ENDED))
        assertEquals(JourneyStage.BUILDING_PLAN, m.stage)
        return m to h
    }

    @Test
    fun aSlowPlanCanBeAskedForAgainOrSkippedToTheChat() = runTest {
        val (m, h) = buildingPlan()
        h.sdk.chats.getValue(JourneyAgent.COACH).value = GraineChatStatus.CONNECTED
        assertEquals(1, h.sdk.reported.size)
        assertFalse(m.planSlow)
        advanceTimeBy(JourneyModel.PLAN_SLOW_MS + 1)
        assertTrue(m.planSlow)
        m.askForPlanAgain()
        assertEquals(2, h.sdk.reported.size, "the same event, once more")
        m.skipToChat()
        assertEquals(JourneyStage.READY, m.stage)
        assertEquals(JourneyAgent.COACH to false, h.sdk.shown.last())
        h.sdk.run(JourneyAgent.COACH, "save_week", week)
        assertNotNull(m.plan, "the plan still arrives in the chat")
        m.close()
    }

    @Test
    fun aChatThatGaveUpIsAskedToConnectWhenAnEventWaits() = runTest {
        val h = JourneyHarness(this)
        h.sdk.chats.getValue(JourneyAgent.COACH).value = GraineChatStatus.OFFLINE
        val (m, _) = atFirstCall(h)
        m.startFirstCall()
        h.sdk.run(JourneyAgent.FIRST_CALL, "save_kickoff", mapOf("level" to "beginner"))
        h.sdk.outcomes.tryEmit(outcome(GraineCallOutcome.Kind.ENDED))
        assertEquals(1, h.sdk.retries, "chat.retry()")
        m.close()
    }

    @Test
    fun theSamplePlanStandsInForTheAgentWhenTesting() = runTest {
        val (m, h) = buildingPlan()
        m.loadSamplePlan()
        assertEquals(JourneyStage.READY, m.stage)
        assertEquals(Plan.Source.SAMPLE, m.plan!!.source)
        assertEquals(3, m.plan!!.itemCount, "the pack's sample")
        assertTrue(h.reminders.scheduled.isNotEmpty())
        m.close()
    }

    // ── 5. check-ins, My week, Today, Progress ──────────────────────────────

    private suspend fun TestScope.ready(): Pair<JourneyModel, JourneyHarness> {
        val (m, h) = buildingPlan()
        h.sdk.chats.getValue(JourneyAgent.COACH).value = GraineChatStatus.CONNECTED
        h.sdk.run(JourneyAgent.COACH, "save_week", week)
        assertEquals(JourneyStage.READY, m.stage)
        h.sdk.chats.getValue(JourneyAgent.COACH).value = GraineChatStatus.IDLE
        h.sdk.reported.clear()
        return m to h
    }

    @Test
    fun remindersComeFromThePlansCheckInKindsOutsideQuietHours() = runTest {
        val (m, h) = ready()
        // Wednesday 12:00, the next 7 days: Lesson 2 (14:00 + 30 min) today, Lesson 3 tomorrow; no practice, nothing past.
        assertEquals(listOf("2026-10-14T14:00", "2026-10-15T08:00"), h.reminders.scheduled.map { it.id })
        assertEquals("Lesson check-in", h.reminders.scheduled[0].title)
        assertEquals("How was Lesson 2?", h.reminders.scheduled[0].body)
        m.setQuietHours("07:00", "09:00")
        assertEquals(listOf("2026-10-14T14:00"), h.reminders.scheduled.map { it.id }, "08:30 is now quiet")
        m.setReminders(false)
        assertTrue(h.reminders.scheduled.isEmpty())
        m.close()
    }

    @Test
    fun anOpenedReminderOpensTheChatAndTellsTheCoachOnceItIsOnline() = runTest {
        val (m, h) = ready()
        m.go(JourneyRoute.TODAY)
        m.openCheckIn(CheckInSchedule.id(LocalDate(2026, 10, 14), "14:00"))
        assertEquals(JourneyRoute.CHAT, m.route)
        assertTrue(h.sdk.reported.isEmpty(), "waits for ONLINE")
        h.sdk.chats.getValue(JourneyAgent.COACH).value = GraineChatStatus.CONNECTED
        assertEquals(listOf<Pair<String, String?>>("lesson_check_in" to "Opened the lesson reminder: Lesson 2 at 2:00 pm on Wednesday."), h.sdk.reported)
        m.openCheckIn("not-an-id")
        m.openCheckIn(CheckInSchedule.id(LocalDate(2026, 10, 14), "09:00"))
        assertEquals(1, h.sdk.reported.size, "an id that names no item does nothing")
        m.close()
    }

    @Test
    fun simulateReminderOpensTheNextCheckIn() = runTest {
        val (m, h) = ready()
        h.sdk.chats.getValue(JourneyAgent.COACH).value = GraineChatStatus.CONNECTED
        m.simulateReminder()
        assertEquals("lesson_check_in", h.sdk.reported.single().first)
        assertTrue(h.sdk.reported.single().second!!.contains("Lesson 2"))
        m.close()
    }

    @Test
    fun aTapThatCameBeforeTheJourneyListenedIsTakenOnce() = runTest {
        val (m, h) = ready()
        m.close()
        ai.graine.demo.journey.ReminderTaps.deliver(CheckInSchedule.id(LocalDate(2026, 10, 14), "14:00"))
        val again = JourneyHarness(this, sdk = h.sdk, files = h.files).model()
        assertEquals(JourneyRoute.CHAT, again.route)
        h.sdk.chats.getValue(JourneyAgent.COACH).value = GraineChatStatus.CONNECTED
        assertEquals("lesson_check_in", h.sdk.reported.last().first)
        again.close()
    }

    @Test
    fun myWeekAndAnItemTellTheAgentWhatIsOnScreen() = runTest {
        val (m, h) = ready()
        m.go(JourneyRoute.WEEK)
        m.showDay(Weekday.WED)
        val week = h.sdk.lastScreen!!
        assertEquals("plan_week", week.screen)
        assertEquals("Wednesday", week.fields.first { it.name == "day" }.value)
        assertEquals(listOf("Lesson 1", "Lesson 2", "Practice"), week.fields.drop(1).map { it.value })
        m.openItem(ItemRef(Weekday.WED, "14:00"))
        val item = h.sdk.lastScreen!!
        assertEquals("plan_item", item.screen)
        assertEquals(listOf("Wednesday", "14:00", "Lesson", "Lesson 2"), item.fields.map { it.value })
        m.close()
    }

    @Test
    fun aSwapChosenInTheAppChangesThePlanAndTellsTheCoach() = runTest {
        val (m, h) = ready()
        val before = h.reminders.replaced
        m.useSwap(ItemRef(Weekday.WED, "14:00"), "Lesson 2 video")
        assertEquals("Lesson 2 video", m.plan!!.item(Weekday.WED, "14:00")!!.title)
        assertEquals("item_changed" to mapOf("day" to "wed", "time" to "14:00", "from" to "Lesson 2", "to" to "Lesson 2 video"), h.sdk.tracked.last())
        assertTrue(h.reminders.replaced > before, "the reminders follow the plan")
        m.useSwap(ItemRef(Weekday.WED, "14:00"), "Not a swap")
        assertEquals("Lesson 2 video", m.plan!!.item(Weekday.WED, "14:00")!!.title)
        m.close()
    }

    @Test
    fun todayAndProgressCountWhatWasDoneAndTellTheCoach() = runTest {
        val (m, h) = ready()
        m.markDone(ItemRef(Weekday.WED, "08:00"), true)
        assertEquals("item_done" to mapOf("day" to "wed", "time" to "08:00", "title" to "Lesson 1", "done" to true), h.sdk.tracked.last())
        assertEquals(ai.graine.demo.journey.JourneyStats.Count(1, 3), ai.graine.demo.journey.JourneyStats.today(m.plan, m.activity, m.todayDate()))
        assertEquals(1, ai.graine.demo.journey.JourneyStats.streak(m.activity, m.todayDate()))
        assertEquals(50, ai.graine.demo.journey.JourneyStats.week(m.plan, m.activity, m.todayDate(), m.timeNow()).percent, "1 of the 2 due by noon")
        m.addToTracker()
        m.addToTracker()
        assertEquals(20.0, m.activity.tracker[m.todayDate()])
        m.weeklyCheckIn(4)
        assertEquals("weekly_score" to mapOf("score" to 4), h.sdk.tracked.last(), "Graine.track(\"weekly_check_in\", mapOf(\"score\" to n))")
        assertEquals(4, m.weeklyScore)
        assertEquals(listOf(6.0), m.measureTrend().map { it.value }, "the profile's answer starts the trend")
        assertNotNull(m.logMeasure("500"), "outside the measure's range")
        assertNull(m.logMeasure("72"))
        assertEquals(listOf(72.0), m.measureTrend().map { it.value }, "logged the day they signed up: it replaces the profile's answer")
        m.close()
    }

    @Test
    fun theCallButtonCallsTheCoach() = runTest {
        val (m, h) = ready()
        m.callCoach()
        assertEquals("coach_call" to JourneyAgent.COACH, h.sdk.started.last(), "Graine.calls.startCall(reason = \"coach_call\")")
        m.close()
    }

    // ── 6. log out, delete, launch ──────────────────────────────────────────

    @Test
    fun logOutSignsOutEverywhereAndLeavesNothingOfTheUserOnThePhone() = runTest {
        val (m, h) = ready()
        m.logOut()
        assertNull(h.sdk.identified.last(), "identify(null)")
        assertTrue(h.sdk.variables.last().values.all { it == null }, "every journey variable removed")
        assertTrue(h.reminders.scheduled.isEmpty(), "no reminders for someone signed out")
        assertFalse(m.open)
        assertNull(m.account)
        assertNull(m.plan)
        assertNull(m.intake)
        assertTrue(m.activity.checkIns.isEmpty())
        assertTrue(h.files.files.isEmpty(), "nothing of theirs on disk")

        // The next person to sign up here starts from nothing, as someone else.
        m.start()
        assertEquals("user-test-2", m.account?.userId)
        assertEquals(JourneyStage.ONBOARDING, m.stage)
        assertNull(m.plan)
        assertTrue(m.profile.values.isEmpty())
        assertEquals(h.sdk.identified.size, h.sdk.identified.indexOfLast { it == null } + 1, "nobody identified until their own profile")
        m.close()
    }

    @Test
    fun deleteAccountForgetsWhatEachAgentRemembersThenSignsOutThenErases() = runTest {
        val (m, h) = ready()
        var result: Boolean? = null
        m.deleteAccount { result = it }
        assertEquals(true, result)
        assertEquals(listOf(JourneyAgent.COACH, JourneyAgent.FIRST_CALL, JourneyAgent.SUPPORT), h.sdk.forgets, "forgetMemory on each agent")
        assertNull(h.sdk.identified.last())
        assertTrue(h.files.files.isEmpty(), "everything on this phone erased")
        assertNull(m.plan)
        assertNull(m.account)
        assertFalse(m.open)
        m.close()
    }

    @Test
    fun deleteAccountThatCouldNotForgetErasesNothing() = runTest {
        val (m, h) = ready()
        h.sdk.forgetResults[JourneyAgent.COACH] = Result.failure(GraineException(GraineErrorCode.Network, "No connection."))
        var result: Boolean? = null
        m.deleteAccount { result = it }
        assertEquals(false, result)
        assertNotNull(m.plan)
        assertTrue(m.open)
        assertTrue(m.notice!!.startsWith("Couldn't delete your account"))
        // Nobody identified with an agent means there was nothing to forget: it goes ahead.
        h.sdk.forgetResults[JourneyAgent.COACH] = Result.failure(GraineException(GraineErrorCode.IdentityRequired, "Sign in."))
        m.deleteAccount { result = it }
        assertEquals(true, result)
        m.close()
    }

    @Test
    fun atLaunchASignedInUserIsIdentifiedAndTheActionsAreThereBeforeAnyCall() = runTest {
        val (m, h) = ready()
        m.close()
        val sdk = FakeJourneySdk()
        val again = JourneyHarness(this, sdk = sdk, files = h.files).model()
        assertEquals(mapOf("user_id" to "user-test-1", "name" to "Sam"), sdk.identified.first(), "identify at every launch")
        assertEquals("36", sdk.held["age"])
        assertTrue(sdk.actions.getValue(JourneyAgent.COACH).containsKey("save_week"))
        assertEquals(JourneyStage.READY, again.stage)
        assertTrue(again.open)
        again.close()
    }

    @Test
    fun downloadMyDataHandsTheShareSheetOneJsonFile() = runTest {
        val (m, h) = ready()
        h.sdk.sessions[JourneyAgent.COACH] = listOf(GraineSessionSummary("s1", Instant.fromEpochMilliseconds(h.now()), channel = GraineSessionChannel.CHAT, turns = 4))
        m.exportData()
        val (name, text) = h.share.files.single()
        assertEquals("my-data.json", name)
        val json = ai.graine.demo.domain.Json.parse(text) as Map<*, *>
        assertEquals("user-test-1", (json["user"] as Map<*, *>)["user_id"])
        assertEquals(listOf("Prefers mornings"), (json["remembered"] as List<*>).map { (it as Map<*, *>)["text"] })
        assertEquals("s1", (((json["conversations"] as Map<*, *>)["default"] as List<*>).single() as Map<*, *>)["id"])
        assertEquals("A first week", (json["plan"] as Map<*, *>)["title"])
        assertFalse(m.exporting)
        m.close()
    }
}
