package ai.graine.demo

import ai.graine.demo.domain.AgentNames
import ai.graine.demo.domain.AppConfig
import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.PackKeys
import ai.graine.demo.domain.UseCasePacks
import ai.graine.demo.ui.AppModel
import ai.graine.inapp.kmp.Graine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * EACH PACK, ITS OWN AGENTS: a pack with agents of its own (AppConfig.packKeys) has its screens talk to them while it
 * is open; a pack without uses the app's. Every agent is configured once at launch, so choosing a pack only switches
 * which one each screen names (AgentNames).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PackAgentsTest {
    @AfterTest
    fun reset() = AgentNames.reset()

    @Test
    fun aPackWithoutAgentsOfItsOwnUsesTheAppsAgents() {
        assertEquals(Graine.DEFAULT_AGENT, AgentNames.MAIN, "the app's main agent is the SDK's default one")
        AgentNames.configured(setOf("retail-shopping"))
        AgentNames.open("health-coach")
        assertEquals("default", DemoAgent.MAIN.sdkName)
        assertEquals("help", DemoAgent.HELP.sdkName)
        AgentNames.open(null)
        assertEquals("default", DemoAgent.MAIN.sdkName, "no pack open: the app's")
    }

    @Test
    fun aPackWithAgentsOfItsOwnUsesThemWhileItIsOpen() {
        AgentNames.configured(setOf("retail-shopping", "banking-support"))
        AgentNames.open("retail-shopping")
        assertEquals("retail-shopping", DemoAgent.MAIN.sdkName)
        assertEquals("retail-shopping.help", DemoAgent.HELP.sdkName)
        AgentNames.open("banking-support")
        assertEquals("banking-support", DemoAgent.MAIN.sdkName)
        assertEquals("banking-support.help", DemoAgent.HELP.sdkName)
    }

    @Test
    fun aCallsEventsNameTheirAgentsRoleWhicheverPackItIsFor() {
        AgentNames.configured(setOf("retail-shopping"))
        AgentNames.open("health-coach")
        assertEquals(DemoAgent.MAIN, DemoAgent.ofSdkName("default"))
        assertEquals(DemoAgent.HELP, DemoAgent.ofSdkName("help"))
        assertEquals(DemoAgent.MAIN, DemoAgent.ofSdkName("retail-shopping"), "another pack's agent keeps its role")
        assertEquals(DemoAgent.HELP, DemoAgent.ofSdkName("retail-shopping.help"))
        assertNull(DemoAgent.ofSdkName("consultation"), "the Journey's first call is the Journey's (JourneyAgent)")
        assertNull(DemoAgent.ofSdkName("banking-support"), "a pack whose agents are not configured")
        assertNull(DemoAgent.ofSdkName(null))
    }

    @Test
    fun eachPacksKeysAreItsOwnOrTheApps() {
        val config = AppConfig.of(
            "https://www.graine.ai", "pk_live_app", "pk_live_ahelp",
            packKeys = mapOf(
                "retail-shopping" to ("pk_live_store" to "pk_live_shelp"),
                "banking-support" to ("pk_live_bank" to null),
                "health-coach" to (null to "pk_live_lone"),
            ),
        )
        assertEquals(setOf("retail-shopping", "banking-support"), config.packKeys.keys, "a help key with no main key of its own is not a pack's own")
        assertEquals(PackKeys("pk_live_store", "pk_live_shelp"), config.keysFor("retail-shopping"))
        assertEquals(PackKeys("pk_live_bank", ""), config.keysFor("banking-support"))
        assertFalse(config.keysFor("banking-support").hasHelpKey, "its own agents only: never the app's help desk")
        assertEquals(PackKeys("pk_live_app", "pk_live_ahelp"), config.keysFor("health-coach"), "a pack without its own uses the app's")
        assertEquals(PackKeys("pk_live_app", "pk_live_ahelp"), config.keysFor(null))
        assertTrue("GRAINE_RETAIL_SHOPPING_HELP_PUBLISHABLE_KEY" in config.helpKeySetting("retail-shopping"))
        assertTrue("graine.pack.retail-shopping.helpPublishableKey" in config.helpKeySetting("retail-shopping"))
        assertTrue("GRAINE_HELP_PUBLISHABLE_KEY" in config.helpKeySetting("health-coach"))
    }

    @Test
    fun androidsPackedKeysReadBackAsTheSettingsGaveThem() {
        // What composeApp's build writes into BuildConfig.GRAINE_PACK_KEYS, for every bundled pack.
        val wire = "health-coach=,;banking-support=pk_live_bank,pk_live_bhelp;retail-shopping=pk_live_store,"
        val config = AppConfig.of(null, "pk_live_app", packKeys = AppConfig.packKeysWire(wire))
        assertEquals(setOf("banking-support", "retail-shopping"), config.packKeys.keys)
        assertEquals(PackKeys("pk_live_bank", "pk_live_bhelp"), config.packKeys["banking-support"])
        assertEquals(PackKeys("pk_live_store", ""), config.packKeys["retail-shopping"])
        assertEquals(emptyMap<String, Pair<String?, String?>>(), AppConfig.packKeysWire(""))
        assertEquals(emptyMap<String, Pair<String?, String?>>(), AppConfig.packKeysWire(null))
    }

    @Test
    fun choosingAPackSwitchesEveryScreenToItsAgents() = runTest {
        val config = AppConfig.of("https://www.graine.ai", "pk_test_app", packKeys = mapOf("retail-shopping" to ("pk_test_store" to "pk_test_shelp")))
        AgentNames.configured(config.packKeys.keys)
        val fake = FakeGraineClient().apply { agents = listOf(DemoAgent.MAIN, DemoAgent.HELP) }
        val packs = UseCasePacks.load().filter { it.id == "health-coach" || it.id == "retail-shopping" }
        val m = AppModel(
            fake, config, packs, MemoryStore(),
            scope = this as CoroutineScope, now = { TestClock.at(12) }, zone = TestClock.zone, collectors = backgroundScope,
        )
        runCurrent()
        assertEquals("health-coach", m.pack?.id)
        assertEquals("default", DemoAgent.MAIN.sdkName, "the health coach has no agents of its own: the app's")
        assertTrue(m.onSecurityCard("default") && m.onSecurityCard("consultation"), "its card: the app's agents and its Journey's first call")
        assertFalse(m.onSecurityCard("retail-shopping"))
        val reads = fake.bookingReads

        m.choosePack("retail-shopping")
        runCurrent()
        assertEquals("retail-shopping", DemoAgent.MAIN.sdkName, "Home, Chat and calls: the store's own agent")
        assertEquals("retail-shopping.help", DemoAgent.HELP.sdkName, "Help: the store's own help desk")
        assertEquals(reads + 2, fake.bookingReads, "booking follows the store's agents, each read once")
        assertTrue(m.onSecurityCard("retail-shopping") && m.onSecurityCard("retail-shopping.help"))
        assertFalse(m.onSecurityCard("default") || m.onSecurityCard("consultation"), "no other pack's agents on its card")

        m.choosePack("health-coach")
        runCurrent()
        assertEquals("default", DemoAgent.MAIN.sdkName, "back to the app's")
    }
}
