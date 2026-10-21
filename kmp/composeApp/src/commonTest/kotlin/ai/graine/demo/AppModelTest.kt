package ai.graine.demo

import ai.graine.demo.domain.AppConfig
import ai.graine.demo.domain.DemoActionResult
import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.DemoScreen
import ai.graine.demo.domain.EndedCall
import ai.graine.demo.graine.CallEvent
import ai.graine.demo.ui.AppModel
import ai.graine.demo.ui.DemoTab
import ai.graine.inapp.kmp.GraineCallConnection
import ai.graine.inapp.kmp.GraineCallSource
import ai.graine.inapp.kmp.GraineCallTransport
import ai.graine.inapp.kmp.GraineCallStatus
import ai.graine.inapp.kmp.GraineErrorCode
import ai.graine.inapp.kmp.GraineException
import ai.graine.inapp.kmp.GraineMemory
import ai.graine.inapp.kmp.GraineScheduledCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The demo's state machine, over a fake SDK — the same rules on Android and iOS. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppModelTest {
    private val config = AppConfig.of("https://www.graine.ai", "pk_test_demo")

    private fun TestScope.model(fake: FakeGraineClient = FakeGraineClient(), store: MemoryStore = MemoryStore()): Pair<AppModel, FakeGraineClient> =
        AppModel(fake, config, listOf(TestPack.pack, TestPack.other), store, scope = this as CoroutineScope, now = { TestClock.at(12) }, zone = TestClock.zone) to fake

    @Test
    fun anyVariableCanBeAddedAndRemovedAndProfileShowsWhatTheAgentReceives() = runTest {
        val (m, fake) = model()
        m.signIn(TestPack.pack.users[0], memory = true)
        advanceUntilIdle()
        assertEquals(mapOf("callee_name" to "Riya Sharma"), m.sentVariables, "the SDK's own read-back")
        assertNull(m.addVariable("name_user", "Riya"))
        assertNull(m.addVariable(" age ", " 34 "), "trimmed, as typed")
        assertEquals(mapOf("callee_name" to "Riya Sharma", "name_user" to "Riya", "age" to "34"), m.sentVariables)
        assertEquals(mapOf<String, String?>("age" to "34"), fake.variables.last(), "one variable, as an API call carries it")
        assertNotNull(m.addVariable("2fast", "x"), "a name starts with a letter")
        assertNotNull(m.addVariable("graine_user_key", "x"), "graine_ names are Graine's")
        assertNotNull(m.addVariable(TestPack.pack.identifierField, "x"), "the identifier is never a variable")
        assertNotNull(m.addVariable("note", "  "), "a value is required")
        assertNotNull(m.addVariable("note", "x".repeat(201)), "a value is at most 200 characters")
        m.removeVariable("age")
        assertEquals(mapOf<String, String?>("age" to null), fake.variables.last())
        assertEquals(mapOf("callee_name" to "Riya Sharma", "name_user" to "Riya"), m.sentVariables)
        m.signOut()
        assertEquals(setOf("callee_name", "name_user"), fake.variables.last().keys, "the pack's and the added")
        assertTrue(fake.variables.last().values.all { it == null })
        assertTrue(m.sentVariables.isEmpty() && m.addedVariables.isEmpty())
    }

    @Test
    fun signingInSetsTheAgentsVariablesThroughTheSdkAndSigningOutClearsThem() = runTest {
        val (m, fake) = model()
        m.signIn(TestPack.pack.users[0], memory = true)
        advanceUntilIdle()
        assertEquals(listOf(mapOf<String, String?>("callee_name" to "Riya Sharma")), fake.variables, "as an API call sends them")
        m.signOut()
        assertEquals(mapOf<String, String?>("callee_name" to null), fake.variables.last(), "nothing of Riya's reaches the next user")
    }

    @Test
    fun signingInIdentifiesOnceWithTheTraitsAndTheMemoryChoice() = runTest {
        val (m, fake) = model()
        m.signIn(TestPack.pack.users[0], memory = false)
        advanceUntilIdle()
        assertEquals(mapOf("plan" to "Diabetes care", "name" to "Riya Sharma", "patient_id" to "P-1") to false, fake.identified.single())
        assertEquals(listOf("metformin", "atorvastatin", "vitamin-d3"), m.items!!.rows.map { it.id }, "only this user's rows")
        assertEquals("home", fake.lastScreen?.screen)
        assertEquals(listOf("memory", "next_call"), fake.lastScreen?.fields?.map { it.name })
        m.signOut()
        assertEquals(null to null, fake.identified.last())
        assertNull(m.user)
        assertEquals("sign_in", fake.lastScreen?.screen)
    }

    @Test
    fun theAgentsActionsRunOnTheSharedRows() = runTest {
        val (m, fake) = model()
        m.signIn(TestPack.pack.users[0], memory = true)
        assertEquals(DemoActionResult.ok("Metformin 500 mg: Taken today."), fake.run("mark_done", mapOf("item" to "metformin")))
        assertEquals(DemoActionResult.ok("Metformin 500 mg is already Taken today."), fake.run("mark_done", mapOf("item" to "Metformin 500 mg")))
        assertEquals("Taken today", m.items!!.statusOf(m.items!!.rows[0]))
        assertEquals(DemoActionResult.refused("Mark as taken is not available for Vitamin D3."), fake.run("mark_done", mapOf("item" to "vitamin")))
        assertEquals(DemoActionResult.ok("Opened Check-in calls."), fake.run("open_screen", mapOf("screen" to "calls")))
        assertEquals(DemoTab.CALLS, m.tab)
        assertEquals(DemoActionResult.ok("Opened Medicines."), fake.run("open_screen", mapOf("path" to "/Medicines")))
        assertEquals(DemoActionResult.ok("Showing Atorvastatin 10 mg."), fake.run("open_item", mapOf("item" to "9 days")))
        assertEquals("atorvastatin", m.expandedRowId)
        assertTrue(fake.run("open_screen", mapOf("screen" to "billing"))!!.message.startsWith("There is no screen called \"billing\""))
        assertEquals(DemoActionResult.refused("This version of the app cannot do \"Teleport\"."), fake.run("teleport"))
        val items = fake.lastScreen!!
        assertEquals("medicines", items.screen)
    }

    @Test
    fun theBookingButtonAlwaysOpensTheSdksSheet() = runTest {
        val fake = FakeGraineClient().apply { agents = listOf(DemoAgent.MAIN, DemoAgent.HELP) }
        val (m, _) = model(fake)
        m.signIn(TestPack.pack.users[0], memory = true)
        advanceUntilIdle()
        assertTrue(m.canBook(DemoAgent.MAIN))
        // Booked in the sheet: the call joins the list, and the list is read again.
        fake.listResults[DemoAgent.MAIN] = Result.success(listOf(GraineScheduledCall("c1", TestClock.instant(18), GraineCallStatus.Scheduled, GraineCallSource.App)))
        m.book()
        advanceUntilIdle()
        assertEquals("Weekly check-in" to DemoAgent.MAIN, fake.bookings.single(), "opened with the pack's first reason")
        assertEquals(listOf("main:c1"), m.upcomingCalls.map { it.id })
        assertFalse(m.bookingOpen)
        // Closed without booking: nothing changes, nothing is said.
        fake.bookingResult = Result.success(null)
        m.book(DemoAgent.HELP)
        advanceUntilIdle()
        assertEquals(DemoAgent.HELP, fake.bookings.last().second)
        assertEquals(listOf("main:c1"), m.upcomingCalls.map { it.id })
        assertNull(m.notice)
        // The SDK says no (booking off, Graine does not ring this phone, or nobody identified): the button still opens
        // the SDK's sheet, which says why itself.
        fake.bookable[DemoAgent.MAIN] = false
        fake.onSessionReady?.invoke()
        assertFalse(m.canBook(DemoAgent.MAIN))
        assertTrue(m.canBook(DemoAgent.HELP), "each agent answers for itself")
        m.book()
        advanceUntilIdle()
        assertEquals(3, fake.bookings.size, "the SDK's sheet opens and explains itself")
        // An agent that cannot be reached: the SDK's refusal in words.
        fake.bookingResult = Result.failure(GraineException(GraineErrorCode.Network, "offline"))
        m.book(DemoAgent.HELP)
        advanceUntilIdle()
        assertEquals("No connection. Check your network and try again.", m.notice)
        m.signOut()
        assertEquals(mapOf(DemoAgent.MAIN to false, DemoAgent.HELP to true), m.bookable, "read again on sign-out")
    }

    @Test
    fun bothAgentsCallsAreListedAndOneFailingDoesNotHideTheOther() = runTest {
        val fake = FakeGraineClient().apply { agents = listOf(DemoAgent.MAIN, DemoAgent.HELP) }
        val (m, _) = model(fake)
        fake.listResults[DemoAgent.MAIN] = Result.success(listOf(GraineScheduledCall("a", TestClock.instant(18), GraineCallStatus.Scheduled, GraineCallSource.App)))
        fake.listResults[DemoAgent.HELP] = Result.failure(GraineException(GraineErrorCode.Network, "offline"))
        m.signIn(TestPack.pack.users[0], memory = true)
        advanceUntilIdle()
        assertEquals(listOf("main:a"), m.upcomingCalls.map { it.id })
        assertEquals("No connection. Check your network and try again.", m.callsError)
        assertEquals("a", m.nextCall?.call?.id)
        m.cancelCall(m.upcomingCalls.single())
        advanceUntilIdle()
        assertEquals("a" to DemoAgent.MAIN, fake.cancelled.single())
        assertEquals("Call cancelled.", m.notice)
    }

    @Test
    fun memoryIsListedErasedAndSwitchedForThisUserOnly() = runTest {
        val (m, fake) = model()
        m.signIn(TestPack.pack.users[0], memory = true)
        fake.memoriesResult = Result.success(listOf(GraineMemory("m1", "Takes metformin at 8 pm", TestClock.instant(9))))
        m.refreshMemories()
        advanceUntilIdle()
        assertEquals(listOf("m1"), m.memories.map { it.id })
        fake.forgetResult = Result.success(1)
        var gone = false
        m.forgetMemory { gone = it }
        advanceUntilIdle()
        assertTrue(gone)
        assertEquals("Erased the 1 thing Asha remembered about you.", m.notice)
        assertTrue(m.memories.isEmpty())
        fake.memoriesResult = Result.failure(GraineException(GraineErrorCode.DeviceLimit, "no"))
        m.refreshMemories()
        advanceUntilIdle()
        assertEquals("This user is signed in on the maximum number of devices. Sign out on another device first.", m.memoriesError)
        m.setMemory(false)
        assertEquals(listOf(false), fake.memorySets)
        assertEquals("Memory: off" to false, m.memoryStatus)
    }

    @Test
    fun oneConversationAtATimeAndCallsCarryThePacksReason() = runTest {
        val fake = FakeGraineClient().apply { agents = listOf(DemoAgent.MAIN, DemoAgent.HELP) }
        val (m, _) = model(fake)
        m.signIn(TestPack.pack.users[0], memory = true)
        m.talk()
        assertEquals(listOf(DemoAgent.MAIN), fake.voiceStarts)
        m.openChat(DemoAgent.HELP)
        assertEquals(1, fake.voiceEnds, "opening a chat ends the voice call")
        assertNull(m.voiceAgent)
        assertEquals("help", fake.lastScreen?.screen)
        m.startChat("Why was I charged twice?", DemoAgent.HELP)
        assertEquals("Why was I charged twice?" to DemoAgent.HELP, fake.chatMessages.single())
        m.callAgent()
        assertEquals(Triple("patient_question", mapOf("entry_point" to "home"), DemoAgent.MAIN), fake.nativeCalls.single())
        assertEquals("placed", m.callLog.entries.first().event)
        fake.nativeCallId = null
        m.callAgent()
        assertEquals("Another call is in progress.", m.notice)
    }

    @Test
    fun theCallLogKeepsWhatTheSdkReportedAndRefreshesCalls() = runTest {
        val store = MemoryStore()
        val (m, fake) = model(store = store)
        m.signIn(TestPack.pack.users[0], memory = true)
        fake.onCallEvent?.invoke(CallEvent("call_9", "ended", "remote"))
        advanceUntilIdle()
        assertEquals("Ended by Asha", m.callLog.entries.first().title("Asha"))
        assertTrue(store.values["demo.callLog"]!!.contains("call_9"))
        m.clearCallLog()
        assertTrue(m.callLog.entries.isEmpty())
    }

    @Test
    fun switchingPackSignsOutAndRemembersTheChoice() = runTest {
        val store = MemoryStore()
        val (m, fake) = model(store = store)
        m.signIn(TestPack.pack.users[0], memory = true)
        m.choosePack("other-pack")
        assertNull(m.user)
        assertEquals("other-pack", store.values["demo.pack"])
        assertEquals("Kabir · Test Bank", fake.callNames.last())
        assertEquals("Kabir", m.agentName)
        assertFalse(m.isAvailable(DemoAgent.HELP))
        m.open(DemoScreen.HOME)
        assertNull(m.user)
    }

    // ── "Your own design" ───────────────────────────────────────────────────

    @Test
    fun yourOwnDesignIsKeptOnTheDeviceAndNamesTheAppsCallScreen() = runTest {
        val store = MemoryStore()
        val (m, fake) = model(store = store)
        assertFalse(m.ownDesign, "Graine's screens by default")
        assertTrue(fake.ownCallScreens.isEmpty(), "Graine's screens change nothing about calls")
        m.signIn(TestPack.pack.users[0], memory = true)
        m.chooseOwnDesign(true)
        assertTrue(m.ownDesign)
        assertEquals(listOf(true), fake.ownCallScreens)
        // The next launch keeps it, and names the app's call screen before any call can ring.
        val (again, fake2) = model(store = store)
        assertTrue(again.ownDesign)
        assertEquals(listOf(true), fake2.ownCallScreens)
        again.chooseOwnDesign(false)
        assertEquals(listOf(true, false), fake2.ownCallScreens)
        assertFalse(model(store = store).first.ownDesign)
    }

    @Test
    fun switchingDesignClosesWhatIsOpen() = runTest {
        val (m, fake) = model()
        m.signIn(TestPack.pack.users[0], memory = true)
        m.openChat(DemoAgent.MAIN)
        m.chooseOwnDesign(true)
        assertNull(m.chatAgent, "the next chat opens in the design chosen")
        m.talk()
        m.openPublicState()
        assertTrue(m.publicStateOpen)
        m.chooseOwnDesign(false)
        assertNull(m.voiceAgent)
        assertEquals(1, fake.voiceEnds)
        assertFalse(m.publicStateOpen)
    }

    @Test
    fun inYourOwnDesignTalkOpensReadyAndStartsOnlyOnItsStart() = runTest {
        val (m, fake) = model()
        m.signIn(TestPack.pack.users[0], memory = true)
        m.chooseOwnDesign(true)
        m.talk()
        assertEquals(DemoAgent.MAIN, m.voiceAgent)
        assertTrue(fake.voiceStarts.isEmpty(), "nothing starts — no microphone — until Start is tapped")
        m.startTalk()
        assertEquals(listOf(DemoAgent.MAIN), fake.voiceStarts)
        m.endTalk()
        assertEquals(1, fake.voiceEnds)
        assertEquals(DemoAgent.MAIN, m.voiceAgent, "End leaves the panel, to start again")
        m.closeTalk()
        assertEquals(2, fake.voiceEnds)
        assertNull(m.voiceAgent)
    }

    @Test
    fun withGraineScreensTalkStillStartsAtOnceAndBookingOpensTheSdksSheet() = runTest {
        val (m, fake) = model()
        m.signIn(TestPack.pack.users[0], memory = true)
        m.talk()
        assertEquals(listOf(DemoAgent.MAIN), fake.voiceStarts)
        m.book()
        advanceUntilIdle()
        assertEquals(1, fake.bookings.size)
        assertNull(m.ownBooking)
    }

    private fun FakeGraineClient.withSlots(): FakeGraineClient = apply {
        slots = { day ->
            when (day) {
                LocalDate(2026, 10, 14) -> listOf(TestClock.instant(18), TestClock.instant(18, 5))
                LocalDate(2026, 10, 15) -> listOf(TestClock.instant(9, day = 15))
                else -> emptyList()
            }
        }
    }

    @Test
    fun inYourOwnDesignBookingIsTheAppsOwnScreenAndBooksThePickedTime() = runTest {
        val (m, fake) = model(FakeGraineClient().withSlots())
        m.signIn(TestPack.pack.users[0], memory = true)
        m.chooseOwnDesign(true)
        m.book()
        assertTrue(fake.bookings.isEmpty(), "the SDK's sheet is not opened")
        val b = assertNotNull(m.ownBooking)
        assertTrue(b.available)
        assertEquals(listOf(2, 1, 0), b.days.map { it.slotCount }, "today through maxDaysAhead, with the SDK's times")
        assertEquals(LocalDate(2026, 10, 14), b.day, "the first day with times")
        assertEquals("Weekly check-in", b.reason, "the pack's first reason")
        assertFalse(b.canBook, "a time first")
        m.pickBookingSlot(TestClock.instant(7))
        assertNull(m.ownBooking?.slot, "only a time the SDK offers")
        m.pickBookingSlot(TestClock.instant(18, 5))
        assertTrue(m.ownBooking!!.canBook)
        // Graine lists it once booked.
        fake.listResults[DemoAgent.MAIN] = Result.success(listOf(GraineScheduledCall("s1", TestClock.instant(18, 5), GraineCallStatus.Scheduled)))
        m.bookOwn()
        advanceUntilIdle()
        assertEquals(Triple(TestClock.instant(18, 5), "Weekly check-in", DemoAgent.MAIN), fake.scheduled.single())
        assertEquals(TestClock.instant(18, 5), m.ownBooking?.booked?.at)
        assertNull(m.ownBooking?.slot)
        assertTrue(m.calls.any { it.call.at == TestClock.instant(18, 5) }, "the booked call is in the list")
        m.pickBookingDay(LocalDate(2026, 10, 15))
        assertEquals(listOf(TestClock.instant(9, day = 15)), m.ownBooking?.slots)
        m.closeOwnBooking()
        assertNull(m.ownBooking)
    }

    @Test
    fun ownBookingSaysWhyItIsOffAndShowsTheSdksRefusal() = runTest {
        val fake = FakeGraineClient().withSlots().apply { unavailable[DemoAgent.MAIN] = "rings_not_set_up" }
        val (m, _) = model(fake)
        m.signIn(TestPack.pack.users[0], memory = true)
        m.chooseOwnDesign(true)
        m.book()
        assertEquals("rings_not_set_up", m.ownBooking?.unavailableReason)
        m.pickBookingSlot(TestClock.instant(18))
        assertFalse(m.ownBooking!!.canBook, "nothing is booked while booking is off")
        m.bookOwn()
        assertTrue(fake.scheduled.isEmpty())

        fake.unavailable.remove(DemoAgent.MAIN)
        m.refreshOwnBooking()
        assertTrue(m.ownBooking!!.available)
        assertEquals(TestClock.instant(18), m.ownBooking?.slot, "the time picked is kept")
        val sentence = "That time is outside the hours we can call — pick another"
        fake.scheduleResult = Result.failure(GraineException(GraineErrorCode.OutsideWindow, sentence))
        m.setBookingReason("x".repeat(200))
        assertEquals(120, m.ownBooking?.reason?.length, "Graine keeps at most 120 characters")
        m.bookOwn()
        advanceUntilIdle()
        assertEquals(sentence, m.ownBooking?.refusal, "the SDK's own sentence")
        assertFalse(m.ownBooking!!.busy)
    }

    @Test
    fun theAppsCallScreenKeepsAnEndedCallWithItsFinalConnectionUntilClosed() = runTest {
        val (m, fake) = model()
        m.signIn(TestPack.pack.users[0], memory = true)
        val connection = GraineCallConnection(transport = GraineCallTransport.WEBRTC, setupMs = 300, tapToVoiceMs = 1500)
        fake.onCallEvent!!(CallEvent("call_0", "ended", "remote", DemoAgent.MAIN, 12, connection))
        assertNull(m.endedCall, "Graine's screens draw their own end")

        m.chooseOwnDesign(true)
        fake.onCallEvent!!(CallEvent("call_1", "ended", "remote", DemoAgent.MAIN, 42, connection, "remote_bye"))
        assertEquals(EndedCall("call_1", DemoAgent.MAIN, "ended", "remote", 42, connection, "remote_bye"), m.endedCall)
        m.callAgent()
        assertNull(m.endedCall, "a new call clears it")
        fake.onCallEvent!!(CallEvent("call_2", "failed", "no_answer", DemoAgent.MAIN))
        assertEquals("failed", m.endedCall?.outcome)
        m.closeEndedCall()
        assertNull(m.endedCall)
    }
}

