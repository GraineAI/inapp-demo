import Foundation

/// Every place the app can show. The same on every platform, whatever the pack (`usecases/README.md`).
enum DemoScreen: String, CaseIterable, Equatable {
    case signIn = "sign_in"
    case home
    case chat
    case items
    case calls
    case memory
    case profile
    /// The help desk: the second agent's chat, voice and calls.
    case help
    /// This app's call activity log, inside Scheduled calls. Not one the agent opens.
    case callLog = "call_log"

    /// The screen's title, from the pack.
    func title(_ pack: UseCasePack?) -> String {
        switch self {
        case .signIn: return "Sign in"
        case .home: return pack?.home.title ?? "Home"
        case .chat: return pack?.agentName ?? "Chat"
        case .items: return pack?.items.title ?? "Items"
        case .calls: return pack?.call.title ?? "Scheduled calls"
        case .memory: return "Memory"
        case .profile: return "Profile"
        case .help: return pack?.profile(.help)?.name ?? "Help"
        case .callLog: return "Call activity"
        }
    }

    /// What the app reports as `screen`: the pack's own word for the Items screen ("medicines").
    func contextName(_ pack: UseCasePack?) -> String {
        self == .items ? (pack?.items.screenName ?? rawValue) : rawValue
    }

    /// The screens `open_screen` accepts, in the order the agent is told them.
    static let openable: [DemoScreen] = [.home, .chat, .items, .calls, .memory, .profile, .help]

    /**
     * `open_screen`'s argument: a generic key (home, chat, items, calls,
     * memory, profile) or the pack's `items.screenName`, case-insensitively.
     * A component's tap sends a `path` ("/medicines"), so a leading slash is ignored.
     */
    static func parse(_ raw: Any?, pack: UseCasePack?) -> DemoScreen? {
        guard let s = raw as? String else { return nil }
        var key = s.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        while key.hasPrefix("/") { key.removeFirst() }
        if let pack = pack, key == pack.items.screenName.lowercased() { return .items }
        guard let screen = DemoScreen(rawValue: key), openable.contains(screen) else { return nil }
        return screen
    }
}

/// What an action handler answers. The agent speaks about exactly this, so it says what really happened.
struct DemoActionResult: Equatable {
    enum Status: Equatable {
        case ok
        case refused
        case error
    }

    let status: Status
    let message: String

    static func ok(_ message: String) -> DemoActionResult { DemoActionResult(status: .ok, message: message) }
    static func refused(_ message: String) -> DemoActionResult { DemoActionResult(status: .refused, message: message) }
}

/// One line of screen context, as the agent reads it.
struct DemoField: Equatable {
    enum Status: String, Equatable {
        case empty
        case filled
        case invalid
        case pending
        case locked
    }

    let name: String
    let label: String
    let value: String?
    let status: Status?

    init(name: String, label: String, value: String?, status: Status? = nil) {
        self.name = name
        self.label = label
        self.value = value
        self.status = status
    }
}

/// What the user is looking at: sent as the screen context on every screen.
struct DemoScreenContext: Equatable {
    /// The screen's name for the agent (`home`, `medicines`, `calls`).
    let screen: String
    let title: String
    let fields: [DemoField]

    init(screen: String, title: String, fields: [DemoField] = []) {
        self.screen = screen
        self.title = title
        self.fields = fields
    }
}
