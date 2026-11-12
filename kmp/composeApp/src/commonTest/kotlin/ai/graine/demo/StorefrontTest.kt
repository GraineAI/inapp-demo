package ai.graine.demo

import ai.graine.demo.domain.AppConfig
import ai.graine.demo.domain.DemoActionResult
import ai.graine.demo.domain.DemoScreen
import ai.graine.demo.domain.ItemsState
import ai.graine.demo.domain.Storefront
import ai.graine.demo.domain.Tone
import ai.graine.demo.domain.UseCasePack
import ai.graine.demo.domain.UseCasePacks
import ai.graine.demo.ui.AppModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * THE STOREFRONT (the Graine Store pack's `store`): how its rows sort into a store's parts, what tomorrow's basket holds
 * and costs, which buttons each part offers, and that a storefront tap and the agent's action are one and the same.
 * Read from the pack the app ships, so the pack and the screens cannot drift apart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StorefrontTest {
    private val store: UseCasePack = UseCasePacks.load().first { it.id == "retail-shopping" }
    private fun user(id: String) = store.users.first { it.id == id }
    private fun state(userId: String, statuses: Map<String, String> = emptyMap()) = ItemsState(store.rowsFor(user(userId)), statuses)
    private fun view(userId: String, statuses: Map<String, String> = emptyMap()) = assertNotNull(Storefront.view(store, state(userId, statuses)))
    private fun ids(lines: List<Storefront.Line>) = lines.map { it.row.id }

    @Test
    fun onlyTheStorePackHasAStorefrontAndItsRolesAreItsOwnStatusActions() {
        val packs = UseCasePacks.load()
        assertEquals(listOf("retail-shopping"), packs.filter { it.store != null }.map { it.id }, "the other packs keep the generic screens")
        val s = assertNotNull(store.store)
        assertEquals("storefront", s.layout)
        for (name in listOf(s.roles.add, s.roles.remove, s.roles.skip, s.roles.pause, s.roles.resume, s.roles.report)) {
            val action = assertNotNull(store.action(name), name)
            assertEquals(UseCasePack.Effect.SET_STATUS, action.knownEffect, name)
        }
        assertTrue(store.items.rows.all { it.kind in setOf("subscription", "addon", "delivery", "wallet") }, "every row is a part of the store")
        assertEquals(listOf("dairy", "bakery", "fruits", "drinks"), s.categories.map { it.id })
        assertNull(Storefront.view(packs.first { it.id == "health-coach" }, ItemsState(emptyList())), "no storefront, no view")
    }

    @Test
    fun tomorrowsBasketIsTheDeliveringSubscriptionsAndTheAddOnsInIt() {
        val v = view("SHP-20031")
        assertEquals(listOf("milk-ananya", "curd-ananya"), ids(v.subscriptions))
        assertEquals(listOf("milk-ananya", "curd-ananya", "bread-ananya"), ids(v.basket))
        assertEquals("₹163", v.total, "₹68 + ₹45 + ₹50")
        assertEquals(listOf("today-ananya"), ids(v.deliveries))
        assertEquals("wallet-ananya", v.wallet?.row?.id)
        assertTrue(v.notComing.isEmpty())
        assertEquals(setOf("bread-ananya", "paneer-ananya", "coconut-ananya", "bananas-ananya"), ids(v.addOns).toSet())
    }

    @Test
    fun skippingAndPausingTakeASubscriptionOutOfTomorrowAndResumingPutsItBack() {
        val skipped = view("SHP-20031", mapOf("milk-ananya" to "Skipped tomorrow"))
        assertEquals(listOf("curd-ananya", "bread-ananya"), ids(skipped.basket))
        assertEquals(listOf("milk-ananya"), ids(skipped.notComing))
        assertEquals("₹95", skipped.total)
        val resumed = view("SHP-20031", mapOf("milk-ananya" to "Active"))
        assertEquals(listOf("milk-ananya", "curd-ananya", "bread-ananya"), ids(resumed.basket))
        // Kavya is away: her milk is paused, the weekly items still come.
        val kavya = view("SHP-20033")
        assertEquals(listOf("milk-kavya"), ids(kavya.notComing))
        assertEquals(listOf("yogurt-kavya", "bananas-kavya"), ids(kavya.basket))
        assertEquals("₹208", kavya.total)
    }

    @Test
    fun anAddOnTogglesBetweenAddAndRemove() {
        val v = view("SHP-20031", mapOf("paneer-ananya" to "In tomorrow's basket"))
        assertEquals(listOf("milk-ananya", "curd-ananya", "bread-ananya", "paneer-ananya"), ids(v.basket))
        assertEquals("₹253", v.total)
        val paneer = v.addOns.first { it.row.id == "paneer-ananya" }
        assertEquals("remove_from_basket", Storefront.addOnButton(paneer, store)?.name)
        val coconut = v.addOns.first { it.row.id == "coconut-ananya" }
        assertEquals("add_to_basket", Storefront.addOnButton(coconut, store)?.name)
        val removed = view("SHP-20031", mapOf("bread-ananya" to "Removed"))
        assertEquals(listOf("milk-ananya", "curd-ananya"), ids(removed.basket))
        assertEquals("add_to_basket", Storefront.addOnButton(removed.addOns.first { it.row.id == "bread-ananya" }, store)?.name)
    }

    @Test
    fun eachPartOffersItsButtons() {
        fun buttons(userId: String, rowId: String, statuses: Map<String, String> = emptyMap()) =
            Storefront.subscriptionButtons(view(userId, statuses).subscriptions.first { it.row.id == rowId }, store).map { it.name }
        assertEquals(listOf("skip_tomorrow", "pause_subscription"), buttons("SHP-20031", "milk-ananya"))
        assertEquals(listOf("resume_subscription"), buttons("SHP-20033", "milk-kavya"), "paused: only Resume")
        assertEquals(listOf("resume_subscription", "pause_subscription"), buttons("SHP-20031", "milk-ananya", mapOf("milk-ananya" to "Skipped tomorrow")))
        val missed = view("SHP-20032").deliveries.single()
        assertEquals("report_issue", Storefront.deliveryButton(missed, store)?.name)
        val reported = view("SHP-20032", mapOf("yesterday-vikram" to "Problem reported")).deliveries.single()
        assertNull(Storefront.deliveryButton(reported, store), "reported once")
    }

    @Test
    fun aChipsColourIsTheRowsOwnUntilATapOrTheAgentChangesIt() {
        val vikram = view("SHP-20032")
        assertEquals(Tone.NEGATIVE, Storefront.tone(vikram.deliveries.single(), store), "Missed")
        assertEquals(Tone.WARNING, Storefront.tone(assertNotNull(vikram.wallet), store), "Low balance")
        val changed = view("SHP-20031", mapOf("milk-ananya" to "Skipped tomorrow", "today-ananya" to "Problem reported"))
        assertEquals(Tone.WARNING, Storefront.tone(changed.subscriptions.first { it.row.id == "milk-ananya" }, store))
        assertEquals(Tone.INFO, Storefront.tone(changed.deliveries.single(), store))
    }

    @Test
    fun categoriesShowOnlyWhereThereIsSomethingToAdd() {
        val v = view("SHP-20032")
        assertEquals(listOf("dairy", "bakery", "drinks"), Storefront.categoriesWithAddOns(store, v).map { it.id })
        assertEquals(listOf("paneer-vikram"), ids(Storefront.addOnsIn(v, "dairy")))
        assertEquals(ids(v.addOns), ids(Storefront.addOnsIn(v, null)))
    }

    @Test
    fun amountsAddUpInThePacksOwnCurrency() {
        assertEquals(1120L, Storefront.amountOf("₹1,120"))
        assertNull(Storefront.amountOf("free"))
        assertEquals("₹1,620", Storefront.total(listOf("₹1,120", "₹500")))
        assertEquals("₹50", Storefront.total(listOf("₹50", "n/a")))
        assertNull(Storefront.total(emptyList()))
    }

    @Test
    fun theWeekStartsTodayAndMarksTomorrowAsTheNextDelivery() {
        val today = LocalDate(2026, 11, 12)
        val week = Storefront.week(today, view("SHP-20031"))
        assertEquals(7, week.size)
        assertEquals(listOf("Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed"), week.map { it.weekday })
        assertTrue(week[0].today && week[1].next && !week[1].nothing)
        val nothing = view("SHP-20033", mapOf("yogurt-kavya" to "Skipped tomorrow", "bananas-kavya" to "Paused"))
        assertTrue(nothing.nothingComing)
        assertTrue(Storefront.week(today, nothing)[1].nothing, "nothing comes tomorrow")
    }

    @Test
    fun theHeaderSaysWhereDeliveriesGo() {
        assertEquals("Green Park, Delhi", Storefront.deliveringTo(store, user("SHP-20031")))
        assertEquals("Indiranagar, Bengaluru", Storefront.deliveringTo(store, user("SHP-20033")))
    }

    // ── a tap and the agent are the same action ─────────────────────────────

    private fun TestScope.storeModel(fake: FakeGraineClient = FakeGraineClient()): Pair<AppModel, FakeGraineClient> =
        AppModel(
            fake, AppConfig.of("https://www.graine.ai", "pk_test_demo"), listOf(store), MemoryStore(),
            scope = this as CoroutineScope, now = { TestClock.at(12) }, zone = TestClock.zone, collectors = backgroundScope,
        ) to fake

    @Test
    fun aStorefrontTapAndTheAgentChangeTheSameStatus() = runTest {
        val (m, fake) = storeModel()
        m.signIn(user("SHP-20031"), memory = true)
        advanceUntilIdle()
        val rows = assertNotNull(m.items).rows
        // The storefront's Skip tomorrow: the pack's action on the row.
        m.press(assertNotNull(store.action("skip_tomorrow")), rows.first { it.id == "milk-ananya" })
        assertEquals("Skipped tomorrow", m.items?.statusOf(rows.first { it.id == "milk-ananya" }))
        // The agent resumes it by the name it heard, and adds paneer.
        assertEquals(DemoActionResult.Status.OK, m.perform("resume_subscription", mapOf("item" to "Cow milk, 1 L")).status)
        assertEquals(DemoActionResult.Status.OK, m.perform("add_to_basket", mapOf("item" to "paneer")).status)
        val v = assertNotNull(Storefront.view(store, assertNotNull(m.items)))
        assertEquals(listOf("milk-ananya", "curd-ananya", "bread-ananya", "paneer-ananya"), ids(v.basket))
        // A delivered item cannot be skipped: the app refuses the agent as it would a button.
        assertEquals(DemoActionResult.Status.REFUSED, m.perform("skip_tomorrow", mapOf("item" to "today's delivery")).status)
    }

    @Test
    fun theStoreHomeTellsTheAgentWhatItShows() = runTest {
        val (m, fake) = storeModel()
        m.signIn(user("SHP-20031"), memory = true)
        advanceUntilIdle()
        m.open(DemoScreen.HOME)
        val context = assertNotNull(fake.lastScreen)
        assertEquals("home", context.screen)
        val fields = context.fields.associate { it.name to it.value }
        assertEquals("Active", fields["milk-ananya"], "the basket, as on the list screen")
        assertEquals("About 3 days left", fields["wallet-ananya"])
        assertTrue("memory" in fields && "next_call" in fields, "and what every Home reports")
        m.perform("skip_tomorrow", mapOf("item" to "Cow milk, 1 L"))
        assertEquals("Skipped tomorrow", fake.lastScreen?.fields?.first { it.name == "milk-ananya" }?.value, "updated at once")
    }
}
