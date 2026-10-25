import GraineInApp
import XCTest
@testable import GraineDemo

/// A time as the wire writes it.
private func at(_ iso: String) -> Date { ISO8601DateFormatter().date(from: iso)! }

/// Past sessions: how the SDK's state reads in the app, and the screen's flow over a fake SDK.
@MainActor
final class PastSessionsTests: XCTestCase {
    private var fake: FakeGraineClient!
    private var model: AppModel!

    // The contract's first page (sdk/contract/past-sessions.json), as the app holds it.
    private let chat = PastSession(id: "ex-7a1c0001", startedAt: at("2026-10-21T12:00:05Z"), endedAt: at("2026-10-21T12:04:41Z"), durationSeconds: 276,
                                   channel: "chat", direction: nil, status: "completed", turns: 9, preview: "Can I move my check-in call to Friday?")
    private let call = PastSession(id: "ex-31f00002", startedAt: at("2026-10-20T08:00:00Z"), endedAt: at("2026-10-20T08:03:12Z"), durationSeconds: 192,
                                   channel: "call", direction: "outbound", status: "completed", turns: 14, preview: "Hi, yes I can talk now.")

    override func setUp() async throws {
        fake = FakeGraineClient()
        model = AppModel(
            graine: fake,
            config: AppConfig(baseURL: "https://www.graine.ai", publishableKey: "pk_test_x"),
            packs: [TestPack.pack],
            defaults: TestClock.defaults(name),
            calendar: TestClock.calendar,
            now: { TestClock.at(12) }
        )
    }

    func testTheSdkStateReadsAsTheAppsOwn() {
        let sdk = GrainePastSessions(
            sessions: [
                GraineSessionSummary(id: "ex-7a1c0001", startedAt: at("2026-10-21T12:00:05Z"), endedAt: at("2026-10-21T12:04:41Z"), durationSeconds: 276,
                                     channel: .chat, status: "completed", turns: 9, preview: "Can I move my check-in call to Friday?"),
                GraineSessionSummary(id: "ex-31f00002", startedAt: at("2026-10-20T08:00:00Z"), endedAt: at("2026-10-20T08:03:12Z"), durationSeconds: 192,
                                     channel: .call, direction: .outbound, status: "completed", turns: 14, preview: "Hi, yes I can talk now."),
            ],
            hasMore: true
        )
        XCTAssertEqual(PastSessionsSdk.state(sdk), PastSessionsState(sessions: [chat, call], loading: false, hasMore: true, error: nil))
        // What the SDK says it does not know reads as nothing: no status, no preview, no length.
        XCTAssertEqual(
            PastSessionsSdk.session(GraineSessionSummary(id: "ex-loose0003", startedAt: at("2026-10-19T18:30:00Z"))),
            PastSession(id: "ex-loose0003", startedAt: at("2026-10-19T18:30:00Z"), endedAt: nil, durationSeconds: nil, channel: "chat", direction: nil, status: nil, turns: 0, preview: nil)
        )
        XCTAssertEqual(PastSessionsSdk.state(GrainePastSessions(error: "device_limit")).error, "device_limit")
        XCTAssertTrue(PastSessionsSdk.state(GrainePastSessions(loading: true)).loading)

        let t = PastSessionsSdk.transcript(GraineSessionTranscript(
            id: "ex-7a1c0001", startedAt: at("2026-10-21T12:00:05Z"), durationSeconds: 276, channel: .voice, direction: .inbound, status: "",
            turns: [
                GraineSessionTurn(role: .agent, text: "Hi Meera, how can I help?", at: at("2026-10-21T12:00:05Z")),
                GraineSessionTurn(role: .customer, text: "Move my call.", at: at("2026-10-21T12:00:20Z")),
            ]
        ))
        XCTAssertEqual(t.turns.map(\.role), ["agent", "customer"])
        XCTAssertEqual(t.channel, "voice")
        XCTAssertEqual(t.direction, "inbound")
        XCTAssertNil(t.status)
        XCTAssertEqual(PastSessionsText.summary(t), "Voice · inbound · 4:36 · Ended")
    }

    func testASessionReadsAsKindLengthAndMessages() {
        XCTAssertEqual(PastSessionsText.summary(chat), "Chat · 4:36 · 9 messages")
        XCTAssertEqual(PastSessionsText.summary(call), "Call · outbound · 3:12 · 14 messages")
        XCTAssertEqual(PastSessionsText.status("completed"), "Completed")
        XCTAssertEqual(PastSessionsText.status("no_answer"), "No answer")
        XCTAssertEqual(PastSessionsText.status(nil), "Ended")
        XCTAssertEqual(PastSessionsText.messages(1), "1 message")
        XCTAssertEqual(PastSessionsText.length(3723), "1:02:03")
        XCTAssertNil(PastSessionsText.length(nil))
        XCTAssertEqual(PastSessionsText.shortId("ex-7a1c0001"), "…1c0001")
        XCTAssertEqual(PastSessionsText.shortId("a1"), "a1")
        XCTAssertTrue(PastSessionsText.isCustomer(TranscriptTurn(role: "customer", text: "x", at: nil)))
        XCTAssertFalse(PastSessionsText.isCustomer(TranscriptTurn(role: "agent", text: "x", at: nil)))
    }

    func testRefusalsReadInPlainWords() {
        XCTAssertEqual(PastSessionsText.error("identity_required"), "Sign in to see past sessions.")
        XCTAssertEqual(PastSessionsText.error("device_limit"), "Not available on this device.")
        XCTAssertEqual(PastSessionsText.error("rate_limited"), "Too many requests. Try again in a few minutes.")
        XCTAssertEqual(PastSessionsText.error("unavailable"), "Past sessions could not be loaded. Try again.")
        XCTAssertEqual(PastSessionsText.error("not_found"), "Past sessions could not be loaded. Try again.", "the list itself is never \"that session\"")
        XCTAssertEqual(PastSessionsText.transcriptError("not_found"), "That session is not available.")
        XCTAssertEqual(PastSessionsText.transcriptError("device_limit"), "Not available on this device.")
        XCTAssertNil(PastSessionsText.error(nil))
        XCTAssertFalse(PastSessionsText.canRetry("identity_required"), "signing in is the answer")
        XCTAssertFalse(PastSessionsText.canRetry("device_limit"), "another device is the answer")
        XCTAssertTrue(PastSessionsText.canRetry("unavailable"))
    }

    func testTheScreenRefreshesOnOpenAndLoadsMoreOnlyWhileThereIsMore() {
        model.signIn(TestPack.pack.users[0], memory: true)
        model.openPastSessions(.main)
        XCTAssertEqual(model.pastSessionsAgent, .main)
        XCTAssertEqual(fake.pastRefreshes, [.main], "the first page, newest first")
        fake.pastChanged(.main, PastSessionsState(sessions: [chat], hasMore: true))
        XCTAssertEqual(model.pastSessions.sessions, [chat])
        model.loadMorePastSessions()
        XCTAssertEqual(fake.pastMores.count, 1)
        fake.pastChanged(.main, PastSessionsState(sessions: [chat], loading: true, hasMore: true))
        model.loadMorePastSessions()
        XCTAssertEqual(fake.pastMores.count, 1, "not while a page is on its way")
        fake.pastChanged(.main, PastSessionsState(sessions: [chat, call], hasMore: false))
        model.loadMorePastSessions()
        XCTAssertEqual(fake.pastMores.count, 1, "not past the end")
        XCTAssertEqual(model.pastSessions.sessions, [chat, call])
        // Another agent's list never reaches this screen.
        fake.pastChanged(.help, PastSessionsState(error: "device_limit"))
        XCTAssertNil(model.pastSessions.error)
        model.closePastSessions()
        XCTAssertNil(model.pastSessionsAgent)
    }

    func testATranscriptOpensReadOnlyOrSaysWhyNot() async {
        model.signIn(TestPack.pack.users[0], memory: true)
        model.openPastSessions(.main)
        let turns = [
            TranscriptTurn(role: "agent", text: "Hi, how can I help?", at: at("2026-10-21T12:00:05Z")),
            TranscriptTurn(role: "customer", text: "Move my call.", at: at("2026-10-21T12:00:20Z")),
        ]
        fake.transcripts[chat.id] = .success(SessionTranscript(id: chat.id, startedAt: chat.startedAt, endedAt: chat.endedAt, durationSeconds: 276,
                                                               channel: "chat", direction: nil, status: "completed", turns: turns))
        await model.openTranscript(chat.id)
        XCTAssertEqual(model.transcript?.turns, turns)
        XCTAssertNil(model.transcriptLoading)
        model.closeTranscript()
        XCTAssertNil(model.transcript)

        fake.transcripts[call.id] = .failure(GraineServiceError(code: .deviceLimit, message: "Not this device."))
        await model.openTranscript(call.id)
        XCTAssertEqual(model.transcriptError, "Not available on this device.")
        await model.openTranscript("someone-else")
        XCTAssertEqual(model.transcriptError, "That session is not available.")
        fake.transcripts["nobody"] = .failure(GraineServiceError(code: .identityRequired, message: "Sign in."))
        await model.openTranscript("nobody")
        XCTAssertEqual(model.transcriptError, "Sign in to see past sessions.")
        XCTAssertNil(model.transcript)
    }

    func testSigningOutOrOpeningAnotherScreenClosesIt() {
        model.openPastSessions(.main)
        XCTAssertNil(model.pastSessionsAgent, "only for a signed-in user")
        model.signIn(TestPack.pack.users[0], memory: true)
        model.openPastSessions(.help)
        XCTAssertNil(model.pastSessionsAgent, "only for an agent this app has")
        model.openPastSessions(.main)
        model.open(.calls)
        XCTAssertNil(model.pastSessionsAgent)
        model.openPastSessions(.main)
        model.signOut()
        XCTAssertNil(model.pastSessionsAgent)
        XCTAssertNil(model.transcript)
    }
}
