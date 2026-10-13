package ai.graine.demo.ui

import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.DemoFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Date

/** Call activity: every native call the SDK reported on this device, and what became of it. */
@Composable
fun CallLogScreen(model: AppModel) {
    val agent = model.agentName
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { model.closeCallLog() }) { Text("Back") }
                Text("Call activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (model.callLog.entries.isNotEmpty()) TextButton(onClick = { model.clearCallLog() }) { Text("Clear") }
            }
        }
        if (model.callLog.entries.isEmpty()) {
            item {
                Text(
                    "No calls yet. Calls you place, and calls $agent makes to you, appear here with their outcome.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(model.callLog.entries, key = { it.id }) { entry ->
            Column {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.title(model.nameOf(DemoAgent.of(entry.agent) ?: DemoAgent.MAIN)))
                        Text("Call ${entry.callId.takeLast(8)}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(DemoFormat.time(Date(entry.at), System.currentTimeMillis()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }
        }
    }
}
