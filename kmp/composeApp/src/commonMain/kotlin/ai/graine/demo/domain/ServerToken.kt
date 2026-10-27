package ai.graine.demo.domain

import ai.graine.demo.platform.KeyValueStore

/*
 * ═══ DEMO ONLY — YOUR SERVER DOES THIS. DO NOT COPY THIS FILE. ═══
 *
 * Server tokens: once an agent requires them, every request needs a short-lived token for that agent and the signed-in
 * user. In your app, YOUR server mints it with your secret API key (POST /v2/client-tokens — the key never ships in an
 * app) for the user its own sign-in proved, and the agent's GraineConfig.tokenProvider fetches it from your server
 * (security/GraineSecurity.kt). One token is for one agent: an app with several agents asks for each agent's.
 *
 * This demo has no server and carries no secret, so Profile → Server token (demo) picks a stand-in:
 *
 *   OFF     (the default) no server token.
 *   DEMO    Graine's demo token endpoint, POST {baseUrl}/api/demo/client-token { publishableKey, userId }: it mints for
 *           Graine's demo agents only (the demo workspace's secret key stays on Graine's server; any other agent's key
 *           is refused) — for every agent of the app, each with its own key.
 *   MINTER  agent-setup/token-minter.mjs, which YOU run on your own computer with YOUR key, for one agent of yours: the
 *           main agent. It mints for whatever user id it is asked for — your real server must mint only for the user
 *           its sign-in proved.
 *   PASTE   a token you minted yourself (curl, your own key) pasted in, for the main agent; it stops opening new
 *           conversations when it expires (an hour at most).
 *
 * The choice takes effect the next time the app starts (the SDK reads tokenProvider once, when it is configured).
 */
enum class ServerTokenMode(val wire: String, val label: String) {
    /** No server token (the default): verified users come from the demo sign-in token. */
    OFF("off", "Off"),
    /** Graine's demo token endpoint — for Graine's demo agents. */
    DEMO("demo", "Graine demo server"),
    MINTER("minter", "Local minter"),
    PASTE("paste", "Paste a token"),
    ;

    /** Whether the mode mints for every agent of the app (each with its own key), or only for the main agent. */
    val everyAgent: Boolean get() = this == DEMO

    companion object {
        /** The stored choice; nothing stored (or anything unknown) is Off. */
        fun of(wire: String?): ServerTokenMode = entries.firstOrNull { it.wire == wire } ?: OFF
    }
}

data class ServerTokenSettings(
    val mode: ServerTokenMode = ServerTokenMode.OFF,
    /** PASTE: the token itself. Kept on this device only. */
    val token: String = "",
    /** MINTER: where agent-setup/token-minter.mjs listens — the emulator reaches the computer at 10.0.2.2. */
    val minterUrl: String = DEFAULT_MINTER_URL,
) {
    /** The mode can do anything at all: a token pasted, or a minter address that is a URL. */
    val usable: Boolean
        get() = when (mode) {
            ServerTokenMode.OFF -> false
            ServerTokenMode.DEMO -> true
            ServerTokenMode.PASTE -> token.isNotBlank()
            ServerTokenMode.MINTER -> minterUrl.startsWith("http://") || minterUrl.startsWith("https://")
        }

    companion object {
        const val DEFAULT_MINTER_URL = "http://10.0.2.2:8787"
    }
}

object ServerTokenStore {
    private const val MODE = "serverToken.mode"
    private const val TOKEN = "serverToken.token"
    private const val MINTER = "serverToken.minterUrl"

    fun load(store: KeyValueStore): ServerTokenSettings = ServerTokenSettings(
        mode = ServerTokenMode.of(store.getString(MODE)),
        token = store.getString(TOKEN)?.trim().orEmpty(),
        minterUrl = store.getString(MINTER)?.trim()?.takeIf { it.isNotEmpty() } ?: ServerTokenSettings.DEFAULT_MINTER_URL,
    )

    fun save(store: KeyValueStore, settings: ServerTokenSettings) {
        store.putString(MODE, settings.mode.wire)
        store.putString(TOKEN, settings.token.trim().takeIf { it.isNotEmpty() })
        store.putString(MINTER, settings.minterUrl.trim().takeIf { it.isNotEmpty() })
    }
}

/** The demo token endpoint on the app's own Graine server. */
fun demoTokenUrl(baseUrl: String): String = baseUrl.trimEnd('/') + "/api/demo/client-token"

/** What the demo endpoint and the local minter are sent: the app's key and the signed-in user. */
fun tokenRequestBody(publishableKey: String, userId: String): String =
    Json.write(mapOf("publishableKey" to publishableKey, "userId" to userId))

/** The local minter's address for one user: `<base>/token?user_id=<value>`, the value percent-encoded. */
fun minterTokenUrl(base: String, userId: String): String {
    val encoded = buildString {
        for (b in userId.encodeToByteArray()) {
            val c = b.toInt() and 0xff
            val ch = c.toChar()
            if (ch.isLetterOrDigit() && c < 0x80 || ch in "-_.~") append(ch) else append('%').append(c.toString(16).uppercase().padStart(2, '0'))
        }
    }
    return base.trimEnd('/') + "/token?user_id=" + encoded
}

/** The token in the minter's answer (`{"token": "…"}`), or null. */
fun minterToken(body: String?): String? {
    val parsed = body?.let { runCatching { Json.parse(it) }.getOrNull() } as? Map<*, *> ?: return null
    return (parsed["token"] as? String)?.takeIf { it.isNotBlank() }
}

/**
 * The demo's tokenProvider (GraineConfig.tokenProvider) for one agent — [publishableKey] is that agent's — in the
 * chosen mode, or null when it is off: then no token is ever sent and nothing changes. DEMO and MINTER ask for a
 * token for [user], whoever is signed in right now; nobody signed in is no token.
 */
object DemoTokens {
    fun provider(
        settings: ServerTokenSettings,
        baseUrl: String,
        publishableKey: String,
        user: () -> String?,
        get: suspend (String) -> String?,
        post: suspend (url: String, json: String) -> String?,
    ): (suspend () -> String?)? {
        if (!settings.usable) return null
        return when (settings.mode) {
            ServerTokenMode.DEMO -> { -> user()?.let { id -> minterToken(post(demoTokenUrl(baseUrl), tokenRequestBody(publishableKey, id))) } }
            ServerTokenMode.PASTE -> { -> settings.token.trim() }
            ServerTokenMode.MINTER -> { -> user()?.let { id -> minterToken(get(minterTokenUrl(settings.minterUrl, id))) } }
            ServerTokenMode.OFF -> null
        }
    }
}
