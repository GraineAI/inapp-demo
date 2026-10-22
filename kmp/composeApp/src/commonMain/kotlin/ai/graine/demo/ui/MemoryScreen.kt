package ai.graine.demo.ui

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Memory: what the assistant remembers about the signed-in user, the consent
 * switch, and erasing it all. Another user sees another list — memories
 * never cross people.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(model: AppModel) {
    val pack = model.pack ?: return
    val agent = pack.agentName
    var confirmErase by remember { mutableStateOf(false) }
    var erasing by remember { mutableStateOf(false) }
    LaunchedEffect(model.user?.id) { model.refreshMemories() }

    PullToRefreshBox(isRefreshing = model.memoriesLoading, onRefresh = { model.refreshMemories() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Memory", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Let $agent remember our conversations", modifier = Modifier.weight(1f))
                    Switch(checked = model.memoryOn, onCheckedChange = { model.setMemory(it) }, modifier = Modifier.testTag("memory_switch"))
                }
                Text(
                    if (model.agentMemoryEnabled == false) "Memory is turned off for this assistant, so nothing is remembered whatever you choose here."
                    else "Applies from your next conversation. What $agent remembers about you is never used for anyone else. " +
                        "Contact details, ID and card numbers, passwords and codes, dates of birth, health conditions, medicines and diagnoses are never kept.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Text("What $agent remembers about you", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
            val error = model.memoriesError
            when {
                error != null -> item { WarningText(error) }
                model.memories.isEmpty() -> item {
                    val example = pack.memoryExamples.firstOrNull()?.tell
                    Text(
                        when {
                            model.memoriesLoading -> "Loading…"
                            example != null -> "Nothing yet. Tell $agent something like “$example” in a chat or a call. " +
                                "About a minute after the conversation ends, it shows here (pull to refresh), and $agent knows it next time."
                            else -> "Nothing yet. Tell $agent about yourself in a chat or a call. " +
                                "About a minute after the conversation ends, it shows here (pull to refresh), and $agent knows it next time."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("memory_empty"),
                    )
                }
            }
            items(model.memories, key = { it.id }) { memory ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(memory.text)
                    (memory.updatedAt ?: memory.createdAt)?.let {
                        Text(DemoFormat.time(it, nowMs()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                }
            }
            item {
                OutlinedButton(
                    onClick = { confirmErase = true },
                    enabled = !erasing,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("erase_memory"),
                ) { Text(if (erasing) "Erasing…" else "Erase everything $agent remembers") }
                Text(
                    "This cannot be undone. Your ${pack.items.title.lowercase()} and scheduled calls are not affected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Erase everything $agent remembers about you?") },
            text = { Text("$agent will no longer recall anything from your earlier conversations.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmErase = false
                    erasing = true
                    model.forgetMemory { erasing = false }
                }) { Text("Erase") }
            },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Keep") } },
        )
    }
}
