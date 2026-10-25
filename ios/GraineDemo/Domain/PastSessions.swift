import Foundation

/*
 * PAST SESSIONS: the signed-in user's earlier sessions with an agent — each
 * chat, voice conversation and call, newest first, like the dashboard's
 * history — and one session's transcript. The SDK keeps them
 * (`chat.pastSessions`, `refreshPastSessions()`, `loadMorePastSessions()`,
 * `sessionTranscript(id:)`); these are the app's view of that state, so its
 * screen and its tests read the same thing. Contract:
 * sdk/contract/past-sessions.json.
 */

/// One earlier session, as the list shows it.
struct PastSession: Equatable, Identifiable {
    /// The session's id (the execution id the dashboard shows).
    let id: String
    let startedAt: Date?
    let endedAt: Date?
    let durationSeconds: Int?
    /// `chat`, `voice` or `call`.
    let channel: String
    /// `inbound`, `outbound`, or nil when it has none (a chat).
    let direction: String?
    /// How it ended, in the SDK's word (`completed`, …).
    let status: String?
    /// How many messages it holds.
    let turns: Int
    /// Its first words, for the row.
    let preview: String?
}

/// The list as the SDK holds it: the sessions loaded so far, newest first, and where loading stands.
struct PastSessionsState: Equatable {
    var sessions: [PastSession] = []
    var loading = false
    var hasMore = false
    /// Why the last load failed: `identity_required`, `device_limit`, `rate_limited`, `unavailable`, or another word.
    var error: String?
}

/// One line of a transcript.
struct TranscriptTurn: Equatable {
    /// `customer` or `agent`.
    let role: String
    let text: String
    let at: Date?
}

/// One session in full, oldest line first: read only.
struct SessionTranscript: Equatable, Identifiable {
    let id: String
    let startedAt: Date?
    let endedAt: Date?
    let durationSeconds: Int?
    let channel: String
    let direction: String?
    let status: String?
    let turns: [TranscriptTurn]
}

/// How past sessions read in the app.
enum PastSessionsText {
    /// The list's empty and loading lines.
    static let empty = "No past sessions yet. A session appears here once it has closed."
    static let loading = "Loading past sessions…"

    /// Why the list did not load, in plain words: the two the SDK names for this list first, then the rest.
    static func error(_ code: String?) -> String? {
        switch code {
        case nil, "": return nil
        case "identity_required": return "Sign in to see past sessions."
        case "device_limit": return "Not available on this device."
        case "rate_limited": return "Too many requests. Try again in a few minutes."
        default: return "Past sessions could not be loaded. Try again."
        }
    }

    /// A refused list offers Try again, except when signing in or another device is the answer.
    static func canRetry(_ code: String?) -> Bool {
        code != "identity_required" && code != "device_limit"
    }

    /// Why a transcript did not open: an unknown session, or another user's, is `not_found`; the rest as for the list.
    static func transcriptError(_ code: String?) -> String? {
        code == "not_found" ? "That session is not available." : error(code)
    }

    /// "Chat", "Voice", "Call · outbound".
    static func kind(_ channel: String, _ direction: String?) -> String {
        let base: String
        switch channel.lowercased() {
        case "chat": base = "Chat"
        case "voice": base = "Voice"
        case "call": base = "Call"
        default: base = channel.prefix(1).uppercased() + channel.dropFirst()
        }
        guard let direction = direction, !direction.trimmingCharacters(in: .whitespaces).isEmpty, direction != "none" else { return base }
        return "\(base) · \(direction)"
    }

    /// How it ended, as a word on a chip: the SDK's word, readable.
    static func status(_ status: String?) -> String {
        guard let status = status, !status.trimmingCharacters(in: .whitespaces).isEmpty else { return "Ended" }
        let words = status.replacingOccurrences(of: "_", with: " ")
        return words.prefix(1).uppercased() + words.dropFirst()
    }

    /// "4 messages", "1 message".
    static func messages(_ turns: Int) -> String {
        turns == 1 ? "1 message" : "\(turns) messages"
    }

    /// A session's length: "4:36", "1:02:03", or nil when it is not known.
    static func length(_ durationSeconds: Int?) -> String? {
        guard let s = durationSeconds, s >= 0 else { return nil }
        let h = s / 3600
        let m = (s % 3600) / 60
        let r = String(format: "%02d", s % 60)
        return h > 0 ? "\(h):\(String(format: "%02d", m)):\(r)" : "\(m):\(r)"
    }

    /// The row's second line: kind · length · messages.
    static func summary(_ s: PastSession) -> String {
        [kind(s.channel, s.direction), length(s.durationSeconds), messages(s.turns)].compactMap { $0 }.joined(separator: " · ")
    }

    /// The transcript's subtitle: kind · length · how it ended.
    static func summary(_ t: SessionTranscript) -> String {
        [kind(t.channel, t.direction), length(t.durationSeconds), status(t.status)].compactMap { $0 }.joined(separator: " · ")
    }

    /// Whose line it is, for a transcript bubble.
    static func isCustomer(_ turn: TranscriptTurn) -> Bool {
        let role = turn.role.lowercased()
        return role == "customer" || role == "user"
    }

    /// A session id as a row shows it: enough to tell two apart, never the whole of it (Copy takes all of it).
    static func shortId(_ id: String?) -> String {
        guard let id = id, !id.isEmpty else { return "—" }
        return id.count <= 8 ? id : "…" + id.suffix(6)
    }
}
