package ai.graine.demo.domain

/** Every place the app can show. The same on every platform, whatever the pack (usecases/README.md). */
enum class DemoScreen(val key: String) {
    SIGN_IN("sign_in"),
    HOME("home"),
    CHAT("chat"),
    ITEMS("items"),
    CALLS("calls"),
    MEMORY("memory"),
    PROFILE("profile"),
    /** The help desk: the second agent's chat, voice and calls. */
    HELP("help"),
    /** This app's call activity log, inside Scheduled calls. Not one the agent opens. */
    CALL_LOG("call_log");

    /** The screen's title, from the pack. */
    fun title(pack: UseCasePack?): String = when (this) {
        SIGN_IN -> "Sign in"
        HOME -> pack?.home?.title ?: "Home"
        CHAT -> pack?.agentName ?: "Chat"
        ITEMS -> pack?.items?.title ?: "Items"
        CALLS -> pack?.call?.title ?: "Scheduled calls"
        MEMORY -> "Memory"
        PROFILE -> "Profile"
        HELP -> pack?.profile(DemoAgent.HELP)?.name ?: "Help"
        CALL_LOG -> "Call activity"
    }

    /** What the app reports as `screen`: the pack's own word for the Items screen ("medicines"). */
    fun contextName(pack: UseCasePack?): String = if (this == ITEMS) pack?.items?.screenName ?: key else key

    companion object {
        /** The screens open_screen accepts, in the order the agent is told them. */
        val openable = listOf(HOME, CHAT, ITEMS, CALLS, MEMORY, PROFILE, HELP)

        /**
         * open_screen's argument: a generic key (home, chat, items, calls,
         * memory, profile) or the pack's items.screenName, case-insensitively.
         * A component's tap sends a path ("/medicines"), so a leading slash is ignored.
         */
        fun parse(raw: Any?, pack: UseCasePack?): DemoScreen? {
            val key = (raw as? String)?.trim()?.lowercase()?.trimStart('/') ?: return null
            if (pack != null && key == pack.items.screenName.lowercase()) return ITEMS
            return openable.firstOrNull { it.key == key }
        }
    }
}

/** What an action handler answers. The agent speaks about exactly this, so it says what really happened. */
data class DemoActionResult(val status: Status, val message: String) {
    enum class Status { OK, REFUSED, ERROR }

    companion object {
        fun ok(message: String) = DemoActionResult(Status.OK, message)
        fun refused(message: String) = DemoActionResult(Status.REFUSED, message)
    }
}

/** One line of screen context, as the agent reads it. [status]: empty, filled, invalid, pending or locked. */
data class DemoField(val name: String, val label: String, val value: String?, val status: String? = null)

/** What the user is looking at: sent as the screen context on every screen. */
data class DemoScreenContext(val screen: String, val title: String, val fields: List<DemoField> = emptyList())
