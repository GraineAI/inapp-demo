package ai.graine.demo.ui.own

import ai.graine.demo.domain.OwnAnswer
import ai.graine.demo.domain.OwnComponents
import ai.graine.demo.domain.OwnWidget
import ai.graine.inapp.kmp.GraineAgent
import ai.graine.inapp.kmp.GraineComponent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * THE AGENT'S COMPONENT, DRAWN BY THE APP — the one waiting for an answer
 * (`agent.component`), or a loader while one is on its way
 * (`agent.componentPending`). Each control answers through
 * `chat.answerWidget(id, data, summary)` (on the live conversation: the chat,
 * else voice), and the close through `chat.dismissWidget(id)`; either way the
 * SDK lets the component go. Nothing is drawn when there is neither.
 */
@Composable
fun OwnComponentCard(agent: GraineAgent, component: GraineComponent?, pending: Boolean, modifier: Modifier = Modifier) {
    if (component == null) {
        if (pending) PendingCard(modifier)
        return
    }
    val widget = remember(component) { OwnComponents.read(component) }
    val answer: (OwnAnswer) -> Unit = { a -> agent.chat.answerWidget(component.id, a.data, a.summary) }
    Card(
        modifier.fillMaxWidth().testTag("own_component"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        component.kind ?: "component",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (widget.title.isNotEmpty()) Text(widget.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (widget.prompt.isNotEmpty()) Text(widget.prompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { agent.chat.dismissWidget(component.id) }, modifier = Modifier.size(32.dp).testTag("own_component_close")) {
                    Icon(OwnIcons.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }
            when (widget) {
                is OwnWidget.Chips -> Chips(widget, answer)
                is OwnWidget.Rows -> Rows(widget, answer)
                is OwnWidget.Calendar -> Calendar(widget, answer)
                is OwnWidget.Form -> Form(widget, answer)
                is OwnWidget.Fallback -> Text(widget.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PendingCard(modifier: Modifier) {
    Card(
        modifier.fillMaxWidth().testTag("own_component_pending"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Preparing…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(widget: OwnWidget.Chips, answer: (OwnAnswer) -> Unit) {
    // "Select all that apply" (select: many): tick several, then one answer.
    val picked = remember(widget) { mutableStateListOf<String>() }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        widget.chips.forEach { chip ->
            if (widget.many) {
                val on = chip.id in picked
                FilterChip(
                    selected = on,
                    onClick = { if (on) picked -= chip.id else if (picked.size < widget.maxSelect) picked += chip.id },
                    label = { Text(chip.label) },
                    modifier = Modifier.testTag("own_chip"),
                )
            } else {
                OutlinedButton(onClick = { answer(OwnComponents.chip(widget, chip)) }, modifier = Modifier.testTag("own_chip")) { Text(chip.label) }
            }
        }
    }
    if (widget.many) {
        Button(
            onClick = { OwnComponents.chips(widget, picked.toList())?.let(answer) },
            enabled = picked.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().testTag("own_chips_send"),
        ) { Text(widget.selectLabel.ifEmpty { "Done" } + if (picked.isEmpty()) "" else " (${picked.size})") }
    }
}

@Composable
private fun Rows(widget: OwnWidget.Rows, answer: (OwnAnswer) -> Unit) {
    val picked = remember(widget) { mutableStateListOf<String>() }
    widget.rows.forEachIndexed { i, row ->
        if (i > 0) HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (widget.many) {
                Checkbox(
                    checked = row.id in picked,
                    enabled = !row.unavailable && (row.id in picked || picked.size < widget.maxSelect),
                    onCheckedChange = { on -> if (on) picked += row.id else picked -= row.id },
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (row.subtitle.isNotEmpty()) Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                row.facts.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (row.price.isNotEmpty()) Text(row.price, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            if (!widget.many) {
                FilledTonalButton(onClick = { answer(OwnComponents.row(widget, row)) }, enabled = !row.unavailable, modifier = Modifier.testTag("own_row")) {
                    Text(row.selectLabel, fontSize = 13.sp)
                }
            }
        }
    }
    if (widget.many) {
        Button(
            onClick = { OwnComponents.many(widget, picked.toList())?.let(answer) },
            enabled = picked.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().testTag("own_rows_send"),
        ) { Text(if (picked.isEmpty()) widget.selectLabel else "${widget.selectLabel} (${picked.size})") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Calendar(widget: OwnWidget.Calendar, answer: (OwnAnswer) -> Unit) {
    widget.days.forEach { day ->
        Text(day.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            day.slots.forEach { slot ->
                OutlinedButton(onClick = { answer(OwnComponents.slot(day, slot)) }, modifier = Modifier.testTag("own_slot")) { Text(slot) }
            }
        }
    }
}

@Composable
private fun Form(widget: OwnWidget.Form, answer: (OwnAnswer) -> Unit) {
    val values = remember(widget) { mutableStateMapOf<String, String>() }
    widget.fields.forEach { field ->
        OutlinedTextField(
            value = values[field.name].orEmpty(),
            onValueChange = { values[field.name] = it },
            label = { Text(if (field.required) "${field.label} *" else field.label) },
            placeholder = if (field.placeholder.isNotEmpty()) ({ Text(field.placeholder) }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = when (field.type) {
                    "email" -> KeyboardType.Email
                    "tel", "phone" -> KeyboardType.Phone
                    "number" -> KeyboardType.Number
                    else -> KeyboardType.Text
                },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Button(
        onClick = { answer(OwnComponents.form(widget, values.toMap())) },
        enabled = OwnComponents.formComplete(widget, values.toMap()),
        modifier = Modifier.fillMaxWidth().testTag("own_form_send"),
    ) { Text(widget.submitLabel) }
}
