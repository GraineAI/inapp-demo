package ai.graine.demo.ui.journey

import ai.graine.demo.journey.ItemRef
import ai.graine.demo.journey.JourneyModel
import ai.graine.demo.journey.JourneyStats
import ai.graine.demo.journey.JourneyText
import ai.graine.demo.journey.JourneyTime
import ai.graine.demo.journey.Weekday
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** TODAY: what is done, the day's counter, the streak, what is up next, and today's items to tick. */
@Composable
fun TodayScreen(journey: JourneyModel, onMenu: () -> Unit) {
    val j = journey.journey
    val today = journey.todayDate()
    val day = Weekday.of(today)
    val count = JourneyStats.today(journey.plan, journey.activity, today)
    JourneyScreen(title = "Today", subtitle = "${day.label} ${today.dayOfMonth} ${MONTHS[today.monthNumber - 1]}", onMenu = onMenu) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            JourneyCard(Modifier.weight(1f)) {
                Text("${count.done} of ${count.of}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("journey_today_done"))
                Text("done today", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            JourneyCard(Modifier.weight(1f)) {
                Text("${JourneyStats.streak(journey.activity, today)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("journey_streak"))
                Text("day streak", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        j.today.tracker?.let { t ->
            val goal = journey.plan?.targets?.get(t.target)
            val value = journey.activity.tracker[today] ?: 0.0
            JourneyCard(title = t.label) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalIconButton(onClick = { journey.addToTracker(-1) }, modifier = Modifier.testTag("journey_tracker_minus")) { Icon(JourneyIcons.Minus, contentDescription = "Less") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            JourneyText.figure(value, t.unit) + (goal?.let { " of ${JourneyText.figure(it, t.unit)}" } ?: ""),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("journey_tracker_value"),
                        )
                        if (goal != null && goal > 0) LinearProgressIndicator(progress = { (value / goal).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                    FilledTonalIconButton(onClick = { journey.addToTracker(1) }, modifier = Modifier.testTag("journey_tracker_plus")) { Icon(JourneyIcons.Plus, contentDescription = "More") }
                }
            }
        }
        val next = JourneyStats.upNext(journey.plan, today, journey.timeNow())
        if (next != null) {
            val (date, item) = next
            JourneyCard(title = j.today.upNext, accent = true) {
                Text(item.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    (if (date == today) "" else "${Weekday.of(date).label}, ") + JourneyTime.spoken(item.time) + " · " + j.plan.kindLabel(item.kind),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (journey.plan != null && count.of > 0 && count.done == count.of) {
            Text(j.today.allDone, style = MaterialTheme.typography.bodyMedium)
        }
        val items = journey.plan?.items(day).orEmpty()
        if (items.isNotEmpty()) SectionLabel("Today's plan")
        items.forEach { item ->
            ItemRow(
                journey, day, item,
                done = journey.activity.followed(today, item.time),
                onDone = { journey.markDone(ItemRef(day, item.time), it) },
            ) { journey.openItem(ItemRef(day, item.time)) }
        }
        if (journey.plan == null) Text("Your plan appears here once it is ready.", style = MaterialTheme.typography.bodyMedium)
        if (journey.testingTools && journey.plan != null) {
            QuietButton("Simulate reminder", { journey.simulateReminder() }, tag = "journey_simulate_reminder")
        }
    }
}

/** PROGRESS: how much of the plan was followed this week, the measure's trend, and the weekly 1–5 check-in. */
@Composable
fun ProgressScreen(journey: JourneyModel, onMenu: () -> Unit) {
    val p = journey.journey.progress
    val week = JourneyStats.week(journey.plan, journey.activity, journey.todayDate(), journey.timeNow())
    JourneyScreen(title = "Progress", onMenu = onMenu) {
        JourneyCard(title = p.completion) {
            Text(week.percent?.let { "$it%" } ?: "—", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("journey_completion"))
            LinearProgressIndicator(progress = { (week.percent ?: 0) / 100f }, modifier = Modifier.fillMaxWidth())
            Text("${week.done} of ${week.of} in the last 7 days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        p.measure?.let { m ->
            var text by remember { mutableStateOf("") }
            var problem by remember { mutableStateOf<String?>(null) }
            val trend = journey.measureTrend()
            JourneyCard(title = m.label) {
                if (trend.size >= 2) {
                    val color = MaterialTheme.colorScheme.primary
                    Canvas(Modifier.fillMaxWidth().height(120.dp).testTag("journey_trend")) {
                        val lo = trend.minOf { it.value }
                        val hi = trend.maxOf { it.value }
                        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
                        val stepX = size.width / (trend.size - 1)
                        val path = Path()
                        trend.forEachIndexed { i, e ->
                            val x = i * stepX
                            val y = (size.height * (1 - (e.value - lo) / span)).toFloat().coerceIn(4f, size.height - 4f)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            drawCircle(color, radius = 5f, center = Offset(x, y))
                        }
                        drawPath(path, color, style = Stroke(width = 4f))
                    }
                }
                trend.lastOrNull()?.let { Text("Latest: ${JourneyText.figure(it.value, m.unit)} on ${it.date.dayOfMonth} ${MONTHS[it.date.monthNumber - 1]}", style = MaterialTheme.typography.bodyMedium) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' }.take(6) },
                        label = { Text("Today") },
                        suffix = { Text(m.unit) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("journey_measure_input"),
                    )
                    TextButton(onClick = {
                        problem = journey.logMeasure(text)
                        if (problem == null) text = ""
                    }, modifier = Modifier.testTag("journey_measure_log")) { Text("Log") }
                }
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
        JourneyCard(title = p.weeklyQuestion) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                (1..5).forEach { n ->
                    FilterChip(
                        selected = journey.weeklyScore == n,
                        onClick = { journey.weeklyCheckIn(n) },
                        label = { Text("$n") },
                        modifier = Modifier.weight(1f).testTag("journey_weekly_$n"),
                    )
                }
            }
            Text(journey.weeklyScore?.let { p.weeklyLabels[it - 1] } ?: "${p.weeklyLabels.first()} … ${p.weeklyLabels.last()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * ACCOUNT: the profile and language, reminders and quiet hours, and privacy — Download my data (what the coach
 * remembers, the conversations, the plan, as one file) and Delete account (forgetMemory on each agent, identify(null),
 * then everything on this phone erased).
 */
@Composable
fun AccountScreen(journey: JourneyModel, onMenu: () -> Unit) {
    val j = journey.journey
    val s = journey.settings
    var confirmDelete by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    JourneyScreen(title = "Account", onMenu = onMenu) {
        JourneyCard(title = journey.profile.values["name"] ?: "You") {
            j.onboarding.fields.filter { it.key != "name" }.forEach { f ->
                val v = journey.profile.values[f.key] ?: return@forEach
                val shown = f.options.firstOrNull { it.value == v }?.label ?: v
                Row(Modifier.fillMaxWidth()) {
                    Text(f.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(shown + (f.unit?.let { " $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
                }
            }
            journey.account?.let { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("User ID ${a.userId}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    TextButton(onClick = { clipboard.setText(AnnotatedString(a.userId)); journey.notice = "User ID copied." }) { Text("Copy") }
                }
            }
        }
        SectionLabel("Language")
        LanguageChoices(journey)
        SectionLabel("Reminders")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Check-in reminders", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = s.reminders, onCheckedChange = { journey.setReminders(it) }, modifier = Modifier.testTag("journey_reminders"))
        }
        Text("Quiet hours: no reminders, and no call times offered", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QUIET_STARTS.forEach { start ->
                FilterChip(selected = s.quietStart == start, onClick = { journey.setQuietHours(start, s.quietEnd) }, label = { Text("From ${JourneyTime.spoken(start)}") })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QUIET_ENDS.forEach { end ->
                FilterChip(selected = s.quietEnd == end, onClick = { journey.setQuietHours(s.quietStart, end) }, label = { Text("To ${JourneyTime.spoken(end)}") })
            }
        }
        SectionLabel("Your data")
        SecondaryButton(if (journey.exporting) "Preparing…" else "Download my data", { journey.exportData() }, enabled = !journey.exporting, tag = "journey_download")
        SecondaryButton("Delete account", { confirmDelete = true }, tag = "journey_delete")
        SectionLabel("Testing")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Testing tools", style = MaterialTheme.typography.bodyLarge)
                Text("Load sample plan, Simulate reminder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Always on in a debug build.
            Switch(checked = journey.testingTools, onCheckedChange = { journey.setTestingTools(it) }, enabled = !journey.isDebugBuild, modifier = Modifier.testTag("journey_testing_tools"))
        }
        QuietButton("Log out", { journey.logOut() }, tag = "journey_log_out")
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete your account?") },
            text = { Text("What ${j.agents.coach.name} remembers about you and your conversations are erased, you are signed out, and your plan is removed from this phone. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; journey.deleteAccount { ok -> if (ok) journey.notice = "Your account was deleted." } }, modifier = Modifier.testTag("journey_delete_confirm")) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private val QUIET_STARTS = listOf("21:00", "22:00", "23:00")
private val QUIET_ENDS = listOf("06:00", "07:00", "08:00")
