import Foundation
import GraineInApp
import SwiftUI

/// The tabs along the bottom.
enum DemoTab: Hashable {
    case home
    case items
    case calls
    case help
    case memory
}

/// Pushed inside the Home tab.
enum HomeRoute: Hashable {
    case profile
}

/// Pushed inside the Scheduled calls tab.
enum CallsRoute: Hashable {
    case callLog
}

/**
 * THE APP'S STATE: the use-case pack, who is signed in, where they are, the
 * Items list, the scheduled calls of both agents, what the main agent
 * remembers and the call activity log. Every agent feature goes through
 * `graine` (a `GraineClient`), so this whole class runs in unit tests against
 * a fake.
 */
@MainActor
final class AppModel: ObservableObject {
    let graine: GraineClient
    let config: AppConfig
    /// Every bundled pack, in the app's order.
    let packs: [UseCasePack]

    private let defaults: UserDefaults
    private let now: () -> Date
    private let calendar: Calendar
    /// Row statuses for the app session, per pack and user: signing out and back in keeps them; a restart does not.
    private var sessionStatuses: [String: ItemsState] = [:]

    // Which pack, and who
    @Published private(set) var pack: UseCasePack?
    @Published private(set) var user: UseCasePack.User?
    /// The user's consent: their conversations are remembered.
    @Published private(set) var memoryOn = true

    // Where
    @Published var tab: DemoTab = .home { didSet { if tab != oldValue { describeScreen() } } }
    @Published var homePath: [HomeRoute] = [] { didSet { if homePath != oldValue { describeScreen() } } }
    @Published var callsPath: [CallsRoute] = [] { didSet { if callsPath != oldValue { describeScreen() } } }
    /// The agent whose chat is open full screen; nil when no chat is open.
    @Published private(set) var chatAgent: DemoAgent?
    /// The agent whose voice bar is up; nil when none is.
    @Published private(set) var voiceAgent: DemoAgent?
    @Published private(set) var callScreenVisible = false
    /// The row shown expanded on the Items screen.
    @Published var expandedRowId: String?

    // What
    @Published private(set) var items: ItemsState?
    /// Every agent's scheduled calls, each with its agent.
    @Published private(set) var calls: [AgentCall] = []
    @Published private(set) var callsLoading = false
    @Published var callsError: String?
    /// The agents that can be booked from the app now (the SDK's `bookingAvailable`): their booking buttons are enabled.
    @Published private(set) var bookable: Set<DemoAgent> = []
    @Published private(set) var memories: [GraineMemory] = []
    @Published private(set) var memoriesLoading = false
    @Published var memoriesError: String?
    @Published private(set) var agentMemoryEnabled: Bool?
    @Published private(set) var callLog: CallLog
    /// A short message for the user: what just happened, or why it did not.
    @Published var notice: String?

    // Past sessions
    /// The Past sessions screen is open, for this agent's sessions (from the chat).
    @Published private(set) var pastSessionsAgent: DemoAgent?
    /// That agent's past sessions, as the SDK holds them (`chat.pastSessions`).
    @Published private(set) var pastSessions = PastSessionsState()
    /// The past session whose transcript is open on it.
    @Published private(set) var transcript: SessionTranscript?
    /// The id of the transcript on its way.
    @Published private(set) var transcriptLoading: String?
    /// Why the last transcript could not be opened, in plain words.
    @Published private(set) var transcriptError: String?

    init(
        graine: GraineClient,
        config: AppConfig,
        packs: [UseCasePack],
        defaults: UserDefaults = .standard,
        calendar: Calendar = .current,
        now: @escaping () -> Date = Date.init
    ) {
        self.graine = graine
        self.config = config
        self.packs = packs
        self.defaults = defaults
        self.calendar = calendar
        self.now = now
        self.callLog = CallLog.load(from: defaults)
        self.pack = UseCasePacks.chosen(packs, defaults: defaults)

        graine.onSessionReady = { [weak self] in self?.sessionArrived() }
        graine.onCallEvent = { [weak self] event in self?.record(event) }
        graine.onCallScreenChange = { [weak self] shown in self?.callScreenVisible = shown }
        graine.onPastSessions = { [weak self] agent, state in
            guard let self = self, agent == self.pastSessionsAgent else { return }
            self.pastSessions = state
        }
        registerActions()
        refreshBookable()
        agentMemoryEnabled = graine.agentMemoryEnabled
        describeScreen()
    }

    /// The main assistant's name in the copy: the pack's ("Asha").
    var agentName: String { profile(.main)?.name ?? pack?.agentName ?? "Assistant" }

    /// An agent's name, role and starters, from the pack.
    func profile(_ agent: DemoAgent) -> AgentProfile? { pack?.profile(agent) }

    /// The agent is configured (its key is set) — always true of the main one.
    func isAvailable(_ agent: DemoAgent) -> Bool { graine.agents.contains(agent) }

    /// The agent's name as the app shows it.
    func name(of agent: DemoAgent) -> String {
        profile(agent)?.name ?? (agent == .main ? agentName : "Help")
    }

    var chatOpen: Bool { chatAgent != nil }
    var voiceBarVisible: Bool { voiceAgent != nil }

    // MARK: - The pack

    /// Switch use case on the sign-in screen. Remembered on the device; whoever was signed in is signed out first.
    func choosePack(_ id: String) {
        guard let next = packs.first(where: { $0.id == id }), next != pack else { return }
        if user != nil { signOut() }
        pack = next
        defaults.set(next.id, forKey: UseCasePacks.choiceKey)
        graine.setCallName(next.call.callName)
        registerActions()
        describeScreen()
    }

    // MARK: - Signing in and out

    /// The demo's sign-in: identify the user once, with their memory consent. The SDK identifies them to every agent.
    func signIn(_ user: UseCasePack.User, memory: Bool) {
        guard let pack = pack else { return }
        graine.identify(pack.traits(for: user), memory: memory)
        graine.setVariables(pack.variables(for: user))
        self.user = user
        memoryOn = memory
        items = sessionStatuses[sessionKey(pack, user)] ?? ItemsState(rows: pack.rows(for: user))
        expandedRowId = nil
        tab = .home
        homePath = []
        callsPath = []
        calls = []
        memories = []
        callsError = nil
        memoriesError = nil
        refreshBookable()
        describeScreen()
        Task { await refreshCalls() }
    }

    /// Sign out everywhere: the SDK forgets the user and releases this device; later conversations are anonymous.
    func signOut() {
        if voiceAgent != nil { graine.endVoice() }
        if user != nil, let pack = pack { graine.setVariables(pack.variableFields.mapValues { _ in nil }) }
        graine.identify(nil, memory: nil)
        user = nil
        items = nil
        expandedRowId = nil
        calls = []
        memories = []
        chatAgent = nil
        voiceAgent = nil
        closePastSessions()
        homePath = []
        callsPath = []
        tab = .home
        notice = nil
        refreshBookable()
        describeScreen()
    }

    // MARK: - Memory

    /// Home's chip: memory works only when the agent has it on AND the user agreed.
    var memoryStatus: (label: String, on: Bool) {
        if agentMemoryEnabled == false { return ("Memory: off for this assistant", false) }
        return memoryOn ? ("Memory: on", true) : ("Memory: off", false)
    }

    func setMemory(_ enabled: Bool) {
        graine.setMemory(enabled)
        memoryOn = enabled
        notice = enabled
            ? "\(agentName) will remember your conversations from the next one on."
            : "\(agentName) will not remember your conversations from the next one on."
        describeScreen()
    }

    func refreshMemories() async {
        guard user != nil else { return }
        memoriesLoading = true
        defer { memoriesLoading = false }
        do {
            memories = try await graine.memories()
            memoriesError = nil
        } catch {
            memoriesError = words(error)
        }
        describeScreen()
    }

    /// Erase everything the assistant remembers about this user. True when it is done.
    @discardableResult
    func forgetMemory() async -> Bool {
        do {
            let deleted = try await graine.forgetMemory()
            memories = []
            memoriesError = nil
            switch deleted {
            case 0: notice = "There was nothing to erase."
            case 1: notice = "Erased the 1 thing \(agentName) remembered about you."
            default: notice = "Erased the \(deleted) things \(agentName) remembered about you."
            }
            describeScreen()
            return true
        } catch {
            notice = words(error)
            return false
        }
    }

    // MARK: - Navigation

    /// Show a screen: from a button, a home card, or the agent's `open_screen`.
    func open(_ screen: DemoScreen) {
        guard user != nil else { return }
        if screen == .chat {
            openChat(.main)
            return
        }
        chatAgent = nil
        closePastSessions()
        switch screen {
        case .signIn, .chat: return
        case .home: tab = .home; homePath = []
        case .items: tab = .items
        case .calls: tab = .calls; callsPath = []
        case .memory: tab = .memory
        case .help: tab = .help
        case .profile: tab = .home; homePath = [.profile]
        case .callLog: tab = .calls; callsPath = [.callLog]
        }
        describeScreen()
    }

    /// A home card's `target`.
    func open(target: String) {
        if let screen = DemoScreen.parse(target, pack: pack) { open(screen) }
    }

    /// Open an agent's chat full screen. One conversation at a time: a voice call with either agent ends.
    func openChat(_ agent: DemoAgent) {
        guard user != nil, isAvailable(agent) else { return }
        if voiceAgent != nil {
            graine.endVoice()
            voiceAgent = nil
        }
        chatAgent = agent
        describeScreen()
    }

    func closeChat() {
        chatAgent = nil
        describeScreen()
    }

    /// A starter chip: send it in that agent's chat, and show the chat.
    func startChat(with text: String, agent: DemoAgent = .main) {
        guard isAvailable(agent) else { return }
        graine.sendChatMessage(text, agent: agent)
        openChat(agent)
    }

    /// "Talk": that agent's voice bar, and the call in it.
    func talk(_ agent: DemoAgent = .main) {
        guard isAvailable(agent) else { return }
        if voiceAgent != nil && voiceAgent != agent { graine.endVoice() }
        voiceAgent = agent
        graine.startVoice(agent: agent)
    }

    func voiceBarClosed() {
        voiceAgent = nil
    }

    // MARK: - Past sessions

    /// The Past sessions screen for `agent`: the SDK loads the first page again, newest first.
    func openPastSessions(_ agent: DemoAgent) {
        guard user != nil, isAvailable(agent) else { return }
        pastSessionsAgent = agent
        pastSessions = graine.pastSessions(agent: agent)
        closeTranscript()
        graine.refreshPastSessions(agent: agent)
    }

    func closePastSessions() {
        pastSessionsAgent = nil
        closeTranscript()
    }

    func refreshPastSessions() {
        if let agent = pastSessionsAgent { graine.refreshPastSessions(agent: agent) }
    }

    /// The next page, at the end of the list: only while there is more and nothing is on its way.
    func loadMorePastSessions() {
        guard let agent = pastSessionsAgent else { return }
        let state = graine.pastSessions(agent: agent)
        guard !state.loading, state.hasMore else { return }
        graine.loadMorePastSessions(agent: agent)
    }

    /// A past session's transcript, read only: what was said, oldest first.
    func openTranscript(_ id: String) async {
        guard let agent = pastSessionsAgent else { return }
        transcriptLoading = id
        transcriptError = nil
        do {
            let result = try await graine.sessionTranscript(id: id, agent: agent)
            guard pastSessionsAgent == agent, transcriptLoading == id else { return }
            transcriptLoading = nil
            transcript = result
        } catch {
            guard pastSessionsAgent == agent, transcriptLoading == id else { return }
            transcriptLoading = nil
            transcriptError = PastSessionsText.transcriptError((error as? GraineServiceError)?.code.wire) ?? words(error)
        }
    }

    func closeTranscript() {
        transcript = nil
        transcriptLoading = nil
        transcriptError = nil
    }

    /// "Call": a native call with that agent on the system's call screen, carrying the pack's `placeReason`.
    func callAgent(_ agent: DemoAgent = .main) {
        guard let pack = pack, isAvailable(agent) else { return }
        let entry = agent == .main ? "home" : "help"
        if let id = graine.startNativeCall(reason: pack.placeReason(agent), variables: ["entry_point": entry], agent: agent) {
            record(CallEvent(callId: id, event: "placed", detail: nil, agent: agent))
        } else {
            notice = "Another call is in progress."
        }
    }

    // MARK: - Items

    func toggleExpanded(_ row: UseCasePack.Row) {
        expandedRowId = expandedRowId == row.id ? nil : row.id
    }

    /// A row's button: the same `set_status` the agent runs.
    func press(_ action: UseCasePack.Action, on row: UseCasePack.Row) {
        let result = perform(action.name, args: ["item": row.id])
        if result.status != .ok { notice = result.message }
    }

    // MARK: - Scheduled calls

    /// The agent's booking button is enabled: the SDK says it can be booked from the app now.
    func canBook(_ agent: DemoAgent) -> Bool {
        bookable.contains(agent)
    }

    /// The next call that will ring, from any agent.
    var nextCall: AgentCall? {
        calls.filter { $0.call.isUpcoming && $0.call.at != nil }
            .min { ($0.call.at ?? .distantFuture) < ($1.call.at ?? .distantFuture) }
    }

    /// Upcoming calls of every agent, soonest first, then the rest, most recent first.
    var upcomingCalls: [AgentCall] {
        calls.filter(\.call.isUpcoming).sorted { ($0.call.at ?? .distantFuture) < ($1.call.at ?? .distantFuture) }
    }

    var pastCalls: [AgentCall] {
        calls.filter { !$0.call.isUpcoming }.sorted { ($0.call.at ?? .distantPast) > ($1.call.at ?? .distantPast) }
    }

    /// Every configured agent's calls, each marked with its agent. One agent failing does not hide the other's.
    func refreshCalls() async {
        guard user != nil else { return }
        callsLoading = true
        defer { callsLoading = false }
        var merged: [AgentCall] = []
        var failure: Error?
        for agent in graine.agents {
            do {
                merged += try await graine.scheduledCalls(agent: agent).map { AgentCall(agent: agent, call: $0) }
            } catch {
                failure = failure ?? error
            }
        }
        calls = merged
        callsError = failure.map(words)
        describeScreen()
    }

    /**
     * The pack's booking button: the SDK's booking sheet for `agent`, its
     * "What's it about?" starting with the pack's reason. The day, the time,
     * the rules and any refusal are the sheet's; a call booked in it joins the
     * list at once, and the list is read again.
     */
    func book(_ agent: DemoAgent = .main) async {
        guard user != nil else { return }
        // Always the SDK's own sheet: while booking is off for this phone, the sheet itself says why.
        guard let booked = await graine.presentBooking(reason: pack?.bookingReason(agent), agent: agent) else { return }
        calls.removeAll { $0.agent == agent && $0.call.id == booked.id }
        calls.append(AgentCall(agent: agent, call: booked))
        notice = "Call with \(name(of: agent)) booked for \(DemoFormat.time(booked.at, now: now(), calendar: calendar))."
        describeScreen()
        await refreshCalls()
    }

    func cancelCall(_ call: AgentCall) async {
        do {
            let status = try await graine.cancelScheduledCall(id: call.call.id, agent: call.agent)
            notice = status == .cancelled ? "Call cancelled." : "The call is \(CallText.label(status).lowercased())."
            await refreshCalls()
        } catch {
            notice = CallText.message(error)
        }
    }

    // MARK: - Call activity

    func record(_ event: CallEvent) {
        var log = callLog
        log.record(CallLogEntry(callId: event.callId, event: event.event, detail: event.detail, at: now(), agent: event.agent?.rawValue))
        callLog = log
        log.save(to: defaults)
        // A call that ended may have been a scheduled one: its status changed.
        if ["answered", "declined", "missed", "ended"].contains(event.event) {
            Task { await refreshCalls() }
        }
    }

    func clearCallLog() {
        var log = callLog
        log.clear()
        callLog = log
        log.save(to: defaults)
    }

    // MARK: - The agent's actions

    /// Every action in the pack, registered under its name with every configured agent (each runs the ones its dashboard declares).
    private func registerActions() {
        graine.unregisterActions()
        for agent in graine.agents {
            for action in pack?.actions ?? [] {
                let name = action.name
                graine.registerAction(name, agent: agent) { [weak self] args in
                    guard let self = self else { return .refused("The app is not ready.") }
                    return self.perform(name, args: args)
                }
            }
        }
    }

    /// Run one of the pack's actions by its effect, answering in the pack's words (`usecases/README.md`).
    func perform(_ name: String, args: [String: Any]) -> DemoActionResult {
        guard let pack = pack, let action = pack.action(named: name) else {
            return .refused("This app has no action called \(name).")
        }
        guard user != nil, var state = items else {
            return .refused("Nobody is signed in yet. Ask the user to sign in first.")
        }
        switch action.knownEffect {
        case .openScreen:
            let raw = (args["screen"] as? String) ?? (args["path"] as? String) ?? ""
            guard let screen = DemoScreen.parse(raw, pack: pack) else {
                let names = DemoScreen.openable.map { $0.contextName(pack) }.joined(separator: ", ")
                return .refused("There is no screen called \"\(raw)\". Screens: \(names).")
            }
            open(screen)
            return .ok("Opened \(screen.title(pack)).")

        case .openItem:
            switch findRow(args["item"], in: state, pack: pack) {
            case .failure(let refusal): return refusal.result
            case .success(let row):
                open(.items)
                expandedRowId = row.id
                describeScreen()
                return .ok("Showing \(row.title).")
            }

        case .setStatus:
            switch findRow(args["item"], in: state, pack: pack) {
            case .failure(let refusal): return refusal.result
            case .success(let row):
                guard let status = action.status, row.actions.contains(action.name) else {
                    return .refused("\(action.label) is not available for \(row.title).")
                }
                if state.status(of: row) == status { return .ok("\(row.title) is already \(status).") }
                state.setStatus(status, of: row)
                items = state
                if let user = user { sessionStatuses[sessionKey(pack, user)] = state }
                describeScreen()
                return .ok("\(row.title): \(status).")
            }

        case nil:
            return .refused("This version of the app cannot do \"\(action.label)\".")
        }
    }

    private func findRow(_ raw: Any?, in state: ItemsState, pack: UseCasePack) -> Result<UseCasePack.Row, Refusal> {
        let asked = (raw as? String) ?? ""
        switch state.find(asked) {
        case .found(let row):
            return .success(row)
        case .ambiguous(let rows):
            return .failure(Refusal(.refused("Which one? \(rows.map(\.title).joined(separator: ", ")).")))
        case .noMatch:
            let titles = state.rows.map(\.title).joined(separator: ", ")
            return .failure(Refusal(.refused("No \(pack.items.itemNoun) called \"\(asked)\". \(pack.items.title): \(titles).")))
        }
    }

    // MARK: - Screen context

    /// The screen the user is looking at now.
    var visibleScreen: DemoScreen {
        guard user != nil else { return .signIn }
        if let agent = chatAgent { return agent == .main ? .chat : .help }
        switch tab {
        case .home: return homePath.last == .profile ? .profile : .home
        case .items: return .items
        case .calls: return callsPath.last == .callLog ? .callLog : .calls
        case .help: return .help
        case .memory: return .memory
        }
    }

    /// Tell the agents what is on screen (the SDK shares it with every agent). Called on every change.
    func describeScreen() {
        graine.setScreen(screenContext())
    }

    /// The same keys on every platform (`usecases/README.md`, "Screen context").
    func screenContext() -> DemoScreenContext {
        let screen = visibleScreen
        var fields: [DemoField] = []
        switch screen {
        case .home:
            fields = [
                DemoField(name: "memory", label: "Memory", value: memoryOn ? "on" : "off", status: .filled),
                DemoField(name: "next_call", label: "Next call", value: nextCall?.call.at.map(DemoFormat.iso) ?? "none", status: .filled),
            ]
        case .items:
            if let state = items {
                fields = state.rows.map { row in
                    DemoField(name: row.id, label: row.title, value: state.value(of: row), status: .filled)
                }
            }
        case .calls:
            fields = [DemoField(name: "upcoming", label: "Upcoming calls", value: String(upcomingCalls.count), status: .filled)]
        case .memory:
            fields = [
                DemoField(name: "memory", label: "Memory", value: memoryOn ? "on" : "off", status: .filled),
                DemoField(name: "remembered", label: "Remembered", value: String(memories.count), status: .filled),
            ]
        case .signIn, .chat, .profile, .help, .callLog:
            break
        }
        return DemoScreenContext(screen: screen.contextName(pack), title: screen.title(pack), fields: fields)
    }

    // MARK: - Private

    /// A refusal carried through `Result`.
    struct Refusal: Error {
        let result: DemoActionResult
        init(_ result: DemoActionResult) { self.result = result }
    }

    private func sessionArrived() {
        refreshBookable()
        agentMemoryEnabled = graine.agentMemoryEnabled
        describeScreen()
    }

    /// Whether each agent can be booked changes with its session and with who is signed in: read it again then.
    private func refreshBookable() {
        bookable = Set(graine.agents.filter { graine.bookingAvailable($0) })
    }

    private func sessionKey(_ pack: UseCasePack, _ user: UseCasePack.User) -> String {
        "\(pack.id)/\(user.id)"
    }

    private func words(_ error: Error) -> String {
        CallText.message(error)
    }
}
