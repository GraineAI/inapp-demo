package ai.graine.demo.ui

import ai.graine.demo.domain.ItemsState
import ai.graine.demo.domain.Tone
import ai.graine.demo.domain.UseCasePack
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Items: the pack's list ("Medicines", "Cards", "Orders") for the signed-in
 * user. A row opens in place to show its fields and its buttons; the agent
 * works on the same rows (open_item, set_status) and reads them as the
 * screen's fields.
 */
@Composable
fun ItemsScreen(model: AppModel) {
    val pack = model.pack ?: return
    val state = model.items ?: return
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
        item {
            Text(pack.items.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
        }
        if (state.rows.isEmpty()) {
            item { Text(pack.items.emptyText, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(state.rows, key = { it.id }) { row ->
            ItemRow(model, pack, state, row)
            HorizontalDivider()
        }
    }
}

@Composable
private fun ItemRow(model: AppModel, pack: UseCasePack, state: ItemsState, row: UseCasePack.Row) {
    val expanded = model.expandedRowId == row.id
    Column(
        Modifier.fillMaxWidth().clickable { model.toggleExpanded(row) }.padding(horizontal = 16.dp, vertical = 12.dp).testTag("item_${row.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.title, fontWeight = FontWeight.SemiBold)
                if (row.subtitle.isNotEmpty()) Text(row.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.statusOf(row)?.let { StatusChip(it, if (it == row.status) Tone.NEUTRAL else Tone.POSITIVE) }
        }
        if (expanded) {
            row.fields.forEach { field ->
                Row {
                    Text(field.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(field.value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val buttons = state.buttons(row, pack)
            if (buttons.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    buttons.forEach { action ->
                        Button(onClick = { model.press(action, row) }, modifier = Modifier.testTag("action_${action.name}_${row.id}")) { Text(action.label) }
                    }
                }
            }
            TextButton(onClick = { model.startChat("I have a question about ${row.title}.") }) { Text("Ask ${pack.agentName} about this") }
        }
    }
}
