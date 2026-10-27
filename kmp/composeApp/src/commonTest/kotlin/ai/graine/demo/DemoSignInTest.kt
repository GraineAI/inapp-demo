package ai.graine.demo

import ai.graine.demo.domain.DemoSignIn
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The demo's verified users (domain/DemoSignIn.kt): the ID token comes from the demo sign-in server, for the user who signed in. */
class DemoSignInTest {
    @Test
    fun asksTheDemoSignInServerForTheSignedInUser() = runTest {
        val sent = mutableListOf<Pair<String, String>>()
        val token = DemoSignIn.fetch("https://www.graine.ai/", "pk_test_demo", "user-7") { url, json ->
            sent += url to json
            """{"token":"eyJ.demo.sig","expires_at":"2026-10-27T10:10:00Z","expires_in":600}"""
        }
        assertEquals("eyJ.demo.sig", token)
        assertEquals("https://www.graine.ai/api/demo/identity-token", sent.single().first)
        assertEquals("""{"publishableKey":"pk_test_demo","userId":"user-7"}""", sent.single().second)
    }

    @Test
    fun noTokenWhenTheServerCannotGiveOne() = runTest {
        assertNull(DemoSignIn.fetch("https://www.graine.ai", "pk", "u") { _, _ -> null }, "unreachable")
        assertNull(DemoSignIn.fetch("https://www.graine.ai", "pk", "u") { _, _ -> """{"error":"not set up","code":"not_configured"}""" })
        assertNull(DemoSignIn.tokenOf("not json"))
    }
}
