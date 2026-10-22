package ai.graine.demo

import ai.graine.demo.domain.CallLog
import ai.graine.demo.domain.CallLogEntry
import ai.graine.demo.domain.CallText
import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.DemoFormat
import ai.graine.demo.domain.DemoScreen
import ai.graine.demo.domain.ItemsState
import ai.graine.demo.domain.RowMatch
import ai.graine.demo.domain.UseCasePack
import ai.graine.demo.domain.UseCasePacks
import ai.graine.inapp.kmp.GraineCallStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The packs this build ships, the rules for finding rows, and how times read. */
class PackTest {
    @Test
    fun everyBundledPackReads() {
        val packs = UseCasePacks.load()
        assertEquals(listOf("health-coach", "banking-support", "retail-shopping"), packs.map { it.id })
        for (p in packs) {
            assertEquals(3, p.users.size, p.id)
            assertTrue(p.items.rows.isNotEmpty(), p.id)
            assertNotNull(p.profile(DemoAgent.HELP), "${p.id} has a help desk")
            assertTrue(p.actions.all { it.knownEffect != null }, "${p.id}: every effect is one this build runs")
            assertTrue(p.starters.size <= 4)
        }
        assertEquals("health-coach", UseCasePacks.chosen(packs, null)?.id)
        assertEquals("retail-shopping", UseCasePacks.chosen(packs, "retail-shopping")?.id)
    }

    @Test
    fun identifySendsTheIdUnderThePacksFieldAndUnderUserId() {
        val health = UseCasePacks.load().first { it.id == "health-coach" }
        val user = health.users[0]
        val traits = health.traitsFor(user)
        assertEquals(user.id, traits[health.identifierField], "the pack's own field")
        assertEquals(user.id, traits["user_id"], "and User ID, so an agent set to it still knows the user")
        val explicit = user.copy(traits = user.traits + ("user_id" to "kept-as-sent"))
        assertEquals("kept-as-sent", health.traitsFor(explicit)["user_id"], "a user_id the person already has is kept")
    }

    @Test
    fun eachPackMapsItsPeopleToTheAgentsVariablesAsAnApiCallWould() {
        val health = UseCasePacks.load().first { it.id == "health-coach" }
        assertEquals(mapOf("callee_name" to "name", "age" to "age"), health.variables)
        assertEquals(mapOf("callee_name" to "Riya Sharma", "age" to "34"), health.variablesFor(health.users[0]))
        // Any name the agent's author chose, from the name or any trait; a field the person lacks is left out.
        val custom = health.copy(variables = mapOf("name_user" to "name", "plan_name" to "plan", "dob" to "date_of_birth"))
        assertEquals(mapOf("name_user" to "Riya Sharma", "plan_name" to "Diabetes care"), custom.variablesFor(health.users[0]))
        // A pack that names none: the name as callee_name — what greetings and Call History read.
        assertEquals(mapOf("callee_name" to "Riya Sharma"), TestPack.pack.variablesFor(TestPack.pack.users[0]))
    }

    @Test
    fun aFileThatIsNotAPackIsRefused() {
        assertFailsWith<UseCasePack.PackException> { UseCasePack.parse("""{"schemaVersion": 1}""") }
        assertFailsWith<UseCasePack.PackException> { UseCasePack.parse("not json") }
    }

    @Test
    fun theAgentNamesAScreenOrARowAsTheReadmeSays() {
        val pack = TestPack.pack
        assertEquals(DemoScreen.ITEMS, DemoScreen.parse("Medicines", pack))
        assertEquals(DemoScreen.CALLS, DemoScreen.parse(" calls ", pack))
        assertNull(DemoScreen.parse("billing", pack))
        val state = ItemsState(pack.rows())
        assertEquals("metformin", (state.find("METFORMIN") as RowMatch.Found).row.id)
        assertEquals("atorvastatin", (state.find("Atorvastatin 10 mg") as RowMatch.Found).row.id)
        assertTrue(state.find("mg") is RowMatch.Ambiguous)
        assertEquals(RowMatch.NoMatch, state.find("insulin"))
    }

    private fun UseCasePack.rows() = items.rows

    @Test
    fun theSdksAgentNamesAreTheAppsAgents() {
        assertEquals(DemoAgent.MAIN, DemoAgent.ofSdkName("default"))
        assertEquals(DemoAgent.HELP, DemoAgent.ofSdkName("help"))
        assertNull(DemoAgent.ofSdkName(null))
        assertNull(DemoAgent.ofSdkName("sales"))
    }

    @Test
    fun timesStatusesAndLogLinesReadPlainly() {
        val now = TestClock.at(12)
        assertEquals("Today, 6:00 pm", DemoFormat.time(TestClock.instant(18), now, TestClock.zone))
        assertEquals("Tomorrow, 9:30 am", DemoFormat.time(TestClock.instant(9, 30, day = 15), now, TestClock.zone))
        assertEquals("Fri 16 Oct, 12:00 pm", DemoFormat.time(TestClock.instant(12, 0, day = 16), now, TestClock.zone))
        assertEquals("2026-10-14T12:30:00Z", DemoFormat.iso(TestClock.at(18) + 234))
        assertEquals("No device", CallText.label(GraineCallStatus.NoDevice))
        assertEquals("Snoozed later", CallText.label(GraineCallStatus.Unknown("snoozed_later")))
        val log = CallLog().record(CallLogEntry(callId = "c1", event = "missed", detail = "busy", at = now))
        assertEquals("Missed (another call was live)", log.entries.single().title("Asha"))
        assertEquals(log.entries, CallLog.fromJson(log.toJson()).entries)
    }
}
