import Foundation

/**
 * The publishable key and base URL. They come from `Config.xcconfig`
 * (git-ignored; copy `Config.example.xcconfig`), through the build settings
 * `GRAINE_PUBLISHABLE_KEY` and `GRAINE_BASE_URL`, into Info.plist.
 */
struct AppConfig: Equatable {
    static let defaultBaseURL = "https://www.graine.ai"

    let baseURL: String
    let publishableKey: String
    /// The help desk agent's key (`GRAINE_HELP_PUBLISHABLE_KEY`). Optional: without it the Help screen says how to add one.
    let helpPublishableKey: String

    init(baseURL: String, publishableKey: String, helpPublishableKey: String = "") {
        self.baseURL = baseURL
        self.publishableKey = publishableKey
        self.helpPublishableKey = helpPublishableKey
    }

    /// A key is present. Without one the app runs, and every agent feature reports that it is not set up.
    var hasKey: Bool { publishableKey.hasPrefix("pk_") }

    /// The second agent is configured.
    var hasHelpKey: Bool { helpPublishableKey.hasPrefix("pk_") }

    static func from(info: [String: Any]) -> AppConfig {
        let url = setting(info["GraineBaseURL"])
        return AppConfig(
            baseURL: url.isEmpty ? defaultBaseURL : url,
            publishableKey: setting(info["GrainePublishableKey"]),
            helpPublishableKey: setting(info["GraineHelpPublishableKey"])
        )
    }

    static var current: AppConfig {
        from(info: Bundle.main.infoDictionary ?? [:], launch: OfflineDemo.launchArguments)
    }

    /// Launched with `-GraineOffline <url>` (`OfflineDemo`, below): that server, with its stand-in keys.
    static func from(info: [String: Any], launch: [String: Any]) -> AppConfig {
        if let server = OfflineDemo.server(launch) {
            return AppConfig(baseURL: server, publishableKey: OfflineDemo.mainKey, helpPublishableKey: OfflineDemo.helpKey)
        }
        return from(info: info)
    }

    /// A build setting as Info.plist carries it: "" when unset, and never a literal "$(NAME)".
    private static func setting(_ raw: Any?) -> String {
        let s = (raw as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return s.hasPrefix("$(") ? "" : s
    }
}

/**
 * THE OFFLINE DEMO — every screen with no workspace and no publishable key,
 * against `scripts/offline-server.mjs` running on this Mac (it plays the agent
 * from the use-case pack). Launch arguments only, never a build setting, so a
 * build made for a phone cannot carry it:
 *
 *     node scripts/offline-server.mjs
 *     xcrun simctl launch booted com.example.grainedemo -GraineOffline http://127.0.0.1:8790
 *
 * The other arguments sign in and open a screen at launch, so each screen can
 * be captured from a script:
 *
 *     -GraineDemoUser <id>        sign in as the pack's user with this id, memory on
 *     -GraineDemoOpen <screen>    home | items | calls | memory | profile | help | call_log | chat | talk | call
 *     -GraineDemoSay <text>       in the chat, send this a moment after it opens (-GraineDemoSayAfter <seconds>, 2)
 *     -GraineDemoRing <seconds>   that long after sign-in, a booked call rings as Graine's VoIP push would ring it
 *                                 (on a phone; a simulator has no CallKit, so the SDK shows the call as missed)
 */
enum OfflineDemo {
    /// The stand-in keys `scripts/offline-server.mjs` accepts, one per agent.
    static let mainKey = "pk_offline_demo"
    static let helpKey = "pk_offline_help"

    /// The server, when the app was launched with `-GraineOffline <url>`.
    static func server(_ launch: [String: Any]) -> String? {
        guard let raw = (launch["GraineOffline"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
              raw.hasPrefix("http://") || raw.hasPrefix("https://") else { return nil }
        return raw
    }

    /// Launch arguments, and nothing the app saved: the argument domain alone.
    static var launchArguments: [String: Any] {
        UserDefaults.standard.volatileDomain(forName: UserDefaults.argumentDomain)
    }

    struct Script: Equatable {
        var userId: String?
        var open: String?
        var say: String?
        var sayAfter: Double = 2
        var ringAfter: Double?

        static func from(_ launch: [String: Any]) -> Script {
            func text(_ key: String) -> String? {
                let s = (launch[key] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                return s.isEmpty ? nil : s
            }
            return Script(
                userId: text("GraineDemoUser"),
                open: text("GraineDemoOpen"),
                say: text("GraineDemoSay"),
                sayAfter: text("GraineDemoSayAfter").flatMap(Double.init) ?? 2,
                ringAfter: text("GraineDemoRing").flatMap(Double.init)
            )
        }
    }

    @MainActor private static var ran = false

    /// Run the launch script, once, when the app first appears. Only in offline mode.
    @MainActor
    static func run(_ model: AppModel) {
        guard !ran, server(launchArguments) != nil else { return }
        ran = true
        let script = Script.from(launchArguments)
        guard let pack = model.pack, let id = script.userId, let user = pack.users.first(where: { $0.id == id }) else { return }
        model.signIn(user, memory: true)
        Task { @MainActor in
            // Let the agent's configuration arrive before anything is opened.
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            switch script.open {
            case "chat":
                model.openChat(.main)
                if let words = script.say {
                    try? await Task.sleep(nanoseconds: UInt64(script.sayAfter * 1_000_000_000))
                    model.graine.sendChatMessage(words, agent: .main)
                }
            case "talk": model.talk()
            case "call": model.callAgent()
            case let screen?: if let s = DemoScreen(rawValue: screen) { model.open(s) }
            case nil: break
            }
            if let seconds = script.ringAfter {
                try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
                (model.graine as? GraineService)?.demoRing(callName: pack.call.callName, reason: pack.call.reasons.first)
            }
        }
    }
}
