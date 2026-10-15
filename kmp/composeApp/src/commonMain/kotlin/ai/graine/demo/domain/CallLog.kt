package ai.graine.demo.domain

import kotlin.random.Random

/** Something that happened to a native call, kept so the demo can show what the SDK reported. */
data class CallLogEntry(
    val id: String = "${Random.nextLong().toULong().toString(16)}",
    val callId: String,
    /** placed, incoming, ringing, answered, declined, missed, ended or failed. */
    val event: String,
    /** ended: local, remote or failed. failed: why. missed: busy or no_answer. incoming: "scheduled" for a call Graine rang. */
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
    fun toJson(): String = Json.write(entries.map { e ->
        mapOf("id" to e.id, "callId" to e.callId, "event" to e.event, "detail" to e.detail, "at" to e.at, "agent" to e.agent)
    })

    companion object {
        const val CAP = 100
        const val STORAGE_KEY = "demo.callLog"

        fun fromJson(text: String?): CallLog {
            val list = runCatching { Json.parse(text ?: return CallLog()) as? List<*> }.getOrNull() ?: return CallLog()
            val entries = list.mapNotNull { item ->
                @Suppress("UNCHECKED_CAST")
                val o = item as? JsonObject ?: return@mapNotNull null
                CallLogEntry(
                    id = o["id"] as? String ?: Random.nextLong().toULong().toString(16),
                    callId = o["callId"] as? String ?: "",
                    event = o["event"] as? String ?: "",
                    detail = o["detail"] as? String,
                    at = (o["at"] as? Long) ?: 0L,
                    agent = o["agent"] as? String,
                )
            }
            return CallLog(entries.take(CAP))
        }

        fun describe(event: String, detail: String?, agentName: String): String = when (event) {
            "placed" -> "You called $agentName"
            "incoming" -> if (detail == "scheduled") "$agentName called (scheduled call)" else "$agentName called"
            "ringing" -> "Ringing"
            "answered" -> "Answered"
            "declined" -> "Declined"
            "missed" -> if (detail == "busy") "Missed (another call was live)" else "Missed"
            "ended" -> when (detail) {
                "local" -> "Ended by you"
                "remote" -> "Ended by $agentName"
                "failed" -> "Call dropped"
                else -> "Ended"
            }
            "failed" -> if (!detail.isNullOrEmpty()) "Could not connect: $detail" else "Could not connect"
            "audio" -> when {
                detail == "webrtc" -> "Call audio: WebRTC"
                detail != null && detail.startsWith("websocket") ->
                    "Call audio: standard connection" + (detail.substringAfter(":", "").takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: "")
                else -> "Call audio: ${detail ?: "unknown"}"
            }
            else -> event.replaceFirstChar { it.uppercase() }
        }
    }
}
