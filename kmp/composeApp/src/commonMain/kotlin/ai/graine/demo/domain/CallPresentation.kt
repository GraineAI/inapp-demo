package ai.graine.demo.domain

import ai.graine.inapp.kmp.GraineCallSource
import ai.graine.inapp.kmp.GraineCallStatus
import ai.graine.inapp.kmp.GraineErrorCode
import ai.graine.inapp.kmp.GraineException
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/*
 * HOW SCHEDULED CALLS AND REFUSALS READ IN THE APP. Presentation only: the
 * booking sheet, its rules and refusals, and what "upcoming" or "can be
 * cancelled" mean all belong to the SDK (presentBooking, bookingAvailable,
 * GraineScheduledCall).
 */

/** How a status reads at a glance. The screens pick the colour. */
enum class Tone { NEUTRAL, INFO, POSITIVE, WARNING, NEGATIVE }

object CallText {
    /** The one hint under a booking button that is off: the SDK's bookingAvailable said no. */
    const val BOOKING_UNAVAILABLE = "Booking isn't available for this agent"

    fun label(status: GraineCallStatus): String = when (status) {
        GraineCallStatus.Scheduled -> "Scheduled"
        GraineCallStatus.Ringing -> "Ringing"
        GraineCallStatus.Answered -> "Answered"
        GraineCallStatus.Completed -> "Completed"
        GraineCallStatus.Declined -> "Declined"
        GraineCallStatus.Missed -> "Missed"
        GraineCallStatus.Failed -> "Failed"
        GraineCallStatus.Cancelled -> "Cancelled"
        GraineCallStatus.Expired -> "Expired"
        GraineCallStatus.NoDevice -> "No device"
        // A status this build does not know passes through, readable.
        is GraineCallStatus.Unknown -> status.raw.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    fun tone(status: GraineCallStatus): Tone = when (status) {
        GraineCallStatus.Scheduled, GraineCallStatus.Ringing, GraineCallStatus.Answered -> Tone.INFO
        GraineCallStatus.Completed -> Tone.POSITIVE
        GraineCallStatus.Declined, GraineCallStatus.Missed, GraineCallStatus.Expired, GraineCallStatus.NoDevice -> Tone.WARNING
        GraineCallStatus.Failed -> Tone.NEGATIVE
        else -> Tone.NEUTRAL
    }

    /** Who booked it, in words. */
    fun bookedBy(source: GraineCallSource?, agentName: String): String? = when (source) {
        GraineCallSource.Agent -> "Booked by $agentName"
        GraineCallSource.App -> "Booked in the app"
        GraineCallSource.Dashboard, GraineCallSource.Api -> "Booked by the team"
        else -> null
    }

    /**
     * Plain words for a refusal of the list or a cancel (the SDK's error codes; a
     * booking's refusals are the sheet's own). For a code the app has no words of
     * its own for, the SDK's message — the server's sentence when there was one.
     */
    fun message(error: Throwable?): String {
        val e = error as? GraineException
        // Not the SDK's refusal (an agent that is not configured, say): its own words.
        if (error != null && e == null) return error.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Try again."
        return when (e?.code ?: GraineErrorCode.Network) {
            GraineErrorCode.IdentityRequired -> "Sign in to use this."
            GraineErrorCode.DeviceLimit -> "This user is signed in on the maximum number of devices. Sign out on another device first."
            GraineErrorCode.RateLimited -> "Too many requests. Try again in a few minutes."
            GraineErrorCode.NotFound -> "That call no longer exists. Pull to refresh."
            GraineErrorCode.Network -> "No connection. Check your network and try again."
            GraineErrorCode.InvalidKey, GraineErrorCode.OriginRefused ->
                "This app is not set up for the assistant yet. Check the publishable key and the agent's allowed domains."
            else -> e?.message?.trim()?.takeIf { it.isNotEmpty() } ?: "Something went wrong. Try again."
        }
    }
}

/** How times read in the app: "Today, 6:00 pm", "Tomorrow, 9:30 am", "Thu 16 Oct, 6:00 pm". */
object DemoFormat {
    private val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** The time as the screen context carries it: ISO-8601, UTC, whole seconds. */
    fun iso(ms: Long): String = Instant.fromEpochMilliseconds(ms - ms.mod(1000L)).toString()

    /** "6:00 pm". */
    fun clock(ms: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
        val t = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone)
        val hour = (t.hour % 12).let { if (it == 0) 12 else it }
        return "$hour:${t.minute.toString().padStart(2, '0')} ${if (t.hour < 12) "am" else "pm"}"
    }

    fun dayWord(ms: Long, nowMs: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String? {
        val day = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone).date
        val today = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(zone).date
        return when (day) {
            today -> "Today"
            today.plus(1, DateTimeUnit.DAY) -> "Tomorrow"
            today.minus(1, DateTimeUnit.DAY) -> "Yesterday"
            else -> null
        }
    }

    fun time(at: Instant?, nowMs: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
        if (at == null) return "Time not available"
        val ms = at.toEpochMilliseconds()
        val word = dayWord(ms, nowMs, zone) ?: Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone).let {
            "${days[it.dayOfWeek.ordinal]} ${it.dayOfMonth} ${months[it.monthNumber - 1]}"
        }
        return "$word, ${clock(ms, zone)}"
    }
}
