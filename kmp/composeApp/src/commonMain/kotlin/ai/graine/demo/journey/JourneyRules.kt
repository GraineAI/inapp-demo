package ai.graine.demo.journey

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/*
 * THE JOURNEY'S RULES, pure: what a profile answer must be and the variables it gives the agents, the few times the
 * first-call picker offers, the check-in reminders a plan makes, and the figures Today and Progress show. No SDK and
 * no storage here — each is a function of what it is handed, so the tests read like the rules.
 */

/** A profile's answers, checked, and turned into what identify and setVariables send. */
object ProfileRules {
    /** Why each answer cannot be kept, by field key; empty when every answer can. */
    fun problems(fields: List<JourneyPack.ProfileField>, values: Map<String, String>, today: LocalDate): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (f in fields) {
            val v = values[f.key]?.trim().orEmpty()
            if (v.isEmpty()) {
                if (f.required) out[f.key] = "Required."
                continue
            }
            when (f.type) {
                JourneyPack.FieldType.TEXT -> f.maxLength?.let { if (v.length > it) out[f.key] = "At most $it characters." }
                JourneyPack.FieldType.CHOICE -> if (f.options.none { it.value == v }) out[f.key] = "Choose one."
                JourneyPack.FieldType.NUMBER -> {
                    val n = Args.number(v)
                    val min = f.min
                    val max = f.max
                    if (n == null) out[f.key] = "Enter a number."
                    else if ((min != null && n < min) || (max != null && n > max)) {
                        out[f.key] = "Between ${Args.numberText(min ?: n)} and ${Args.numberText(max ?: n)}${f.unit?.let { " $it" }.orEmpty()}."
                    }
                }
                JourneyPack.FieldType.DATE -> {
                    val date = runCatching { LocalDate.parse(v) }.getOrNull()
                    val age = date?.let { age(it, today) }
                    if (date == null || date > today || age == null) out[f.key] = "Enter a date."
                    else {
                        val min = f.min?.toInt()
                        val max = f.max?.toInt()
                        if ((min != null && age < min) || (max != null && age > max)) out[f.key] = "Age ${min ?: 0} to ${max ?: 150}."
                    }
                }
            }
        }
        return out
    }

    /** Whole years from [birth] to [today]. */
    fun age(birth: LocalDate, today: LocalDate): Int {
        var years = today.year - birth.year
        if (today.monthNumber < birth.monthNumber || (today.monthNumber == birth.monthNumber && today.dayOfMonth < birth.dayOfMonth)) years--
        return years
    }

    /**
     * Every variable the journey sets, from the profile and the language chosen: a text or choice as given, a number
     * as its digits, a date as the age in years, the language's code and that choice's own variables. A variable with
     * no answer is null, so setVariables removes any earlier value. The name goes with identify instead.
     */
    fun variables(journey: JourneyPack, profile: JourneyProfile, today: LocalDate): Map<String, String?> {
        val out = LinkedHashMap<String, String?>()
        for (f in journey.onboarding.fields) {
            val variable = f.variable ?: continue
            val v = profile.values[f.key]?.trim()?.takeIf { it.isNotEmpty() }
            out[variable] = when {
                v == null -> null
                f.type == JourneyPack.FieldType.NUMBER -> Args.number(v)?.let { Args.numberText(it) }
                f.type == JourneyPack.FieldType.DATE -> runCatching { age(LocalDate.parse(v), today).toString() }.getOrNull()
                else -> Args.clip(v, VALUE_MAX)
            }
        }
        val language = journey.onboarding.language
        val chosen = language.options.firstOrNull { it.id == profile.languageId }
        out[language.variable] = chosen?.code
        language.options.flatMap { it.variables.keys }.distinct().forEach { k -> out[k] = chosen?.variables?.get(k)?.let { Args.clip(it, VALUE_MAX) } }
        return out
    }

    /** identify's traits: the id under the journey's field — and under user_id, the dashboard's usual choice — and the name. */
    fun traits(journey: JourneyPack, userId: String, profile: JourneyProfile): Map<String, String> = LinkedHashMap<String, String>().apply {
        put(journey.identifierField, userId)
        getOrPut("user_id") { userId }
        profile.values[JourneyPack.NAME_KEY]?.trim()?.takeIf { it.isNotEmpty() }?.let { put("name", Args.clip(it, VALUE_MAX)) }
    }

    /** Every answer the profile needs is there. */
    fun complete(journey: JourneyPack, profile: JourneyProfile, today: LocalDate): Boolean =
        problems(journey.onboarding.fields, profile.values, today).isEmpty()

    /** The longest variable value the SDK sends. */
    const val VALUE_MAX = 200
}

/** One day of the first-call picker: the few times offered on it. */
data class PickerDay(val date: LocalDate, val times: List<Instant>)

/** The first-call picker: its days, the day and time chosen, and how booking went. */
data class PickerState(
    val days: List<PickerDay>,
    val day: LocalDate?,
    val time: Instant?,
    val busy: Boolean = false,
    val error: String? = null,
) {
    val times: List<Instant> get() = days.firstOrNull { it.date == day }?.times.orEmpty()
}

object Picker {
    /**
     * The picker's days — from today, [dayCount] of them within the agent's days ahead — and on each, the few times
     * offered: of every time the agent's rules allow that day ([slots], the SDK's bookingSlots), those at the pack's
     * preferred times of day, outside the user's quiet hours. A day where none of them is allowed offers a few of
     * its own times instead, spread out, so a narrow calling window still has times.
     */
    fun days(
        today: LocalDate,
        dayCount: Int,
        maxDaysAhead: Int,
        preferred: List<String>,
        quietStart: String,
        quietEnd: String,
        zone: TimeZone,
        slots: (LocalDate) -> List<Instant>,
    ): List<PickerDay> {
        val count = dayCount.coerceAtMost(maxDaysAhead + 1).coerceAtLeast(1)
        return (0 until count).map { i ->
            val date = today.plus(i, DateTimeUnit.DAY)
            val open = slots(date).filter { !JourneyTime.quiet(JourneyTime.of(it, zone), quietStart, quietEnd) }
            val byTime = open.associateBy { JourneyTime.of(it, zone) }
            val chosen = preferred.mapNotNull { byTime[it] }
            PickerDay(date, chosen.ifEmpty { spread(open, preferred.size.coerceAtLeast(1), zone) })
        }
    }

    /** Up to [n] of [slots], on the hour or half hour, at least 90 minutes apart. */
    fun spread(slots: List<Instant>, n: Int, zone: TimeZone): List<Instant> {
        val out = mutableListOf<Instant>()
        for (s in slots.sorted()) {
            if (JourneyTime.minutes(JourneyTime.of(s, zone)) % 30 != 0) continue
            if (out.isNotEmpty() && (s - out.last()).inWholeMinutes < 90) continue
            out += s
            if (out.size == n) break
        }
        return out
    }

    /** The first day with a time, and nothing chosen yet. */
    fun open(days: List<PickerDay>): PickerState = PickerState(days, day = days.firstOrNull { it.times.isNotEmpty() }?.date ?: days.firstOrNull()?.date, time = null)
}

/** A check-in reminder: shown by the phone at [at] (epoch ms); [id] says which item, on which date. */
data class Reminder(val id: String, val at: Long, val title: String, val body: String)

object CheckInSchedule {
    /** Never more reminders on the phone at once than this (iOS keeps 64 an app). */
    const val MAX = 48

    /**
     * The reminders a plan makes over the next [days] days: for each item of the pack's check-in kinds, [afterMinutes]
     * after its time — none in the past, and none inside the user's quiet hours. None with reminders off.
     */
    fun build(
        plan: Plan?,
        journey: JourneyPack,
        settings: JourneySettings,
        now: Instant,
        zone: TimeZone,
        days: Int = 7,
    ): List<Reminder> {
        if (plan == null || !settings.reminders) return emptyList()
        val today = now.toLocalDateTime(zone).date
        val c = journey.checkIns
        val out = mutableListOf<Reminder>()
        for (i in 0 until days) {
            val date = today.plus(i, DateTimeUnit.DAY)
            val day = Weekday.of(date)
            for (item in plan.items(day).filter { it.kind in c.kinds }) {
                val at = JourneyTime.at(date, item.time, zone).plus(c.afterMinutes.toLong(), DateTimeUnit.MINUTE, zone)
                if (at <= now) continue
                if (JourneyTime.quiet(JourneyTime.of(at, zone), settings.quietStart, settings.quietEnd)) continue
                out += Reminder(
                    id = id(date, item.time),
                    at = at.toEpochMilliseconds(),
                    title = JourneyText.fill(c.title, journey, item, day).replaceFirstChar { it.uppercase() },
                    body = JourneyText.fill(c.body, journey, item, day),
                )
            }
        }
        return out.sortedBy { it.at }.take(MAX)
    }

    /** "2026-10-27T08:00": the item at 08:00 on that date. */
    fun id(date: LocalDate, time: String): String = "${date}T$time"

    fun parse(id: String): Pair<LocalDate, String>? {
        val parts = id.split('T')
        if (parts.size != 2) return null
        val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return null
        val time = JourneyTime.normalise(parts[1]) ?: return null
        return date to time
    }
}

/** What Today and Progress show, from the plan and what the user did. */
object JourneyStats {
    data class Count(val done: Int, val of: Int) {
        /** 0–100; null with nothing due. */
        val percent: Int? get() = if (of == 0) null else (done * 100 + of / 2) / of
    }

    /** Today's items done, of today's items. */
    fun today(plan: Plan?, activity: JourneyActivity, today: LocalDate): Count {
        val items = plan?.items(Weekday.of(today)).orEmpty()
        return Count(items.count { activity.followed(today, it.time) == true }, items.size)
    }

    /** Items followed over the last 7 days, of the items due by now (today's only once their time has come). */
    fun week(plan: Plan?, activity: JourneyActivity, today: LocalDate, nowTime: String): Count {
        if (plan == null) return Count(0, 0)
        var done = 0
        var due = 0
        for (i in 0..6) {
            val date = today.minus(i, DateTimeUnit.DAY)
            for (item in plan.items(Weekday.of(date))) {
                if (date == today && item.time > nowTime) continue
                due++
                if (activity.followed(date, item.time) == true) done++
            }
        }
        return Count(done, due)
    }

    /** Days in a row with at least one item followed, to today — or to yesterday while today has none yet. */
    fun streak(activity: JourneyActivity, today: LocalDate): Int {
        val days = activity.checkIns.filter { it.followed }.map { it.date }.toSet()
        var date = if (today in days) today else today.minus(1, DateTimeUnit.DAY)
        var n = 0
        while (date in days) {
            n++
            date = date.minus(1, DateTimeUnit.DAY)
        }
        return n
    }

    /** The next item after [nowTime] today, else the first of the next day that has one. */
    fun upNext(plan: Plan?, today: LocalDate, nowTime: String): Pair<LocalDate, PlanItem>? {
        if (plan == null) return null
        plan.items(Weekday.of(today)).firstOrNull { it.time >= nowTime }?.let { return today to it }
        for (i in 1..7) {
            val date = today.plus(i, DateTimeUnit.DAY)
            plan.items(Weekday.of(date)).firstOrNull()?.let { return date to it }
        }
        return null
    }

    /** The Monday of [date]'s week: the weekly check-in's key. */
    fun weekOf(date: LocalDate): LocalDate = date.minus(date.dayOfWeek.ordinal, DateTimeUnit.DAY)
}

/** The pack's templates, filled: {kind}, {title}, {time}, {day}, {days}, {actions}. */
object JourneyText {
    fun fill(template: String, journey: JourneyPack, item: PlanItem, day: Weekday): String = template
        .replace("{kind}", journey.plan.kindLabel(item.kind).lowercase())
        .replace("{title}", item.title)
        .replace("{time}", JourneyTime.spoken(item.time))
        .replace("{day}", day.label)

    fun planLine(template: String, plan: Plan): String =
        template.replace("{days}", plan.days.size.toString()).replace("{actions}", plan.itemCount.toString())

    /** A figure with its unit: "1700 kcal", "2.5 L". */
    fun figure(value: Double, unit: String): String = (Args.numberText(round1(value)) + if (unit.isBlank()) "" else " $unit").trim()

    fun round1(v: Double): Double = kotlin.math.round(v * 10) / 10
}
