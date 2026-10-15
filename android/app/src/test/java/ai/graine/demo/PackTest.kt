package ai.graine.demo

import ai.graine.demo.domain.AgentProfile
import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.DemoScreen
import ai.graine.demo.domain.ItemsState
import ai.graine.demo.domain.RowMatch
import ai.graine.demo.domain.UseCasePack
import ai.graine.demo.domain.UseCasePacks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The packs the app ships, read from demo/usecases exactly as the app reads its bundled copies. */
class BundledPackTest {
    private val dir = File(System.getProperty("demo.usecases") ?: "../../usecases")
    private val packs = UseCasePacks.load { id -> File(dir, "$id.json").takeIf { it.exists() }?.readText() }

    @Test fun everyShippedPackLoadsInTheAppsOrder() {
        assertEquals("a pack in demo/usecases did not read", UseCasePacks.bundled, packs.map { it.id })
        assertEquals("health-coach", packs.first().id)
    }

    @Test fun everyShippedPackHoldsTogether() {
        for (pack in packs) {
            assertEquals(pack.id, 1, pack.schemaVersion)
            for (user in pack.users) {
                val traits = pack.traitsFor(user)
                assertEquals(pack.id, user.id, traits[pack.identifierField])
                assertEquals(pack.id, user.name, traits["name"])
                assertFalse("${pack.id}: ${user.name} has no rows", pack.rowsFor(user).isEmpty())
            }
            for (row in pack.items.rows) for (name in row.actions) {
                val action = pack.action(name)
                assertEquals("${pack.id}: ${row.id} offers $name", UseCasePack.Effect.SET_STATUS, action?.knownEffect)
                assertNotNull("${pack.id}: $name has no status", action?.status)
            }
            for (action in pack.actions) assertNotNull("${pack.id}: ${action.name}", action.knownEffect)
            for (card in pack.home.cards) assertNotNull("${pack.id}: card ${card.id}", DemoScreen.parse(card.target, pack))
            assertTrue(pack.id, pack.call.bookLabel.isNotBlank())
            assertTrue(pack.id, pack.call.reasons.size in 2..4 && pack.call.reasons.all { it.length <= 120 })
            // Every shipped pack names both agents; the main one repeats the pack's own name.
            assertEquals(pack.id, pack.agentName, pack.profile(DemoAgent.MAIN)?.name)
            assertNotNull(pack.id, pack.profile(DemoAgent.HELP))
            assertTrue(pack.id, pack.bookingReason(DemoAgent.MAIN) != null && pack.bookingReason(DemoAgent.HELP) != null)
        }
    }

    @Test fun theChoiceIsRememberedElseTheDefault() {
        val two = listOf(TestPack.pack, TestPack.other)
        assertEquals("test-pack", UseCasePacks.chosen(two, null)?.id)
        assertEquals("other-pack", UseCasePacks.chosen(two, "other-pack")?.id)
        assertEquals("test-pack", UseCasePacks.chosen(two, "gone")?.id)
    }
}

class PackModelTest {
    private val pack = TestPack.pack

    @Test fun traitsCarryTheIdentifierUnderTheAgentsField() {
        assertEquals(mapOf("plan" to "Diabetes care", "name" to "Riya Sharma", "patient_id" to "P-1"), pack.traitsFor(pack.users[0]))
    }

    @Test fun rowsAreTheUsersOwnPlusTheShared() {
        assertEquals(listOf("metformin", "atorvastatin", "vitamin-d3"), pack.rowsFor(pack.users[0]).map { it.id })
        assertEquals(listOf("aspirin", "vitamin-d3"), pack.rowsFor(pack.users[1]).map { it.id })
    }

    @Test fun anUnknownEffectStillReads() {
        assertNull(pack.action("teleport")?.knownEffect)
        assertEquals(UseCasePack.Effect.SET_STATUS, pack.action("mark_done")?.knownEffect)
    }

    @Test fun bookingFieldsRead() {
        assertEquals("Schedule a consultation call", pack.call.bookLabel)
        // The booking sheet starts with the pack's first reason.
        assertEquals("Weekly check-in", pack.bookingReason(DemoAgent.MAIN))
    }

    @Test fun theTwoAgentsComeFromThePackOrFallBack() {
        assertEquals(AgentProfile("Asha", "care coach", listOf("What is due today?")), pack.profile(DemoAgent.MAIN))
        assertNull(pack.profile(DemoAgent.HELP))
        val two = UseCasePack.parse(
            TestPack.json.replace(
                "\"starters\": [\"What is due today?\"]",
                "\"starters\": [\"What is due today?\"], \"agents\": { \"main\": { \"name\": \"Asha\", \"role\": \"Care coach\" }, " +
                    "\"help\": { \"name\": \"Billing & appointments help\", \"role\": \"Bills, payments and appointments\", \"starters\": [\"Where is my bill?\"], " +
                    "\"callName\": \"Test Care · Billing\", \"placeReason\": \"billing_question\", \"bookLabel\": \"Book a call with billing\", " +
                    "\"reasons\": [\"Billing question\", \"Change an appointment\"] } }",
            ),
        )
        assertEquals("Care coach", two.profile(DemoAgent.MAIN)?.role)
        assertEquals(listOf("What is due today?"), two.profile(DemoAgent.MAIN)?.starters)
        assertEquals(AgentProfile("Billing & appointments help", "Bills, payments and appointments", listOf("Where is my bill?")), two.profile(DemoAgent.HELP))
        assertEquals("Billing & appointments help", DemoScreen.HELP.title(two))
        // The help desk books and calls with its own words; the main agent with the pack's.
        assertEquals("Book a call with billing", two.bookLabel(DemoAgent.HELP))
        assertEquals(listOf("Billing question", "Change an appointment"), two.reasons(DemoAgent.HELP))
        assertEquals("Billing question", two.bookingReason(DemoAgent.HELP))
        assertEquals("billing_question", two.placeReason(DemoAgent.HELP))
        assertEquals("Schedule a consultation call", two.bookLabel(DemoAgent.MAIN))
        assertEquals("patient_question", two.placeReason(DemoAgent.MAIN))
        assertEquals("Schedule a consultation call", pack.bookLabel(DemoAgent.HELP))
    }

    @Test fun theSdksAgentNamesAreTheAppsAgents() {
        assertEquals(DemoAgent.MAIN, DemoAgent.ofSdkName("default"))
        assertEquals(DemoAgent.HELP, DemoAgent.ofSdkName("help"))
        assertNull(DemoAgent.ofSdkName(null))
        assertNull(DemoAgent.ofSdkName("sales"))
    }

    @Test(expected = UseCasePack.PackException::class)
    fun aFileThatIsNotAPackIsRefused() {
        UseCasePack.parse("{\"id\": \"x\"}")
    }
}

class ItemsStateTest {
    private val pack = TestPack.pack
    private val riya get() = ItemsState(pack.rowsFor(pack.users[0]))

    private fun found(q: String?) = (riya.find(q) as? RowMatch.Found)?.row?.id

    @Test fun findsByIdThenTitleThenTheOneRowContainingIt() {
        assertEquals("metformin", found("metformin"))
        assertEquals("atorvastatin", found("  ATORVASTATIN 10 MG "))
        assertEquals("vitamin-d3", found("vitamin"))
        assertEquals("atorvastatin", found("9 days"))
    }

    @Test fun twoMatchesAreAskedAboutAndNoneIsNone() {
        assertEquals(listOf("metformin", "atorvastatin"), (riya.find("mg") as RowMatch.Ambiguous).rows.map { it.id })
        assertEquals(RowMatch.NoMatch, riya.find("insulin"))
        assertEquals(RowMatch.NoMatch, riya.find(""))
        assertEquals(RowMatch.NoMatch, riya.find(null))
        assertEquals(RowMatch.NoMatch, riya.find("aspirin"))
    }

    @Test fun statusValueAndButtons() {
        val metformin = riya.rows[0]
        val atorvastatin = riya.rows[1]
        assertEquals("Due 8:00 pm", riya.valueOf(metformin))
        assertEquals("At bedtime", riya.valueOf(atorvastatin))
        assertEquals(listOf("mark_done"), riya.buttons(metformin, pack).map { it.name })
        val taken = riya.withStatus("Taken today", metformin)
        assertEquals("Taken today", taken.valueOf(metformin))
        assertTrue(taken.buttons(metformin, pack).isEmpty())
        assertTrue(riya.buttons(riya.rows[2], pack).isEmpty())
    }
}

class DemoScreenTest {
    private val pack = TestPack.pack

    @Test fun openScreenTakesTheGenericKeysAndThePacksName() {
        assertEquals(DemoScreen.HOME, DemoScreen.parse("home", pack))
        assertEquals(DemoScreen.CHAT, DemoScreen.parse("CHAT", pack))
        assertEquals(DemoScreen.ITEMS, DemoScreen.parse("items", pack))
        assertEquals(DemoScreen.ITEMS, DemoScreen.parse("Medicines", pack))
        assertEquals(DemoScreen.ITEMS, DemoScreen.parse("/medicines", pack))
        assertEquals(DemoScreen.CALLS, DemoScreen.parse("calls", pack))
        assertEquals(DemoScreen.MEMORY, DemoScreen.parse("memory", pack))
        assertEquals(DemoScreen.PROFILE, DemoScreen.parse(" profile ", pack))
        assertEquals(DemoScreen.HELP, DemoScreen.parse("help", pack))
    }

    @Test fun everythingElseIsNotAScreen() {
        for (raw in listOf("sign_in", "call_log", "settings", null, 7)) assertNull("$raw", DemoScreen.parse(raw, pack))
    }

    @Test fun titlesAndNamesComeFromThePack() {
        assertEquals("Today", DemoScreen.HOME.title(pack))
        assertEquals("Asha", DemoScreen.CHAT.title(pack))
        assertEquals("Medicines", DemoScreen.ITEMS.title(pack))
        assertEquals("Check-in calls", DemoScreen.CALLS.title(pack))
        assertEquals("medicines", DemoScreen.ITEMS.contextName(pack))
        assertEquals("calls", DemoScreen.CALLS.contextName(pack))
    }
}
