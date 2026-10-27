package ai.graine.demo.ui

import ai.graine.demo.security.AgentSecurity
import ai.graine.demo.security.SecurityText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * THE SECURITY CARD — what testers and your developers look at. For every agent this app talks to: its name, the name
 * it is configured under and its id; what Graine has proven about this build (App), the signed-in user (User) and the
 * server token, each with Graine's reason; the last refusal and error its requests met. In a debug build only, this
 * install's debug token, with Copy, to register on each agent. Everything is read from security/GraineSecurity.kt.
 */
@Composable
@Suppress("DEPRECATION") // LocalClipboardManager: plain text on both platforms, without a platform clip entry.
fun SecurityCard(statuses: List<AgentSecurity>, signedIn: Boolean, debugToken: String?, nameOf: (String) -> String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(debugToken) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("security_card")) {
        Text(
            "Set per agent in the dashboard: Agent → Embed & Widgets → Install → Your app and your users, and Server tokens.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (statuses.isEmpty()) {
            Text("No agent is configured in this build.", style = MaterialTheme.typography.bodyMedium)
        }
        statuses.forEach { s ->
            Column(Modifier.padding(top = 6.dp).testTag("security_agent_${s.name}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    nameOf(s.name) + " · " + s.name + (s.agentId?.let { " · " + SecurityText.idPrefix(it) } ?: ""),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                s.identityField?.let {
                    Text("Who is the user? $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SecurityText.lines(s, signedIn).forEach { SecurityLine(it) }
            }
        }
        if (debugToken != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Debug token (this install)", style = MaterialTheme.typography.bodyMedium)
                    SelectionContainer {
                        Text(debugToken, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("security_debug_token"))
                    }
                }
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(debugToken)); copied = true },
                    modifier = Modifier.testTag("security_copy_debug_token"),
                ) { Text(if (copied) "Copied" else "Copy") }
            }
            Text(
                "Debug builds only — a release build has none. Register it on each agent: Your app and your users → Prove it's " +
                    "your app → Development → Debug tokens.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SecurityLine(line: SecurityText.Line) {
    Row(Modifier.fillMaxWidth()) {
        Text(line.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.34f))
        Column(Modifier.weight(0.66f)) {
            Text(
                line.value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = when (line.ok) {
                    true -> MaterialTheme.colorScheme.primary
                    false -> MaterialTheme.colorScheme.error
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            line.detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
