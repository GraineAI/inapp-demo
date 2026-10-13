package ai.graine.demo.domain

import ai.graine.inapp.kmp.GraineCallSource
import ai.graine.inapp.kmp.GraineCallStatus
import ai.graine.inapp.kmp.GraineErrorCode
import ai.graine.inapp.kmp.GraineException
import ai.graine.inapp.kmp.GraineScheduledCall
import ai.graine.inapp.kmp.GraineSchedulingRules
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.ceil

/*
 * HOW SCHEDULED CALLS AND REFUSALS READ IN THE APP. Presentation only: the
 * rules, the local checks before a booking is sent, and what "upcoming" or
 * "can be cancelled" mean all belong to the SDK (GraineSchedulingRules,
 * scheduleCall, GraineScheduledCall).
 */

/** How a status reads at a glance. The screens pick the colour. */
enum class Tone { NEUTRAL, INFO, POSITIVE, WARNING, NEGATIVE }

object CallText {
    /** Shown in place of the booking button's hint when the agent takes no bookings from the app. */
    const val SCHEDULING_OFF = "Booking calls from the app is turned off for this assistant."

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

    /** The next call that will ring, for Home. */
    fun next(calls: List<GraineScheduledCall>): GraineScheduledCall? =
        calls.filter { it.isUpcoming && it.at != null }.minByOrNull { it.at!! }

    /**
     * The time the picker opens on: at least 30 minutes out, never before the
     * rules' earliest, rounded up to the next quarter hour.
     */
    fun suggestedTime(nowMs: Long, rules: GraineSchedulingRules): Long {
        val base = maxOf(rules.earliest(Instant.fromEpochMilliseconds(nowMs)).toEpochMilliseconds(), nowMs + 30 * 60_000L)
        // Every time zone's offset is a whole number of quarter hours, so a UTC quarter is a local one.
        val quarter = 15 * 60_000L
        return ceil(base.toDouble() / quarter).toLong() * quarter
    }

    /**
     * Plain words for a refusal (the SDK's error codes). For a code the app has
     * no words of its own for, the SDK's message — the server's sentence when
     * there was one.
     */
    fun message(error: Throwable?, rules: GraineSchedulingRules = GraineSchedulingRules()): String {
        val e = error as? GraineException
        // Not the SDK's refusal (an agent that is not configured, say): its own words.
        if (error != null && e == null) return error.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Try again."
        return when (e?.code ?: GraineErrorCode.Network) {
            GraineErrorCode.SchedulingOff -> SCHEDULING_OFF
            GraineErrorCode.TooSoon -> "Choose a time at least ${if (rules.minLeadMinutes == 1) "1 minute" else "${rules.minLeadMinutes} minutes"} from now."
            GraineErrorCode.TooFar -> "Choose a time within the next ${rules.maxDaysAhead} days."
            GraineErrorCode.OutsideWindow -> "That time is outside the assistant's calling hours. Choose another time."
            GraineErrorCode.TooManyPending -> "You already have ${rules.maxPendingPerUser} upcoming calls. Cancel one to book another."
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

    /** "16 Oct 2026". */
    fun date(ms: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
        val d = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone).date
        return "${d.dayOfMonth} ${months[d.monthNumber - 1]} ${d.year}"
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
