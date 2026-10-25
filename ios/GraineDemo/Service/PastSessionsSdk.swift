import Foundation
import GraineInApp

/**
 * PAST SESSIONS, READ FROM THE SDK — the SDK's past-sessions types
 * (`chat.pastSessions`: `GrainePastSessions`, the sessions loaded so far,
 * loading, more to load, the last refusal's word; and
 * `chat.sessionTranscript(id:)`'s `GraineSessionTranscript`) turned into the
 * app's own `PastSessionsState` and `SessionTranscript`. `GraineService`
 * calls the SDK; this only reads what it answers, so the tests run it as it is.
 */
enum PastSessionsSdk {
    static func state(_ s: GrainePastSessions) -> PastSessionsState {
        PastSessionsState(sessions: s.sessions.map(session), loading: s.loading, hasMore: s.hasMore, error: s.error)
    }

    static func session(_ p: GraineSessionSummary) -> PastSession {
        PastSession(
            id: p.id,
            startedAt: p.startedAt,
            endedAt: p.endedAt,
            durationSeconds: p.durationSeconds,
            channel: p.channel.rawValue,
            direction: p.direction?.rawValue,
            status: blankIsNil(p.status),
            turns: p.turns,
            preview: blankIsNil(p.preview)
        )
    }

    static func transcript(_ t: GraineSessionTranscript) -> SessionTranscript {
        SessionTranscript(
            id: t.id,
            startedAt: t.startedAt,
            endedAt: t.endedAt,
            durationSeconds: t.durationSeconds,
            channel: t.channel.rawValue,
            direction: t.direction?.rawValue,
            status: blankIsNil(t.status),
            turns: t.turns.map { TranscriptTurn(role: $0.role == .customer ? "customer" : "agent", text: $0.text, at: $0.at) }
        )
    }

    /// The SDK's "" (it did not say) is the app's nil.
    private static func blankIsNil(_ s: String) -> String? {
        s.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : s
    }
}
