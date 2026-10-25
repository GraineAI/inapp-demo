package ai.graine.demo.domain

import java.util.Date

/*
 * PAST SESSIONS: the signed-in user's earlier sessions with an agent — each chat, voice conversation and call, newest
 * first, like the dashboard's history — and one session's transcript. The SDK keeps them (chat.pastSessions,
 * refreshPastSessions, loadMorePastSessions, sessionTranscript); these are the app's view of that state, so its
 * screen and its tests read the same thing. Contract: sdk/contract/past-sessions.json.
 */

/** One earlier session, as the list shows it. */
data class PastSession(
    /** The session's id (the execution id the dashboard shows). */
    val id: String,
    val startedAt: Date?,
    val endedAt: Date?,
    val durationSeconds: Long?,
    /** `chat`, `voice` or `call`. */
    val channel: String,
    /** `inbound`, `outbound`, or null when it has none (a chat). */
    val direction: String?,
    /** How it ended, in the SDK's word (`completed`, …). */
    val status: String?,
    /** How many messages it holds. */
    val turns: Int,
    /** Its first words, for the row. */
    val preview: String?,
)

/** The list as the SDK holds it: the sessions loaded so far, newest first, and where loading stands. */
data class PastSessionsState(
    val sessions: List<PastSession> = emptyList(),
    val loading: Boolean = false,
    val hasMore: Boolean = false,
    /** Why the last load failed: `identity_required`, `device_limit`, `rate_limited`, `unavailable`, or another word. */
    val error: String? = null,
)

/** One line of a transcript. */
data class TranscriptTurn(
    /** `customer` or `agent`. */
    val role: String,
    val text: String,
    val at: Date?,
)

/** One session in full, oldest line first: read only. */
data class SessionTranscript(
    val id: String,
    val startedAt: Date?,
    val endedAt: Date?,
    val durationSeconds: Long?,
    val channel: String,
    val direction: String?,
    val status: String?,
    val turns: List<TranscriptTurn>,
)

/** How past sessions read in the app. */
object PastSessionsText {
    /** The list's empty, loading and error lines. */
    const val EMPTY = "No past sessions yet. A session appears here once it has closed."
    const val LOADING = "Loading past sessions…"

    /** Why the list did not load, in plain words: the two the SDK names for this list first, then the rest. */
    fun error(code: String?): String? = when (code) {
        null, "" -> null
        "identity_required" -> "Sign in to see past sessions."
        "device_limit" -> "Not available on this device."
        "rate_limited" -> "Too many requests. Try again in a few minutes."
        else -> "Past sessions could not be loaded. Try again."
    }

    /** A refused list offers Try again, except when signing in or another device is the answer. */
    fun canRetry(code: String?): Boolean = code != "identity_required" && code != "device_limit"

    /** Why a transcript did not open: an unknown session, or another user's, is `not_found`; the rest as for the list. */
    fun transcriptError(code: String?): String? = when (code) {
        "not_found" -> "That session is not available."
        else -> error(code)
    }

    /** "Chat", "Voice", "Call · outbound". */
    fun kind(channel: String, direction: String?): String {
        val base = when (channel.lowercase()) {
            "chat" -> "Chat"
            "voice" -> "Voice"
            "call" -> "Call"
            else -> channel.replaceFirstChar { it.uppercase() }
        }
        return if (direction.isNullOrBlank() || direction == "none") base else "$base · $direction"
    }

    /** How it ended, as a word on a chip: the SDK's word, readable. */
    fun status(status: String?): String = status?.takeIf { it.isNotBlank() }?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Ended"

    /** "4 messages", "1 message". */
    fun messages(turns: Int): String = if (turns == 1) "1 message" else "$turns messages"

    /** A session's length: "4:36", "1:02:03", or nothing when it is not known. */
    fun length(durationSeconds: Long?): String? = durationSeconds?.takeIf { it >= 0 }?.let { s ->
        val h = s / 3600
        val m = (s % 3600) / 60
        val r = (s % 60).toString().padStart(2, '0')
        if (h > 0) "$h:${m.toString().padStart(2, '0')}:$r" else "$m:$r"
    }

    /** The row's second line: kind · length · messages. */
    fun summary(s: PastSession): String =
        listOfNotNull(kind(s.channel, s.direction), length(s.durationSeconds), messages(s.turns)).joinToString(" · ")

    /** The transcript's subtitle: kind · length · how it ended. */
    fun transcriptSummary(t: SessionTranscript): String =
        listOfNotNull(kind(t.channel, t.direction), length(t.durationSeconds), status(t.status)).joinToString(" · ")

    /** Whose line it is, for a transcript bubble. */
    fun isCustomer(turn: TranscriptTurn): Boolean = turn.role.equals("customer", ignoreCase = true) || turn.role.equals("user", ignoreCase = true)

    /** A session id as a row shows it: enough to tell two apart, never the whole of it (Copy takes all of it). */
    fun shortId(id: String?): String = when {
        id.isNullOrEmpty() -> "—"
        id.length <= 8 -> id
        else -> "…" + id.takeLast(6)
    }
}
