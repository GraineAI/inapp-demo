package ai.graine.demo

import ai.graine.demo.domain.DemoTokens
import ai.graine.demo.domain.ServerTokenMode
import ai.graine.demo.domain.ServerTokenSettings
import ai.graine.demo.domain.ServerTokenStore
import ai.graine.demo.domain.minterToken
import ai.graine.demo.domain.minterTokenUrl
import ai.graine.demo.platform.KeyValueStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The demo's server-token stand-ins (domain/ServerToken.kt): off by default, kept on the device, the minter asked for whoever is signed in. */
class ServerTokenTest {
    private class MapStore : KeyValueStore {
        val map = mutableMapOf<String, String>()
        override fun getString(key: String): String? = map[key]
        override fun putString(key: String, value: String?) {
            if (value == null) map.remove(key) else map[key] = value
        }
    }

    private fun provider(s: ServerTokenSettings, get: suspend (String) -> String? = { null }, post: suspend (String, String) -> String? = { _, _ -> null }) =
        DemoTokens.provider(s, "https://www.graine.ai", "pk_test_demo", get, post)

    @Test
    fun offByDefault() {
        val s = ServerTokenStore.load(MapStore())
        assertEquals(ServerTokenMode.OFF, s.mode, "nothing stored: off — verified users come from the sign-in token")
        assertNull(provider(s), "off: the SDK is given no tokenProvider")
        assertEquals(ServerTokenMode.OFF, ServerTokenMode.of("something-else"), "an unknown stored choice is off too")
    }

    @Test
    fun theDemoServerIsAskedForWhoeverIsSignedIn() = runTest {
        val s = ServerTokenSettings(ServerTokenMode.DEMO)
        val sent = mutableListOf<Pair<String, String>>()
        val p = assertNotNull(provider(s, post = { url, json -> sent += url to json; """{"token":"from-the-server"}""" }))
        DemoTokens.userId = "user-7"
        assertEquals("from-the-server", p())
        assertEquals("https://www.graine.ai/api/demo/client-token", sent.single().first)
        assertEquals("""{"publishableKey":"pk_test_demo","userId":"user-7"}""", sent.single().second)
        DemoTokens.userId = null
    }

    @Test
    fun offSendsNothing() {
        assertNull(provider(ServerTokenSettings(ServerTokenMode.OFF)), "off: no tokenProvider at all, so nothing is ever sent")
    }

    @Test
    fun keptOnTheDevice() {
        val store = MapStore()
        ServerTokenStore.save(store, ServerTokenSettings(ServerTokenMode.MINTER, "", "http://localhost:8787"))
        assertEquals(ServerTokenSettings(ServerTokenMode.MINTER, "", "http://localhost:8787"), ServerTokenStore.load(store))
    }

    @Test
    fun pasteReturnsTheToken() = runTest {
        val p = assertNotNull(provider(ServerTokenSettings(ServerTokenMode.PASTE, " tok ", "")))
        assertEquals("tok", p())
        assertNull(provider(ServerTokenSettings(ServerTokenMode.PASTE, " ", "")), "nothing pasted: off")
    }

    @Test
    fun theMinterIsAskedForWhoeverIsSignedIn() = runTest {
        val asked = mutableListOf<String>()
        val p = assertNotNull(
            provider(ServerTokenSettings(ServerTokenMode.MINTER, "", "http://10.0.2.2:8787/"), get = { url ->
                asked += url
                """{"token":"minted","expires_at":"2026-10-27T10:00:00Z"}"""
            }),
        )
        DemoTokens.userId = null
        assertNull(p(), "nobody signed in: no token")
        DemoTokens.userId = "user 1+é"
        assertEquals("minted", p())
        assertEquals(listOf("http://10.0.2.2:8787/token?user_id=user%201%2B%C3%A9"), asked)
        DemoTokens.userId = null
    }

    @Test
    fun minterAnswers() {
        assertEquals("http://h/token?user_id=a-b_c.d~", minterTokenUrl("http://h", "a-b_c.d~"))
        assertNull(minterToken(null))
        assertNull(minterToken("not json"))
        assertNull(minterToken("""{"error":"no key"}"""))
    }
}
