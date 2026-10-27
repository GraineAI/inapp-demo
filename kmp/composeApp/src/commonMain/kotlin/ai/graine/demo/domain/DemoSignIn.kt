package ai.graine.demo.domain

/*
 * ═══ DEMO ONLY — YOUR SIGN-IN DOES THIS. DO NOT COPY THIS FILE. ═══
 *
 * Step 4, Prove who the user is: GraineSecurity.signIn needs the signed-in user's ID token. In your app it comes from
 * YOUR sign-in — Firebase `user.getIdToken(false)`, or your Auth0, Cognito or Okta session — a token your identity
 * provider signs for the person who really signed in. The agent's Prove who the user is names that provider's JWKS
 * URL, issuer, audience and user-ID claim; Graine holds no secret for it, and neither does your app.
 *
 * This demo has no sign-in — you pick a fictional person — so it asks Graine's DEMO SIGN-IN SERVER, which signs a
 * ten-minute ID token for whatever user id it is given, with a private key that never leaves Graine's server. Graine
 * enables it for its own demo workspace only: any other workspace's publishable key is refused.
 *
 *   POST {baseUrl}/api/demo/identity-token  { publishableKey, userId }  →  { token, expires_at }
 *
 * Signing for any user id it is asked for is what makes it a stand-in, never a proof: your sign-in signs only for the
 * person who signed in, and that is what makes the token a proof. (For sensitive data, add server tokens minted by
 * your own server: https://www.graine.ai/docs/in-app/secure-your-agent.) The demo agent's Prove who the user is uses
 * JWKS URL {baseUrl}/api/demo/idp/jwks, issuer {baseUrl}/api/demo/idp, audience graine-demo and claim sub
 * (agent-setup/YOUR-APPS-AND-USERS.md).
 */
object DemoSignIn {
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
