import Foundation

/**
 * THE AGENT'S FACE, as the demo shows it: the SDK's state (off, available,
 * live, and why it is off) and the one line under the switch on Home. Pure, so
 * the words are tested without the SDK.
 */
struct DemoAvatar: Equatable {
    enum Phase: String {
        case off
        case available
        case live
    }

    var phase: Phase
    var reason: String?

    static let off = DemoAvatar(phase: .off, reason: nil)

    /// A face to draw: ready or speaking.
    var showsFace: Bool { phase != .off }

    /// The line under "Show the agent's face".
    func caption(switchedOn: Bool) -> String {
        guard switchedOn else { return "Calls are voice only." }
        switch phase {
        case .live:
            return "The face is speaking."
        case .available:
            return "Connecting the face…"
        case .off:
            switch reason {
            case "busy": return "Every face is in use right now. This call is voice only."
            case "off": return "This agent has no face yet: turn on Avatar for it in the dashboard."
            case "unsupported": return "This call can't show a face. It continues with voice."
            case "failed", "stalled", "no_voice": return "The face isn't available right now. The call continues with voice."
            default: return "Shown on your next call with the agent."
            }
        }
    }
}
