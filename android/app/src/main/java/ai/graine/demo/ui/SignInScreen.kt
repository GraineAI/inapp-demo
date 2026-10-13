package ai.graine.demo.ui

import ai.graine.demo.domain.UseCasePack
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Sign in (demo): pick the use case and a fictional user; consent to memory. */
@Composable
fun SignInScreen(model: AppModel) {
    val pack = model.pack ?: return
    var selected by remember(pack.id) { mutableStateOf<UseCasePack.User?>(null) }
    var consent by remember { mutableStateOf(true) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Graine Demo", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)

        if (model.packs.size > 1) {
            Text("Use case", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                model.packs.forEach { p ->
                    FilterChip(
                        selected = p.id == pack.id,
                        onClick = { model.choosePack(p.id) },
                        label = { Text(p.appTitle) },
                        modifier = Modifier.testTag("pack_${p.id}"),
                    )
                }
            }
        }
        Text(pack.tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("Choose a ${pack.userNoun}", style = MaterialTheme.typography.titleSmall)
        pack.users.forEach { user ->
            Row(
                Modifier.fillMaxWidth().clickable { selected = user }.padding(vertical = 6.dp).testTag("user_${user.id}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == user, onClick = { selected = user })
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(user.name, fontWeight = FontWeight.SemiBold)
                    val extra = user.traits.keys.sorted().firstOrNull()?.let { user.traits[it] }
                    Text(listOfNotNull(user.id, extra).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("Demo sign-in. Every person and record in this app is fictional.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Let ${pack.agentName} remember our conversations", modifier = Modifier.weight(1f))
            Switch(checked = consent, onCheckedChange = { consent = it }, modifier = Modifier.testTag("memory_consent"))
        }
        Text(
            "${pack.agentName} uses what you said before to pick up where you left off. You can turn this off, or erase what is remembered, at any time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = { selected?.let { model.signIn(it, consent) } },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth().testTag("continue"),
        ) { Text("Continue") }

        if (!model.config.hasKey) {
            WarningText("No publishable key is set. Add graine.publishableKey to local.properties and build again.")
        }
    }
}
