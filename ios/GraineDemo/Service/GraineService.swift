import Combine
import GraineInApp
import SwiftUI

/**
 * THE ONE FILE THAT CALLS THE GRAINE SDK.
 *
 * Every SDK call the app makes goes through here, behind `GraineClient`, so
 * the tests run against a fake and moving to a new SDK version means reading
 * one file. The scheduling and memory values are the SDK's own types, passed
 * through unchanged: their rules live in the SDK, not in the app.
 *
 * Two agents, each configured with its own publishable key: the main one is
 * the SDK's default agent, the help desk is configured under the name "help"
 * when its key is set. The SDK shares the person, the memory choice and the
 * screen between them; calls are one engine for the whole app.
 *
 * Launch order matters for calls: iOS wakes a terminated app for a VoIP push
 * and delivers it only once `enableVoIPPushes` has run, so `start` runs from
 * the App's initialiser on every launch.
 */
@MainActor
final class GraineService: GraineClient {
    private let main: GraineInApp
    private let help: GraineInApp?
    private let relay = SDKRelay()
    private var callsWatch: AnyCancellable?
    private var callScreenShown = false
    private var registeredActions: [() -> Void] = []

    var onSessionReady: (@MainActor () -> Void)?
    var onCallEvent: (@MainActor (CallEvent) -> Void)?
    var onCallScreenChange: (@MainActor (Bool) -> Void)?

    /// Configure the agents and the calls. Once, at launch.
    static func start(config: AppConfig, callName: String) -> GraineService {
        let appVersion = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String
        #if DEBUG
        let debug = true
        #else
        let debug = false
        #endif
        func agentConfig(_ key: String) -> GraineConfig {
            GraineConfig(baseUrl: config.baseURL, publishableKey: key, appVersion: appVersion, debug: debug)
        }
        let main = GraineInApp.configure(agentConfig(config.publishableKey))
        let help = config.hasHelpKey
            ? GraineInApp.configure(agentConfig(config.helpPublishableKey), name: DemoAgent.help.sdkName)
            : nil
        return GraineService(main: main, help: help, callName: callName)
    }

    private init(main: GraineInApp, help: GraineInApp?, callName: String) {
        self.main = main
        self.help = help

        // Native calls: CallKit and VoIP pushes, one engine for every agent. When a
        // workspace has uploaded its push credentials (In-app calling), the SDK
        // registers this device with that agent's workspace itself and reports what
        // became of every ring: no server.
        setCallName(callName)
        main.calls.enableVoIPPushes()

        relay.sessionReady = { [weak self] in self?.onSessionReady?() }
        relay.callEvent = { [weak self] call in
            guard let self = self else { return }
            self.onCallEvent?(CallEvent(callId: call.callId, event: call.event, detail: call.detail, agent: self.agent(forAgentId: call.agentId)))
        }
        main.addListener(relay)
        help?.addListener(relay)
        main.calls.addListener(relay)

        // The SDK's call screen is up while a call rings, runs, or was just missed.
        callsWatch = main.calls.objectWillChange.sink { [weak self] _ in
            // objectWillChange fires before the value changes: read it on the next turn.
            DispatchQueue.main.async { self?.publishCallScreen() }
        }
    }

    var agents: [DemoAgent] { help == nil ? [.main] : [.main, .help] }

    private func sdk(_ agent: DemoAgent) -> GraineInApp? {
        agent == .main ? main : help
    }

    private func required(_ agent: DemoAgent) throws -> GraineInApp {
        guard let graine = sdk(agent) else {
            throw GraineServiceError(code: .invalidKey, message: "No publishable key is set for this agent.")
        }
        return graine
    }

    /// Which of the app's agents an incoming call is from: the help desk when its session's agent (or web-call twin) id matches.
    private func agent(forAgentId id: String?) -> DemoAgent? {
        guard let id = id else { return nil }
        if let session = help?.session, session.agentId == id || session.webcallAgentId == id { return .help }
        if let session = main.session, session.agentId == id || session.webcallAgentId == id { return .main }
        return nil
    }

    private func publishCallScreen() {
        let shown = main.calls.showsCallScreen
        guard shown != callScreenShown else { return }
        callScreenShown = shown
        onCallScreenChange?(shown)
    }

    // MARK: - Who the user is, and memory (the SDK shares them with every agent)

    func identify(_ traits: [String: String]?, memory: Bool?) {
        // nil signs out everywhere: the device stops being this user's, and later conversations are anonymous.
        main.identify(traits.map { $0.mapValues { Optional<Any>.some($0) } }, memory: memory)
    }

    func setMemory(_ enabled: Bool) {
        main.setMemory(enabled)
    }

    var memoryChoice: Bool { main.memoryChoice }

    var installId: String { main.installId }

    func forgetMemory() async throws -> Int {
        try await main.forgetMemory()
    }

    func memories() async throws -> [GraineMemory] {
        try await main.memories()
    }

    var agentMemoryEnabled: Bool? { main.session?.memoryEnabled }

    // MARK: - Screen context and actions

    func setScreen(_ context: DemoScreenContext?) {
        // One screen for the whole app: the SDK hands it to every agent.
        main.setScreen(context.map(GraineService.screen))
    }

    func registerAction(_ name: String, agent: DemoAgent, handler: @escaping @MainActor ([String: Any]) -> DemoActionResult) {
        guard let graine = sdk(agent) else { return }
        // The SDK runs handlers on the main thread.
        let unregister = graine.registerAction(name) { args in
            let result = MainActor.assumeIsolated { handler(args) }
            switch result.status {
            case .ok: return .ok(result.message)
            case .refused: return .refused(result.message)
            case .error: return .error(result.message)
            }
        }
        registeredActions.append(unregister)
    }

    func unregisterActions() {
        registeredActions.forEach { $0() }
        registeredActions.removeAll()
    }

    // MARK: - Scheduled calls

    func schedulingRules(_ agent: DemoAgent) -> GraineSchedulingRules {
        sdk(agent)?.schedulingRules ?? GraineSchedulingRules()
    }

    func scheduleCall(at date: Date, reason: String?, agent: DemoAgent) async throws -> GraineScheduledCall {
        // The SDK checks the rules (booking on, earliest, latest) before it sends anything.
        try await required(agent).scheduleCall(at: date, reason: reason)
    }

    func scheduledCalls(agent: DemoAgent) async throws -> [GraineScheduledCall] {
        try await required(agent).scheduledCalls()
    }

    func cancelScheduledCall(id: String, agent: DemoAgent) async throws -> GraineCallStatus {
        try await required(agent).cancelScheduledCall(id: id)
    }

    // MARK: - Voice in the bar, chat, and native calls

    func attachVoiceEngine() {
        // Loads the engine idle: nothing dialled, no microphone, until start().
        main.voice.attach()
    }

    func startVoice(agent: DemoAgent) {
        sdk(agent)?.voice.start()
    }

    func endVoice() {
        for agent in agents { sdk(agent)?.voice.end() }
    }

    func sendChatMessage(_ text: String, agent: DemoAgent) {
        sdk(agent)?.chat.send(text)
    }

    func setCallName(_ name: String) {
        main.calls.configure(GraineCallsConfig(
            displayName: name,
            includesCallsInRecents: false,
            ringTimeout: 45
        ))
    }

    /// The offline demo only: a booked call ringing, handed to the SDK as Graine's VoIP push would hand it.
    func demoRing(callName: String, reason: String?) {
        main.calls.reportIncomingCall(payload: [
            "type": "agent_call", "call_id": "call_offline_ring", "caller_name": callName,
            "hosted": "1", "reason": reason ?? "",
        ])
    }

    func startNativeCall(reason: String, variables: [String: String], agent: DemoAgent) -> String? {
        guard sdk(agent) != nil else { return nil }
        let id = main.calls.startCall(reason: reason, variables: variables, agent: agent.sdkName)
        if id != nil { publishCallScreen() }
        return id
    }

    // MARK: - The SDK's own views

    func chatView(agent: DemoAgent, onClose: @escaping () -> Void) -> AnyView {
        guard let graine = sdk(agent) else { return AnyView(EmptyView()) }
        return AnyView(GraineChatView(graine: graine, onClose: onClose))
    }

    func voiceBar(agent: DemoAgent, name: String, bottomInset: CGFloat, onClose: @escaping () -> Void) -> AnyView {
        guard let graine = sdk(agent) else { return AnyView(EmptyView()) }
        return AnyView(GraineAgentBar(graine: graine, name: name, bottomInset: bottomInset, surface: .voice, onClose: onClose))
    }

    func callScreen() -> AnyView {
        AnyView(GraineCallView(calls: main.calls))
    }

    // MARK: - Mapping

    private static func screen(_ c: DemoScreenContext) -> GraineScreen {
        GraineScreen(
            screen: c.screen,
            title: c.title,
            fields: c.fields.map { f in
                GraineField(name: f.name, label: f.label, value: f.value, status: f.status.flatMap { GraineField.Status(rawValue: $0.rawValue) })
            }
        )
    }
}

/// A native call event, before the app knows which of its agents it is with.
private struct RawCallEvent {
    let callId: String
    let event: String
    let detail: String?
    let agentId: String?
}

/**
 * The SDK's listeners, held strongly here because the SDK holds them weakly.
 * Their requirements are not main-actor isolated, so this small class takes
 * the callbacks and hands them to the main actor.
 */
private final class SDKRelay: NSObject, GraineListener, GraineCallsListener {
    var sessionReady: (@MainActor () -> Void)?
    var callEvent: (@MainActor (RawCallEvent) -> Void)?
    /// The agent each ringing call is from, by call id, for the outcome that follows.
    private var agentIds: [String: String] = [:]

    func onSessionReady(_ session: GraineSessionConfig) {
        Task { @MainActor in self.sessionReady?() }
    }

    func onIncomingCall(_ call: GraineIncomingCall) {
        agentIds[call.callId] = call.agentId
        let event = RawCallEvent(callId: call.callId, event: "incoming", detail: call.hosted ? "scheduled" : nil, agentId: call.agentId)
        Task { @MainActor in self.callEvent?(event) }
    }

    func onCallOutcome(_ outcome: GraineCallOutcome, callId: String) {
        var detail: String?
        if case .ended(let why) = outcome { detail = why.rawValue }
        if case .failed(let why) = outcome { detail = why }
        let event = RawCallEvent(callId: callId, event: outcome.wire, detail: detail, agentId: agentIds[callId])
        Task { @MainActor in self.callEvent?(event) }
    }
}
