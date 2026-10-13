import Foundation

/// Something that happened to a native call, kept so the demo can show what the SDK reported.
struct CallLogEntry: Codable, Identifiable, Equatable {
    let id: UUID
    let callId: String
    /// placed, incoming, answered, declined, missed, ended or failed.
    let event: String
    /// ended: local, remote or failed. failed: why. incoming: "scheduled" for a call Graine rang.
    let detail: String?
    let at: Date
    /// Which of the app's agents the call was with (`main`, `help`), when known.
    let agent: String?

    init(id: UUID = UUID(), callId: String, event: String, detail: String? = nil, at: Date, agent: String? = nil) {
        self.id = id
        self.callId = callId
        self.event = event
        self.detail = detail
        self.at = at
        self.agent = agent
    }

    func title(agentName: String) -> String { CallLog.describe(event: event, detail: detail, agentName: agentName) }

    var tone: Tone {
        switch event {
        case "answered", "placed": return .info
        case "ended": return detail == "failed" ? .negative : .positive
        case "declined", "missed": return .warning
        case "failed": return .negative
        default: return .neutral
        }
    }
}

/**
 * THE CALL ACTIVITY LOG: newest first, capped, kept on the device. In a real
 * app these outcomes would go to the app's own analytics; the SDK already
 * reports outcomes of calls Graine rang back to Graine.
 */
struct CallLog: Equatable {
    static let cap = 100
    static let storageKey = "demo.callLog"

    private(set) var entries: [CallLogEntry]

    init(entries: [CallLogEntry] = []) {
        self.entries = Array(entries.prefix(CallLog.cap))
    }

    mutating func record(_ entry: CallLogEntry) {
        entries.insert(entry, at: 0)
        if entries.count > CallLog.cap { entries.removeLast(entries.count - CallLog.cap) }
    }

    mutating func clear() {
        entries.removeAll()
    }

    static func describe(event: String, detail: String?, agentName: String) -> String {
        switch event {
        case "placed": return "You called \(agentName)"
        case "incoming": return detail == "scheduled" ? "\(agentName) called (scheduled call)" : "\(agentName) called"
        case "answered": return "Answered"
        case "declined": return "Declined"
        case "missed": return "Missed"
        case "ended":
            switch detail {
            case "local": return "Ended by you"
            case "remote": return "Ended by \(agentName)"
            case "failed": return "Call dropped"
            default: return "Ended"
            }
        case "failed":
            if let why = detail, !why.isEmpty { return "Could not connect: \(why)" }
            return "Could not connect"
        default:
            return event.prefix(1).uppercased() + event.dropFirst()
        }
    }

    // MARK: - On the device

    static func load(from defaults: UserDefaults) -> CallLog {
        guard let data = defaults.data(forKey: storageKey),
              let entries = try? JSONDecoder().decode([CallLogEntry].self, from: data) else { return CallLog() }
        return CallLog(entries: entries)
    }

    func save(to defaults: UserDefaults) {
        if let data = try? JSONEncoder().encode(entries) {
            defaults.set(data, forKey: CallLog.storageKey)
        }
    }
}
