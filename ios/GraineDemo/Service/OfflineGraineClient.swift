import GraineInApp
import SwiftUI

/**
 * A `GraineClient` that does nothing and reaches no server. The app uses it
 * only while it is hosting its own unit tests, so a test run never talks to
 * Graine; everything else goes through `GraineService`.
 */
@MainActor
final class OfflineGraineClient: GraineClient {
    var onSessionReady: (@MainActor () -> Void)?
    var onCallEvent: (@MainActor (CallEvent) -> Void)?
    var onCallScreenChange: (@MainActor (Bool) -> Void)?
    var onPastSessions: (@MainActor (DemoAgent, PastSessionsState) -> Void)?

    private var offline: GraineServiceError { GraineServiceError(code: .network, message: "Offline.") }

    var agents: [DemoAgent] { [.main] }
    func identify(_ traits: [String: String]?, memory: Bool?) {}
    func setVariables(_ values: [String: String?]) {}
    func setMemory(_ enabled: Bool) {}
    var memoryChoice: Bool { true }
    var installId: String { "00000000-0000-4000-8000-000000000000" }
    func forgetMemory() async throws -> Int { throw offline }
    func memories() async throws -> [GraineMemory] { throw offline }
    var agentMemoryEnabled: Bool? { nil }
    func setScreen(_ context: DemoScreenContext?) {}
    func registerAction(_ name: String, agent: DemoAgent, handler: @escaping @MainActor ([String: Any]) -> DemoActionResult) {}
    func unregisterActions() {}
    func bookingAvailable(_ agent: DemoAgent) -> Bool { false }
    func presentBooking(reason: String?, agent: DemoAgent) async -> GraineScheduledCall? { nil }
    func scheduledCalls(agent: DemoAgent) async throws -> [GraineScheduledCall] { throw offline }
    func cancelScheduledCall(id: String, agent: DemoAgent) async throws -> GraineCallStatus { throw offline }
    func attachVoiceEngine() {}
    func startVoice(agent: DemoAgent) {}
    func endVoice() {}
    func sendChatMessage(_ text: String, agent: DemoAgent) {}
    func setCallName(_ name: String) {}
    func startNativeCall(reason: String, variables: [String: String], agent: DemoAgent) -> String? { nil }
    func pastSessions(agent: DemoAgent) -> PastSessionsState { PastSessionsState() }
    func refreshPastSessions(agent: DemoAgent) {}
    func loadMorePastSessions(agent: DemoAgent) {}
    func sessionTranscript(id: String, agent: DemoAgent) async throws -> SessionTranscript { throw offline }
    func chatView(agent: DemoAgent, onClose: @escaping () -> Void) -> AnyView { AnyView(EmptyView()) }
    func voiceBar(agent: DemoAgent, name: String, bottomInset: CGFloat, onClose: @escaping () -> Void) -> AnyView { AnyView(EmptyView()) }
    func callScreen() -> AnyView { AnyView(EmptyView()) }
}
