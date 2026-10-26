package ai.graine.demo.journey

/**
 * THE USER'S PLAN — a week of days, each with its timed items, and the day's targets — as the coach saves it through
 * the plan action and the app keeps it. Generic: an item is a time, a kind, a title, details, why it fits, up to three
 * swaps and its figures; the pack names the kinds and figures (`plan.kinds`, `plan.metrics`, `plan.targets`).
 */
data class Plan(
    val title: String,
    /** The day's targets by their key in the plan ("kcal" → 1700.0), in the order the agent sent them. */
    val targets: Map<String, Double>,
    /** The days that have items, Monday first. */
    val days: List<PlanDay>,
    val savedAt: Long,
    val source: Source,
) {
    enum class Source(val wire: String) {
        /** Saved by the coach, through the plan action. */
        AGENT("agent"),
        /** The pack's sample, loaded for testing without the agent. */
        SAMPLE("sample");

        companion object {
            fun of(wire: String?): Source = entries.firstOrNull { it.wire == wire } ?: AGENT
        }
    }

    val itemCount: Int get() = days.sumOf { it.items.size }

    fun day(day: Weekday): PlanDay? = days.firstOrNull { it.day == day }

    fun items(day: Weekday): List<PlanItem> = day(day)?.items.orEmpty()

    fun item(day: Weekday, time: String): PlanItem? = items(day).firstOrNull { it.time == time }

    /** The plan with one item replaced (the first at that time on that day). */
    fun replace(day: Weekday, time: String, next: PlanItem): Plan = copy(
        days = days.map { d ->
            if (d.day != day) d else {
                var done = false
                d.copy(items = d.items.map { if (!done && it.time == time) { done = true; next } else it }.sortedBy { it.time })
            }
        },
    )

    /** What the plan action's reader answers and the store keeps: the agent's own shape, [day] alone when given. */
    fun toWire(day: Weekday? = null, withMeta: Boolean = false): Map<String, Any?> = linkedMapOf<String, Any?>(
        "title" to title,
        "daily_targets" to LinkedHashMap(targets),
        "days" to days.filter { day == null || it.day == day }.map { it.toWire() },
    ).apply {
        if (withMeta) {
            put("saved_at", savedAt)
            put("source", source.wire)
        }
    }

    companion object {
        const val TITLE_MAX = 120
        const val TEXT_MAX = 300
        const val SWAP_MAX = 120
        const val SWAPS_MAX = 3
        const val ITEMS_PER_DAY_MAX = 16
        const val FIGURES_MAX = 8

        /** The keys an item's own fields use; any other numeric key is one of its figures. */
        private val ITEM_KEYS = setOf("time", "kind", "title", "details", "why", "swaps", "swapped_from", "swap_reason")

        /** A stored plan (toWire(withMeta = true)), or null when it cannot be read. */
        fun fromStored(m: Map<String, Any?>): Plan? = when (val r = parse(m, savedAt = Args.number(m["saved_at"])?.toLong() ?: 0L, Source.of(m["source"] as? String))) {
            is Parsed.Ok -> r.plan
            is Parsed.Refused -> null
        }

        /**
         * The plan action's arguments, read: a title, `daily_targets`, and `days` — each `{day, items}`, every item
         * with a time, a kind and a title. What cannot be read is left out and said in [Parsed.Ok.notes]; a plan
         * with nothing usable is refused, with what to send instead.
         */
        fun parse(args: Map<String, Any?>, savedAt: Long, source: Source = Source.AGENT): Parsed {
            val title = Args.string(args["title"]) ?: return Parsed.Refused("The plan needs a title. Send the whole week again with a title.")
            val notes = mutableListOf<String>()
            val targets = LinkedHashMap<String, Double>()
            Args.map(args["daily_targets"])?.forEach { (k, v) -> Args.number(v)?.let { if (targets.size < FIGURES_MAX) targets[k] = it } }

            val rawDays = Args.list(args["days"])
                ?: return Parsed.Refused("The plan needs its days: a list of {day, items}, Monday first. Send the whole week again.")
            val byDay = LinkedHashMap<Weekday, List<PlanItem>>()
            var skippedDays = 0
            var skippedItems = 0
            var cut = 0
            for (raw in rawDays) {
                val d = Args.map(raw)
                val day = d?.let { Args.weekday(it["day"]) }
                if (d == null || day == null) { skippedDays++; continue }
                val items = mutableListOf<PlanItem>()
                for (rawItem in Args.list(d["items"]).orEmpty()) {
                    val item = Args.map(rawItem)?.let { item(it) }
                    if (item == null) skippedItems++ else items += item
                }
                if (items.size > ITEMS_PER_DAY_MAX) { cut += items.size - ITEMS_PER_DAY_MAX }
                if (day in byDay) notes += "${day.label} was sent twice; the last one is kept."
                byDay[day] = items.sortedBy { it.time }.take(ITEMS_PER_DAY_MAX)
            }
            if (skippedDays > 0) notes += "$skippedDays day entr${if (skippedDays == 1) "y" else "ies"} without a valid day (mon … sun) left out."
            if (skippedItems > 0) notes += "$skippedItems item${if (skippedItems == 1) "" else "s"} without a valid time (HH:MM), kind and title left out."
            if (cut > 0) notes += "$cut item${if (cut == 1) "" else "s"} over $ITEMS_PER_DAY_MAX a day left out."
            val days = Weekday.entries.mapNotNull { w -> byDay[w]?.takeIf { it.isNotEmpty() }?.let { PlanDay(w, it) } }
            if (days.isEmpty()) {
                return Parsed.Refused("None of the plan's items could be read: each needs a time (HH:MM), a kind and a title. Send the whole week again.")
            }
            return Parsed.Ok(Plan(Args.clip(title, TITLE_MAX), targets, days, savedAt, source), notes)
        }

        /** One item, or null without a time, a kind and a title. */
        internal fun item(m: Map<String, Any?>): PlanItem? {
            val time = Args.time(m["time"]) ?: return null
            val kind = Args.string(m["kind"])?.lowercase()?.replace(' ', '_') ?: return null
            val title = Args.string(m["title"]) ?: return null
            val figures = LinkedHashMap<String, Double>()
            for ((k, v) in m) {
                if (k in ITEM_KEYS || figures.size >= FIGURES_MAX) continue
                Args.number(v)?.let { figures[k] = it }
            }
            return PlanItem(
                time = time,
                kind = Args.clip(kind, 40),
                title = Args.clip(title, TITLE_MAX),
                details = Args.string(m["details"])?.let { Args.clip(it, TEXT_MAX) },
                why = Args.string(m["why"])?.let { Args.clip(it, TEXT_MAX) },
                swaps = Args.strings(m["swaps"]).map { Args.clip(it, SWAP_MAX) }.distinct().take(SWAPS_MAX),
                figures = figures,
                swappedFrom = Args.string(m["swapped_from"])?.let { Args.clip(it, TITLE_MAX) },
                swapReason = Args.string(m["swap_reason"])?.let { Args.clip(it, TEXT_MAX) },
            )
        }
    }

    sealed class Parsed {
        data class Ok(val plan: Plan, val notes: List<String>) : Parsed()
        data class Refused(val message: String) : Parsed()
    }
}

data class PlanDay(val day: Weekday, val items: List<PlanItem>) {
    fun toWire(): Map<String, Any?> = linkedMapOf("day" to day.wire, "items" to items.map { it.toWire() })
}

data class PlanItem(
    /** "HH:MM", 24-hour. */
    val time: String,
    /** One of the pack's kinds (`plan.kinds`), or the agent's own word. */
    val kind: String,
    val title: String,
    val details: String? = null,
    val why: String? = null,
    val swaps: List<String> = emptyList(),
    /** Its figures by their key ("kcal" → 420.0); the pack labels them (`plan.metrics`). */
    val figures: Map<String, Double> = emptyMap(),
    /** What it was before a swap. */
    val swappedFrom: String? = null,
    val swapReason: String? = null,
) {
    fun toWire(): Map<String, Any?> = linkedMapOf<String, Any?>("time" to time, "kind" to kind, "title" to title).apply {
        details?.let { put("details", it) }
        why?.let { put("why", it) }
        if (swaps.isNotEmpty()) put("swaps", swaps)
        figures.forEach { (k, v) -> put(k, v) }
        swappedFrom?.let { put("swapped_from", it) }
        swapReason?.let { put("swap_reason", it) }
    }

    /**
     * This item, swapped for [title]: the old title becomes a swap the user can go back to, and what described the
     * old one (its details and figures) gives way to what came with the new one.
     */
    fun swappedFor(title: String, details: String?, figures: Map<String, Double>, reason: String?): PlanItem = copy(
        title = Args.clip(title.trim(), Plan.TITLE_MAX),
        details = details?.trim()?.takeIf { it.isNotEmpty() }?.let { Args.clip(it, Plan.TEXT_MAX) },
        figures = figures,
        swaps = (swaps.filter { !it.equals(title.trim(), ignoreCase = true) } + this.title).distinct().takeLast(Plan.SWAPS_MAX),
        swappedFrom = this.title,
        swapReason = reason?.trim()?.takeIf { it.isNotEmpty() }?.let { Args.clip(it, Plan.TEXT_MAX) },
    )
}
