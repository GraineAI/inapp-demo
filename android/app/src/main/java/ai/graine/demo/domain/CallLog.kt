package ai.graine.demo.domain

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Something that happened to a native call, kept so the demo can show what the SDK reported. */
data class CallLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val callId: String,
    /** placed, incoming, answered, declined, missed, ended or failed. */
    val event: String,
    /** ended: local, remote or failed. failed: why. incoming: "scheduled" for a call Graine rang. */
    val detail: String? = null,
    val at: Long,
    /** Which of the app's agents the call was with ("main", "help"), when known. */
    val agent: String? = null,
) {
    fun title(agentName: String): String = CallLog.describe(event, detail, agentName)

    val tone: Tone
        get() = when (event) {
            "answered", "placed" -> Tone.INFO
            "ended" -> if (detail == "failed") Tone.NEGATIVE else Tone.POSITIVE
            "declined", "missed" -> Tone.WARNING
            "failed" -> Tone.NEGATIVE
            else -> Tone.NEUTRAL
        }
}

/**
 * THE CALL ACTIVITY LOG: newest first, capped, kept on the device. In a real
 * app these outcomes would go to the app's own analytics; the SDK already
 * reports the outcome of every call Graine rang back to Graine.
 */
data class CallLog(val entries: List<CallLogEntry> = emptyList()) {
    fun record(entry: CallLogEntry): CallLog = CallLog((listOf(entry) + entries).take(CAP))

    fun cleared(): CallLog = CallLog()

    /** As kept on the device. */
    fun toJson(): String = JSONArray().apply {
        entries.forEach { e ->
            put(JSONObject().put("id", e.id).put("callId", e.callId).put("event", e.event).put("detail", e.detail ?: JSONObject.NULL).put("at", e.at)
                .put("agent", e.agent ?: JSONObject.NULL))
        }
    }.toString()

    companion object {
        const val CAP = 100
        const val STORAGE_KEY = "demo.callLog"

        fun fromJson(text: String?): CallLog {
            val a = runCatching { JSONArray(text ?: return CallLog()) }.getOrNull() ?: return CallLog()
            val entries = (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                CallLogEntry(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    callId = o.optString("callId"),
                    event = o.optString("event"),
                    detail = if (o.isNull("detail")) null else o.optString("detail"),
                    at = o.optLong("at"),
                    agent = if (o.isNull("agent")) null else o.optString("agent"),
                )
            }
            return CallLog(entries.take(CAP))
        }

        fun describe(event: String, detail: String?, agentName: String): String = when (event) {
            "placed" -> "You called $agentName"
            "incoming" -> if (detail == "scheduled") "$agentName called (scheduled call)" else "$agentName called"
            "answered" -> "Answered"
            "declined" -> "Declined"
            "missed" -> "Missed"
            "ended" -> when (detail) {
                "local" -> "Ended by you"
                "remote" -> "Ended by $agentName"
                "failed" -> "Call dropped"
                else -> "Ended"
            }
            "failed" -> if (!detail.isNullOrEmpty()) "Could not connect: $detail" else "Could not connect"
            else -> event.replaceFirstChar { it.uppercase() }
        }
    }
}
