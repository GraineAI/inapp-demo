package ai.graine.demo.ui.own

import ai.graine.demo.domain.OwnAnswer
import ai.graine.demo.domain.OwnAnswered
import ai.graine.demo.domain.OwnComponents
import ai.graine.demo.domain.OwnLook
import ai.graine.demo.domain.OwnText
import ai.graine.demo.domain.OwnWidget
import ai.graine.demo.ui.parseHex
import ai.graine.inapp.kmp.GraineAgent
import ai.graine.inapp.kmp.GraineComponent
import ai.graine.inapp.kmp.GraineComponentButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * THE AGENT'S COMPONENT, DRAWN BY THE APP — the one waiting for an answer
 * (`agent.component`), or a loader while one is on its way
 * (`agent.componentPending`). Each control answers through
 * `chat.answerWidget(id, data, summary)` (on the live conversation: the chat,
 * else voice), and the close through `chat.dismissWidget(id)`; either way the
 * SDK lets the component go. Nothing is drawn when there is neither.
 *
 * It follows the look the dashboard gave the component (`component.style`, SDK
 * 0.14.8) where a card in the thread can ([OwnComponents.look]): its accent and
 * corners (else the app's theme), its density, its item buttons (filled,
 * outlined, or the whole row), two columns, its fact lines, and one choice or
 * several. [onAnswered] hears each answer first, so the screen can keep it on
 * show ([AnsweredLine]) while the agent answers it.
 */
@Composable
fun OwnComponentCard(
    agent: GraineAgent,
    component: GraineComponent?,
    pending: Boolean,
    modifier: Modifier = Modifier,
    onAnswered: (OwnAnswer) -> Unit = {},
) {
    if (component == null) {
        if (pending) PendingCard(modifier)
        return
    }
    val widget = remember(component) { OwnComponents.read(component) }
    val skin = skinFor(remember(component, widget) { OwnComponents.look(component.style, widget) })
    val answer: (OwnAnswer) -> Unit = { a ->
        // The screen hears it before the SDK starts waiting for the agent's reply.
        onAnswered(a)
        agent.chat.answerWidget(component.id, a.data, a.summary)
    }
    Card(
        modifier.fillMaxWidth().testTag("own_component"),
        shape = RoundedCornerShape(skin.look.cardRadius.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        Column(Modifier.padding(skin.look.padding.dp), verticalArrangement = Arrangement.spacedBy(skin.gap)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        component.kind ?: "component",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = skin.accent,
                    )
                    if (widget.title.isNotEmpty()) Text(widget.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (widget.prompt.isNotEmpty()) Text(widget.prompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { agent.chat.dismissWidget(component.id) }, modifier = Modifier.size(32.dp).testTag("own_component_close")) {
                    Icon(OwnIcons.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }
            when (widget) {
                is OwnWidget.Chips -> Chips(widget, skin, answer)
                is OwnWidget.Rows -> Rows(widget, skin, answer)
                is OwnWidget.Calendar -> Calendar(widget, skin, answer)
                is OwnWidget.Form -> Form(widget, skin, answer)
                is OwnWidget.Fallback -> Text(widget.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * WHAT THE USER CHOSE, WHILE THE AGENT ANSWERS IT — drawn where the card was, which goes the moment the user
 * answers: their answer and a loader while `agent.componentAwaitingReply` (SDK 0.14.8) is true, until the agent's
 * reply starts (at most 12 seconds), so a tap never looks unheard.
 */
@Composable
fun AnsweredLine(chose: String, agentName: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("own_component_answered"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(chose, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(OwnText.waitingFor(agentName), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The user's last answer to [agent]'s components on this screen ([OwnAnswered]): hand [OwnComponentCard] an
 * `onAnswered` that records it, and draw [AnsweredLine] from its `line` while there is no card.
 */
@Composable
fun rememberAnswered(agent: GraineAgent, component: GraineComponent?): MutableState<OwnAnswered> {
    val answered = remember(agent) { mutableStateOf(OwnAnswered()) }
    LaunchedEffect(agent) { agent.componentAwaitingReply.collect { answered.value = answered.value.awaiting(it) } }
    LaunchedEffect(component?.id) { if (component != null) answered.value = answered.value.componentShown() }
    return answered
}

/** The look in Compose's terms: the accent and the colour on it, the controls' corners, the spacing. */
private class Skin(val look: OwnLook, val accent: Color, val onAccent: Color) {
    /** The controls' corners when the component sets a radius; null: each control's own shape. */
    val shape: Shape? = look.controlRadius?.let { RoundedCornerShape(it.dp) }
    val gap = look.gap.dp
    /** Between chips and between times: 8 as before, tighter when the look is compact. */
    val chipGap = minOf(look.gap, 8).dp
}

@Composable
private fun skinFor(look: OwnLook): Skin {
    val own = parseHex(look.accent)
    return Skin(look, own ?: MaterialTheme.colorScheme.primary, if (own == null) MaterialTheme.colorScheme.onPrimary else onAccentFor(own))
}

/** Black or white on the component's own accent: whichever contrasts more with it. */
internal fun onAccentFor(accent: Color): Color = if (accent.luminance() > 0.179f) Color.Black else Color.White

/** A button filled with the accent: a FILLED item button, and every Done and Send. */
@Composable
private fun AccentButton(skin: Skin, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = skin.shape ?: ButtonDefaults.shape,
        colors = ButtonDefaults.buttonColors(containerColor = skin.accent, contentColor = skin.onAccent),
        content = content,
    )
}

/** An outline in the accent: an OUTLINE item button, a choice, a time. */
@Composable
private fun AccentOutlinedButton(skin: Skin, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = skin.shape ?: ButtonDefaults.outlinedShape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = skin.accent),
        border = BorderStroke(1.dp, if (enabled) skin.accent else MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
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

/** "Choose up to 3", under several to tick when not all of them may be. */
@Composable
private fun UpTo(max: Int, of: Int) {
    if (max < of) Text("Choose up to $max", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(widget: OwnWidget.Chips, skin: Skin, answer: (OwnAnswer) -> Unit) {
    // "Select all that apply" (select: many): tick several, at most maxSelect, then one answer.
    val picked = remember(widget) { mutableStateListOf<String>() }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(skin.chipGap), verticalArrangement = Arrangement.spacedBy(skin.chipGap)) {
        widget.chips.forEach { chip ->
            if (widget.many) {
                val on = chip.id in picked
                FilterChip(
                    selected = on,
                    onClick = { if (on) picked -= chip.id else if (picked.size < widget.maxSelect) picked += chip.id },
                    label = { Text(chip.label) },
                    shape = skin.shape ?: FilterChipDefaults.shape,
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = skin.accent, selectedLabelColor = skin.onAccent),
                    modifier = Modifier.testTag("own_chip"),
                )
            } else {
                AccentOutlinedButton(skin, onClick = { answer(OwnComponents.chip(widget, chip)) }, modifier = Modifier.testTag("own_chip")) { Text(chip.label) }
            }
        }
    }
    if (widget.many) {
        UpTo(widget.maxSelect, widget.chips.size)
        AccentButton(
            skin,
            onClick = { OwnComponents.chips(widget, picked.toList())?.let(answer) },
            enabled = picked.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().testTag("own_chips_send"),
        ) { Text(widget.selectLabel.ifEmpty { "Done" } + if (picked.isEmpty()) "" else " (${picked.size})") }
    }
}

@Composable
private fun Rows(widget: OwnWidget.Rows, skin: Skin, answer: (OwnAnswer) -> Unit) {
    val picked = remember(widget) { mutableStateListOf<String>() }
    if (skin.look.grid) {
        // The look's two columns: each row a tile, as tall as the one beside it.
        Column(verticalArrangement = Arrangement.spacedBy(skin.gap)) {
            widget.rows.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(skin.gap)) {
                    pair.forEach { row -> RowItem(widget, row, skin, picked, answer, tile = true, modifier = Modifier.weight(1f).fillMaxHeight()) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    } else {
        widget.rows.forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider()
            RowItem(widget, row, skin, picked, answer, tile = false, modifier = Modifier.fillMaxWidth())
        }
    }
    if (widget.many) {
        UpTo(widget.maxSelect, widget.rows.size)
        AccentButton(
            skin,
            onClick = { OwnComponents.many(widget, picked.toList())?.let(answer) },
            enabled = picked.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().testTag("own_rows_send"),
        ) { Text(if (picked.isEmpty()) widget.selectLabel else "${widget.selectLabel} (${picked.size})") }
    }
}

/**
 * One row: its tick when several may be chosen, its words, and its button in the look's kind — filled, an outline,
 * or none, the whole row tapped. A [tile] stacks them, for two columns.
 */
@Composable
private fun RowItem(
    widget: OwnWidget.Rows,
    row: OwnWidget.Row,
    skin: Skin,
    picked: SnapshotStateList<String>,
    answer: (OwnAnswer) -> Unit,
    tile: Boolean,
    modifier: Modifier,
) {
    val pick = { answer(OwnComponents.row(widget, row)) }
    val wholeRow = !widget.many && skin.look.button == GraineComponentButton.ROW
    val framed = if (tile) {
        val shape = RoundedCornerShape((skin.look.controlRadius ?: 10).dp)
        val on = row.id in picked
        modifier.clip(shape).border(if (on) 2.dp else 1.dp, if (on) skin.accent else MaterialTheme.colorScheme.outlineVariant, shape)
    } else modifier
    val tapped = if (wholeRow) framed.heightIn(min = 44.dp).clickable(enabled = !row.unavailable, onClick = pick).testTag("own_row") else framed
    val tick: @Composable () -> Unit = {
        Checkbox(
            checked = row.id in picked,
            enabled = !row.unavailable && (row.id in picked || picked.size < widget.maxSelect),
            onCheckedChange = { on -> if (on) picked += row.id else picked -= row.id },
            colors = CheckboxDefaults.colors(checkedColor = skin.accent, checkmarkColor = skin.onAccent),
        )
    }
    if (tile) {
        Column(tapped.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (widget.many) tick()
            RowWords(row, skin.look)
            Spacer(Modifier.weight(1f))
            if (!widget.many) RowButton(row, skin, pick, Modifier.fillMaxWidth())
        }
    } else {
        Row(tapped, verticalAlignment = Alignment.CenterVertically) {
            if (widget.many) tick()
            RowWords(row, skin.look, Modifier.weight(1f))
            if (!widget.many) RowButton(row, skin, pick, Modifier)
        }
    }
}

@Composable
private fun RowWords(row: OwnWidget.Row, look: OwnLook, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(row.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        if (row.subtitle.isNotEmpty()) Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (look.facts) row.facts.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (row.price.isNotEmpty()) Text(row.price, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** A row's own button: filled with the accent, an outline in it, or — the whole row being tapped — its label and "›". */
@Composable
private fun RowButton(row: OwnWidget.Row, skin: Skin, pick: () -> Unit, modifier: Modifier) {
    when (skin.look.button) {
        GraineComponentButton.FILLED -> AccentButton(skin, onClick = pick, enabled = !row.unavailable, modifier = modifier.testTag("own_row")) {
            Text(row.selectLabel, fontSize = 13.sp)
        }
        GraineComponentButton.OUTLINE -> AccentOutlinedButton(skin, onClick = pick, enabled = !row.unavailable, modifier = modifier.testTag("own_row")) {
            Text(row.selectLabel, fontSize = 13.sp)
        }
        GraineComponentButton.ROW -> Text(
            "${row.selectLabel} ›",
            style = MaterialTheme.typography.labelLarge,
            color = if (row.unavailable) MaterialTheme.colorScheme.onSurfaceVariant else skin.accent,
            modifier = modifier.padding(start = 8.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Calendar(widget: OwnWidget.Calendar, skin: Skin, answer: (OwnAnswer) -> Unit) {
    widget.days.forEach { day ->
        Text(day.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(skin.chipGap), verticalArrangement = Arrangement.spacedBy(skin.chipGap)) {
            day.slots.forEach { slot ->
                AccentOutlinedButton(skin, onClick = { answer(OwnComponents.slot(day, slot)) }, modifier = Modifier.testTag("own_slot")) { Text(slot) }
            }
        }
    }
}

@Composable
private fun Form(widget: OwnWidget.Form, skin: Skin, answer: (OwnAnswer) -> Unit) {
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
            shape = skin.shape ?: OutlinedTextFieldDefaults.shape,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = skin.accent, focusedLabelColor = skin.accent, cursorColor = skin.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    AccentButton(
        skin,
        onClick = { answer(OwnComponents.form(widget, values.toMap())) },
        enabled = OwnComponents.formComplete(widget, values.toMap()),
        modifier = Modifier.fillMaxWidth().testTag("own_form_send"),
    ) { Text(widget.submitLabel) }
}
