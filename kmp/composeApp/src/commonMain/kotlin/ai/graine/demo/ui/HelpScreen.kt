package ai.graine.demo.ui

import ai.graine.demo.domain.CallText
import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.security.SecurityText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Help: a second agent in the same app — the pack's help desk, with its own
 * publishable key. Its chat, voice and calls go to that agent; the person,
 * their memory choice and the screen are the same as on Home.
 */
@Composable
fun HelpScreen(model: AppModel) {
    val profile = model.profile(DemoAgent.HELP)
    val name = profile?.name ?: "Help"

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        profile?.role?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (model.isAvailable(DemoAgent.HELP)) {
            DemoCard(name) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(onClick = { model.talk(DemoAgent.HELP) }, modifier = Modifier.weight(1f).testTag("help_talk")) { Text("Talk") }
                    FilledTonalButton(onClick = { model.openChat(DemoAgent.HELP) }, modifier = Modifier.weight(1f).testTag("help_chat")) { Text("Chat") }
                    FilledTonalButton(onClick = { model.callAgent(DemoAgent.HELP) }, modifier = Modifier.weight(1f).testTag("help_call")) { Text("Call") }
                }
            }
            val starters = profile?.starters ?: emptyList()
            if (starters.isNotEmpty()) {
                DemoCard("Ask $name") {
                    starters.forEach { starter ->
                        Text(
                            starter,
                            modifier = Modifier.fillMaxWidth().clickable { model.startChat(starter, DemoAgent.HELP) }.padding(vertical = 6.dp).testTag("help_starter"),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            DemoCard("Book a call") {
                Text(
                    model.bookingHint(DemoAgent.HELP) ?: "$name can call you at a time you choose. Its calls are listed with ${model.agentName}'s on the Calls tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("help_book_hint"),
                )
                TextButton(
                    onClick = { model.book(DemoAgent.HELP) },
                    enabled = !model.bookingOpen,
                    modifier = Modifier.testTag("help_book"),
                ) { Text(model.pack?.bookLabel(DemoAgent.HELP) ?: "Schedule a call") }
            }
            Text(
                "Signed in once: $name knows who you are, and ${model.agentName} does too.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NotLockedNote(model, name)
        } else {
            DemoCard(name) {
                Text(
                    if (model.config.hasHelpKey) "The help agent's key is set, but the help agent could not be started on this phone (the app's log says why). Everything else works."
                    else "This screen talks to a second agent, with its own publishable key, in the same app. To try it, add the help agent's key (graine.helpPublishableKey in local.properties on Android, GRAINE_HELP_PUBLISHABLE_KEY in Config.xcconfig on iOS) and build again.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("help_not_configured"),
                )
            }
        }
        Spacer(Modifier.height(72.dp))
    }
}

/**
 * A second agent's key ships in the app too, so it must be locked like the first: once this agent's settings have
 * loaded and its verification reads off — it checks neither the app nor its users — the Help screen says so. Every
 * agent has its own Your app and your users in the dashboard; nothing is shared with the main agent.
 */
@Composable
private fun NotLockedNote(model: AppModel, name: String) {
    val statuses by model.graine.security.collectAsState()
    val help = statuses.firstOrNull { it.name == DemoAgent.HELP.sdkName } ?: return
    if (!SecurityText.checksNothing(help)) return
    val main = statuses.firstOrNull { it.name == DemoAgent.MAIN.sdkName }
    val mainChecks = main != null && main.agentId != null && !SecurityText.checksNothing(main)
    DemoCard("Security checks are off for $name") {
        Text(
            "Graine proves neither this app nor its users for $name: its verification reads off" +
                (if (mainChecks) ", while it is on for ${model.agentName} — and the key nobody checks is the one anyone would use" else "") +
                ". Every agent whose key ships in an app needs its own Your app and your users: set up its apps, proofs and " +
                "switches too, under $name's Agent → Embed & Widgets → Install.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("help_not_locked"),
        )
    }
}
