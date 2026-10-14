import Foundation
import GraineInApp

/**
 * THE APP'S TWO AGENTS: the main one (Home, Chat, the Items screen) and a help
 * desk (the Help screen), each with its own publishable key. The person, the
 * device, the memory choice and the screen are shared between them by the SDK;
 * each has its own chat, voice, actions and scheduled calls.
 */
enum DemoAgent: String, CaseIterable, Hashable, Identifiable {
    case main
    case help

    var id: String { rawValue }

    /// The name the SDK knows it by (`GraineInApp.configure(_:name:)`).
    var sdkName: String { self == .main ? "default" : "help" }

    /// The app's agent for the SDK's name of one (`GraineCalls.agent`); nil for none, or one the app does not have.
    init?(sdkName: String?) {
        guard let agent = DemoAgent.allCases.first(where: { $0.sdkName == sdkName }) else { return nil }
        self = agent
    }
}

/// What the app shows of an agent: its name, its role line and its chat starters (the pack's `agents`).
struct AgentProfile: Equatable {
    let name: String
    let role: String
    let starters: [String]
}

/// A scheduled call, and the agent it is booked with.
struct AgentCall: Identifiable, Equatable {
    let agent: DemoAgent
    let call: GraineScheduledCall

    var id: String { "\(agent.rawValue):\(call.id)" }
}

extension UseCasePack {
    /// The booking button for an agent: the help desk's own, else the pack's.
    func bookLabel(_ agent: DemoAgent) -> String {
        (agent == .help ? agents?.help?.bookLabel : nil) ?? call.bookLabel
    }

    /// The booking reasons for an agent: the help desk's own, else the pack's.
    func reasons(_ agent: DemoAgent) -> [String] {
        (agent == .help ? agents?.help?.reasons : nil) ?? call.reasons
    }

    /// The reason a call placed with an agent carries: the help desk's own, else the pack's.
    func placeReason(_ agent: DemoAgent) -> String {
        (agent == .help ? agents?.help?.placeReason : nil) ?? call.placeReason
    }

    /// The agent's profile: the pack's `agents.main` / `agents.help`; the main one falls back to the pack's own name, role and starters.
    func profile(_ agent: DemoAgent) -> AgentProfile? {
        switch agent {
        case .main:
            let info = agents?.main
            return AgentProfile(name: info?.name ?? agentName, role: info?.role ?? agentRole, starters: info?.starters ?? starters)
        case .help:
            guard let info = agents?.help else { return nil }
            return AgentProfile(name: info.name, role: info.role ?? "", starters: info.starters ?? [])
        }
    }
}
