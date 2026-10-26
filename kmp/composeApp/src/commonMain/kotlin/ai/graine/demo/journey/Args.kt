package ai.graine.demo.journey

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * READING AN ACTION'S ARGUMENTS AS THEY ARRIVE — maps and lists of what the model sent, on either platform: a number
 * may be an Int, a Long, a Double or the number written as text; a boolean may be `true` or "yes"; a day "Tue" or
 * "tuesday"; a time "9:30", "09:30" or "9:30 pm". Never throws: a value that cannot be read is null, and the action
 * says what was wrong.
 */
object Args {
    /** Trimmed text; a number as its digits; blank is null. */
    fun string(v: Any?): String? = when (v) {
        null -> null
        is String -> v.trim().takeIf { it.isNotEmpty() }
        is Number -> numberText(v.toDouble())
        is Boolean -> v.toString()
        else -> null
    }

    /** A number, from any number type or its text ("2.5"); NaN and infinity are not numbers. */
    fun number(v: Any?): Double? = when (v) {
        is Number -> v.toDouble()
        is String -> v.trim().toDoubleOrNull()
        else -> null
    }?.takeIf { it.isFinite() }

    fun int(v: Any?): Int? = number(v)?.let { if (it in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) kotlin.math.round(it).toInt() else null }

    /** true / false, "yes" / "no", "true" / "false", 1 / 0. */
    fun bool(v: Any?): Boolean? = when (v) {
        is Boolean -> v
        is Number -> when (v.toDouble()) { 1.0 -> true; 0.0 -> false; else -> null }
        is String -> when (v.trim().lowercase()) {
            "true", "yes", "y", "1", "done", "followed" -> true
            "false", "no", "n", "0", "skipped", "missed" -> false
            else -> null
        }
        else -> null
    }

    fun list(v: Any?): List<Any?>? = (v as? List<*>)?.toList()

    /** An object, its keys as text. */
    fun map(v: Any?): Map<String, Any?>? = (v as? Map<*, *>)?.entries?.associate { (k, value) -> k.toString() to value }

    /** A list of text items (numbers written out); one text value counts as a list of one. */
    fun strings(v: Any?, max: Int = 50): List<String> = when (v) {
        is List<*> -> v.mapNotNull { string(it) }
        is String -> listOfNotNull(string(v))
        else -> emptyList()
    }.take(max)

    /** A weekday: "mon", "Mon", "monday". */
    fun weekday(v: Any?): Weekday? = string(v)?.let { Weekday.parse(it) }

    /** A time of day as "HH:MM". */
    fun time(v: Any?): String? = string(v)?.let { JourneyTime.normalise(it) }

    /** "1800" for 1800.0, "2.5" for 2.5. */
    fun numberText(d: Double): String =
        if (d == kotlin.math.floor(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString() else d.toString()

    /** Text cut to [max] characters, whole characters only. */
    fun clip(text: String, max: Int): String = if (text.length <= max) text else text.take(max).trimEnd()
}

/** The plan's days, Monday first, by the word the plan action uses for each. */
enum class Weekday(val wire: String, val label: String, val dayOfWeek: DayOfWeek) {
    MON("mon", "Monday", DayOfWeek.MONDAY),
    TUE("tue", "Tuesday", DayOfWeek.TUESDAY),
    WED("wed", "Wednesday", DayOfWeek.WEDNESDAY),
    THU("thu", "Thursday", DayOfWeek.THURSDAY),
    FRI("fri", "Friday", DayOfWeek.FRIDAY),
    SAT("sat", "Saturday", DayOfWeek.SATURDAY),
    SUN("sun", "Sunday", DayOfWeek.SUNDAY);

    val short: String get() = label.take(3)

    companion object {
        fun parse(text: String): Weekday? {
            val t = text.trim().lowercase()
            if (t.length < 2) return null
            return entries.firstOrNull { it.wire == t || it.label.lowercase() == t } ?: entries.firstOrNull { it.label.lowercase().startsWith(t) && t.length >= 3 }
        }

        fun of(day: DayOfWeek): Weekday = entries.first { it.dayOfWeek == day }

        fun of(date: LocalDate): Weekday = of(date.dayOfWeek)
    }
}

/** Times of day as the journey keeps them: "HH:MM", 24-hour. */
object JourneyTime {
    private val CLOCK = Regex("^(\\d{1,2})(?:[:.h](\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?$", RegexOption.IGNORE_CASE)

    /** "9:30" → "09:30", "9:30 pm" → "21:30", "21.30" → "21:30", "7 am" → "07:00"; null for anything else. */
    fun normalise(text: String): String? {
        val m = CLOCK.matchEntire(text.trim()) ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
        val half = m.groupValues[3].lowercase().replace(".", "")
        if (minute > 59) return null
        when (half) {
            "am" -> { if (hour !in 1..12) return null; if (hour == 12) hour = 0 }
            "pm" -> { if (hour !in 1..12) return null; if (hour != 12) hour += 12 }
            else -> if (m.groupValues[2].isEmpty()) return null // "9" alone is not a time
        }
        if (hour !in 0..23) return null
        return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
    }

    fun minutes(hhmm: String): Int = hhmm.substring(0, 2).toInt() * 60 + hhmm.substring(3, 5).toInt()

    fun localTime(hhmm: String): LocalTime = LocalTime(hhmm.substring(0, 2).toInt(), hhmm.substring(3, 5).toInt())

    /** "9:30 am", "1:00 pm" — the way the app's screens say a time. */
    fun spoken(hhmm: String): String {
        val h = hhmm.substring(0, 2).toInt()
        val m = hhmm.substring(3, 5)
        val twelve = (h % 12).let { if (it == 0) 12 else it }
        return "$twelve:$m ${if (h < 12) "am" else "pm"}"
    }

    /** That time of day on [date], in [zone]. */
    fun at(date: LocalDate, hhmm: String, zone: TimeZone): Instant = LocalDateTime(date, localTime(hhmm)).toInstant(zone)

    /** "HH:MM" of [instant] in [zone]. */
    fun of(instant: Instant, zone: TimeZone): String {
        val t = instant.toLocalDateTime(zone)
        return "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
    }

    /**
     * Whether [hhmm] falls inside quiet hours from [start] to [end] (the end not included). Quiet hours may cross
     * midnight (22:00–07:00); equal start and end means none.
     */
    fun quiet(hhmm: String, start: String, end: String): Boolean {
        val t = minutes(hhmm)
        val s = minutes(start)
        val e = minutes(end)
        return when {
            s == e -> false
            s < e -> t in s until e
            else -> t >= s || t < e
        }
    }
}
