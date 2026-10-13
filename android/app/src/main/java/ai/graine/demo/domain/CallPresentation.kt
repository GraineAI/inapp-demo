package ai.graine.demo.domain

import ai.graine.inapp.GraineCallSource
import ai.graine.inapp.GraineCallStatus
import ai.graine.inapp.GraineError
import ai.graine.inapp.GraineErrorCode
import ai.graine.inapp.GraineScheduledCall
import ai.graine.inapp.GraineSchedulingRules
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
        GraineCallStatus.SCHEDULED -> "Scheduled"
        GraineCallStatus.RINGING -> "Ringing"
        GraineCallStatus.ANSWERED -> "Answered"
        GraineCallStatus.COMPLETED -> "Completed"
        GraineCallStatus.DECLINED -> "Declined"
        GraineCallStatus.MISSED -> "Missed"
        GraineCallStatus.FAILED -> "Failed"
        GraineCallStatus.CANCELLED -> "Cancelled"
        GraineCallStatus.EXPIRED -> "Expired"
        GraineCallStatus.NO_DEVICE -> "No device"
        // A status this build does not know passes through, readable.
        else -> status.wire.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    fun tone(status: GraineCallStatus): Tone = when (status) {
        GraineCallStatus.SCHEDULED, GraineCallStatus.RINGING, GraineCallStatus.ANSWERED -> Tone.INFO
        GraineCallStatus.COMPLETED -> Tone.POSITIVE
        GraineCallStatus.DECLINED, GraineCallStatus.MISSED, GraineCallStatus.EXPIRED, GraineCallStatus.NO_DEVICE -> Tone.WARNING
        GraineCallStatus.FAILED -> Tone.NEGATIVE
        else -> Tone.NEUTRAL
    }

    /** Who booked it, in words. */
    fun bookedBy(source: GraineCallSource?, agentName: String): String? = when (source) {
        GraineCallSource.AGENT -> "Booked by $agentName"
        GraineCallSource.APP -> "Booked in the app"
        GraineCallSource.DASHBOARD, GraineCallSource.API -> "Booked by the team"
        else -> null
    }

    /** Upcoming calls (the server lists them first), then the rest, in the server's order. */
    fun split(calls: List<GraineScheduledCall>): Pair<List<GraineScheduledCall>, List<GraineScheduledCall>> =
        calls.filter { it.isUpcoming } to calls.filter { !it.isUpcoming }

    /** The next call that will ring, for Home. */
    fun next(calls: List<GraineScheduledCall>): GraineScheduledCall? =
        calls.filter { it.isUpcoming && it.at != null }.minByOrNull { it.at!!.time }

    /**
     * The time the picker opens on: at least 30 minutes out, never before the
     * rules' earliest, rounded up to the next quarter hour.
     */
    fun suggestedTime(nowMs: Long, rules: GraineSchedulingRules): Long {
        val base = maxOf(rules.earliest(Date(nowMs)).time, nowMs + 30 * 60_000L)
        // Every time zone's offset is a whole number of quarter hours, so a UTC quarter is a local one.
        val quarter = 15 * 60_000L
        return ceil(base.toDouble() / quarter).toLong() * quarter
    }

    /**
     * Plain words for a refusal (the platform's error codes). For a code the
     * app has no words of its own for, the SDK's message — the server's
     * sentence when there was one.
     */
    fun message(error: GraineError?, rules: GraineSchedulingRules = GraineSchedulingRules()): String {
        val code = error?.code ?: GraineErrorCode.NETWORK
        return when (code) {
            GraineErrorCode.SCHEDULING_OFF -> SCHEDULING_OFF
            GraineErrorCode.TOO_SOON -> "Choose a time at least ${if (rules.minLeadMinutes == 1) "1 minute" else "${rules.minLeadMinutes} minutes"} from now."
            GraineErrorCode.TOO_FAR -> "Choose a time within the next ${rules.maxDaysAhead} days."
            GraineErrorCode.OUTSIDE_WINDOW -> "That time is outside the assistant's calling hours. Choose another time."
            GraineErrorCode.TOO_MANY_PENDING -> "You already have ${rules.maxPendingPerUser} upcoming calls. Cancel one to book another."
            GraineErrorCode.IDENTITY_REQUIRED -> "Sign in to use this."
            GraineErrorCode.DEVICE_LIMIT -> "This user is signed in on the maximum number of devices. Sign out on another device first."
            GraineErrorCode.RATE_LIMITED -> "Too many requests. Try again in a few minutes."
            GraineErrorCode.NOT_FOUND -> "That call no longer exists. Pull to refresh."
            GraineErrorCode.NETWORK -> "No connection. Check your network and try again."
            GraineErrorCode.INVALID_KEY, GraineErrorCode.ORIGIN_REFUSED ->
                "This app is not set up for the assistant yet. Check the publishable key and the agent's allowed domains."
            else -> error?.message?.trim()?.takeIf { it.isNotEmpty() } ?: "Something went wrong. Try again."
        }
    }
}

/** How times read in the app: "Today, 18:00", "Tomorrow, 09:30", "Thu 16 Oct, 18:00". */
object DemoFormat {
    /** The time as the screen context carries it: ISO-8601, UTC. */
    fun iso(ms: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(ms))

    fun dayWord(ms: Long, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): String? {
        fun day(t: Long) = Calendar.getInstance(zone).apply { timeInMillis = t }.let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }
        fun shifted(days: Int) = Calendar.getInstance(zone).apply { timeInMillis = nowMs; add(Calendar.DAY_OF_YEAR, days) }.timeInMillis
        return when (day(ms)) {
            day(nowMs) -> "Today"
            day(shifted(1)) -> "Tomorrow"
            day(shifted(-1)) -> "Yesterday"
            else -> null
        }
    }

    fun time(date: Date?, nowMs: Long, zone: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.getDefault()): String {
        if (date == null) return "Time not available"
        val clock = DateFormat.getTimeInstance(DateFormat.SHORT, locale).apply { timeZone = zone }.format(date)
        val word = dayWord(date.time, nowMs, zone) ?: SimpleDateFormat("EEE d MMM", locale).apply { timeZone = zone }.format(date)
        return "$word, $clock"
    }
}
