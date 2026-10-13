package ai.graine.demo.domain

import ai.graine.inapp.kmp.GraineSchedulingRules
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

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
    fun resolve(quick: UseCasePack.QuickTime, nowMs: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Long? {
        quick.inMinutes?.let { return ceilToStep(nowMs + it * 60_000L) }
        val parts = quick.time?.split(":")?.mapNotNull { it.toIntOrNull() } ?: return null
        if (parts.size != 2 || parts[0] !in 0..23 || parts[1] !in 0..59) return null
        val offset = when (quick.day?.lowercase() ?: "today") {
            "today" -> 0
            "tomorrow" -> 1
            else -> return null
        }
        val day = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(zone).date.plus(offset, DateTimeUnit.DAY)
        return LocalDateTime(day, LocalTime(parts[0], parts[1])).toInstant(zone).toEpochMilliseconds()
    }

    /** Inside the SDK's bounds: a chip outside them is shown disabled. */
    fun isBookable(atMs: Long, nowMs: Long, rules: GraineSchedulingRules): Boolean {
        val now = Instant.fromEpochMilliseconds(nowMs)
        return atMs >= rules.earliest(now).toEpochMilliseconds() && atMs <= rules.latest(now).toEpochMilliseconds()
    }

    /** What the pickers offer: the SDK's bounds, moved inwards onto the 5-minute grid. */
    fun range(nowMs: Long, rules: GraineSchedulingRules): LongRange {
        val now = Instant.fromEpochMilliseconds(nowMs)
        val low = ceilToStep(rules.earliest(now).toEpochMilliseconds())
        val high = maxOf(low, floorToStep(rules.latest(now).toEpochMilliseconds()))
        return low..high
    }

    /** A picked time on the grid (the nearest step). */
    fun snapped(ms: Long): Long {
        val floor = floorToStep(ms)
        return if (ms - floor < floor + STEP_MS - ms) floor else floor + STEP_MS
    }

    /** [ms] moved into [range] (a time picked outside the bounds goes to the nearest bound). */
    fun clamp(ms: Long, range: LongRange): Long = ms.coerceIn(range.first, range.last)

    /** "Asia/Kolkata · 6:00 pm": the device's zone, which the time is booked in. */
    fun zoneLabel(ms: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String = "${zone.id} · ${DemoFormat.clock(ms, zone)}"

    /** Every zone's offset is a whole number of quarter hours, so the UTC grid is the local grid. */
    fun floorToStep(ms: Long): Long = ms.floorDiv(STEP_MS) * STEP_MS

    fun ceilToStep(ms: Long): Long = floorToStep(ms).let { if (it == ms) ms else it + STEP_MS }

    /** The picked date (a day, as the date picker gives it: UTC midnight) at [hour]:[minute] in [zone]. */
    fun combine(dateUtcMidnight: Long, hour: Int, minute: Int, zone: TimeZone = TimeZone.currentSystemDefault()): Long {
        val day = Instant.fromEpochMilliseconds(dateUtcMidnight).toLocalDateTime(TimeZone.UTC).date
        return LocalDateTime(day, LocalTime(hour, minute)).toInstant(zone).toEpochMilliseconds()
    }

    /** The local day of [ms], as the date picker counts days: UTC midnight of that calendar date. */
    fun utcDayOf(ms: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Long =
        Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone).date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

    /** The hour and minute of [ms] in [zone], for the time picker. */
    fun hourMinute(ms: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Pair<Int, Int> =
        Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone).let { it.hour to it.minute }
}
