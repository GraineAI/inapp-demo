package ai.graine.demo.graine

import ai.graine.demo.domain.PastSession
import ai.graine.demo.domain.PastSessionsState
import ai.graine.demo.domain.SessionTranscript
import ai.graine.demo.domain.TranscriptTurn
import ai.graine.inapp.kmp.GraineAgent
import ai.graine.inapp.kmp.GrainePastSessions
import ai.graine.inapp.kmp.GraineRole
import ai.graine.inapp.kmp.GraineSessionSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * PAST SESSIONS, READ FROM THE SDK — the one place the app touches them: `chat.pastSessions` (GrainePastSessions:
 * the sessions loaded so far, loading, more to load, the last refusal's word), `chat.refreshPastSessions()`,
 * `chat.loadMorePastSessions()` and `chat.sessionTranscript(id)`, turned into the app's own PastSessionsState and
 * SessionTranscript.
 */
internal object PastSessionsSdk {
    fun state(agent: GraineAgent, scope: CoroutineScope): StateFlow<PastSessionsState> =
        agent.chat.pastSessions.map { of(it) }.stateIn(scope, SharingStarted.Eagerly, of(agent.chat.pastSessions.value))

    fun refresh(agent: GraineAgent) = agent.chat.refreshPastSessions()

    fun loadMore(agent: GraineAgent) = agent.chat.loadMorePastSessions()

    suspend fun transcript(agent: GraineAgent, id: String): SessionTranscript {
        val t = agent.chat.sessionTranscript(id)
        return SessionTranscript(
            id = t.id,
            startedAt = t.startedAt,
            endedAt = t.endedAt,
            durationSeconds = t.durationSeconds?.toLong(),
            channel = t.channel.wire,
            direction = t.direction?.wire,
            status = t.status.takeIf { it.isNotBlank() },
            turns = t.turns.map { line -> TranscriptTurn(if (line.role == GraineRole.USER) "customer" else "agent", line.text, line.at) },
        )
    }

    private fun of(s: GrainePastSessions) = PastSessionsState(
        sessions = s.sessions.map { of(it) },
        loading = s.loading,
        hasMore = s.hasMore,
        error = s.error,
    )

    private fun of(p: GraineSessionSummary) = PastSession(
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
}
