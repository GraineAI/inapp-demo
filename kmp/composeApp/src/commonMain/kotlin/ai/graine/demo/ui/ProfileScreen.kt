package ai.graine.demo.ui

import ai.graine.demo.domain.DemoScreen
import ai.graine.demo.platform.CallReadiness
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Profile: who is signed in, their memory choice, this device, and signing out. */
@Composable
fun ProfileScreen(model: AppModel) {
    val pack = model.pack ?: return
    val user = model.user ?: return
    val agent = pack.agentName
    var confirmErase by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { model.closeProfile() }) { Text("Back") }
            Text("Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }

        Section("Signed in as") {
            Field("Name", user.name)
            Field(pack.identifierLabel, user.id)
            user.traits.keys.sorted().forEach { key -> Field(label(key), user.traits[key] ?: "") }
            Field("Use case", pack.appTitle)
        }

        Section("Memory") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Let $agent remember our conversations", modifier = Modifier.weight(1f))
                Switch(checked = model.memoryOn, onCheckedChange = { model.setMemory(it) })
            }
            TextButton(onClick = { model.open(DemoScreen.MEMORY) }) { Text("What $agent remembers") }
            TextButton(onClick = { confirmErase = true }) { Text("Erase everything $agent remembers", color = MaterialTheme.colorScheme.error) }
        }

        Section("Calls") {
            TextButton(onClick = { model.open(DemoScreen.CALL_LOG) }) { Text("Call activity") }
            CallReadiness()
        }

        Section("This device") {
            Field("Install ID", model.graine.installId, monospace = true)
            Field("Server", model.config.baseUrl)
            Text(
                "The install ID identifies this installation of the app. It is made on this device and is not linked to your phone number.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedButton(
            onClick = { model.signOut() },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().testTag("sign_out"),
        ) { Text("Sign out") }
        Spacer(Modifier.height(72.dp))
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Erase everything $agent remembers about you?") },
            text = { Text("$agent will no longer recall anything from your earlier conversations.") },
            confirmButton = { TextButton(onClick = { confirmErase = false; model.forgetMemory() }) { Text("Erase") } },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
        HorizontalDivider(Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun Field(label: String, value: String, monospace: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        SelectionContainer {
            Text(
                value,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = if (monospace) FontFamily.Monospace else null,
                style = if (monospace) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** "care_team" → "Care team". */
private fun label(key: String) = key.replace('_', ' ').replaceFirstChar { it.uppercase() }
