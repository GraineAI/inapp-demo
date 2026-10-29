package ai.graine.demo.domain

import ai.graine.inapp.kmp.GraineComponent
import ai.graine.inapp.kmp.GraineComponentButton
import ai.graine.inapp.kmp.GraineComponentDensity
import ai.graine.inapp.kmp.GraineComponentSelect
import ai.graine.inapp.kmp.GraineComponentStyle

/**
 * A COMPONENT THE AGENT DREW, READ FOR THE APP'S OWN WIDGETS — from what the
 * SDK's renderer reads (`GraineComponent`: its kind, `data`, `fallbackText` and,
 * from SDK 0.14.8, `style`, the look the dashboard gave it) to what to draw, and
 * what each control answers. The answers have the same shape the SDK's own views
 * send (`Graine.chat.answerWidget(id, data, summary)`), so the agent — and the
 * webhooks behind its tools — hear the same thing whichever screen drew it:
 *
 *   chips      `choices` (data.options) and `quick_actions` (data.actions); several at once — "Select all
 *              that apply" — where its look says `select` MANY, sent as one answer
 *   rows       `product_list`, `plan_comparison` (data.plans), `info_card`, and any
 *              kind carrying data.items; one tap, or several where its look says
 *              `select` MANY
 *   calendar   `calendar_slots`: days and their times
 *   form       `lead_form`: data.fields, answered as the website's form answers
 *   fallback   anything else: the component's own sentence (fallbackText)
 */
sealed class OwnWidget {
    abstract val title: String
    /** The line under the title: the component's prompt, or a calendar's note. */
    abstract val prompt: String

    data class Chip(val id: String, val label: String)

    data class Row(
        val id: String,
        val title: String,
        val subtitle: String,
        val price: String,
        val facts: List<String>,
        val unavailable: Boolean,
        /** The row's own button, else the component's. */
        val selectLabel: String,
    )

    data class Day(val label: String, val slots: List<String>)

    data class Field(val name: String, val label: String, val type: String, val placeholder: String, val required: Boolean)

    /** How rows answer: a plan, an info card's one action, or an item. */
    enum class Shape { PLANS, CARD, ITEMS }

    data class Chips(
        override val title: String,
        override val prompt: String,
        val chips: List<Chip>,
        val quickActions: Boolean,
        /** Several may be ticked and sent as one answer (`component.style.select` MANY, with more than one chip). */
        val many: Boolean = false,
        /** The most that may be ticked: `component.style.maxSelect`, never more than there are. */
        val maxSelect: Int = chips.size,
        /** What the one button says, and leads the sentence ("Done" → "Cravings and Late dinners"). */
        val selectLabel: String = "",
    ) : OwnWidget()

    data class Rows(
        override val title: String,
        override val prompt: String,
        val rows: List<Row>,
        val selectLabel: String,
        val shape: Shape,
        /** Several may be chosen and sent as one answer (`component.style.select` MANY; never an info card). */
        val many: Boolean,
        /** The most that may be chosen: `component.style.maxSelect`, never more than there are. */
        val maxSelect: Int,
        /** An info card's action, what its one button says. */
        val actionLabel: String?,
    ) : OwnWidget()

    data class Calendar(override val title: String, override val prompt: String, val days: List<Day>) : OwnWidget()

    data class Form(override val title: String, override val prompt: String, val fields: List<Field>, val submitLabel: String) : OwnWidget()

    data class Fallback(override val title: String, override val prompt: String, val text: String) : OwnWidget()
}

/** What a control sends: the structured answer (for the agent's webhook) and the sentence that is the user's turn. */
data class OwnAnswer(val data: Map<String, Any?>, val summary: String)

/**
 * HOW THE APP'S OWN CARD FOLLOWS A COMPONENT'S LOOK — the dashboard's Look & feel (`component.style`, SDK 0.14.8)
 * where it applies to a card in the thread, as numbers (dp) and choices the card draws with. The card stays a card
 * whatever the look's surface, and the app draws no pictures, so the picture size has nothing to size here.
 */
data class OwnLook(
    /** The accent, `#RRGGBB`; null: the app's own (its theme's primary colour). */
    val accent: String?,
    /** The card's corners: the component's radius, else the app's 14. */
    val cardRadius: Int,
    /** The controls' corners when the component sets a radius; null: the app's own shapes. */
    val controlRadius: Int?,
    /** The room inside the card, and between its parts (rows, chips, times): tighter when the look is COMPACT. */
    val padding: Int,
    val gap: Int,
    /** An item's button: filled with the accent, an outline in it, or none — the whole row is tapped. */
    val button: GraineComponentButton,
    /** Rows two to a line: the look's two columns, where there are two rows or more to lay out. */
    val grid: Boolean,
    /** Each row's fact lines. */
    val facts: Boolean,
)

/**
 * THE USER'S ANSWER, KEPT ON SCREEN UNTIL THE AGENT ANSWERS IT. The card goes the moment the user answers
 * (`chat.answerWidget`); `agent.componentAwaitingReply` (SDK 0.14.8) is true from then until the agent starts its
 * reply — its first words, its voice, its next component — at most 12 seconds. Where the card was, the screen shows
 * what the user chose and a loader for that long ([line]), then nothing. Each screen keeps its own.
 */
data class OwnAnswered(val chose: String? = null, val awaiting: Boolean = false) {
    /** What the user chose, while the agent is answering it; null: no line. */
    val line: String? get() = if (awaiting) chose else null

    /** The user answered with [summary], the sentence that is their turn. A blank one starts no wait: nothing to keep. */
    fun answered(summary: String): OwnAnswered = copy(chose = summary.trim().ifEmpty { null })

    /** `componentAwaitingReply` is [now]. False ends it: the agent's reply started, or there is nothing to wait for. */
    fun awaiting(now: Boolean): OwnAnswered = if (now) copy(awaiting = true) else OwnAnswered()

    /** A component is on screen: what was chosen before is history. */
    fun componentShown(): OwnAnswered = copy(chose = null)
}

object OwnComponents {
    private val ROW_KINDS = setOf("product_list", "products", "plan_comparison", "info_card")

    /** What the app draws for [component]. */
    fun read(component: GraineComponent): OwnWidget {
        val d = component.data
        val kind = component.kind?.trim().orEmpty()
        val title = text(d["title"]) ?: component.title?.trim().orEmpty()
        val prompt = text(d["prompt"]).orEmpty()

        // Chips: the agent's own options, or quick_actions' actions.
        val options: List<Any?> = when {
            d["options"] is List<*> -> d["options"] as List<*>
            kind == "quick_actions" && d["actions"] is List<*> -> d["actions"] as List<*>
            else -> emptyList()
        }
        val quickActions = kind == "quick_actions" && options.isNotEmpty()
        if ((kind == "choices" || quickActions) && options.isNotEmpty()) {
            val chips = options.map { o ->
                val m = o as? Map<*, *>
                val label = str(first(m?.get("label"), m?.get("title")) ?: o)
                OwnWidget.Chip(id = str(first(m?.get("id")) ?: label), label = label)
            }
            // The look the SDK resolved (0.14.8): several where it says MANY and there is more than one to tick, at
            // most its maxSelect (2–8; 3 when the dashboard chose none).
            val look = component.style
            val many = look.select == GraineComponentSelect.MANY && chips.size > 1
            return OwnWidget.Chips(title, prompt, chips, quickActions, many, look.maxSelect.coerceAtMost(chips.size), text(d["selectLabel"]).orEmpty())
        }

        if (kind == "calendar_slots") {
            val days = (d["days"] as? List<*>).orEmpty().take(4).mapIndexed { i, day ->
                val m = day as? Map<*, *>
                OwnWidget.Day(
                    label = str(first(m?.get("label"), m?.get("id")) ?: "Day ${i + 1}"),
                    slots = (m?.get("slots") as? List<*>).orEmpty().map { str(it) }.filter { it.isNotBlank() }.take(8),
                )
            }.filter { it.slots.isNotEmpty() }
            if (days.isNotEmpty()) return OwnWidget.Calendar(title, text(d["note"]).orEmpty(), days)
        }

        val shape = when {
            kind == "plan_comparison" && d["plans"] is List<*> -> OwnWidget.Shape.PLANS
            kind == "info_card" && (truthy(d["title"]) || truthy(d["summary"])) -> OwnWidget.Shape.CARD
            else -> OwnWidget.Shape.ITEMS
        }
        val actionLabel = text(d["actionLabel"])
        val selectLabel = when (shape) {
            OwnWidget.Shape.PLANS -> "Select"
            OwnWidget.Shape.CARD -> actionLabel ?: "Tell me more"
            OwnWidget.Shape.ITEMS -> text(d["selectLabel"]) ?: "Choose"
        }
        val items: List<Map<*, *>> = when (shape) {
            OwnWidget.Shape.PLANS -> (d["plans"] as List<*>).map { p ->
                val m = p as? Map<*, *>
                val price = m?.get("price")
                mapOf(
                    "id" to m?.get("id"),
                    "title" to m?.get("name"),
                    "subtitle" to m?.get("badge"),
                    "price" to if (truthy(price)) str(price) + (m?.get("priceSuffix")?.let { str(it) } ?: "") else "",
                    "facts" to facts(m?.get("facts")),
                    "selectLabel" to m?.get("selectLabel"),
                )
            }
            OwnWidget.Shape.CARD -> listOf(
                mapOf(
                    "id" to "card",
                    "title" to d["title"],
                    "subtitle" to d["category"],
                    "facts" to (listOf(d["summary"]) + facts(d["facts"])).filter { truthy(it) }.map { str(it) },
                ),
            )
            OwnWidget.Shape.ITEMS -> (d["items"] as? List<*>).orEmpty().map { it as? Map<*, *> ?: emptyMap<String, Any?>() }
        }
        val rows = items.take(8).mapIndexed { i, m ->
            OwnWidget.Row(
                id = str(first(m["id"], m["title"]) ?: i),
                title = str(first(m["title"], m["id"]) ?: i),
                subtitle = if (truthy(m["subtitle"])) str(m["subtitle"]) else "",
                price = if (truthy(m["price"])) str(m["price"]) else "",
                facts = (m["facts"] as? List<*>).orEmpty().map { str(it) }.take(3),
                unavailable = m["unavailable"] == true,
                selectLabel = if (truthy(m["selectLabel"])) str(m["selectLabel"]) else selectLabel,
            )
        }
        if (rows.isNotEmpty() && (kind in ROW_KINDS || options.isEmpty())) {
            val look = component.style
            val many = look.select == GraineComponentSelect.MANY && shape != OwnWidget.Shape.CARD
            return OwnWidget.Rows(title, prompt, rows, selectLabel, shape, many, look.maxSelect.coerceAtMost(rows.size), actionLabel)
        }

        if (kind == "lead_form") {
            val fields = (d["fields"] as? List<*>).orEmpty().mapNotNull { f ->
                val m = f as? Map<*, *> ?: return@mapNotNull null
                val name = text(m["name"]) ?: return@mapNotNull null
                OwnWidget.Field(
                    name = name,
                    label = text(m["label"]) ?: name.replace('_', ' ').replaceFirstChar { it.uppercase() },
                    type = text(m["type"]) ?: "text",
                    placeholder = text(m["placeholder"]).orEmpty(),
                    required = m["required"] == true,
                )
            }
            if (fields.isNotEmpty()) return OwnWidget.Form(title, prompt, fields, text(d["submitLabel"]) ?: "Submit")
        }

        return OwnWidget.Fallback(title, prompt, component.fallbackText ?: "Reply in the chat to answer.")
    }

    /** How the app's card draws [widget] in its component's look, [style] (`component.style`). */
    fun look(style: GraineComponentStyle, widget: OwnWidget): OwnLook {
        val compact = style.density == GraineComponentDensity.COMPACT
        return OwnLook(
            accent = style.accent,
            cardRadius = style.radius ?: 14,
            controlRadius = style.radius,
            padding = if (compact) 10 else 14,
            gap = if (compact) 6 else 10,
            button = style.button,
            // Two columns only come with Gallery and Cards (the SDK resolves that); one row alone stays full width.
            grid = style.columns == 2 && widget is OwnWidget.Rows && widget.rows.size > 1,
            facts = style.facts,
        )
    }

    /** A chip tapped: quick_actions answer `{id, label}`, choices `{chosen, label}`. */
    fun chip(widget: OwnWidget.Chips, chip: OwnWidget.Chip): OwnAnswer = OwnAnswer(
        if (widget.quickActions) linkedMapOf("id" to chip.id, "label" to chip.label) else linkedMapOf("chosen" to chip.id, "label" to chip.label),
        "I'll go with ${chip.label}.",
    )

    /**
     * Several chips ticked — "Select all that apply" — as ONE answer, in the shape several rows answer with: every
     * pick (each as one tap would send it), the ids and labels flattened, how many, and one sentence naming them.
     */
    fun chips(widget: OwnWidget.Chips, ids: List<String>): OwnAnswer? {
        val picked = ids.mapNotNull { id -> widget.chips.firstOrNull { it.id == id } }
        if (picked.isEmpty()) return null
        return OwnAnswer(
            linkedMapOf(
                "items" to picked.map { chip(widget, it).data },
                "ids" to picked.joinToString(",") { it.id },
                "titles" to picked.joinToString(", ") { it.label },
                "count" to picked.size,
            ),
            manySummary(picked.map { it.label }, widget.selectLabel),
        )
    }

    /** One row's button. */
    fun row(widget: OwnWidget.Rows, row: OwnWidget.Row): OwnAnswer {
        val at = if (row.price.isNotEmpty()) " at ${row.price}" else ""
        return when (widget.shape) {
            OwnWidget.Shape.PLANS -> OwnAnswer(linkedMapOf("id" to row.id, "name" to row.title, "price" to row.price), "I'll take ${row.title}$at.")
            OwnWidget.Shape.CARD -> OwnAnswer(linkedMapOf("action" to (widget.actionLabel ?: "Tell me more")), widget.actionLabel ?: "Tell me more about this.")
            OwnWidget.Shape.ITEMS -> OwnAnswer(linkedMapOf("id" to row.id, "title" to row.title, "price" to row.price), "I'll take a look at ${row.title}$at.")
        }
    }

    /** Several rows, sent as ONE answer: every item, the ids and titles flattened, and one sentence naming them. */
    fun many(widget: OwnWidget.Rows, ids: List<String>): OwnAnswer? {
        val chosen = ids.mapNotNull { id -> widget.rows.firstOrNull { it.id == id } }
        if (chosen.isEmpty()) return null
        val titles = chosen.map { it.title }.filter { it.isNotEmpty() }
        return OwnAnswer(
            linkedMapOf(
                "items" to chosen.map { row(widget, it).data },
                "ids" to chosen.joinToString(",") { it.id },
                "titles" to titles.joinToString(", "),
                "count" to chosen.size,
            ),
            manySummary(titles, widget.selectLabel),
        )
    }

    /** A time tapped: the day and the slot. */
    fun slot(day: OwnWidget.Day, slot: String): OwnAnswer =
        OwnAnswer(linkedMapOf("day" to day.label, "slot" to slot), "${day.label} at $slot, please.")

    /** A form sent: every field by its name; the sentence lists what was filled in ("merchant: …, amount: …"). */
    fun form(widget: OwnWidget.Form, values: Map<String, String>): OwnAnswer {
        val data = LinkedHashMap<String, Any?>()
        widget.fields.forEach { data[it.name] = values[it.name]?.trim().orEmpty() }
        val summary = data.entries.filter { (it.value as String).isNotBlank() }
            .joinToString(", ") { "${it.key.replace('_', ' ')}: ${it.value}" }
        return OwnAnswer(data, summary.ifEmpty { widget.submitLabel })
    }

    /** The form can be sent: every required field has something in it. */
    fun formComplete(widget: OwnWidget.Form, values: Map<String, String>): Boolean =
        widget.fields.all { !it.required || !values[it.name].isNullOrBlank() }

    /** "Taken: Morning and Evening" — the label, then the titles as a person lists them. */
    fun manySummary(titles: List<String>, label: String): String {
        val names = titles.map { it.trim() }.filter { it.isNotEmpty() }
        if (names.isEmpty()) return ""
        val list = if (names.size == 1) names[0] else "${names.dropLast(1).joinToString(", ")} and ${names.last()}"
        val lead = label.trim()
        return if (lead.isEmpty()) list else "$lead: $list"
    }

    // ── values as the agent sent them ───────────────────────────────────────

    /** The first value that is there at all. */
    private fun first(vararg values: Any?): Any? = values.firstOrNull { it != null }

    /** Present and not empty: a non-blank string, a number, true, a list or a map. */
    private fun truthy(v: Any?): Boolean = when (v) {
        null -> false
        is String -> v.isNotEmpty()
        is Boolean -> v
        is Number -> v.toDouble() != 0.0
        else -> true
    }

    /** A value as words: a whole number without a decimal point. */
    private fun str(v: Any?): String = when (v) {
        null -> ""
        is String -> v
        is Double -> if (v.isFinite() && v == kotlin.math.floor(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()
        is Float -> str(v.toDouble())
        else -> v.toString()
    }

    private fun text(v: Any?): String? = (v as? String)?.trim()?.takeIf { it.isNotEmpty() }
        ?: (v as? Number)?.let { str(it) }

    /** Fact lines: `{label, value}` as "label value", else the value on its own. */
    private fun facts(v: Any?): List<String> = (v as? List<*>).orEmpty().map { f ->
        val m = f as? Map<*, *>
        val label = m?.get("label")
        if (truthy(label)) "${str(label)} ${str(m?.get("value"))}" else str(m?.get("value") ?: f.takeIf { m == null })
    }.filter { it.isNotBlank() }
}
