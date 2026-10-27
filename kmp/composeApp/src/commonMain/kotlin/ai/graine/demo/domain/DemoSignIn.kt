package ai.graine.demo.domain

/*
 * VERIFIED USERS IN THE DEMO — the user's ID token comes from a SERVER, never
 * from a secret in the app.
 *
 * In your app, your own sign-in gives the app an ID token (a JWT) for the person
 * who signed in; the app passes it to identify() and gives the SDK a refresher
 * that asks for a fresh one. The agent's Verified users setting names your sign-in's
 * JWKS URL, issuer, audience and the claim that carries the user id — Graine
 * holds no secret for it.
 *
 * The demo apps have no sign-in server of their own, so they ask Graine's DEMO
 * SIGN-IN SERVER, which signs only for Graine's demo apps, with a private key
 * held on Graine's server:
 *
 *   POST {baseUrl}/api/demo/identity-token  { publishableKey, userId }  →  { token, expires_at }
 *
 * The demo agent's Verified users setting then uses the JWT method with
 * JWKS URL {baseUrl}/api/demo/idp/jwks, issuer {baseUrl}/api/demo/idp, audience
 * graine-demo and claim sub (agent-setup/YOUR-APPS-AND-USERS.md).
 */
object DemoSignIn {
    /** Who is signed in now, as the demo identifies them; null when nobody is. */
    var userId: String? = null

    fun url(baseUrl: String): String = baseUrl.trimEnd('/') + "/api/demo/identity-token"

    fun body(publishableKey: String, userId: String): String =
        Json.write(mapOf("publishableKey" to publishableKey, "userId" to userId))

    /** The token in the server's answer, or null. */
    fun tokenOf(answer: String?): String? {
        val parsed = answer?.let { runCatching { Json.parse(it) }.getOrNull() } as? Map<*, *> ?: return null
        return (parsed["token"] as? String)?.takeIf { it.isNotBlank() }
    }

    /** A fresh ID token for [userId] from the demo sign-in server, or null when it cannot be had right now. */
    suspend fun fetch(baseUrl: String, publishableKey: String, userId: String, post: suspend (url: String, json: String) -> String?): String? =
        tokenOf(post(url(baseUrl), body(publishableKey, userId)))
}
