package ai.graine.demo.ui

import ai.graine.demo.domain.DemoScreen
import ai.graine.demo.domain.ServerTokenMode
import ai.graine.demo.domain.ServerTokenSettings
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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

        VariablesSection(model, agent)

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

        SecuritySection(model)
        ServerTokenSection(model)

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

/**
 * The variables the agent receives — the SDK's own read-back (currentVariables) — and a form to add any
 * variable, the way an API call carries `variables`. The agent's prompt and welcome use each as {name}.
 */
@Composable
private fun VariablesSection(model: AppModel, agent: String) {
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { model.refreshVariables() }

    Section("Variables $agent receives") {
        Text(
            "Sent with every chat, voice conversation and call, like variables on an API call. " +
                "In the agent's prompt or welcome message, write {name} to use one.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        model.sentVariables.toList().sortedBy { it.first }.forEach { (key, text) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag("variable_$key")) {
                Text(
                    "{" + key + "}",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                if (key in model.addedVariables) {
                    TextButton(onClick = { model.removeVariable(key) }) { Text("Remove") }
                }
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("variable_name"),
        )
        OutlinedTextField(
            value = value,
            onValueChange = { value = it; error = null },
            label = { Text("Value") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("variable_value"),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(
            onClick = {
                error = model.addVariable(name, value)
                if (error == null) { name = ""; value = "" }
            },
            modifier = Modifier.testTag("add_variable"),
        ) { Text("Add variable") }
    }
}

/**
 * Security: for every agent this app talks to, what Graine has proven about this build, the person and the server
 * token, the last refusal — and, in a debug build only, this install's debug token with Copy (SecurityCard, read from
 * security/GraineSecurity.kt). Everything says "not checked" or "off" until the agent's settings ask for it.
 */
@Composable
private fun SecuritySection(model: AppModel) {
    val statuses by model.graine.security.collectAsState()
    Section("Security") {
        SecurityCard(statuses, signedIn = model.user != null, debugToken = model.graine.debugToken, nameOf = { model.securityName(it) })
        Text(
            "This demo proves its users with an ID token from Graine's demo sign-in server — a stand-in for your own " +
                "sign-in, which signs only for the person who signed in. No secret is ever in the app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Profile → Server token (demo): how this demo gets a server token. DEMO ONLY, clearly marked — a real app's
 * tokenProvider asks the app's own server, which mints with its secret API key (domain/ServerToken.kt).
 */
@Composable
private fun ServerTokenSection(model: AppModel) {
    val saved = model.serverToken
    var mode by remember(saved) { mutableStateOf(saved.mode) }
    var token by remember(saved) { mutableStateOf(saved.token) }
    var minter by remember(saved) { mutableStateOf(saved.minterUrl) }
    val draft = ServerTokenSettings(mode, token.trim(), minter.trim())
    Section("Server token (demo)") {
        Text(
            "Demo only. The app never holds a secret: its token comes from a server. In your app, your server mints it for " +
                "the signed-in user with your secret API key, one per agent, and that agent's tokenProvider returns it. " +
                "Here: Graine's demo server for Graine's demo agents (every agent of the app), or — for your own main agent — " +
                "the local minter you run with your own key (agent-setup/token-minter.mjs), or a token you paste.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (m in ServerTokenMode.entries) {
                val chosen = m == mode
                if (chosen) {
                    OutlinedButton(onClick = {}, modifier = Modifier.testTag("server_token_${m.wire}")) { Text(m.label, fontWeight = FontWeight.SemiBold) }
                } else {
                    TextButton(onClick = { mode = m }, modifier = Modifier.testTag("server_token_${m.wire}")) { Text(m.label) }
                }
            }
        }
        when (mode) {
            ServerTokenMode.PASTE -> OutlinedTextField(
                value = token, onValueChange = { token = it }, singleLine = true,
                label = { Text("Token from POST /v2/client-tokens") },
                modifier = Modifier.fillMaxWidth().testTag("server_token_value"),
            )
            ServerTokenMode.MINTER -> OutlinedTextField(
                value = minter, onValueChange = { minter = it }, singleLine = true,
                label = { Text("Local minter address") },
                supportingText = { Text("Android emulator: http://10.0.2.2:8787 · iOS simulator: http://localhost:8787") },
                modifier = Modifier.fillMaxWidth().testTag("server_token_minter"),
            )
            ServerTokenMode.DEMO -> Text(
                "The app asks Graine's demo token endpoint for a token for the signed-in user. It signs only for Graine's demo " +
                    "agents, with the demo workspace's secret key held on Graine's server.",
                style = MaterialTheme.typography.bodySmall,
            )
            ServerTokenMode.OFF -> Text("No token is sent: the agent's publishable key is used alone.", style = MaterialTheme.typography.bodySmall)
        }
        val changed = draft != saved
        TextButton(
            enabled = changed && (draft.mode == ServerTokenMode.OFF || draft.usable),
            onClick = { model.saveServerToken(draft) },
            modifier = Modifier.testTag("server_token_save"),
        ) { Text("Save — takes effect when the app starts again") }
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
