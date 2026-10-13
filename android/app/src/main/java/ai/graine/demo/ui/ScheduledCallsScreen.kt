package ai.graine.demo.ui

import ai.graine.demo.domain.AgentCall
import ai.graine.demo.domain.Booking
import ai.graine.demo.domain.CallText
import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.DemoFormat
import ai.graine.demo.domain.DemoScreen
import android.text.format.DateFormat as AndroidDateFormat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** Scheduled calls: every agent's calls, each with the agent's name, its status chip and Cancel; and booking one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledCallsScreen(model: AppModel) {
    val pack = model.pack ?: return
    var booking by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf<AgentCall?>(null) }
    LaunchedEffect(model.user?.id) { model.refreshCalls() }
    val canBook = model.graine.agents.any { model.rulesFor(it).usersCanBook }

    PullToRefreshBox(isRefreshing = model.callsLoading, onRefresh = { model.refreshCalls() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(pack.call.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            item {
                Button(onClick = { booking = true }, enabled = canBook, modifier = Modifier.fillMaxWidth().testTag("book_call")) {
                    Text(pack.call.bookLabel)
                }
                Text(
                    if (canBook) "The assistant calls you at the time you choose. It rings like a phone call, even when the app is closed."
                    else CallText.SCHEDULING_OFF,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            model.callsError?.let { item { WarningText(it) } }

            val upcoming = model.upcomingCalls
            item { Text("Upcoming", style = MaterialTheme.typography.titleSmall) }
            if (upcoming.isEmpty()) {
                item { Text(if (model.callsLoading) "Loading…" else "No upcoming calls.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(upcoming, key = { it.id }) { item -> CallRow(model, item, onCancel = { cancelling = item }) }

            val past = model.pastCalls
            if (past.isNotEmpty()) {
                item { Text("Earlier", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
                items(past, key = { it.id }) { item -> CallRow(model, item, onCancel = null) }
            }
            item {
                TextButton(onClick = { model.open(DemoScreen.CALL_LOG) }) { Text("Call activity") }
            }
        }
    }

    if (booking) BookingSheet(model, DemoAgent.MAIN) { booking = false }

    cancelling?.let { item ->
        AlertDialog(
            onDismissRequest = { cancelling = null },
            title = { Text("Cancel this call?") },
            text = { Text("${model.nameOf(item.agent)} · ${DemoFormat.time(item.call.at, System.currentTimeMillis())}") },
            confirmButton = { TextButton(onClick = { model.cancelCall(item); cancelling = null }) { Text("Cancel call") } },
            dismissButton = { TextButton(onClick = { cancelling = null }) { Text("Keep it") } },
        )
    }
}

@Composable
private fun CallRow(model: AppModel, item: AgentCall, onCancel: (() -> Unit)?) {
    val call = item.call
    val agentName = model.nameOf(item.agent)
    Column(Modifier.fillMaxWidth().testTag("call_${item.id}")) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(DemoFormat.time(call.at, System.currentTimeMillis()), fontWeight = FontWeight.SemiBold)
                Text(agentName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("call_agent_${item.id}"))
                call.reason?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                CallText.bookedBy(call.source, agentName)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusChip(CallText.label(call.status), CallText.tone(call.status))
                if (onCancel != null && call.canCancel) {
                    TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel_${item.id}")) { Text("Cancel") }
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

/**
 * The booking sheet: which agent calls (when two are configured), one of the
 * pack's reasons, its quick-time chips, and the platform's own date and time
 * pickers, moved onto 5-minute steps and kept inside that agent's earliest and
 * latest from the SDK, in the device's time zone. The SDK checks the time
 * again before it books; whatever it or the server refuses comes back in
 * plain words.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingSheet(model: AppModel, initialAgent: DemoAgent, onClose: () -> Unit) {
    val pack = model.pack ?: return
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    var agent by remember { mutableStateOf(initialAgent) }
    val rules = model.rulesFor(agent)
    val range = model.bookingRange(agent)
    var whenMs by remember { mutableLongStateOf(Booking.clamp(model.suggestedCallTime(initialAgent), model.bookingRange(initialAgent))) }
    var reason by remember { mutableStateOf(pack.reasons(initialAgent).firstOrNull()) }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(pack.bookLabel(agent), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

            if (model.graine.agents.size > 1) {
                Text("Who should call you", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    model.graine.agents.forEach { a ->
                        FilterChip(
                            selected = agent == a,
                            onClick = {
                                agent = a
                                whenMs = Booking.clamp(whenMs, model.bookingRange(a))
                                // Each agent books with its own reasons.
                                if (reason !in pack.reasons(a)) reason = pack.reasons(a).firstOrNull()
                            },
                            label = { Text(model.nameOf(a)) },
                            modifier = Modifier.testTag("booking_agent_${a.key}"),
                        )
                    }
                }
            }

            Text("Reason", style = MaterialTheme.typography.titleSmall)
            pack.reasons(agent).forEach { r ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = reason == r, onClick = { reason = r }).testTag("reason_$r"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = reason == r, onClick = { reason = r })
                    Text(r)
                }
            }

            if (pack.call.quickTimes.isNotEmpty()) {
                Text("Quick times", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pack.call.quickTimes.forEach { q ->
                        val time = Booking.resolve(q, now)
                        val usable = time != null && Booking.isBookable(time, now, rules)
                        FilterChip(
                            selected = time == whenMs,
                            onClick = { if (time != null) whenMs = time },
                            label = { Text(q.label) },
                            enabled = usable,
                            modifier = Modifier.testTag("quick_${q.label}"),
                        )
                    }
                }
            }

            Text("Date and time", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.testTag("pick_date")) {
                    Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(whenMs)))
                }
                OutlinedButton(onClick = { pickingTime = true }, modifier = Modifier.testTag("pick_time")) {
                    Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(whenMs)))
                }
            }
            Text(Booking.zoneLabel(whenMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("time_zone"))
            Text(
                "Between ${rules.minLeadMinutes} minutes and ${rules.maxDaysAhead} days from now, within the assistant's calling hours.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { WarningText(it, Modifier.testTag("booking_error")) }

            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onClose) { Text("Close") }
                Button(
                    onClick = {
                        sending = true
                        error = null
                        model.scheduleCall(Booking.snapped(whenMs), reason, agent) { problem ->
                            sending = false
                            if (problem == null) onClose() else error = problem
                        }
                    },
                    enabled = !sending && rules.usersCanBook,
                    modifier = Modifier.testTag("book"),
                ) { Text(if (sending) "Booking…" else "Book") }
            }
        }
    }

    if (pickingDate) {
        val firstDay = Booking.utcDayOf(range.first)
        val lastDay = Booking.utcDayOf(range.last)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = Booking.utcDayOf(whenMs),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in firstDay..lastDay
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { day ->
                        val c = Calendar.getInstance().apply { timeInMillis = whenMs }
                        whenMs = Booking.clamp(Booking.combine(day, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)), range)
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (pickingTime) {
        val c = Calendar.getInstance().apply { timeInMillis = whenMs }
        val state = rememberTimePickerState(
            initialHour = c.get(Calendar.HOUR_OF_DAY),
            initialMinute = c.get(Calendar.MINUTE),
            is24Hour = AndroidDateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            title = { Text("Time") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    // The picker moves by the minute; a booking moves in 5-minute steps.
                    val picked = Booking.combine(Booking.utcDayOf(whenMs), state.hour, state.minute)
                    whenMs = Booking.clamp(Booking.snapped(picked), range)
                    pickingTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text("Cancel") } },
        )
    }
}
