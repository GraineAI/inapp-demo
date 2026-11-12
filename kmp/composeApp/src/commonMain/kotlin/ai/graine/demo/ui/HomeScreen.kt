package ai.graine.demo.ui

import ai.graine.demo.domain.CallText
import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.DemoFormat
import ai.graine.demo.domain.DemoScreen
import ai.graine.demo.domain.FaceText
import ai.graine.demo.domain.Tone
import ai.graine.demo.platform.CallReadiness
import ai.graine.inapp.kmp.Graine
import ai.graine.demo.ui.own.DesignSwitch
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Home: the memory chip, the three ways to reach the assistant, the next scheduled call and the pack's cards. */
@Composable
fun HomeScreen(model: AppModel) {
    val pack = model.pack ?: return
    val user = model.user ?: return
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(pack.home.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { model.open(DemoScreen.PROFILE) }, modifier = Modifier.testTag("profile")) { Text("Profile") }
            }
            Text("Hello, ${user.firstName}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(pack.appTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val (label, on) = model.memoryStatus
                TextButton(onClick = { model.open(DemoScreen.MEMORY) }, modifier = Modifier.testTag("memory_chip")) {
                    StatusChip(label, if (on) Tone.POSITIVE else Tone.NEUTRAL)
                }
            }
        }

        DesignSwitch(model)

        CallReadiness()

        pack.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        DemoCard("${model.nameOf(DemoAgent.MAIN)}, your ${model.profile(DemoAgent.MAIN)?.role ?: pack.agentRole}") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = { model.talk() }, modifier = Modifier.weight(1f).testTag("talk")) { Text("Talk") }
                FilledTonalButton(onClick = { model.open(DemoScreen.CHAT) }, modifier = Modifier.weight(1f).testTag("chat")) { Text("Chat") }
                FilledTonalButton(onClick = { model.callAgent() }, modifier = Modifier.weight(1f).testTag("call")) { Text("Call") }
            }
            FaceSwitch(model)
        }

        DemoCard("Next scheduled call") {
            val next = model.nextCall
            if (next != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(DemoFormat.time(next.call.at, nowMs()), fontWeight = FontWeight.SemiBold)
                        Text("With ${model.nameOf(next.agent)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusChip(CallText.label(next.call.status), CallText.tone(next.call.status))
                }
            } else {
                Text(if (model.callsLoading) "Loading…" else "No call booked.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { model.book(DemoAgent.MAIN) }, enabled = !model.bookingOpen, modifier = Modifier.testTag("home_book")) {
                    Text(pack.bookLabel(DemoAgent.MAIN))
                }
                TextButton(onClick = { model.open(DemoScreen.CALLS) }) { Text(pack.call.title) }
            }
        }

        pack.home.cards.forEach { card ->
            DemoCard(card.title, modifier = Modifier.testTag("card_${card.id}"), onClick = { model.openTarget(card.target) }) {
                Text(card.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(72.dp))
    }
}

/** "Show the agent's face": over the voice bar while a call in it has one, and on the SDK's call screen. Also on the storefront's Home. */
@Composable
internal fun FaceSwitch(model: AppModel) {
    val face by Graine.avatar.state.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Show the agent's face", fontWeight = FontWeight.SemiBold)
            Text(
                FaceText.caption(model.faceOn, face),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = model.faceOn, onCheckedChange = { model.setFace(it) }, modifier = Modifier.testTag("face_switch"))
    }
}

