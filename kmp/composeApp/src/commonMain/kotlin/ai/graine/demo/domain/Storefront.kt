package ai.graine.demo.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * THE STOREFRONT, as the screens draw it: the signed-in user's rows (their statuses as they are now — set by a tap or
 * by the agent, the same set_status either way) sorted into a store's parts by each row's `kind`, and what follows
 * from them: tomorrow's basket and its total, the buttons each part offers, the week strip, the header's line. Pure,
 * so the rules are tested without drawing anything (StorefrontTest). The words are the pack's; only the layout is here.
 */
object Storefront {
    const val SUBSCRIPTION = "subscription"
    const val ADDON = "addon"
    const val DELIVERY = "delivery"
    const val WALLET = "wallet"

    /** One row with its status now. */
    data class Line(val row: UseCasePack.Row, val status: String?)

    data class View(
        val subscriptions: List<Line>,
        val addOns: List<Line>,
        val deliveries: List<Line>,
        val wallet: Line?,
        /** What arrives next: subscriptions still delivering, and add-ons in the basket. */
        val basket: List<Line>,
        /** Subscriptions that will not come next time: skipped or paused. */
        val notComing: List<Line>,
        /** The basket's total in the pack's own currency ("₹208"), or null when nothing in it has a price. */
        val total: String?,
    ) {
        /** Nothing is coming next time (every subscription paused or skipped, and no add-on in the basket). */
        val nothingComing: Boolean get() = basket.isEmpty()
    }

    fun view(pack: UseCasePack, state: ItemsState): View? {
        val store = pack.store ?: return null
        val lines = state.rows.map { Line(it, state.statusOf(it)) }
        val subscriptions = lines.filter { it.row.kind == SUBSCRIPTION }
        val addOns = lines.filter { it.row.kind == ADDON }
        val delivering = subscriptions.filter { delivering(it, pack, store) }
        val inBasket = addOns.filter { inBasket(it, pack, store) }
        val basket = delivering + inBasket
        return View(
            subscriptions = subscriptions,
            addOns = addOns,
            deliveries = lines.filter { it.row.kind == DELIVERY },
            wallet = lines.firstOrNull { it.row.kind == WALLET },
            basket = basket,
            notComing = subscriptions - delivering.toSet(),
            total = total(basket.mapNotNull { it.row.price }),
        )
    }

    /** A subscription delivers next time unless it is paused or skipped. */
    fun delivering(line: Line, pack: UseCasePack, store: UseCasePack.Store): Boolean {
        val stopped = setOfNotNull(pack.action(store.roles.pause)?.status, pack.action(store.roles.skip)?.status)
        return line.status !in stopped
    }

    /** An add-on is in the basket while it has the add action's status. */
    fun inBasket(line: Line, pack: UseCasePack, store: UseCasePack.Store): Boolean =
        pack.action(store.roles.add)?.status?.let { it == line.status } ?: false

    /**
     * A subscription's buttons: paused, only Resume; skipped, Resume (undoing the skip) and Pause; delivering, Skip and
     * Pause. Only actions the row offers — the app refuses any other, exactly as it refuses the agent.
     */
    fun subscriptionButtons(line: Line, pack: UseCasePack): List<UseCasePack.Action> {
        val store = pack.store ?: return emptyList()
        val r = store.roles
        val pause = pack.action(r.pause)
        val skip = pack.action(r.skip)
        val names = when (line.status) {
            pause?.status -> listOf(r.resume)
            skip?.status -> listOf(r.resume, r.pause)
            else -> listOf(r.skip, r.pause)
        }
        return names.filter { it in line.row.actions }.mapNotNull { pack.action(it) }
    }

    /** An add-on's one button: Remove while it is in the basket, else Add. */
    fun addOnButton(line: Line, pack: UseCasePack): UseCasePack.Action? {
        val store = pack.store ?: return null
        val name = if (inBasket(line, pack, store)) store.roles.remove else store.roles.add
        return name.takeIf { it in line.row.actions }?.let { pack.action(it) }
    }

    /** A delivery's one button: Report, until it is reported. */
    fun deliveryButton(line: Line, pack: UseCasePack): UseCasePack.Action? {
        val store = pack.store ?: return null
        val report = pack.action(store.roles.report) ?: return null
        return report.takeIf { line.status != it.status && it.name in line.row.actions }
    }

    /**
     * A status chip's colour. A status a tap or the agent set has its role's: delivering again or in the basket
     * positive, paused or skipped warning, reported info, anything else neutral. The pack's starting status has the
     * row's own statusTone ("Missed" negative, "Low balance" warning), neutral without one.
     */
    fun tone(line: Line, pack: UseCasePack): Tone {
        val store = pack.store ?: return Tone.NEUTRAL
        val r = store.roles
        if (line.status != line.row.status) {
            return when (line.status) {
                pack.action(r.pause)?.status, pack.action(r.skip)?.status -> Tone.WARNING
                pack.action(r.report)?.status -> Tone.INFO
                pack.action(r.add)?.status, pack.action(r.resume)?.status -> Tone.POSITIVE
                else -> Tone.NEUTRAL
            }
        }
        return when (line.row.statusTone) {
            "positive" -> Tone.POSITIVE
            "warning" -> Tone.WARNING
            "negative" -> Tone.NEGATIVE
            "info" -> Tone.INFO
            else -> Tone.NEUTRAL
        }
    }

    /** The add-ons a category chip shows: all of them for no chip, else that category's. */
    fun addOnsIn(view: View, category: String?): List<Line> =
        if (category == null) view.addOns else view.addOns.filter { it.row.category == category }

    /** The categories that have an add-on for this user, in the pack's order: a chip with nothing behind it is not shown. */
    fun categoriesWithAddOns(pack: UseCasePack, view: View): List<UseCasePack.Store.Category> =
        pack.store?.categories.orEmpty().filter { c -> view.addOns.any { it.row.category == c.id } }

    /** "Green Park, Delhi": the store's area trait and the city trait, as many as the user has. */
    fun deliveringTo(pack: UseCasePack, user: UseCasePack.User): String? {
        val area = pack.store?.areaTrait?.let { user.traits[it] }
        return listOfNotNull(area, user.traits["city"]).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { null }
    }

    // ── amounts ─────────────────────────────────────────────────────────────

    /** The whole number in a display amount ("₹1,120" → 1120), or null when there is none. */
    fun amountOf(text: String): Long? = text.filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.toLongOrNull()

    /**
     * The sum of display amounts, written like them: the first one's currency mark and its digit grouping ("₹68" +
     * "₹50" + "₹90" → "₹208"; "₹1,120" style grouping for four digits and more). Null for none.
     */
    fun total(prices: List<String>): String? {
        val amounts = prices.mapNotNull { amountOf(it) }
        if (amounts.isEmpty()) return null
        val first = prices.first { amountOf(it) != null }
        val mark = first.takeWhile { !it.isDigit() }
        return mark + grouped(amounts.sum())
    }

    private fun grouped(n: Long): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

    // ── the week strip ──────────────────────────────────────────────────────

    /** One day of the strip. [next]: the next delivery day (tomorrow); [nothing]: nothing comes that day. */
    data class Day(val date: LocalDate, val weekday: String, val today: Boolean, val next: Boolean, val nothing: Boolean)

    /** Seven days from [today]; tomorrow is the next delivery, and shows nothing coming when the basket is empty. */
    fun week(today: LocalDate, view: View): List<Day> = (0 until 7).map { i ->
        val date = today.plus(i, DateTimeUnit.DAY)
        Day(date, short(date.dayOfWeek), today = i == 0, next = i == 1, nothing = i == 1 && view.nothingComing)
    }

    private fun short(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Mon"
        DayOfWeek.TUESDAY -> "Tue"
        DayOfWeek.WEDNESDAY -> "Wed"
        DayOfWeek.THURSDAY -> "Thu"
        DayOfWeek.FRIDAY -> "Fri"
        DayOfWeek.SATURDAY -> "Sat"
        DayOfWeek.SUNDAY -> "Sun"
        else -> day.name.take(3)
    }
}
