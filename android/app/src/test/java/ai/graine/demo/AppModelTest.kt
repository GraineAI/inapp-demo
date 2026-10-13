package ai.graine.demo

import ai.graine.demo.domain.AppConfig
import ai.graine.demo.domain.DemoActionResult
import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.DemoField
import ai.graine.demo.domain.DemoScreen
import ai.graine.demo.domain.UseCasePacks
import ai.graine.demo.graine.CallEvent
import ai.graine.demo.ui.AppModel
import ai.graine.demo.ui.DemoTab
import ai.graine.inapp.GraineCallSource
import ai.graine.inapp.GraineCallStatus
import ai.graine.inapp.GraineError
import ai.graine.inapp.GraineErrorCode
import ai.graine.inapp.GraineMemory
import ai.graine.inapp.GraineScheduledCall
import ai.graine.inapp.GraineSchedulingRules
import android.app.Activity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/** The app's behaviour around the SDK, against a fake SDK: every adapter call the screens make. */
class AppModelTest {
    private lateinit var fake: FakeGraineClient
    private lateinit var store: MemoryStore
    private lateinit var model: AppModel
    private var clock = TestClock.at(9, 30)
    private lateinit var savedZone: TimeZone

    private val riya get() = TestPack.pack.users[0]
    private val arjun get() = TestPack.pack.users[1]

    @Before fun setUp() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TestClock.zone)
        fake = FakeGraineClient()
        store = MemoryStore()
        model = make()
    }

    @After fun restore() = TimeZone.setDefault(savedZone)

    private fun make() = AppModel(fake, AppConfig.of(null, "pk_test_x"), listOf(TestPack.pack, TestPack.other), store) { clock }

    /** The same app with the help desk configured. */
    private fun withHelp() {
        fake.agents = listOf(DemoAgent.MAIN, DemoAgent.HELP)
        model = make()
    }

    // ── packs ──

    @Test fun registersEveryActionOfThePackAndReportsSignIn() {
        assertEquals(setOf("open_screen", "open_item", "mark_done", "pause_reminders", "teleport"), fake.actions[DemoAgent.MAIN]?.keys)
        assertNull(fake.actions[DemoAgent.HELP])
        assertEquals("sign_in", fake.lastScreen?.screen)
        assertEquals(emptyList<DemoField>(), fake.lastScreen?.fields)
    }

    @Test fun choosingAPackIsRememberedRenamesTheCallAndSignsOut() {
        model.signIn(riya, true)
        model.choosePack("other-pack")
        assertNull(model.user)
        assertEquals(null to null, fake.identified.last())
        assertEquals("other-pack", model.pack?.id)
        assertEquals("other-pack", store.getString(UseCasePacks.CHOICE_KEY))
        assertEquals(listOf("Kabir · Test Bank"), fake.callNames)
        assertEquals("other-pack", make().pack?.id)
    }

    // ── identity and memory ──

    @Test fun signInIdentifiesTheUserWithTheirConsent() {
        model.signIn(riya, false)
        assertEquals(mapOf("plan" to "Diabetes care", "name" to "Riya Sharma", "patient_id" to "P-1") to false, fake.identified.last())
        assertEquals("Memory: off" to false, model.memoryStatus)
        assertEquals("home", fake.lastScreen?.screen)
        assertEquals(listOf("metformin", "atorvastatin", "vitamin-d3"), model.items?.rows?.map { it.id })
    }

    @Test fun signOutForgetsTheUser() {
        model.signIn(riya, true)
        model.talk()
        model.signOut()
        assertEquals(null to null, fake.identified.last())
        assertEquals(1, fake.voiceEnds)
        assertNull(model.user)
        assertEquals("sign_in", fake.lastScreen?.screen)
    }

    @Test fun memoryChipNeedsTheAgentAndTheConsent() {
        model.signIn(riya, true)
        assertEquals("Memory: on" to true, model.memoryStatus)
        fake.agentMemoryEnabled = false
        fake.onSessionReady?.invoke()
        assertEquals("Memory: off for this assistant" to false, model.memoryStatus)
    }

    @Test fun memoriesArePerUserAndErasingSaysHowMany() {
        fake.memoriesResult = listOf(GraineMemory("m1", "Takes metformin at 8 pm")) to null
        model.signIn(riya, true)
        model.refreshMemories()
        assertEquals(listOf("Takes metformin at 8 pm"), model.memories.map { it.text })
        model.open(DemoScreen.MEMORY)
        assertEquals(listOf("memory", "remembered"), fake.lastScreen?.fields?.map { it.name })
        assertEquals("1", fake.lastScreen?.fields?.last()?.value)

        fake.forgetResult = 3 to null
        var erased = false
        model.forgetMemory { erased = it }
        assertTrue(erased)
        assertTrue(model.memories.isEmpty())
        assertEquals("Erased the 3 things Asha remembered about you.", model.notice)

        fake.memoriesResult = emptyList<GraineMemory>() to null
        model.signOut()
        model.signIn(arjun, true)
        model.refreshMemories()
        assertTrue(model.memories.isEmpty())

        fake.forgetResult = null to GraineError(GraineErrorCode.IDENTITY_REQUIRED, "x")
        model.forgetMemory { erased = it }
        assertFalse(erased)
        assertEquals("Sign in to use this.", model.notice)
    }

    @Test fun memoriesErrorIsShownInWords() {
        fake.memoriesResult = null to GraineError(GraineErrorCode.DEVICE_LIMIT, "x")
        model.signIn(riya, true)
        model.refreshMemories()
        assertEquals("This user is signed in on the maximum number of devices. Sign out on another device first.", model.memoriesError)
    }

    // ── actions, in the pack's words ──

    @Test fun actionsNeedASignedInUser() {
        assertEquals(DemoActionResult.Status.REFUSED, fake.run("open_screen", mapOf("screen" to "home"))?.status)
    }

    @Test fun openScreen() {
        model.signIn(riya, true)
        assertEquals(DemoActionResult.ok("Opened Medicines."), fake.run("open_screen", mapOf("screen" to "medicines")))
        assertEquals(DemoTab.ITEMS, model.tab)
        assertEquals("medicines", fake.lastScreen?.screen)
        assertEquals(DemoActionResult.ok("Opened Asha."), fake.run("open_screen", mapOf("screen" to "chat")))
        assertTrue(model.chatOpen)
        assertEquals(DemoActionResult.ok("Opened Memory."), fake.run("open_screen", mapOf("path" to "/memory")))
        assertFalse(model.chatOpen)
        assertEquals(DemoActionResult.ok("Opened Profile."), fake.run("open_screen", mapOf("screen" to "profile")))
        assertTrue(model.profileOpen)
        assertEquals("profile", fake.lastScreen?.screen)
        assertEquals(
            DemoActionResult.refused("There is no screen called \"billing\". Screens: home, chat, medicines, calls, memory, profile, help."),
            fake.run("open_screen", mapOf("screen" to "billing")),
        )
    }

    @Test fun openItemAndTheRowRules() {
        model.signIn(riya, true)
        assertEquals(DemoActionResult.ok("Showing Metformin 500 mg."), fake.run("open_item", mapOf("item" to "Metformin 500 mg")))
        assertEquals("metformin", model.expandedRowId)
        assertEquals(DemoActionResult.refused("Which one? Metformin 500 mg, Atorvastatin 10 mg."), fake.run("open_item", mapOf("item" to "mg")))
        assertEquals(
            DemoActionResult.refused("No medicine called \"insulin\". Medicines: Metformin 500 mg, Atorvastatin 10 mg, Vitamin D3."),
            fake.run("open_item", mapOf("item" to "insulin")),
        )
    }

    @Test fun setStatusFollowsThePack() {
        model.signIn(riya, true)
        model.open(DemoScreen.ITEMS)
        assertEquals(DemoActionResult.ok("Metformin 500 mg: Taken today."), fake.run("mark_done", mapOf("item" to "metformin")))
        assertEquals(DemoActionResult.ok("Metformin 500 mg is already Taken today."), fake.run("mark_done", mapOf("item" to "metformin")))
        assertEquals(DemoField("metformin", "Metformin 500 mg", "Taken today", "filled"), fake.lastScreen?.fields?.first { it.name == "metformin" })
        assertEquals("At bedtime", fake.lastScreen?.fields?.first { it.name == "atorvastatin" }?.value)
        assertEquals(DemoActionResult.refused("Pause reminders is not available for Metformin 500 mg."), fake.run("pause_reminders", mapOf("item" to "metformin")))
        assertEquals(DemoActionResult.refused("This version of the app cannot do \"Teleport\"."), fake.run("teleport"))
    }

    @Test fun statusesLastTheSessionPerUser() {
        model.signIn(riya, true)
        fake.run("mark_done", mapOf("item" to "metformin"))
        model.signOut()
        model.signIn(arjun, true)
        assertEquals("Due 2:00 pm", model.items?.statusOf(TestPack.pack.items.rows[2]))
        model.signOut()
        model.signIn(riya, true)
        assertEquals("Taken today", model.items?.statusOf(TestPack.pack.items.rows[0]))
        val restarted = make()
        restarted.signIn(riya, true)
        assertEquals("Due 8:00 pm", restarted.items?.statusOf(TestPack.pack.items.rows[0]))
    }

    // ── talk, chat, call (the main agent) ──

    @Test fun talkChatAndCall() {
        model.signIn(riya, true)
        model.talk()
        assertTrue(model.voiceBarVisible)
        assertEquals(listOf(DemoAgent.MAIN), fake.voiceStarts)
        model.startChat("What is due today?")
        assertEquals(listOf("What is due today?" to DemoAgent.MAIN), fake.chatMessages)
        assertEquals("chat", fake.lastScreen?.screen)

        model.activity = Activity()
        model.callAgent()
        assertEquals("patient_question", fake.nativeCalls.first().first)
        assertEquals(DemoAgent.MAIN, fake.nativeCalls.first().third)
        assertEquals("placed", model.callLog.entries.first().event)
        fake.nativeCallId = null
        model.callAgent()
        assertEquals("Another call is in progress.", model.notice)
        assertEquals(1, model.callLog.entries.size)
    }

    @Test fun callOutcomesAreLoggedAndKept() {
        model.signIn(riya, true)
        fake.onCallEvent?.invoke(CallEvent("c9", "incoming", "scheduled"))
        fake.onCallEvent?.invoke(CallEvent("c9", "missed", null))
        assertEquals(listOf("Missed", "Asha called (scheduled call)"), model.callLog.entries.map { it.title("Asha") })
        assertEquals(2, make().callLog.entries.size)
    }

    // ── two agents ──

    @Test fun withoutAHelpKeyTheHelpDeskIsNotThere() {
        model.signIn(riya, true)
        model.activity = Activity()
        assertFalse(model.isAvailable(DemoAgent.HELP))
        model.talk(DemoAgent.HELP)
        model.openChat(DemoAgent.HELP)
        model.callAgent(DemoAgent.HELP)
        assertTrue(fake.voiceStarts.isEmpty())
        assertNull(model.chatAgent)
        assertTrue(fake.nativeCalls.isEmpty())
        model.open(DemoScreen.HELP)
        assertEquals(DemoTab.HELP, model.tab)
        assertEquals("help", fake.lastScreen?.screen)
    }

    @Test fun theHelpDeskHasItsOwnChatVoiceAndCalls() {
        withHelp()
        model.signIn(riya, true)
        model.activity = Activity()
        assertTrue(model.isAvailable(DemoAgent.HELP))
        // Identified once; the SDK shares it with both agents.
        assertEquals(1, fake.identified.size)
        assertNotNull(fake.actions[DemoAgent.HELP]?.get("open_screen"))

        model.talk(DemoAgent.HELP)
        assertEquals(listOf(DemoAgent.HELP), fake.voiceStarts)
        model.startChat("Where is my bill?", DemoAgent.HELP)
        assertEquals("Where is my bill?" to DemoAgent.HELP, fake.chatMessages.last())
        assertEquals(DemoAgent.HELP, model.chatAgent)
        assertEquals("help", fake.lastScreen?.screen)

        model.callAgent(DemoAgent.HELP)
        assertEquals(DemoAgent.HELP, fake.nativeCalls.last().third)
        assertEquals("help", fake.nativeCalls.last().second["entry_point"])
        assertEquals("help", model.callLog.entries.first().agent)
    }

    @Test fun talkingToOneAgentEndsTheOthersVoice() {
        withHelp()
        model.signIn(riya, true)
        model.talk(DemoAgent.MAIN)
        model.talk(DemoAgent.HELP)
        assertEquals(1, fake.voiceEnds)
        assertEquals(DemoAgent.HELP, model.voiceAgent)
    }

    @Test fun scheduledCallsListBothAgentsCallsWithTheirAgent() {
        withHelp()
        fake.listResults[DemoAgent.MAIN] = listOf(GraineScheduledCall("m1", TestClock.date(18), GraineCallStatus.SCHEDULED)) to null
        fake.listResults[DemoAgent.HELP] = listOf(
            GraineScheduledCall("h1", TestClock.date(12), GraineCallStatus.SCHEDULED),
            GraineScheduledCall("h0", TestClock.date(8), GraineCallStatus.COMPLETED),
        ) to null
        model.signIn(riya, true)
        assertEquals(listOf("help:h1", "main:m1"), model.upcomingCalls.map { it.id })
        assertEquals(listOf("help:h0"), model.pastCalls.map { it.id })
        assertEquals(DemoAgent.HELP, model.nextCall?.agent)
        model.open(DemoScreen.CALLS)
        assertEquals("2", fake.lastScreen?.fields?.first()?.value)

        model.cancelCall(model.upcomingCalls[0])
        assertEquals("h1" to DemoAgent.HELP, fake.cancelled.first())
    }

    @Test fun oneAgentFailingDoesNotHideTheOthersCalls() {
        withHelp()
        fake.listResults[DemoAgent.MAIN] = listOf(GraineScheduledCall("m1", TestClock.date(18), GraineCallStatus.SCHEDULED)) to null
        fake.listResults[DemoAgent.HELP] = null to GraineError(GraineErrorCode.NETWORK, "offline")
        model.signIn(riya, true)
        assertEquals(listOf("main:m1"), model.calls.map { it.id })
        assertEquals("No connection. Check your network and try again.", model.callsError)
    }

    @Test fun bookingWithTheHelpDeskGoesToItAndUsesItsRules() {
        withHelp()
        fake.rules[DemoAgent.HELP] = GraineSchedulingRules(usersCanBook = true, minLeadMinutes = 30, maxDaysAhead = 3)
        fake.scheduleResult = GraineScheduledCall("h9", TestClock.date(18), GraineCallStatus.SCHEDULED, GraineCallSource.APP) to null
        model.signIn(riya, true)
        assertEquals(TestClock.at(10, 0), model.bookingRange(DemoAgent.HELP).first)
        var said: String? = "unset"
        model.scheduleCall(TestClock.at(18), "Medicine review", DemoAgent.HELP) { said = it }
        assertNull(said)
        assertEquals(DemoAgent.HELP, fake.scheduled.first().third)
        assertTrue(model.notice!!.startsWith("Call with"))

        fake.scheduleResult = null to GraineError(GraineErrorCode.TOO_SOON, "x")
        model.scheduleCall(TestClock.at(9, 40), null, DemoAgent.HELP) { said = it }
        assertEquals("Choose a time at least 30 minutes from now.", said)
    }

    // ── scheduled calls (the main agent) ──

    @Test fun theSDKsRefusalIsShownInWordsAndTheAppDoesNotCheckFirst() {
        fake.rules[DemoAgent.MAIN] = GraineSchedulingRules(usersCanBook = true, minLeadMinutes = 10)
        fake.scheduleResult = null to GraineError(GraineErrorCode.TOO_SOON, "Too soon.")
        model.signIn(riya, true)
        var said: String? = "unset"
        model.scheduleCall(TestClock.at(9, 32), "Weekly check-in") { said = it }
        assertEquals("Choose a time at least 10 minutes from now.", said)
        assertEquals(1, fake.scheduled.size)
    }

    @Test fun bookingSendsTheReasonAndShowsTheCall() {
        fake.scheduleResult = GraineScheduledCall("c42", TestClock.date(18), GraineCallStatus.SCHEDULED, GraineCallSource.APP, "Medicine review") to null
        model.signIn(riya, true)
        var said: String? = "unset"
        model.scheduleCall(TestClock.at(18), "Medicine review") { said = it }
        assertNull(said)
        assertEquals(TestClock.at(18), fake.scheduled.first().first.time)
        assertEquals("Medicine review", fake.scheduled.first().second)
        assertEquals(DemoAgent.MAIN, fake.scheduled.first().third)
        assertTrue(model.notice!!.startsWith("Call with Asha booked for Today"))
    }

    @Test fun bookingRangeComesFromTheSDKsRules() {
        fake.rules[DemoAgent.MAIN] = GraineSchedulingRules(usersCanBook = true, minLeadMinutes = 10, maxDaysAhead = 2)
        model.signIn(riya, true)
        fake.onSessionReady?.invoke()
        assertEquals(TestClock.at(9, 40)..TestClock.at(9, 30, day = 16), model.bookingRange())
        assertEquals(TestClock.at(10, 0), model.suggestedCallTime())
    }

    @Test fun refreshCancelAndTheCallsContext() {
        fake.listResults[DemoAgent.MAIN] = listOf(
            GraineScheduledCall("a", TestClock.date(12), GraineCallStatus.SCHEDULED),
            GraineScheduledCall("b", TestClock.date(8), GraineCallStatus.COMPLETED),
        ) to null
        model.signIn(riya, true)
        assertEquals(listOf("a", "b"), model.calls.map { it.call.id })
        model.open(DemoScreen.CALLS)
        assertEquals(listOf(DemoField("upcoming", "Upcoming calls", "1", "filled")), fake.lastScreen?.fields)
        model.open(DemoScreen.HOME)
        assertEquals("2026-10-14T06:30:00Z", fake.lastScreen?.fields?.first { it.name == "next_call" }?.value)

        fake.listResults[DemoAgent.MAIN] = listOf(GraineScheduledCall("a", TestClock.date(12), GraineCallStatus.CANCELLED)) to null
        model.cancelCall(model.calls[0])
        assertEquals("a" to DemoAgent.MAIN, fake.cancelled.first())
        assertEquals(GraineCallStatus.CANCELLED, model.calls.first().call.status)
        assertEquals("Call cancelled.", model.notice)
    }

    @Test fun listErrorIsShownInWords() {
        fake.listResults[DemoAgent.MAIN] = null to GraineError(GraineErrorCode.NETWORK, "offline")
        model.signIn(riya, true)
        assertEquals("No connection. Check your network and try again.", model.callsError)
    }

    // ── screen context ──

    @Test fun homeContextHasExactlyTheSharedKeys() {
        model.signIn(riya, true)
        val context = model.screenContext()
        assertEquals("home", context.screen)
        assertEquals("Today", context.title)
        assertEquals(listOf("memory", "next_call"), context.fields.map { it.name })
        assertEquals(listOf("on", "none"), context.fields.map { it.value })
        assertFalse(context.fields.any { it.value?.contains("P-1") == true })
        model.open(DemoScreen.PROFILE)
        assertEquals(emptyList<DemoField>(), fake.lastScreen?.fields)
        model.open(DemoScreen.HELP)
        assertEquals("help", fake.lastScreen?.screen)
        assertEquals("Help", fake.lastScreen?.title)
    }
}
