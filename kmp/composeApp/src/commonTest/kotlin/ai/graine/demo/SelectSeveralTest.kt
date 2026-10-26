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

/** "Select all that apply": a choices or quick-actions component whose style says `select: many`, answered once. */
class SelectSeveralTest {
    private fun component(kind: String, style: Map<String, Any?>?, key: String = "options") = GraineComponent(
        id = "w7",
        kind = kind,
        title = null,
        payload = buildMap {
            put("data", mapOf(
                "title" to "What gets in the way?",
                "prompt" to "Select all that apply",
                key to listOf(mapOf("id" to "cravings", "label" to "Cravings"), mapOf("id" to "time", "label" to "No time"), mapOf("id" to "late", "label" to "Late dinners")),
            ))
            if (style != null) put("style", style)
        },
    )

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
}
