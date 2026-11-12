package ai.graine.demo.domain

/**
 * WHICH SDK AGENT EACH SCREEN TALKS TO. Every agent is configured once, at launch, under its own name: the app's keys
 * as "default" and "help" (and the Journey's first call as "consultation"), and a pack with agents of its own
 * (AppConfig.packKeys) as "<pack id>" and "<pack id>.help". While a pack is open, DemoAgent.MAIN and DemoAgent.HELP are
 * that pack's — its own agents when it has them, else the app's — so choosing another pack switches agents without
 * configuring anything again.
 */
object AgentNames {
    /** The app's main agent: Graine.DEFAULT_AGENT. */
    const val MAIN = "default"

    /** The app's help desk. */
    const val HELP = "help"

    private const val HELP_SUFFIX = ".help"

    /** The packs whose own agents are configured. */
    private var own: Set<String> = emptySet()

    /** The pack whose screens are on. */
    var openPack: String? = null
        private set

    /** At launch, once the agents are configured: the packs that have their own. */
    fun configured(packIds: Set<String>) {
        own = packIds
    }

    /** A pack was opened (at launch, or chosen on the sign-in screen). */
    fun open(packId: String?) {
        openPack = packId
    }

    /** A pack's own main agent's name. */
    fun mainOf(packId: String): String = packId

    /** A pack's own help desk's name. */
    fun helpOf(packId: String): String = packId + HELP_SUFFIX

    /** The name [agent] has for the open pack. */
    fun of(agent: DemoAgent): String {
        val pack = openPack?.takeIf { it in own }
        return when (agent) {
            DemoAgent.MAIN -> pack?.let(::mainOf) ?: MAIN
            DemoAgent.HELP -> pack?.let(::helpOf) ?: HELP
        }
    }

    /** The role a name has, whichever pack it is configured for (a call's events say which); null for one the app does not have. */
    fun roleOf(name: String?): DemoAgent? = when {
        name == null -> null
        name == MAIN -> DemoAgent.MAIN
        name == HELP -> DemoAgent.HELP
        name.endsWith(HELP_SUFFIX) && name.removeSuffix(HELP_SUFFIX) in own -> DemoAgent.HELP
        name in own -> DemoAgent.MAIN
        else -> null
    }

    /** Tests: the app's agents only, no pack open. */
    fun reset() {
        own = emptySet()
        openPack = null
    }
}
