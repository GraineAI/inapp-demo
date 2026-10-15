package ai.graine.demo

import ai.graine.demo.domain.AppConfig
import ai.graine.demo.domain.DemoActionResult
import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.DemoScreen
import ai.graine.demo.graine.CallEvent
import ai.graine.demo.ui.AppModel
import ai.graine.demo.ui.DemoTab
import ai.graine.inapp.kmp.GraineCallSource
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The demo's state machine, over a fake SDK — the same rules on Android and iOS. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppModelTest {
    private val config = AppConfig.of("https://www.graine.ai", "pk_test_demo")

    private fun TestScope.model(fake: FakeGraineClient = FakeGraineClient(), store: MemoryStore = MemoryStore()): Pair<AppModel, FakeGraineClient> =
        AppModel(fake, config, listOf(TestPack.pack, TestPack.other), store, scope = this as CoroutineScope, now = { TestClock.at(12) }) to fake

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
    fun theBookingButtonOpensTheSdksSheetOnlyWhenTheSdkSaysItCan() = runTest {
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
        // The SDK says no (booking off, Graine does not ring this phone, or nobody identified): off, and nothing opens.
        fake.bookable[DemoAgent.MAIN] = false
        fake.onSessionReady?.invoke()
        assertFalse(m.canBook(DemoAgent.MAIN))
        assertTrue(m.canBook(DemoAgent.HELP), "each agent answers for itself")
        m.book()
        advanceUntilIdle()
        assertEquals(2, fake.bookings.size)
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
}
