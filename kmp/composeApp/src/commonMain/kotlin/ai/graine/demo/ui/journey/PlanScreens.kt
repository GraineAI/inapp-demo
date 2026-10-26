package ai.graine.demo.ui.journey

import ai.graine.demo.journey.ItemRef
import ai.graine.demo.journey.JourneyModel
import ai.graine.demo.journey.JourneyText
import ai.graine.demo.journey.JourneyTime
import ai.graine.demo.journey.PlanItem
import ai.graine.demo.journey.PlanText
import ai.graine.demo.journey.Weekday
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * THE PLAN IS BEING BUILT: the coach's chat is connected (`chat.shown(true)`), and once it is ONLINE the app reports
 * the first call's end (`Graine.reportEvent`); the coach saves the week with the plan action. A plan that is slow is
 * offered again or skipped — this screen never spins for ever.
 */
@Composable
fun BuildingPlanScreen(journey: JourneyModel) {
    val b = journey.journey.plan.building
    var shown by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (shown < b.steps.size) {
            delay(2_500)
            shown++
        }
    }
    StepScreen(
        title = b.title,
        body = b.body,
        footer = {
            if (journey.planSlow) {
                Text(b.slow, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("journey_plan_slow"))
                PrimaryButton("Ask again", { journey.askForPlanAgain() }, tag = "journey_plan_ask_again")
                SecondaryButton("Go to the chat", { journey.skipToChat() }, tag = "journey_plan_skip")
            }
            if (journey.testingTools) QuietButton("Load sample plan", { journey.loadSamplePlan() }, tag = "journey_load_sample")
        },
    ) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        b.steps.take(shown).forEachIndexed { i, s ->
            val done = i < shown - 1 || journey.planSlow
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (done) Icon(JourneyIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                else CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Text(s, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** MY WEEK: the day's targets, the days as tabs, each day's timed items; an item opens its own screen. */
@Composable
fun WeekScreen(journey: JourneyModel, onMenu: () -> Unit) {
    val plan = journey.plan
    val copy = journey.journey.plan
    JourneyScreen(
        title = copy.weekTitle,
        subtitle = plan?.title,
        onMenu = onMenu,
        actions = {
            if (plan != null) IconButton(onClick = { journey.openShare(true) }, modifier = Modifier.testTag("journey_share_open")) {
                Icon(JourneyIcons.Share, contentDescription = copy.shareTitle)
            }
        },
    ) {
        if (plan == null) {
            Text("Your plan appears here once it is ready.", style = MaterialTheme.typography.bodyMedium)
            return@JourneyScreen
        }
        Targets(journey)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Weekday.entries.toList(), key = { it.wire }) { d ->
                FilterChip(
                    selected = journey.weekDay == d,
                    onClick = { journey.showDay(d) },
                    label = { Text(d.short) },
                    modifier = Modifier.testTag("journey_day_tab_${d.wire}"),
                )
            }
        }
        val items = plan.items(journey.weekDay)
        if (items.isEmpty()) Text("Nothing planned for ${journey.weekDay.label}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        items.forEach { item -> ItemRow(journey, journey.weekDay, item) { journey.openItem(ItemRef(journey.weekDay, item.time)) } }
    }
}

/** The plan's daily targets, labelled by the pack. */
@Composable
fun Targets(journey: JourneyModel) {
    val plan = journey.plan ?: return
    val copy = journey.journey.plan
    val shown = copy.targets.mapNotNull { m -> plan.targets[m.key]?.let { m to it } }
    if (shown.isEmpty()) return
    JourneyCard(title = copy.targetsTitle) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            shown.forEach { (m, v) ->
                Column {
                    Text(JourneyText.figure(v, m.unit), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(m.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** One timed item: its time, kind, title and figures; done or not when [done] says. */
@Composable
fun ItemRow(journey: JourneyModel, day: Weekday, item: PlanItem, done: Boolean? = null, onDone: ((Boolean) -> Unit)? = null, onClick: () -> Unit) {
    val copy = journey.journey.plan
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp).testTag("journey_item_${day.wire}_${item.time}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(JourneyTime.spoken(item.time), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 4.dp))
        Column(Modifier.weight(1f)) {
            Text(copy.kindLabel(item.kind), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            val figures = copy.metrics.mapNotNull { m -> item.figures[m.key]?.let { JourneyText.figure(it, m.unit) } }
            if (figures.isNotEmpty()) Text(figures.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onDone != null) {
            IconButton(onClick = { onDone(done != true) }, modifier = Modifier.testTag("journey_done_${item.time}")) {
                Icon(
                    if (done == true) JourneyIcons.CheckCircle else JourneyIcons.Circle,
                    contentDescription = if (done == true) "Done" else "Mark done",
                    tint = if (done == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Icon(JourneyIcons.Chevron, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider()
}

/**
 * AN ITEM: why it fits, its details and figures, and its swaps — "Use selected swap" changes the plan and tells the
 * coach (`Graine.track`). The screen tells the agent which item is on it (`Graine.setScreen`).
 */
@Composable
fun ItemScreen(journey: JourneyModel, ref: ItemRef) {
    val item = journey.plan?.item(ref.day, ref.time) ?: return
    val copy = journey.journey.plan
    var chosen by remember(item) { mutableStateOf<String?>(null) }
    JourneyScreen(
        title = item.title,
        subtitle = "${ref.day.label} · ${JourneyTime.spoken(ref.time)} · ${copy.kindLabel(item.kind)}",
        onBack = { journey.closeItem() },
        modifier = Modifier.testTag("journey_item_screen"),
    ) {
        val figures = copy.metrics.mapNotNull { m -> item.figures[m.key]?.let { m to it } }
        if (figures.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                figures.forEach { (m, v) ->
                    Column {
                        Text(JourneyText.figure(v, m.unit), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(m.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item.why?.let { JourneyCard(title = copy.item.why) { Text(it, style = MaterialTheme.typography.bodyMedium) } }
        item.details?.let { JourneyCard(title = copy.item.details) { Text(it, style = MaterialTheme.typography.bodyMedium) } }
        item.swappedFrom?.let {
            Text("Changed from $it" + (item.swapReason?.let { r -> " · $r" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (item.swaps.isNotEmpty()) {
            SectionLabel(copy.item.swaps)
            item.swaps.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().clickable { chosen = s }.testTag("journey_swap"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = chosen == s, onClick = { chosen = s })
                    Text(s, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 4.dp))
                }
            }
            PrimaryButton(copy.item.useSwap, { chosen?.let { journey.useSwap(ref, it) } }, enabled = chosen != null, tag = "journey_use_swap")
        }
        SecondaryButton(copy.item.ask, { journey.askAbout(ref) }, tag = "journey_ask_about")
    }
}

/** The plan as it is shared: the text the share sheet sends, and Share. */
@Composable
fun ShareScreen(journey: JourneyModel) {
    val plan = journey.plan ?: return
    val copy = journey.journey.plan
    JourneyScreen(title = copy.shareTitle, onBack = { journey.openShare(false) }, modifier = Modifier.testTag("journey_share")) {
        JourneyCard { Text(PlanText.share(journey.journey, plan), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
        PrimaryButton("Share", { journey.sharePlan() }, tag = "journey_share_send")
    }
}
