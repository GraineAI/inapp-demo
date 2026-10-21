package ai.graine.demo

import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.AppConfig
import ai.graine.demo.domain.PastSession
import ai.graine.demo.domain.PastSessionsState
import ai.graine.demo.domain.PastSessionsText
import ai.graine.demo.domain.SessionTranscript
import ai.graine.demo.domain.TranscriptTurn
import ai.graine.demo.ui.AppModel
import ai.graine.inapp.kmp.GraineErrorCode
import ai.graine.inapp.kmp.GraineException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Past sessions: how they read, and the screen's flow over a fake SDK. */
@OptIn(ExperimentalCoroutinesApi::class)
class PastSessionsTest {
    private val config = AppConfig.of("https://www.graine.ai", "pk_test_demo")

    private fun TestScope.model(fake: FakeGraineClient = FakeGraineClient()): Pair<AppModel, FakeGraineClient> =
        AppModel(fake, config, listOf(TestPack.pack), MemoryStore(), scope = this as CoroutineScope, now = { TestClock.at(12) }, zone = TestClock.zone) to fake

    // The contract's first page (sdk/contract/past-sessions.json), as the app holds it.
    private val chat = PastSession("ex-7a1c0001", Instant.parse("2026-10-21T12:00:05Z"), Instant.parse("2026-10-21T12:04:41Z"), 276, "chat", null, "completed", 9, "Can I move my check-in call to Friday?")
    private val call = PastSession("ex-31f00002", Instant.parse("2026-10-20T08:00:00Z"), Instant.parse("2026-10-20T08:03:12Z"), 192, "call", "outbound", "completed", 14, "Hi, yes I can talk now.")

    @Test
    fun aSessionReadsAsKindLengthAndMessages() {
        assertEquals("Chat · 4:36 · 9 messages", PastSessionsText.summary(chat))
        assertEquals("Call · outbound · 3:12 · 14 messages", PastSessionsText.summary(call))
        assertEquals("Completed", PastSessionsText.status("completed"))
        assertEquals("No answer", PastSessionsText.status("no_answer"))
        assertEquals("Ended", PastSessionsText.status(null))
        assertEquals("1 message", PastSessionsText.messages(1))
        assertNull(PastSessionsText.length(null))
        assertTrue(PastSessionsText.isCustomer(TranscriptTurn("customer", "x", null)))
        assertTrue(!PastSessionsText.isCustomer(TranscriptTurn("agent", "x", null)))
    }

    @Test
    fun refusalsReadInPlainWords() {
        assertEquals("Sign in to see past sessions.", PastSessionsText.error("identity_required"))
        assertEquals("Not available on this device.", PastSessionsText.error("device_limit"))
        assertEquals("Too many requests. Try again in a few minutes.", PastSessionsText.error("rate_limited"))
        assertEquals("Past sessions could not be loaded. Try again.", PastSessionsText.error("unavailable"))
        assertEquals("Past sessions could not be loaded. Try again.", PastSessionsText.error("not_found"), "the list itself is never \"that session\"")
        assertEquals("That session is not available.", PastSessionsText.transcriptError("not_found"))
        assertEquals("Not available on this device.", PastSessionsText.transcriptError("device_limit"))
        assertNull(PastSessionsText.error(null))
    }

    @Test
    fun theScreenRefreshesOnOpenAndLoadsMoreOnlyWhileThereIsMore() = runTest {
        val (m, fake) = model()
        val flow = MutableStateFlow(PastSessionsState())
        fake.past[DemoAgent.MAIN] = flow
        m.signIn(TestPack.pack.users[0], memory = true)
        m.openPastSessions(DemoAgent.MAIN)
        assertEquals(DemoAgent.MAIN, m.pastSessionsAgent)
        assertEquals(listOf(DemoAgent.MAIN), fake.pastRefreshes, "the first page, newest first")
        flow.value = PastSessionsState(sessions = listOf(chat), hasMore = true)
        m.loadMorePastSessions()
        assertEquals(1, fake.pastMores.size)
        flow.value = PastSessionsState(sessions = listOf(chat), hasMore = true, loading = true)
        m.loadMorePastSessions()
        assertEquals(1, fake.pastMores.size, "not while a page is on its way")
        flow.value = PastSessionsState(sessions = listOf(chat, call), hasMore = false)
        m.loadMorePastSessions()
        assertEquals(1, fake.pastMores.size, "not past the end")
        assertEquals(15, m.graine.chatCloseDelaySeconds(DemoAgent.MAIN))
        m.closePastSessions()
        assertNull(m.pastSessionsAgent)
    }

    @Test
    fun aTranscriptOpensReadOnlyOrSaysWhyNot() = runTest {
        val (m, fake) = model()
        m.signIn(TestPack.pack.users[0], memory = true)
        m.openPastSessions(DemoAgent.MAIN)
        val turns = listOf(TranscriptTurn("agent", "Hi, how can I help?", Instant.parse("2026-10-21T12:00:05Z")), TranscriptTurn("customer", "Move my call.", Instant.parse("2026-10-21T12:00:20Z")))
        fake.transcripts[chat.id] = Result.success(SessionTranscript(chat.id, chat.startedAt, chat.endedAt, 276, "chat", null, "completed", turns))
        m.openTranscript(chat.id)
        assertEquals(chat.id, m.transcriptLoading)
        advanceUntilIdle()
        assertEquals(turns, m.transcript?.turns)
        assertNull(m.transcriptLoading)
        m.closeTranscript()
        assertNull(m.transcript)

        fake.transcripts[call.id] = Result.failure(GraineException(GraineErrorCode.DeviceLimit, "Not this device."))
        m.openTranscript(call.id)
        advanceUntilIdle()
        assertEquals("Not available on this device.", m.transcriptError)
        fake.transcripts["other"] = Result.failure(GraineException(GraineErrorCode.IdentityRequired, "Sign in."))
        m.openTranscript("other")
        advanceUntilIdle()
        assertEquals("Sign in to see past sessions.", m.transcriptError)
    }

    @Test
    fun signingOutOrSwitchingDesignClosesIt() = runTest {
        val (m, _) = model()
        m.signIn(TestPack.pack.users[0], memory = true)
        m.chooseOwnDesign(true)
        m.openPastSessions(DemoAgent.MAIN)
        m.chooseOwnDesign(false)
        assertNull(m.pastSessionsAgent)
        m.chooseOwnDesign(true)
        m.openPastSessions(DemoAgent.MAIN)
        m.signOut()
        assertNull(m.pastSessionsAgent)
        m.openPastSessions(DemoAgent.MAIN)
        assertNull(m.pastSessionsAgent, "only for a signed-in user")
    }
}
