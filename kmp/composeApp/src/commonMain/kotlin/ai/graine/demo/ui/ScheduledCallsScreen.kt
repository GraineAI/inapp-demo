package ai.graine.demo.ui

import ai.graine.demo.domain.AgentCall
import ai.graine.demo.domain.CallText
import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.DemoFormat
import ai.graine.demo.domain.DemoScreen
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
 * Scheduled calls: every agent's calls, each with the agent's name, its status chip and Cancel; and the
 * button that opens the SDK's own booking sheet for the main agent (the Help tab books with the help desk).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledCallsScreen(model: AppModel) {
    val pack = model.pack ?: return
    var cancelling by remember { mutableStateOf<AgentCall?>(null) }
    LaunchedEffect(model.user?.id) {
        model.refreshBooking()
        model.refreshCalls()
    }
    val canBook = model.canBook(DemoAgent.MAIN)

    PullToRefreshBox(isRefreshing = model.callsLoading, onRefresh = { model.refreshCalls() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(pack.call.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            item {
                Button(
                    onClick = { model.book(DemoAgent.MAIN) },
                    enabled = canBook && !model.bookingOpen,
                    modifier = Modifier.fillMaxWidth().testTag("book_call"),
                ) { Text(pack.bookLabel(DemoAgent.MAIN)) }
                Text(
                    if (canBook) "${model.agentName} calls you at the time you choose. It rings like a phone call, even when the app is closed."
                    else CallText.BOOKING_UNAVAILABLE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp).testTag("book_hint"),
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

    cancelling?.let { item ->
        AlertDialog(
            onDismissRequest = { cancelling = null },
            title = { Text("Cancel this call?") },
            text = { Text("${model.nameOf(item.agent)} · ${DemoFormat.time(item.call.at, nowMs())}") },
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
                Text(DemoFormat.time(call.at, nowMs()), fontWeight = FontWeight.SemiBold)
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
