import GraineInApp
import XCTest
@testable import GraineDemo

/// The app's behaviour around the SDK, against a fake SDK: every adapter call the screens make.
@MainActor
final class AppModelTests: XCTestCase {
    private var fake: FakeGraineClient!
    private var defaults: UserDefaults!
    private var model: AppModel!
    private var clock = TestClock.at(9, 30)

    override func setUp() async throws {
        fake = FakeGraineClient()
        defaults = TestClock.defaults(name)
        clock = TestClock.at(9, 30)
        model = makeModel()
    }

    private func makeModel() -> AppModel {
        AppModel(
            graine: fake,
            config: AppConfig(baseURL: "https://www.graine.ai", publishableKey: "pk_test_x"),
            packs: [TestPack.pack, TestPack.other],
            defaults: defaults,
            calendar: TestClock.calendar,
            now: { [unowned self] in self.clock }
        )
    }

    /// The same app with the help desk configured.
    private func withHelp() {
        fake.agents = [.main, .help]
        model = makeModel()
    }

    private var riya: UseCasePack.User { TestPack.pack.users[0] }
    private var arjun: UseCasePack.User { TestPack.pack.users[1] }

    // MARK: - Packs

    func testRegistersEveryActionOfThePackAndReportsSignIn() {
        XCTAssertEqual(Set(fake.actions[.main]?.keys.map { $0 } ?? []), ["open_screen", "open_item", "mark_done", "pause_reminders", "teleport"])
        XCTAssertNil(fake.actions[.help])
        XCTAssertEqual(fake.lastScreen?.screen, "sign_in")
        XCTAssertEqual(fake.lastScreen?.title, "Sign in")
        XCTAssertEqual(fake.lastScreen?.fields, [])
    }

    func testChoosingAPackIsRememberedAndRenamesTheCall() {
        model.choosePack("other-pack")
        XCTAssertEqual(model.pack?.id, "other-pack")
        XCTAssertEqual(defaults.string(forKey: UseCasePacks.choiceKey), "other-pack")
        XCTAssertEqual(fake.callNames, ["Kabir · Test Bank"])
        XCTAssertGreaterThanOrEqual(fake.unregisterCount, 2)
        XCTAssertEqual(makeModel().pack?.id, "other-pack")
    }

    func testChoosingAPackSignsTheUserOutFirst() {
        model.signIn(riya, memory: true)
        model.choosePack("other-pack")
        XCTAssertNil(model.user)
        XCTAssertNil(fake.identified.last?.0)
    }

    // MARK: - Identity and memory

    func testSignInIdentifiesTheUserWithTheirConsent() {
        model.signIn(riya, memory: false)
        XCTAssertEqual(fake.identified.last?.0, ["patient_id": "P-1", "name": "Riya Sharma", "plan": "Diabetes care"])
        XCTAssertEqual(fake.identified.last?.1, false)
        XCTAssertFalse(model.memoryOn)
        XCTAssertEqual(model.memoryStatus.label, "Memory: off")
        XCTAssertEqual(fake.lastScreen?.screen, "home")
        XCTAssertEqual(model.items?.rows.map(\.id), ["metformin", "atorvastatin", "vitamin-d3"])
    }

    func testSignOutForgetsTheUser() {
        model.signIn(riya, memory: true)
        model.talk()
        model.signOut()
        XCTAssertNil(fake.identified.last?.0)
        XCTAssertNil(fake.identified.last?.1)
        XCTAssertEqual(fake.voiceEnds, 1)
        XCTAssertNil(model.user)
        XCTAssertEqual(fake.lastScreen?.screen, "sign_in")
    }

    func testMemoryChipNeedsTheAgentAndTheConsent() {
        model.signIn(riya, memory: true)
        XCTAssertEqual(model.memoryStatus.label, "Memory: on")
        fake.agentMemoryEnabled = false
        fake.onSessionReady?()
        XCTAssertEqual(model.memoryStatus.label, "Memory: off for this assistant")
        XCTAssertFalse(model.memoryStatus.on)
    }

    func testSetMemoryGoesToTheSDK() {
        model.signIn(riya, memory: true)
        model.setMemory(false)
        XCTAssertEqual(fake.memorySets, [false])
        XCTAssertFalse(model.memoryOn)
        XCTAssertEqual(model.notice, "Asha will not remember your conversations from the next one on.")
    }

    func testMemoriesAreListedPerUser() async {
        fake.memoriesResult = .success([GraineMemory(id: "m1", text: "Takes metformin at 8 pm", createdAt: TestClock.at(9))])
        model.signIn(riya, memory: true)
        await model.refreshMemories()
        XCTAssertEqual(model.memories.map(\.text), ["Takes metformin at 8 pm"])
        model.open(.memory)
        XCTAssertEqual(fake.lastScreen?.fields.map(\.name), ["memory", "remembered"])
        XCTAssertEqual(fake.lastScreen?.fields.last?.value, "1")

        // Another user: their own list, never the last user's.
        fake.memoriesResult = .success([])
        model.signOut()
        XCTAssertTrue(model.memories.isEmpty)
        model.signIn(arjun, memory: true)
        await model.refreshMemories()
        XCTAssertTrue(model.memories.isEmpty)
    }

    func testMemoriesErrorIsShownInWords() async {
        fake.memoriesResult = .failure(GraineServiceError(code: .deviceLimit, message: "x"))
        model.signIn(riya, memory: true)
        await model.refreshMemories()
        XCTAssertEqual(model.memoriesError, "This user is signed in on the maximum number of devices. Sign out on another device first.")
    }

    func testForgetMemoryClearsTheListOrSaysWhy() async {
        fake.memoriesResult = .success([GraineMemory(id: "m1", text: "Prefers morning calls")])
        fake.forgetResult = .success(1)
        model.signIn(riya, memory: true)
        await model.refreshMemories()
        let erased = await model.forgetMemory()
        XCTAssertTrue(erased)
        XCTAssertEqual(fake.forgetCount, 1)
        XCTAssertTrue(model.memories.isEmpty)
        XCTAssertEqual(model.notice, "Erased the 1 thing Asha remembered about you.")

        fake.forgetResult = .success(0)
        _ = await model.forgetMemory()
        XCTAssertEqual(model.notice, "There was nothing to erase.")

        fake.forgetResult = .failure(GraineServiceError(code: .identityRequired, message: "x"))
        let again = await model.forgetMemory()
        XCTAssertFalse(again)
        XCTAssertEqual(model.notice, "Sign in to use this.")
    }

    // MARK: - Actions, in the pack's words

    func testActionsNeedASignedInUser() {
        XCTAssertEqual(fake.run("open_screen", ["screen": "home"])?.status, .refused)
    }

    func testOpenScreen() {
        model.signIn(riya, memory: true)
        XCTAssertEqual(fake.run("open_screen", ["screen": "medicines"]), .ok("Opened Medicines."))
        XCTAssertEqual(model.tab, .items)
        XCTAssertEqual(fake.lastScreen?.screen, "medicines")

        XCTAssertEqual(fake.run("open_screen", ["screen": "calls"]), .ok("Opened Check-in calls."))
        XCTAssertEqual(fake.lastScreen?.screen, "calls")

        XCTAssertEqual(fake.run("open_screen", ["screen": "chat"]), .ok("Opened Asha."))
        XCTAssertTrue(model.chatOpen)
        XCTAssertEqual(fake.lastScreen?.screen, "chat")

        // A component's tap sends a path.
        XCTAssertEqual(fake.run("open_screen", ["path": "/memory"]), .ok("Opened Memory."))
        XCTAssertFalse(model.chatOpen)
        XCTAssertEqual(model.tab, .memory)

        XCTAssertEqual(fake.run("open_screen", ["screen": "profile"]), .ok("Opened Profile."))
        XCTAssertEqual(model.homePath, [.profile])
        XCTAssertEqual(fake.lastScreen?.screen, "profile")
    }

    func testOpenScreenRefusesAnUnknownScreen() {
        model.signIn(riya, memory: true)
        XCTAssertEqual(
            fake.run("open_screen", ["screen": "billing"]),
            .refused("There is no screen called \"billing\". Screens: home, chat, medicines, calls, memory, profile, help.")
        )
    }

    func testOpenItemShowsTheRowExpanded() {
        model.signIn(riya, memory: true)
        XCTAssertEqual(fake.run("open_item", ["item": "Metformin 500 mg"]), .ok("Showing Metformin 500 mg."))
        XCTAssertEqual(model.tab, .items)
        XCTAssertEqual(model.expandedRowId, "metformin")
    }

    func testFindingARowFollowsThePackRules() {
        model.signIn(riya, memory: true)
        XCTAssertEqual(fake.run("open_item", ["item": "mg"]), .refused("Which one? Metformin 500 mg, Atorvastatin 10 mg."))
        XCTAssertEqual(
            fake.run("open_item", ["item": "insulin"]),
            .refused("No medicine called \"insulin\". Medicines: Metformin 500 mg, Atorvastatin 10 mg, Vitamin D3.")
        )
        XCTAssertEqual(fake.run("open_item", ["item": "aspirin"])?.status, .refused)
    }

    func testSetStatusSetsTheChipAndTellsTheAgent() {
        model.signIn(riya, memory: true)
        model.open(.items)
        XCTAssertEqual(fake.run("mark_done", ["item": "metformin"]), .ok("Metformin 500 mg: Taken today."))
        XCTAssertEqual(fake.run("mark_done", ["item": "metformin"]), .ok("Metformin 500 mg is already Taken today."))
        let field = fake.lastScreen?.fields.first { $0.name == "metformin" }
        XCTAssertEqual(field, DemoField(name: "metformin", label: "Metformin 500 mg", value: "Taken today", status: .filled))
        XCTAssertEqual(fake.lastScreen?.fields.first { $0.name == "atorvastatin" }?.value, "At bedtime")
    }

    func testSetStatusOnlyWhereTheRowOffersIt() {
        model.signIn(riya, memory: true)
        XCTAssertEqual(fake.run("pause_reminders", ["item": "metformin"]), .refused("Pause reminders is not available for Metformin 500 mg."))
        XCTAssertEqual(fake.run("mark_done", ["item": "vitamin"]), .refused("Mark as taken is not available for Vitamin D3."))
    }

    func testAnEffectThisBuildDoesNotKnowIsRefused() {
        model.signIn(riya, memory: true)
        XCTAssertEqual(fake.run("teleport"), .refused("This version of the app cannot do \"Teleport\"."))
    }

    func testStatusesLastTheSessionPerUser() {
        model.signIn(riya, memory: true)
        _ = fake.run("mark_done", ["item": "metformin"])
        model.signOut()
        model.signIn(arjun, memory: true)
        XCTAssertEqual(model.items?.status(of: TestPack.pack.items.rows[2]), "Due 2:00 pm")
        model.signOut()
        model.signIn(riya, memory: true)
        XCTAssertEqual(model.items?.status(of: TestPack.pack.items.rows[0]), "Taken today")
        let restarted = makeModel()
        restarted.signIn(riya, memory: true)
        XCTAssertEqual(restarted.items?.status(of: TestPack.pack.items.rows[0]), "Due 8:00 pm")
    }

    func testARowButtonRunsTheSameAction() {
        model.signIn(riya, memory: true)
        let row = TestPack.pack.items.rows[0]
        model.press(TestPack.pack.action(named: "mark_done")!, on: row)
        XCTAssertEqual(model.items?.status(of: row), "Taken today")
        model.press(TestPack.pack.action(named: "pause_reminders")!, on: row)
        XCTAssertEqual(model.notice, "Pause reminders is not available for Metformin 500 mg.")
    }

    // MARK: - Talk, chat, call (the main agent)

    func testTalkShowsTheBarAndStartsTheCall() {
        model.signIn(riya, memory: true)
        model.talk()
        XCTAssertTrue(model.voiceBarVisible)
        XCTAssertEqual(fake.voiceStarts, [.main])
        model.voiceBarClosed()
        XCTAssertFalse(model.voiceBarVisible)
    }

    func testAStarterIsSentInTheChat() {
        model.signIn(riya, memory: true)
        model.startChat(with: "What is due today?")
        XCTAssertEqual(fake.chatMessages.map(\.0), ["What is due today?"])
        XCTAssertEqual(fake.chatMessages.map(\.1), [.main])
        XCTAssertEqual(model.chatAgent, .main)
        XCTAssertEqual(fake.lastScreen?.screen, "chat")
    }

    func testCallPlacesANativeCallWithThePacksReasonAndLogsIt() {
        model.signIn(riya, memory: true)
        model.callAgent()
        XCTAssertEqual(fake.nativeCalls.first?.0, "patient_question")
        XCTAssertEqual(fake.nativeCalls.first?.2, .main)
        XCTAssertEqual(model.callLog.entries.first?.event, "placed")
        XCTAssertEqual(model.callLog.entries.first?.callId, "call_1")

        fake.nativeCallId = nil
        model.callAgent()
        XCTAssertEqual(model.notice, "Another call is in progress.")
        XCTAssertEqual(model.callLog.entries.count, 1)
    }

    func testCallOutcomesAreLoggedAndTheCallScreenFollowsTheSDK() {
        model.signIn(riya, memory: true)
        fake.onCallEvent?(CallEvent(callId: "c9", event: "incoming", detail: "scheduled"))
        fake.onCallEvent?(CallEvent(callId: "c9", event: "missed", detail: nil))
        XCTAssertEqual(model.callLog.entries.map { $0.title(agentName: "Asha") }, ["Missed", "Asha called (scheduled call)"])
        fake.onCallScreenChange?(true)
        XCTAssertTrue(model.callScreenVisible)
        fake.onCallScreenChange?(false)
        XCTAssertFalse(model.callScreenVisible)
    }

    // MARK: - Two agents

    func testWithoutAHelpKeyTheHelpDeskIsNotThere() {
        model.signIn(riya, memory: true)
        XCTAssertFalse(model.isAvailable(.help))
        model.talk(.help)
        model.openChat(.help)
        model.callAgent(.help)
        XCTAssertTrue(fake.voiceStarts.isEmpty)
        XCTAssertNil(model.chatAgent)
        XCTAssertTrue(fake.nativeCalls.isEmpty)
        model.open(.help)
        XCTAssertEqual(model.tab, .help)
        XCTAssertEqual(fake.lastScreen?.screen, "help")
    }

    func testTheHelpDeskHasItsOwnChatVoiceAndCalls() {
        withHelp()
        model.signIn(riya, memory: true)
        XCTAssertTrue(model.isAvailable(.help))
        // Identified once; the SDK shares it with both agents.
        XCTAssertEqual(fake.identified.count, 1)
        // Both agents run the pack's actions.
        XCTAssertNotNil(fake.actions[.help]?["open_screen"])

        model.talk(.help)
        XCTAssertEqual(fake.voiceStarts, [.help])
        XCTAssertEqual(model.voiceAgent, .help)

        model.startChat(with: "Where is my bill?", agent: .help)
        XCTAssertEqual(fake.chatMessages.last?.1, .help)
        XCTAssertEqual(model.chatAgent, .help)
        XCTAssertEqual(fake.lastScreen?.screen, "help")

        model.callAgent(.help)
        XCTAssertEqual(fake.nativeCalls.last?.2, .help)
        XCTAssertEqual(fake.nativeCalls.last?.1["entry_point"], "help")
        XCTAssertEqual(model.callLog.entries.first?.agent, "help")
    }

    func testTalkingToOneAgentEndsTheOthersVoice() {
        withHelp()
        model.signIn(riya, memory: true)
        model.talk(.main)
        model.talk(.help)
        XCTAssertEqual(fake.voiceEnds, 1)
        XCTAssertEqual(model.voiceAgent, .help)
    }

    func testScheduledCallsListBothAgentsCallsWithTheirAgent() async {
        withHelp()
        fake.listResults[.main] = .success([GraineScheduledCall(id: "m1", at: TestClock.at(18), status: .scheduled)])
        fake.listResults[.help] = .success([
            GraineScheduledCall(id: "h1", at: TestClock.at(12), status: .scheduled),
            GraineScheduledCall(id: "h0", at: TestClock.at(8), status: .completed),
        ])
        model.signIn(riya, memory: true)
        await model.refreshCalls()
        XCTAssertEqual(model.upcomingCalls.map(\.id), ["help:h1", "main:m1"])
        XCTAssertEqual(model.pastCalls.map(\.id), ["help:h0"])
        XCTAssertEqual(model.nextCall?.agent, .help)
        model.open(.calls)
        XCTAssertEqual(fake.lastScreen?.fields.first?.value, "2")

        await model.cancelCall(model.upcomingCalls[0])
        XCTAssertEqual(fake.cancelled.first?.0, "h1")
        XCTAssertEqual(fake.cancelled.first?.1, .help)
    }

    func testOneAgentFailingDoesNotHideTheOthersCalls() async {
        withHelp()
        fake.listResults[.main] = .success([GraineScheduledCall(id: "m1", at: TestClock.at(18), status: .scheduled)])
        fake.listResults[.help] = .failure(GraineServiceError(code: .network, message: "offline"))
        model.signIn(riya, memory: true)
        await model.refreshCalls()
        XCTAssertEqual(model.calls.map(\.id), ["main:m1"])
        XCTAssertEqual(model.callsError, "No connection. Check your network and try again.")
    }

    func testBookingWithTheHelpDeskGoesToItAndUsesItsRules() async {
        withHelp()
        fake.rules[.help] = GraineSchedulingRules(usersCanBook: true, minLeadMinutes: 30, maxDaysAhead: 3)
        fake.scheduleResult = .success(GraineScheduledCall(id: "h9", at: TestClock.at(18), status: .scheduled, source: .app))
        model.signIn(riya, memory: true)
        XCTAssertEqual(model.bookingRange(for: .help).lowerBound, TestClock.at(10, 0))
        let message = await model.scheduleCall(at: TestClock.at(18), reason: "Medicine review", agent: .help)
        XCTAssertNil(message)
        XCTAssertEqual(fake.scheduled.first?.2, .help)
        XCTAssertTrue(model.notice?.hasPrefix("Call with") == true)

        fake.scheduleResult = .failure(GraineServiceError(code: .tooSoon, message: "x"))
        let refused = await model.scheduleCall(at: TestClock.at(9, 40), reason: nil, agent: .help)
        XCTAssertEqual(refused, "Choose a time at least 30 minutes from now.")
    }

    // MARK: - Scheduled calls (the main agent)

    func testTheSDKsLocalRefusalIsShownInWords() async {
        // The app does not check the time itself: the SDK refuses before anything is sent.
        fake.rules[.main] = GraineSchedulingRules(usersCanBook: true, minLeadMinutes: 10)
        fake.scheduleResult = .failure(GraineServiceError(code: .tooSoon, message: "Too soon."))
        model.signIn(riya, memory: true)
        let message = await model.scheduleCall(at: TestClock.at(9, 32), reason: "Weekly check-in")
        XCTAssertEqual(message, "Choose a time at least 10 minutes from now.")
        XCTAssertEqual(fake.scheduled.count, 1)
    }

    func testBookingRangeComesFromTheSDKsRules() {
        fake.rules[.main] = GraineSchedulingRules(usersCanBook: true, minLeadMinutes: 10, maxDaysAhead: 2)
        model.signIn(riya, memory: true)
        fake.onSessionReady?()
        XCTAssertEqual(model.bookingRange().lowerBound, TestClock.at(9, 40))
        XCTAssertEqual(model.bookingRange().upperBound, TestClock.at(9, 30, day: 16))
        XCTAssertEqual(model.suggestedCallTime(), TestClock.at(10, 0))
    }

    func testBookingSendsThePacksReason() async {
        fake.scheduleResult = .success(GraineScheduledCall(id: "c42", at: TestClock.at(18), status: .scheduled, source: .app, reason: "Medicine review"))
        model.signIn(riya, memory: true)
        let message = await model.scheduleCall(at: TestClock.at(18), reason: "Medicine review")
        XCTAssertNil(message)
        XCTAssertEqual(fake.scheduled.first?.0, TestClock.at(18))
        XCTAssertEqual(fake.scheduled.first?.1, "Medicine review")
        XCTAssertEqual(fake.scheduled.first?.2, .main)
        XCTAssertEqual(model.calls.map(\.call.id), ["c42"])
        XCTAssertEqual(model.nextCall?.call.id, "c42")
        model.open(.home)
        XCTAssertEqual(fake.lastScreen?.fields.first { $0.name == "next_call" }?.value, "2026-10-14T12:30:00Z")
    }

    func testTheServersRefusalIsShownInWords() async {
        fake.scheduleResult = .failure(GraineServiceError(code: .outsideWindow, message: "Outside calling hours."))
        model.signIn(riya, memory: true)
        let message = await model.scheduleCall(at: TestClock.at(23), reason: nil)
        XCTAssertEqual(message, "That time is outside the assistant's calling hours. Choose another time.")
    }

    func testTheServersPendingLimitIsShownWithTheRules() async {
        fake.rules[.main] = GraineSchedulingRules(usersCanBook: true, maxPendingPerUser: 1)
        fake.scheduleResult = .failure(GraineServiceError(code: .tooManyPending, message: "x"))
        model.signIn(riya, memory: true)
        let message = await model.scheduleCall(at: TestClock.at(18), reason: nil)
        XCTAssertEqual(message, "You already have 1 upcoming calls. Cancel one to book another.")
    }

    func testRefreshAndCancel() async {
        fake.listResults[.main] = .success([
            GraineScheduledCall(id: "a", at: TestClock.at(12), status: .scheduled),
            GraineScheduledCall(id: "b", at: TestClock.at(8), status: .completed),
        ])
        model.signIn(riya, memory: true)
        await model.refreshCalls()
        XCTAssertEqual(model.calls.map(\.call.id), ["a", "b"])
        model.open(.calls)
        XCTAssertEqual(fake.lastScreen?.fields, [DemoField(name: "upcoming", label: "Upcoming calls", value: "1", status: .filled)])

        fake.listResults[.main] = .success([GraineScheduledCall(id: "a", at: TestClock.at(12), status: .cancelled)])
        await model.cancelCall(model.calls[0])
        XCTAssertEqual(fake.cancelled.map(\.0), ["a"])
        XCTAssertEqual(model.calls.first?.call.status, .cancelled)
        XCTAssertEqual(model.notice, "Call cancelled.")
    }

    func testListErrorIsShownInWords() async {
        fake.listResults[.main] = .failure(GraineServiceError(code: .network, message: "offline"))
        model.signIn(riya, memory: true)
        await model.refreshCalls()
        XCTAssertEqual(model.callsError, "No connection. Check your network and try again.")
    }

    // MARK: - Screen context

    func testHomeContextHasExactlyTheSharedKeys() {
        model.signIn(riya, memory: true)
        let context = model.screenContext()
        XCTAssertEqual(context.screen, "home")
        XCTAssertEqual(context.title, "Today")
        XCTAssertEqual(context.fields.map(\.name), ["memory", "next_call"])
        XCTAssertEqual(context.fields.map(\.value), ["on", "none"])
        // The identifier never rides in the screen context: identity has its own channel.
        XCTAssertFalse(context.fields.contains { ($0.value ?? "").contains("P-1") })
    }

    func testProfileAndHelpReportNoFields() {
        model.signIn(riya, memory: true)
        model.open(.profile)
        XCTAssertEqual(fake.lastScreen?.screen, "profile")
        XCTAssertEqual(fake.lastScreen?.fields, [])
        model.open(.help)
        XCTAssertEqual(fake.lastScreen?.screen, "help")
        XCTAssertEqual(fake.lastScreen?.title, "Help")
    }
}
