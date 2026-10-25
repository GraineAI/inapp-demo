package ai.graine.demo.graine

import ai.graine.demo.domain.PastSession
import ai.graine.demo.domain.PastSessionsState
import ai.graine.demo.domain.SessionTranscript
import ai.graine.demo.domain.TranscriptTurn
import ai.graine.inapp.GrainePastSessions
import ai.graine.inapp.GraineSessionSummary
import ai.graine.inapp.GraineSessionTranscript
import ai.graine.inapp.Role

/**
 * PAST SESSIONS, READ FROM THE SDK — the SDK's past-sessions types (`chat.pastSessions`: GrainePastSessions, the
 * sessions loaded so far, loading, more to load, the last refusal's word; and `chat.sessionTranscript(id)`'s
 * GraineSessionTranscript) turned into the app's own PastSessionsState and SessionTranscript. GraineService calls
 * the SDK; this only reads what it answers, so the tests run it as it is.
 */
internal object PastSessionsSdk {
    fun state(s: GrainePastSessions) = PastSessionsState(
        sessions = s.sessions.map { session(it) },
        loading = s.loading,
        hasMore = s.hasMore,
        error = s.error,
    )

    fun session(p: GraineSessionSummary) = PastSession(
        id = p.id,
        startedAt = p.startedAt,
        endedAt = p.endedAt,
        durationSeconds = p.durationSeconds?.toLong(),
        channel = p.channel.wire,
        direction = p.direction?.wire,
        status = p.status.takeIf { it.isNotBlank() },
        turns = p.turns,
        preview = p.preview.takeIf { it.isNotBlank() },
    )

    fun transcript(t: GraineSessionTranscript) = SessionTranscript(
        id = t.id,
        startedAt = t.startedAt,
        endedAt = t.endedAt,
        durationSeconds = t.durationSeconds?.toLong(),
        channel = t.channel.wire,
        direction = t.direction?.wire,
        status = t.status.takeIf { it.isNotBlank() },
        turns = t.turns.map { line -> TranscriptTurn(if (line.role == Role.CUSTOMER) "customer" else "agent", line.text, line.at) },
    )
}
