import GraineInApp
import SwiftUI
@testable import GraineDemo

/// A stand-in for the SDK with up to two agents: records every call, answers what the test says.
@MainActor
final class FakeGraineClient: GraineClient {
    var agents: [DemoAgent] = [.main]

    var identified: [([String: String]?, Bool?)] = []
    /// Every setVariables, in order: what the agents' prompt variables were set to.
    var variables: [[String: String?]] = []
    var memorySets: [Bool] = []
    var memoryChoice = true
    var installId = "0f8b2a4c-1d3e-4f5a-9b6c-7d8e9f0a1b2c"
    var forgetResult: Result<Int, GraineServiceError> = .success(0)
    var forgetCount = 0
    var memoriesResult: Result<[GraineMemory], GraineServiceError> = .success([])
    var agentMemoryEnabled: Bool? = true

    var screens: [DemoScreenContext?] = []
    /// Registered actions, per agent.
    var actions: [DemoAgent: [String: @MainActor ([String: Any]) -> DemoActionResult]] = [:]
    var unregisterCount = 0

    /// The agents the SDK says can be booked now.
    var bookable: Set<DemoAgent> = [.main, .help]
    /// What the booking sheet hands back when it closes: the booked call, or nil (closed without booking).
    var bookingResult: GraineScheduledCall? = GraineScheduledCall(id: "c1", at: Date(), status: .scheduled, source: .app)
    /// Every booking sheet opened: the reason it started with, and the agent.
    var bookingSheets: [(String?, DemoAgent)] = []
    var listResults: [DemoAgent: Result<[GraineScheduledCall], GraineServiceError>] = [:]
    var cancelled: [(String, DemoAgent)] = []
    var cancelResult: Result<GraineCallStatus, GraineServiceError> = .success(.cancelled)

    var voiceStarts: [DemoAgent] = []
    var voiceEnds = 0
    var chatMessages: [(String, DemoAgent)] = []
    var callNames: [String] = []
    var nativeCallId: String? = "call_1"
    var nativeCalls: [(String, [String: String], DemoAgent)] = []

    /// Each agent's past sessions as the SDK holds them; set with `pastChanged` to tell the app, as the SDK's publisher does.
    var past: [DemoAgent: PastSessionsState] = [:]
    var pastRefreshes: [DemoAgent] = []
    var pastMores: [DemoAgent] = []
    /// What each transcript asked for answers: the transcript, or why not.
    var transcripts: [String: Result<SessionTranscript, GraineServiceError>] = [:]

    var onSessionReady: (@MainActor () -> Void)?
    var onCallEvent: (@MainActor (CallEvent) -> Void)?
    var onCallScreenChange: (@MainActor (Bool) -> Void)?
    var onPastSessions: (@MainActor (DemoAgent, PastSessionsState) -> Void)?

    /// The SDK's past sessions for `agent` changed.
    func pastChanged(_ agent: DemoAgent, _ state: PastSessionsState) {
        past[agent] = state
        onPastSessions?(agent, state)
    }

    func pastSessions(agent: DemoAgent) -> PastSessionsState { past[agent] ?? PastSessionsState() }
    func refreshPastSessions(agent: DemoAgent) { pastRefreshes.append(agent) }
    func loadMorePastSessions(agent: DemoAgent) { pastMores.append(agent) }

    func sessionTranscript(id: String, agent: DemoAgent) async throws -> SessionTranscript {
        try (transcripts[id] ?? .failure(GraineServiceError(code: .notFound, message: "Session not found."))).get()
    }

    func identify(_ traits: [String: String]?, memory: Bool?) { identified.append((traits, memory)) }
    func setVariables(_ values: [String: String?]) { variables.append(values) }
    func setMemory(_ enabled: Bool) { memorySets.append(enabled); memoryChoice = enabled }

    func forgetMemory() async throws -> Int {
        forgetCount += 1
        return try forgetResult.get()
    }

    func memories() async throws -> [GraineMemory] { try memoriesResult.get() }

    func setScreen(_ context: DemoScreenContext?) { screens.append(context) }

    func registerAction(_ name: String, agent: DemoAgent, handler: @escaping @MainActor ([String: Any]) -> DemoActionResult) {
        actions[agent, default: [:]][name] = handler
    }

    func unregisterActions() {
        unregisterCount += 1
        actions.removeAll()
    }

    func bookingAvailable(_ agent: DemoAgent) -> Bool { agents.contains(agent) && bookable.contains(agent) }

    func presentBooking(reason: String?, agent: DemoAgent) async -> GraineScheduledCall? {
        bookingSheets.append((reason, agent))
        return bookingResult
    }

    func scheduledCalls(agent: DemoAgent) async throws -> [GraineScheduledCall] {
        try (listResults[agent] ?? .success([])).get()
    }

    func cancelScheduledCall(id: String, agent: DemoAgent) async throws -> GraineCallStatus {
        cancelled.append((id, agent))
        return try cancelResult.get()
    }

    func attachVoiceEngine() {}
    func startVoice(agent: DemoAgent) { voiceStarts.append(agent) }
    func endVoice() { voiceEnds += 1 }
    func sendChatMessage(_ text: String, agent: DemoAgent) { chatMessages.append((text, agent)) }
    func setCallName(_ name: String) { callNames.append(name) }

    func startNativeCall(reason: String, variables: [String: String], agent: DemoAgent) -> String? {
        nativeCalls.append((reason, variables, agent))
        return nativeCallId
    }

    func chatView(agent: DemoAgent, onClose: @escaping () -> Void) -> AnyView { AnyView(EmptyView()) }
    func voiceBar(agent: DemoAgent, name: String, bottomInset: CGFloat, onClose: @escaping () -> Void) -> AnyView { AnyView(EmptyView()) }
    func callScreen() -> AnyView { AnyView(EmptyView()) }

    /// Run a registered action as an agent would (the main one unless named).
    func run(_ name: String, _ args: [String: Any] = [:], agent: DemoAgent = .main) -> DemoActionResult? {
        actions[agent]?[name]?(args)
    }

    var lastScreen: DemoScreenContext? { screens.last ?? nil }
}
