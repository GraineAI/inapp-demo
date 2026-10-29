package ai.graine.demo

import ai.graine.demo.domain.OwnComponents
import ai.graine.demo.domain.OwnWidget
import ai.graine.inapp.kmp.GraineComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "Select all that apply": a component whose look (`component.style`, as the SDK resolves it) says `select` MANY,
 * answered once, with at most its `maxSelect`.
 */
class SelectSeveralTest {
    private val three = listOf(mapOf("id" to "cravings", "label" to "Cravings"), mapOf("id" to "time", "label" to "No time"), mapOf("id" to "late", "label" to "Late dinners"))

    private fun component(kind: String, style: Map<String, Any?>?, key: String = "options", options: List<Map<String, String>> = three) = GraineComponent(
        id = "w7",
        kind = kind,
        title = null,
        payload = buildMap {
            put("data", mapOf(
                "title" to "What gets in the way?",
                "prompt" to "Select all that apply",
                key to options,
            ))
            if (style != null) put("style", style)
        },
    )

    private fun items(style: Map<String, Any?>?, count: Int = 5, kind: String = "product_list") = GraineComponent(
        id = "w8",
        kind = kind,
        title = null,
        payload = buildMap {
            put("data", mapOf("selectLabel" to "Compare", "items" to (1..count).map { mapOf("id" to "p$it", "title" to "Plan $it") }))
            if (style != null) put("style", style)
        },
    )

    private val five = (1..5).map { mapOf("id" to "o$it", "label" to "Option $it") }

    @Test
    fun choicesSetToSeveralAreTickedAndSentAsOneAnswer() {
        val w = assertIs<OwnWidget.Chips>(OwnComponents.read(component("choices", mapOf("select" to "many", "maxSelect" to 8))))
        assertTrue(w.many)
        assertEquals(3, w.maxSelect, "never more than there are")
        assertEquals("Select all that apply", w.prompt)
        val a = OwnComponents.chips(w, listOf("late", "cravings", "nope"))!!
        assertEquals("Late dinners and Cravings", a.summary, "the user's turn names what they picked, in their order")
        assertEquals(
            mapOf(
                "items" to listOf(mapOf("chosen" to "late", "label" to "Late dinners"), mapOf("chosen" to "cravings", "label" to "Cravings")),
                "ids" to "late,cravings",
                "titles" to "Late dinners, Cravings",
                "count" to 2,
            ),
            a.data,
            "the shape several rows answer with; each pick as one tap would send it",
        )
        assertNull(OwnComponents.chips(w, emptyList()), "nothing picked, nothing sent")
    }

    @Test
    fun quickActionsSetToSeveralTooWhatTheDashboardMakes() {
        val w = assertIs<OwnWidget.Chips>(OwnComponents.read(component("quick_actions", mapOf("select" to "MANY", "maxSelect" to 2), key = "actions")))
        assertTrue(w.many && w.quickActions)
        assertEquals(2, w.maxSelect)
        assertEquals(listOf(mapOf("id" to "time", "label" to "No time")), OwnComponents.chips(w, listOf("time"))!!.data["items"])
    }

    @Test
    fun withoutSeveralAChipStillAnswersAtOnce() {
        val w = assertIs<OwnWidget.Chips>(OwnComponents.read(component("choices", mapOf("select" to "one"))))
        assertFalse(w.many)
        assertEquals("I'll go with No time.", OwnComponents.chip(w, w.chips[1]).summary)
        assertFalse(assertIs<OwnWidget.Chips>(OwnComponents.read(component("choices", null))).many)
    }

    @Test
    fun withoutAMaximumAtMostThreeTheSdksDefault() {
        // The contract's default when the dashboard chose none — not every option, as the app used to read it.
        assertEquals(3, assertIs<OwnWidget.Chips>(OwnComponents.read(component("choices", mapOf("select" to "many"), options = five))).maxSelect)
        assertEquals(3, assertIs<OwnWidget.Rows>(OwnComponents.read(items(mapOf("select" to "many")))).maxSelect)
        assertEquals(2, assertIs<OwnWidget.Rows>(OwnComponents.read(items(mapOf("select" to "many"), count = 2))).maxSelect, "never more than there are")
    }

    @Test
    fun aMaximumKeepsToTheContractsRange() {
        fun max(style: Map<String, Any?>) = assertIs<OwnWidget.Chips>(OwnComponents.read(component("choices", style, options = five))).maxSelect
        assertEquals(2, max(mapOf("select" to "many", "maxSelect" to 1)), "two at the least")
        assertEquals(2, max(mapOf("select" to "many", "maxSelect" to 0)))
        assertEquals(4, max(mapOf("select" to "many", "maxSelect" to 3.6)), "rounded")
        assertEquals(5, max(mapOf("select" to "many", "maxSelect" to 20)), "eight at the most, and never more than there are")
        assertEquals(3, max(mapOf("select" to "many", "maxSelect" to "4")), "a number the SDK cannot read is its default")
    }

    @Test
    fun rowsReadTheLookAsTheSdkDoes() {
        // Rows used to need exactly "many"; the SDK's reading takes its case and spaces off, as the chips' did.
        assertTrue(assertIs<OwnWidget.Rows>(OwnComponents.read(items(mapOf("select" to "MANY")))).many)
        assertTrue(assertIs<OwnWidget.Rows>(OwnComponents.read(items(mapOf("select" to " many ")))).many)
        assertFalse(assertIs<OwnWidget.Rows>(OwnComponents.read(items(mapOf("select" to "several")))).many, "a word it does not know is one")
        assertFalse(assertIs<OwnWidget.Rows>(OwnComponents.read(items(null))).many)
        val card = GraineComponent("w9", "info_card", null, mapOf("data" to mapOf("title" to "About", "summary" to "One line."), "style" to mapOf("select" to "many")))
        assertFalse(assertIs<OwnWidget.Rows>(OwnComponents.read(card)).many, "an info card is never several")
    }

    @Test
    fun oneChipIsNeverSeveral() {
        val w = assertIs<OwnWidget.Chips>(OwnComponents.read(component("choices", mapOf("select" to "many"), options = three.take(1))))
        assertFalse(w.many, "with nothing else to tick, the one chip answers at once")
    }
}
