package ai.graine.demo.ui

import ai.graine.demo.domain.CallText
import ai.graine.demo.domain.DemoAgent
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
                    "$name can call you at a time you choose. Its calls are listed with ${model.agentName}'s on the Calls tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Booked with the help desk's own agent, in the SDK's sheet.
                val canBook = model.canBook(DemoAgent.HELP)
                TextButton(
                    onClick = { model.book(DemoAgent.HELP) },
                    modifier = Modifier.testTag("help_book"),
                ) { Text(model.pack?.bookLabel(DemoAgent.HELP) ?: "Book a call") }
                if (!canBook) {
                    Text(
                        CallText.BOOKING_UNAVAILABLE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("help_booking_unavailable"),
                    )
                }
            }
            Text(
                "Signed in once: $name knows who you are, and ${model.agentName} does too.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            DemoCard(name) {
                Text(
                    "This screen talks to a second agent, with its own publishable key, in the same app. To try it, add the help agent's key as graine.helpPublishableKey in local.properties and build again.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("help_not_configured"),
                )
            }
        }
        Spacer(Modifier.height(72.dp))
    }
}
