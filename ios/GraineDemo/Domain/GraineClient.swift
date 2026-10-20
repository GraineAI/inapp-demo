import GraineInApp
import SwiftUI

/// What the SDK reported about a native call.
struct CallEvent: Equatable {
    let callId: String
    /// incoming, answered, declined, missed, ended or failed.
    let event: String
    let detail: String?
    /// Which of the app's agents the call is with, when known.
    let agent: DemoAgent?

    init(callId: String, event: String, detail: String?, agent: DemoAgent? = nil) {
        self.callId = callId
        self.event = event
        self.detail = detail
        self.agent = agent
    }
}

/**
 * EVERYTHING THE APP ASKS OF THE GRAINE SDK. The scheduling and memory types
 * are the SDK's own (`GraineScheduledCall`, `GraineMemory`,
 * `GraineServiceError`): booking — the sheet, its rules and its checks — and
 * status logic live in the SDK, and the app only presents them.
 *
 * Two agents: what belongs to one agent (its chat, voice, calls, actions and
 * scheduled calls) takes `agent`; what the SDK shares between them (who the
 * person is, the memory choice, the screen) does not. The real implementation
 * is `GraineService`; the tests use a fake. Main thread.
 */
@MainActor
protocol GraineClient: AnyObject {
    /// The agents configured: the main one always, the help desk when its key is set.
    var agents: [DemoAgent] { get }

    // Who the user is, and memory (shared by every agent)
    func identify(_ traits: [String: String]?, memory: Bool?)
    /// The agents' prompt {variables} — what an API call sends as `variables` — set through the SDK, for every
    /// agent: merged into each chat, voice session and call from now on; nil under a key removes it.
    func setVariables(_ values: [String: String?])
    func setMemory(_ enabled: Bool)
    var memoryChoice: Bool { get }
    var installId: String { get }
    /// How many memories were deleted (the main agent's memory).
    func forgetMemory() async throws -> Int
    /// What the main agent remembers about the signed-in user, newest first.
    func memories() async throws -> [GraineMemory]
    /// The main agent's memory switch, from its session. Nil until the session has said.
    var agentMemoryEnabled: Bool? { get }

    // Screen context (shared) and actions (per agent)
    func setScreen(_ context: DemoScreenContext?)
    func registerAction(_ name: String, agent: DemoAgent, handler: @escaping @MainActor ([String: Any]) -> DemoActionResult)
    func unregisterActions()

    // Scheduled calls, per agent
    /// The agent can be booked from the app now (the SDK's `bookingAvailable`). Read again after sign-in and when a session arrives.
    func bookingAvailable(_ agent: DemoAgent) -> Bool
    /// The SDK's booking sheet for the agent, its "What's it about?" starting with `reason`: the call booked, or nil when closed without booking.
    func presentBooking(reason: String?, agent: DemoAgent) async -> GraineScheduledCall?
    func scheduledCalls(agent: DemoAgent) async throws -> [GraineScheduledCall]
    /// The call's status afterwards (cancelled).
    func cancelScheduledCall(id: String, agent: DemoAgent) async throws -> GraineCallStatus

    // Voice in the bar, chat, and native calls, per agent
    func attachVoiceEngine()
    func startVoice(agent: DemoAgent)
    func endVoice()
    /// Send a message in that agent's chat (it connects when it is opened).
    func sendChatMessage(_ text: String, agent: DemoAgent)
    /// The name a ringing call shows when the push names none.
    func setCallName(_ name: String)
    /// The call's id, or nil when another call is live.
    func startNativeCall(reason: String, variables: [String: String], agent: DemoAgent) -> String?

    // What the SDK reports
    var onSessionReady: (@MainActor () -> Void)? { get set }
    var onCallEvent: (@MainActor (CallEvent) -> Void)? { get set }
    var onCallScreenChange: (@MainActor (Bool) -> Void)? { get set }

    // The SDK's own views
    func chatView(agent: DemoAgent, onClose: @escaping () -> Void) -> AnyView
    func voiceBar(agent: DemoAgent, name: String, bottomInset: CGFloat, onClose: @escaping () -> Void) -> AnyView
    func callScreen() -> AnyView
}

extension GraineMemory {
    /// When it was last learned or changed.
    var when: Date? { updatedAt ?? createdAt }
}
