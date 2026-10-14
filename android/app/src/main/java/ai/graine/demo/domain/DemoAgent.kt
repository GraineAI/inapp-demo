package ai.graine.demo.domain

import ai.graine.inapp.GraineScheduledCall

/**
 * THE APP'S TWO AGENTS: the main one (Home, Chat, the Items screen) and a help
 * desk (the Help screen), each with its own publishable key. The person, the
 * device, the memory choice and the screen are shared between them by the SDK;
 * each has its own chat, voice, actions and scheduled calls.
 */
enum class DemoAgent(val key: String) {
    MAIN("main"),
    HELP("help");

    /** The name the SDK knows it by (GraineInApp.init's name). */
    val sdkName: String get() = if (this == MAIN) "default" else "help"

    companion object {
        fun of(key: String?): DemoAgent? = values().firstOrNull { it.key == key }

        /** The app's agent for the SDK's name of one (GraineInApp.name); null for none, or one the app does not have. */
        fun ofSdkName(name: String?): DemoAgent? = values().firstOrNull { it.sdkName == name }
    }
}

/** What the app shows of an agent: its name, its role line and its chat starters (the pack's `agents`). */
data class AgentProfile(val name: String, val role: String, val starters: List<String>)

/** A scheduled call, and the agent it is booked with. */
data class AgentCall(val agent: DemoAgent, val call: GraineScheduledCall) {
    val id: String get() = "${agent.key}:${call.id}"
}
