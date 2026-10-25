package ai.graine.demo

import ai.graine.demo.domain.AppConfig
import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.PastSession
import ai.graine.demo.domain.PastSessionsState
import ai.graine.demo.domain.PastSessionsText
import ai.graine.demo.domain.SessionTranscript
import ai.graine.demo.domain.TranscriptTurn
import ai.graine.demo.graine.PastSessionsSdk
import ai.graine.demo.ui.AppModel
import ai.graine.demo.ui.DemoTab
import ai.graine.inapp.GraineError
import ai.graine.inapp.GraineErrorCode
import ai.graine.inapp.GrainePastSessions
import ai.graine.inapp.GraineSessionChannel
import ai.graine.inapp.GraineSessionDirection
import ai.graine.inapp.GraineSessionSummary
import ai.graine.inapp.GraineSessionTranscript
import ai.graine.inapp.GraineSessionTurn
import ai.graine.inapp.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.Date

/** Past sessions: how the SDK's state reads in the app, and the screen's flow over a fake SDK. */
class PastSessionsTest {
    private lateinit var fake: FakeGraineClient
    private lateinit var model: AppModel

    private fun at(iso: String): Date = Date.from(Instant.parse(iso))

    // The contract's first page (sdk/contract/past-sessions.json), as the app holds it.
    private val chat = PastSession("ex-7a1c0001", at("2026-10-21T12:00:05Z"), at("2026-10-21T12:04:41Z"), 276, "chat", null, "completed", 9, "Can I move my check-in call to Friday?")
    private val call = PastSession("ex-31f00002", at("2026-10-20T08:00:00Z"), at("2026-10-20T08:03:12Z"), 192, "call", "outbound", "completed", 14, "Hi, yes I can talk now.")

    @Before fun setUp() {
        fake = FakeGraineClient()
        model = AppModel(fake, AppConfig.of(null, "pk_test_x"), listOf(TestPack.pack), MemoryStore())
    }

    @Test fun theSdkStateReadsAsTheAppsOwn() {
        val sdk = GrainePastSessions(
            sessions = listOf(
                GraineSessionSummary("ex-7a1c0001", at("2026-10-21T12:00:05Z"), at("2026-10-21T12:04:41Z"), 276, GraineSessionChannel.CHAT, null, "completed", 9, "Can I move my check-in call to Friday?"),
                GraineSessionSummary("ex-31f00002", at("2026-10-20T08:00:00Z"), at("2026-10-20T08:03:12Z"), 192, GraineSessionChannel.CALL, GraineSessionDirection.OUTBOUND, "completed", 14, "Hi, yes I can talk now."),
            ),
            loading = false,
            hasMore = true,
        )
        assertEquals(PastSessionsState(listOf(chat, call), loading = false, hasMore = true, error = null), PastSessionsSdk.state(sdk))
        // What the SDK says it does not know reads as nothing: no status, no preview, no length.
        val bare = PastSessionsSdk.session(GraineSessionSummary("ex-loose0003", at("2026-10-19T18:30:00Z")))
        assertEquals(PastSession("ex-loose0003", at("2026-10-19T18:30:00Z"), null, null, "chat", null, null, 0, null), bare)
        assertEquals("device_limit", PastSessionsSdk.state(GrainePastSessions(error = "device_limit")).error)
        assertTrue(PastSessionsSdk.state(GrainePastSessions(loading = true)).loading)

        val t = PastSessionsSdk.transcript(
            GraineSessionTranscript(
                "ex-7a1c0001", at("2026-10-21T12:00:05Z"), null, 276, GraineSessionChannel.VOICE, GraineSessionDirection.INBOUND, "",
                listOf(GraineSessionTurn(Role.AGENT, "Hi Meera, how can I help?", at("2026-10-21T12:00:05Z")), GraineSessionTurn(Role.CUSTOMER, "Move my call.", at("2026-10-21T12:00:20Z"))),
            ),
        )
        assertEquals(listOf("agent", "customer"), t.turns.map { it.role })
        assertEquals("voice", t.channel)
        assertEquals("inbound", t.direction)
        assertNull(t.status)
        assertEquals("Voice · inbound · 4:36 · Ended", PastSessionsText.transcriptSummary(t))
    }

    @Test fun aSessionReadsAsKindLengthAndMessages() {
        assertEquals("Chat · 4:36 · 9 messages", PastSessionsText.summary(chat))
        assertEquals("Call · outbound · 3:12 · 14 messages", PastSessionsText.summary(call))
        assertEquals("Completed", PastSessionsText.status("completed"))
        assertEquals("No answer", PastSessionsText.status("no_answer"))
        assertEquals("Ended", PastSessionsText.status(null))
        assertEquals("1 message", PastSessionsText.messages(1))
        assertEquals("1:02:03", PastSessionsText.length(3723))
        assertNull(PastSessionsText.length(null))
        assertEquals("…1c0001", PastSessionsText.shortId("ex-7a1c0001"))
        assertEquals("a1", PastSessionsText.shortId("a1"))
        assertTrue(PastSessionsText.isCustomer(TranscriptTurn("customer", "x", null)))
        assertFalse(PastSessionsText.isCustomer(TranscriptTurn("agent", "x", null)))
    }

    @Test fun refusalsReadInPlainWords() {
        assertEquals("Sign in to see past sessions.", PastSessionsText.error("identity_required"))
        assertEquals("Not available on this device.", PastSessionsText.error("device_limit"))
        assertEquals("Too many requests. Try again in a few minutes.", PastSessionsText.error("rate_limited"))
        assertEquals("Past sessions could not be loaded. Try again.", PastSessionsText.error("unavailable"))
        assertEquals("the list itself is never \"that session\"", "Past sessions could not be loaded. Try again.", PastSessionsText.error("not_found"))
        assertEquals("That session is not available.", PastSessionsText.transcriptError("not_found"))
        assertEquals("Not available on this device.", PastSessionsText.transcriptError("device_limit"))
        assertNull(PastSessionsText.error(null))
        assertFalse("signing in is the answer", PastSessionsText.canRetry("identity_required"))
        assertFalse("another device is the answer", PastSessionsText.canRetry("device_limit"))
        assertTrue(PastSessionsText.canRetry("unavailable"))
    }

    @Test fun theScreenRefreshesOnOpenAndLoadsMoreOnlyWhileThereIsMore() {
        model.signIn(TestPack.pack.users[0], memory = true)
        model.openPastSessions(DemoAgent.MAIN)
        assertEquals(DemoAgent.MAIN, model.pastSessionsAgent)
        assertEquals("the first page, newest first", listOf(DemoAgent.MAIN), fake.pastRefreshes)
        fake.pastChanged(DemoAgent.MAIN, PastSessionsState(sessions = listOf(chat), hasMore = true))
        assertEquals(listOf(chat), model.pastSessions.sessions)
        model.loadMorePastSessions()
        assertEquals(1, fake.pastMores.size)
        fake.pastChanged(DemoAgent.MAIN, PastSessionsState(sessions = listOf(chat), hasMore = true, loading = true))
        model.loadMorePastSessions()
        assertEquals("not while a page is on its way", 1, fake.pastMores.size)
        fake.pastChanged(DemoAgent.MAIN, PastSessionsState(sessions = listOf(chat, call), hasMore = false))
        model.loadMorePastSessions()
        assertEquals("not past the end", 1, fake.pastMores.size)
        assertEquals(listOf(chat, call), model.pastSessions.sessions)
        // Another agent's list never reaches this screen.
        fake.pastChanged(DemoAgent.HELP, PastSessionsState(error = "device_limit"))
        assertNull(model.pastSessions.error)
        model.closePastSessions()
        assertNull(model.pastSessionsAgent)
    }

    @Test fun aTranscriptOpensReadOnlyOrSaysWhyNot() {
        model.signIn(TestPack.pack.users[0], memory = true)
        model.openPastSessions(DemoAgent.MAIN)
        val turns = listOf(TranscriptTurn("agent", "Hi, how can I help?", at("2026-10-21T12:00:05Z")), TranscriptTurn("customer", "Move my call.", at("2026-10-21T12:00:20Z")))
        model.openTranscript(chat.id)
        assertEquals("on its way", chat.id, model.transcriptLoading)
        assertNull(model.transcript)
        fake.transcripts[chat.id] = SessionTranscript(chat.id, chat.startedAt, chat.endedAt, 276, "chat", null, "completed", turns) to null
        model.openTranscript(chat.id)
        assertEquals(turns, model.transcript?.turns)
        assertNull(model.transcriptLoading)
        model.closeTranscript()
        assertNull(model.transcript)

        fake.transcripts[call.id] = null to GraineError(GraineErrorCode.DEVICE_LIMIT, "Not this device.")
        model.openTranscript(call.id)
        assertEquals("Not available on this device.", model.transcriptError)
        fake.transcripts["other"] = null to GraineError(GraineErrorCode.NOT_FOUND, "Session not found.")
        model.openTranscript("other")
        assertEquals("That session is not available.", model.transcriptError)
        fake.transcripts["nobody"] = null to GraineError(GraineErrorCode.IDENTITY_REQUIRED, "Sign in.")
        model.openTranscript("nobody")
        assertEquals("Sign in to see past sessions.", model.transcriptError)
    }

    @Test fun signingOutOrChangingTabClosesIt() {
        model.openPastSessions(DemoAgent.MAIN)
        assertNull("only for a signed-in user", model.pastSessionsAgent)
        model.signIn(TestPack.pack.users[0], memory = true)
        model.openPastSessions(DemoAgent.HELP)
        assertNull("only for an agent this app has", model.pastSessionsAgent)
        model.openPastSessions(DemoAgent.MAIN)
        model.select(DemoTab.CALLS)
        assertNull(model.pastSessionsAgent)
        model.openPastSessions(DemoAgent.MAIN)
        model.signOut()
        assertNull(model.pastSessionsAgent)
        assertNull(model.transcript)
    }
}
