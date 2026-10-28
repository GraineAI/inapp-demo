package ai.graine.demo

import ai.graine.demo.security.UserIdentity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Step 4's rules (security/GraineSecurity.kt): identify WITH the identity token — the one in hand at once, else the one
 * fetched first, waited for at most ten seconds; after that without it, the token following when it comes — and a
 * conversation started before then is the SDK's to hold (0.14.6 asks the refresher, which joins this fetch). Nobody else
 * is ever identified meanwhile, a token fetched for one user never reaches the next, and sign-out cancels everything.
 * The SDK side is recorded here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserIdentityTest {
    private class Sdk {
        val identified = mutableListOf<Triple<Map<String, Any?>?, Boolean?, String?>>()
        val tokens = mutableListOf<String>()
        val logs = mutableListOf<String>()
    }

    private fun traitsOf(id: String): Map<String, Any?> = mapOf("patient_id" to id, "user_id" to id)

    private val wait = UserIdentity.TOKEN_WAIT_MS

    private fun TestScope.identity(sdk: Sdk, fetch: suspend (String) -> String?) = UserIdentity(
        this,
        fetch,
        identify = { traits, memory, token -> sdk.identified += Triple(traits, memory, token) },
        setIdentityToken = { sdk.tokens += it },
        log = { sdk.logs += it },
    )

    @Test
    fun theTokenIsFetchedFirstAndGoesWithIdentify() = runTest {
        val sdk = Sdk()
        val gate = CompletableDeferred<String?>()
        val id = identity(sdk) { gate.await() }
        id.signIn("P-1", traitsOf("P-1"), memory = true, identityToken = null)
        runCurrent()
        assertTrue(sdk.identified.isEmpty(), "nothing identified before the token, so nothing goes out unproven")
        assertEquals("P-1", id.current)
        gate.complete("jwt-P-1")
        runCurrent()
        assertEquals(Triple(traitsOf("P-1"), true, "jwt-P-1"), sdk.identified.single(), "identified WITH the token")
        assertTrue(sdk.tokens.isEmpty(), "no separate setIdentityToken: it went with identify")
    }

    @Test
    fun aTokenInHandGoesWithIdentifyAtOnce() = runTest {
        val sdk = Sdk()
        var fetched = 0
        val id = identity(sdk) { fetched++; "fresh" }
        id.signIn("P-1", traitsOf("P-1"), memory = null, identityToken = "from-the-sign-in")
        assertEquals(Triple(traitsOf("P-1"), null, "from-the-sign-in"), sdk.identified.single(), "at once")
        advanceUntilIdle()
        assertEquals(0, fetched, "nothing to fetch")
        assertTrue(sdk.tokens.isEmpty())
    }

    @Test
    fun aSlowTokenIsWaitedForThenFollows() = runTest {
        val sdk = Sdk()
        val gate = CompletableDeferred<String?>()
        val id = identity(sdk) { gate.await() }
        id.signIn("P-1", traitsOf("P-1"), memory = null, identityToken = null)
        advanceTimeBy(wait - 1)
        assertTrue(sdk.identified.isEmpty(), "still waiting for the token")
        advanceTimeBy(2)
        assertEquals(Triple(traitsOf("P-1"), null, null), sdk.identified.single(), "after the wait: without it — a call that rings knows the user")
        gate.complete("jwt-P-1")
        runCurrent()
        assertEquals(listOf("jwt-P-1"), sdk.tokens, "the token follows when it comes")
        assertEquals(1, sdk.identified.size)
    }

    @Test
    fun aFetchThatFailsIdentifiesAtOnceWithoutAToken() = runTest {
        val sdk = Sdk()
        var fetched = 0
        val id = identity(sdk) { fetched++; throw IllegalStateException("offline") }
        id.signIn("P-1", traitsOf("P-1"), memory = null, identityToken = null)
        runCurrent()
        assertTrue(currentTime < wait, "no waiting out the wait for a fetch that already failed")
        assertEquals(Triple(traitsOf("P-1"), null, null), sdk.identified.single())
        advanceUntilIdle()
        assertTrue(sdk.tokens.isEmpty())
        assertEquals(1, fetched, "not asked again at once: the SDK's refresher asks when it needs one")
        assertTrue(sdk.logs.single().contains("no identity token"), sdk.logs.toString())
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
        runCurrent()
        late.complete("jwt-P-1")
        advanceUntilIdle()
        assertEquals(listOf<Triple<Map<String, Any?>?, Boolean?, String?>>(Triple(traitsOf("P-2"), null, "jwt-P-2")), sdk.identified, "only P-2, with P-2's token")
        assertTrue(sdk.tokens.isEmpty(), "P-1's token, come late, is never handed over")
    }

    @Test
    fun whileTheNextUsersTokenIsFetchedNobodyIsIdentified() = runTest {
        val sdk = Sdk()
        val gate = CompletableDeferred<String?>()
        val id = identity(sdk) { user -> if (user == "P-1") "jwt-P-1" else gate.await() }
        id.signIn("P-1", traitsOf("P-1"), memory = null, identityToken = null)
        runCurrent()
        id.signIn("P-2", traitsOf("P-2"), memory = null, identityToken = null)
        assertEquals(Triple(null, null, null), sdk.identified.last(), "P-1 let go at once — never P-1 in P-2's place")
        gate.complete("jwt-P-2")
        runCurrent()
        assertEquals(Triple(traitsOf("P-2"), null, "jwt-P-2"), sdk.identified.last())
    }

    @Test
    fun signOutWhileTheTokenIsOnItsWayCancelsEverything() = runTest {
        val sdk = Sdk()
        val late = CompletableDeferred<String?>()
        val id = identity(sdk) { withContext(NonCancellable) { late.await() } }
        id.signIn("P-1", traitsOf("P-1"), memory = null, identityToken = null)
        runCurrent()
        id.signOut()
        assertEquals(listOf<Triple<Map<String, Any?>?, Boolean?, String?>>(Triple(null, null, null)), sdk.identified, "identify(null): signed out everywhere")
        late.complete("jwt-P-1")
        advanceUntilIdle()
        assertEquals(1, sdk.identified.size, "never identified after sign-out")
        assertTrue(sdk.tokens.isEmpty(), "the token, come late, goes nowhere")
        assertNull(id.current)
        assertNull(id.fresh(), "the refresher has nobody to ask for")
    }

    @Test
    fun theSameUserAgainWhileWaitingIsIdentifiedOnceWithTheLatestTraits() = runTest {
        val sdk = Sdk()
        val gate = CompletableDeferred<String?>()
        var fetched = 0
        val id = identity(sdk) { fetched++; gate.await() }
        id.signIn("P-1", traitsOf("P-1"), memory = true, identityToken = null)
        runCurrent()
        id.signIn("P-1", traitsOf("P-1") + ("name" to "Riya"), memory = null, identityToken = null)
        runCurrent()
        gate.complete("jwt")
        advanceUntilIdle()
        assertEquals(1, fetched, "one fetch")
        assertEquals(
            Triple(traitsOf("P-1") + ("name" to "Riya"), true, "jwt"), sdk.identified.single(),
            "once, with the latest traits and the memory choice made",
        )
    }

    @Test
    fun theSameUserAgainAfterwardsIsIdentifiedAtOnceAndKeepsTheirToken() = runTest {
        val sdk = Sdk()
        var fetched = 0
        val id = identity(sdk) { fetched++; "jwt" }
        id.signIn("P-1", traitsOf("P-1"), memory = true, identityToken = null)
        advanceUntilIdle()
        // The profile changed: the same user, identified again with new traits.
        id.signIn("P-1", traitsOf("P-1") + ("name" to "Riya"), memory = null, identityToken = null)
        assertEquals(Triple(traitsOf("P-1") + ("name" to "Riya"), null, null), sdk.identified.last(), "at once — the SDK keeps the token it holds")
        advanceUntilIdle()
        assertEquals(1, fetched)
        assertEquals(2, sdk.identified.size)
    }

    @Test
    fun theRefresherJoinsTheFetchOnItsWay() = runTest {
        val sdk = Sdk()
        val gate = CompletableDeferred<String?>()
        var fetched = 0
        val id = identity(sdk) { fetched++; gate.await() }
        id.signIn("P-1", traitsOf("P-1"), memory = null, identityToken = null)
        runCurrent()
        val refreshed = async { id.fresh() }
        runCurrent()
        gate.complete("jwt")
        advanceUntilIdle()
        assertEquals("jwt", refreshed.await())
        assertEquals(1, fetched, "one fetch, for the sign-in and the SDK's refresher alike")
        assertEquals("jwt", sdk.identified.single().third)
    }

    @Test
    fun aRingBeforeTheTokenIdentifiesAtOnceAndTheTokenFollows() = runTest {
        val sdk = Sdk()
        val gate = CompletableDeferred<String?>()
        val id = identity(sdk) { gate.await() }
        id.signIn("P-1", traitsOf("P-1"), memory = null, identityToken = null)
        runCurrent()
        id.identifyPendingNow()
        assertEquals(Triple(traitsOf("P-1"), null, null), sdk.identified.single(), "the ringing call knows the user")
        gate.complete("jwt")
        runCurrent()
        assertEquals(listOf("jwt"), sdk.tokens)
        id.identifyPendingNow()
        assertEquals(1, sdk.identified.size, "nothing waits any more: a later ring changes nothing")
    }

    @Test
    fun aCallWaitsForTheIdentifyAtMostTheTokenWait() = runTest {
        val sdk = Sdk()
        val gate = CompletableDeferred<String?>()
        val id = identity(sdk) { gate.await() }
        id.awaitReady()
        assertEquals(0L, currentTime, "nothing waits: at once")

        id.signIn("P-1", traitsOf("P-1"), memory = null, identityToken = null)
        var started = false
        launch { id.awaitReady(); started = true }
        runCurrent()
        assertFalse(started, "a call placed now waits for the token")
        gate.complete("jwt")
        runCurrent()
        assertTrue(started, "and starts once the user is identified with it")
        assertEquals("jwt", sdk.identified.single().third)

        // A token that never comes: the call waits for the identify wait, no more.
        val never = CompletableDeferred<String?>()
        val other = identity(Sdk()) { never.await() }
        other.signIn("P-2", traitsOf("P-2"), memory = null, identityToken = null)
        val from = currentTime
        other.awaitReady()
        assertEquals(wait, currentTime - from)
        never.complete(null)
    }

    @Test
    fun aTokenSlowerThanIdentifyIsTheSdksToWaitFor() = runTest {
        // Seen on a phone: the first token after launch took about 6 s. With a token slower than the identify wait, identify goes without it, and a call placed
        // then starts at once: the SDK (0.14.6) asks the refresher before the conversation and waits for it, at most
        // 10 s, and the refresher joins the fetch already on its way — one fetch, never a second.
        val sdk = Sdk()
        val gate = CompletableDeferred<String?>()
        var fetched = 0
        val id = identity(sdk) { fetched++; gate.await() }
        id.signIn("P-1", traitsOf("P-1"), memory = null, identityToken = null)
        advanceTimeBy(wait + 1)
        assertEquals(null, sdk.identified.single().third, "identified without the token after the identify wait")
        val from = currentTime
        id.awaitReady()
        assertEquals(from, currentTime, "the app holds no conversation back for the token: the SDK does")
        val refreshed = async { id.fresh() }
        advanceTimeBy(4_000)
        gate.complete("jwt")
        runCurrent()
        assertEquals("jwt", refreshed.await(), "what the SDK's refresher gets: the token of the fetch on its way")
        assertEquals(1, fetched)
    }

    @Test
    fun aTokenThatTakesSixSecondsStillGoesWithIdentify() = runTest {
        // Seen on a phone: the first token after launch took 3 to 6 s. Identify waits for it, as long as the SDK would, so
        // no request names the user without proving them (the SDK's background preparation of the next call included).
        val sdk = Sdk()
        val gate = CompletableDeferred<String?>()
        val id = identity(sdk) { gate.await() }
        id.signIn("P-1", traitsOf("P-1"), memory = null, identityToken = null)
        advanceTimeBy(6_000)
        assertTrue(sdk.identified.isEmpty(), "nobody identified yet: requests name nobody, so none is anonymous")
        gate.complete("jwt")
        runCurrent()
        assertEquals("jwt", sdk.identified.single().third, "identified WITH the token")
        assertTrue(sdk.tokens.isEmpty(), "no separate setIdentityToken: it went with identify")
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
