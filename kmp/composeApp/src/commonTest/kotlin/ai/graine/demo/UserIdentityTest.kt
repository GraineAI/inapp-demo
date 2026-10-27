package ai.graine.demo

import ai.graine.demo.security.UserIdentity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Step 4's rules (security/GraineSecurity.kt): the user is identified at once, their identity token goes with identify
 * when it is in hand or follows when it comes, a token fetched for one user never reaches the next, and sign-out
 * drops it all. The SDK side is recorded here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserIdentityTest {
    private class Sdk {
        val identified = mutableListOf<Triple<Map<String, Any?>?, Boolean?, String?>>()
        val tokens = mutableListOf<String>()
        val logs = mutableListOf<String>()
    }

    private fun traitsOf(id: String): Map<String, Any?> = mapOf("patient_id" to id, "user_id" to id)

    private fun TestScope.identity(sdk: Sdk, fetch: suspend (String) -> String?) = UserIdentity(
        this,
        fetch,
        identify = { traits, memory, token -> sdk.identified += Triple(traits, memory, token) },
        setIdentityToken = { sdk.tokens += it },
        log = { sdk.logs += it },
    )

    @Test
    fun identifiesAtOnceThenHandsOverTheTokenWhenItComes() = runTest {
        val sdk = Sdk()
        val gate = CompletableDeferred<String?>()
        val id = identity(sdk) { gate.await() }
        id.signIn("P-1", traitsOf("P-1"), memory = true, identityToken = null)
        assertEquals(Triple(traitsOf("P-1"), true, null), sdk.identified.single(), "identified at once: a call ringing now knows the user")
        runCurrent()
        assertTrue(sdk.tokens.isEmpty())
        gate.complete("jwt-P-1")
        advanceUntilIdle()
        assertEquals(listOf("jwt-P-1"), sdk.tokens)
        assertEquals("P-1", id.current)
    }

    @Test
    fun aTokenInHandGoesWithIdentify() = runTest {
        val sdk = Sdk()
        var fetched = 0
        val id = identity(sdk) { fetched++; "fresh" }
        id.signIn("P-1", traitsOf("P-1"), memory = null, identityToken = "from-the-sign-in")
        advanceUntilIdle()
        assertEquals("from-the-sign-in", sdk.identified.single().third)
        assertEquals(0, fetched, "nothing to fetch")
        assertTrue(sdk.tokens.isEmpty())
    }

    @Test
    fun aTokenFetchedForOneUserNeverReachesTheNext() = runTest {
        val sdk = Sdk()
        val late = CompletableDeferred<String?>()
        // P-1's fetch answers late, and even through a cancellation.
        val id = identity(sdk) { user -> if (user == "P-1") withContext(NonCancellable) { late.await() } else "jwt-$user" }
        id.signIn("P-1", traitsOf("P-1"), memory = null, identityToken = null)
        runCurrent()
        id.signIn("P-2", traitsOf("P-2"), memory = null, identityToken = null)
        advanceUntilIdle()
        late.complete("jwt-P-1")
        advanceUntilIdle()
        assertEquals(listOf("jwt-P-2"), sdk.tokens, "P-1's token, come late, is never handed over for P-2")
        assertEquals(listOf("P-1", "P-2"), sdk.identified.map { it.first?.get("user_id") })
    }

    @Test
    fun theSameUserAgainKeepsTheirToken() = runTest {
        val sdk = Sdk()
        var fetched = 0
        val id = identity(sdk) { fetched++; "jwt" }
        id.signIn("P-1", traitsOf("P-1"), memory = true, identityToken = null)
        advanceUntilIdle()
        // The profile changed: the same user, identified again with new traits.
        id.signIn("P-1", traitsOf("P-1") + ("name" to "Riya"), memory = null, identityToken = null)
        advanceUntilIdle()
        assertEquals(1, fetched, "fetched once: the SDK keeps the token for the same user")
        assertEquals(listOf("jwt"), sdk.tokens)
        assertEquals(2, sdk.identified.size)
        assertNull(sdk.identified.last().third, "no token: the SDK keeps the one it holds")
    }

    @Test
    fun signOutDropsTheUserAndTheirToken() = runTest {
        val sdk = Sdk()
        val id = identity(sdk) { "jwt" }
        id.signIn("P-1", traitsOf("P-1"), memory = null, identityToken = null)
        advanceUntilIdle()
        id.signOut()
        assertEquals(Triple(null, null, null), sdk.identified.last(), "identify(null): signed out everywhere")
        assertNull(id.current)
        assertNull(id.fresh(), "the refresher has nobody to ask for")
        // Signing in again fetches again: a token is never kept across a sign-out.
        id.signIn("P-1", traitsOf("P-1"), memory = null, identityToken = null)
        advanceUntilIdle()
        assertEquals(listOf("jwt", "jwt"), sdk.tokens)
    }

    @Test
    fun theRefresherAsksForWhoeverIsSignedIn() = runTest {
        val asked = mutableListOf<String>()
        val id = identity(Sdk()) { user -> asked += user; "jwt-$user" }
        assertNull(id.fresh(), "nobody signed in: null keeps whatever the SDK holds")
        id.signIn("P-1", traitsOf("P-1"), memory = null, identityToken = "in-hand")
        assertEquals("jwt-P-1", id.fresh())
        assertEquals(listOf("P-1"), asked)
    }

    @Test
    fun aFetchThatFailsIsNoTokenAndIsLogged() = runTest {
        val sdk = Sdk()
        val id = identity(sdk) { throw IllegalStateException("offline") }
        id.signIn("P-1", traitsOf("P-1"), memory = null, identityToken = null)
        advanceUntilIdle()
        assertTrue(sdk.tokens.isEmpty())
        assertTrue(sdk.logs.single().contains("no identity token"), sdk.logs.toString())
        assertNull(id.fresh(), "a refresher that fails keeps the token the SDK holds")
    }

    @Test
    fun anAgentWhoseIdentityFieldIsNotSentIsLogged() = runTest {
        val sdk = Sdk()
        val id = identity(sdk) { null }
        id.signIn("P-1", traitsOf("P-1"), memory = null, identityToken = "t")
        id.checkField("default", "patient_id")
        id.checkField("help", "user_id")
        assertTrue(sdk.logs.isEmpty(), "both fields are sent")
        id.checkField("consultation", "email")
        assertTrue(sdk.logs.single().contains("\"email\""), sdk.logs.toString())
    }
}
