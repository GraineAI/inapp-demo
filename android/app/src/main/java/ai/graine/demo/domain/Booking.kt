package ai.graine.demo.domain

import ai.graine.inapp.GraineSchedulingRules
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * THE BOOKING SHEET'S TIMES: the pack's quick-time chips, the pickers'
 * 5-minute grid, and the device's time zone as the sheet shows it. The bounds
 * are the SDK's (GraineSchedulingRules.earliest / latest); the SDK checks them
 * again when the call is booked.
 */
object Booking {
    /** The pickers move in 5-minute steps. */
    const val STEP_MINUTES = 5
    private const val STEP_MS = STEP_MINUTES * 60_000L

    /** The time a quick-time chip stands for, on the 5-minute grid, in [zone]. Null when it names no time. */
    fun resolve(quick: UseCasePack.QuickTime, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Long? {
        quick.inMinutes?.let { return ceilToStep(nowMs + it * 60_000L) }
        val parts = quick.time?.split(":")?.mapNotNull { it.toIntOrNull() } ?: return null
        if (parts.size != 2 || parts[0] !in 0..23 || parts[1] !in 0..59) return null
        val offset = when (quick.day?.lowercase() ?: "today") {
            "today" -> 0
            "tomorrow" -> 1
            else -> return null
        }
        return Calendar.getInstance(zone).apply {
            timeInMillis = nowMs
            add(Calendar.DAY_OF_YEAR, offset)
            set(Calendar.HOUR_OF_DAY, parts[0])
            set(Calendar.MINUTE, parts[1])
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** Inside the SDK's bounds: a chip outside them is shown disabled. */
    fun isBookable(atMs: Long, nowMs: Long, rules: GraineSchedulingRules): Boolean {
        val now = Date(nowMs)
        return atMs >= rules.earliest(now).time && atMs <= rules.latest(now).time
    }

    /** What the pickers offer: the SDK's bounds, moved inwards onto the 5-minute grid. */
    fun range(nowMs: Long, rules: GraineSchedulingRules): LongRange {
        val now = Date(nowMs)
        val low = ceilToStep(rules.earliest(now).time)
        val high = maxOf(low, floorToStep(rules.latest(now).time))
        return low..high
    }

    /** A picked time on the grid (the nearest step). */
    fun snapped(ms: Long): Long {
        val floor = floorToStep(ms)
        return if (ms - floor < floor + STEP_MS - ms) floor else floor + STEP_MS
    }

    /** [ms] moved into [range] (a time picked outside the bounds goes to the nearest bound). */
    fun clamp(ms: Long, range: LongRange): Long = ms.coerceIn(range.first, range.last)

    /** "Asia/Kolkata · 6:00 PM": the device's zone, which the time is booked in. */
    fun zoneLabel(ms: Long, zone: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.getDefault()): String {
        val clock = DateFormat.getTimeInstance(DateFormat.SHORT, locale).apply { timeZone = zone }.format(Date(ms))
        return "${zone.id} · $clock"
    }

    /** Every zone's offset is a whole number of quarter hours, so the UTC grid is the local grid. */
    fun floorToStep(ms: Long): Long = Math.floorDiv(ms, STEP_MS) * STEP_MS

    fun ceilToStep(ms: Long): Long = floorToStep(ms).let { if (it == ms) ms else it + STEP_MS }

    /** The picked date (a day, as the date picker gives it: UTC midnight) at [hour]:[minute] in [zone]. */
    fun combine(dateUtcMidnight: Long, hour: Int, minute: Int, zone: TimeZone = TimeZone.getDefault()): Long {
        val day = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = dateUtcMidnight }
        return Calendar.getInstance(zone).apply {
            clear()
            set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH), hour, minute, 0)
        }.timeInMillis
    }

    /** The local day of [ms], as the date picker counts days: UTC midnight of that calendar date. */
    fun utcDayOf(ms: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        val local = Calendar.getInstance(zone).apply { timeInMillis = ms }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
}
