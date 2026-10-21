package ai.graine.demo.ui.own

import ai.graine.demo.domain.CallText
import ai.graine.demo.domain.DemoFormat
import ai.graine.demo.domain.OwnBooking
import ai.graine.demo.domain.OwnText
import ai.graine.demo.ui.AppModel
import ai.graine.demo.ui.StatusChip
import ai.graine.demo.ui.WarningText
import ai.graine.demo.ui.nowMs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * BOOKING, DRAWN BY THE APP — the SDK's own rule and times, in the app's own
 * picker: whether this agent can be booked here (`bookingAvailable`, and
 * `bookingUnavailableReason` in plain words when not), its rules, a day picker
 * with the times `bookingSlots(day)` offers, what the call is about, and Book
 * (`scheduleCall`); then the user's calls with this agent, each with Cancel.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OwnBookingScreen(model: AppModel) {
    val b = model.ownBooking ?: return
    val pack = model.pack ?: return
    val name = model.nameOf(b.agent)
    val zone = TimeZone.currentSystemDefault()
    val today = Instant.fromEpochMilliseconds(nowMs()).toLocalDateTime(zone).date

    OwnScreen(
        title = "Book a call",
        subtitle = "With $name · drawn by this app",
        onBack = { model.closeOwnBooking() },
        modifier = Modifier.testTag("own_booking"),
        actions = {
            IconButton(onClick = { model.refreshOwnBooking() }, modifier = Modifier.testTag("own_booking_refresh")) {
                Icon(OwnIcons.Refresh, contentDescription = "Read again")
            }
        },
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "availability") {
                val why = OwnText.bookingUnavailable(b.unavailableReason)
                if (why != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Booking is off for this phone", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.testTag("own_booking_reason"))
                        }
                    }
                } else {
                    Text(
                        "$name calls you at the time you choose. It rings like a phone call, even when the app is closed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(OwnText.rulesLine(b.rules, zone), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }

            item(key = "days") {
                OwnLabel("Day")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp).testTag("own_booking_days")) {
                    items(b.days, key = { it.date.toString() }) { day ->
                        FilterChip(
                            selected = day.date == b.day,
                            enabled = day.slotCount > 0,
                            onClick = { model.pickBookingDay(day.date) },
                            label = { Text(OwnText.dayLabel(day.date, today)) },
                        )
                    }
                }
            }

            item(key = "times") {
                // The day's times, every 5 minutes: the hour first, then the minutes within it.
                val hours = OwnText.byHour(b.slots, zone)
                val slotHour = b.slot?.toLocalDateTime(zone)?.hour
                var hour by remember(b.day) { mutableStateOf(slotHour ?: hours.firstOrNull()?.first) }
                val shown = hours.firstOrNull { it.first == hour } ?: hours.firstOrNull()
                OwnLabel("Time")
                if (hours.isEmpty()) {
                    Text("No times on this day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 6.dp).testTag("own_booking_hours"),
                ) {
                    hours.forEach { (h, _) ->
                        FilterChip(selected = h == shown?.first, onClick = { hour = h }, label = { Text(OwnText.hourLabel(h)) })
                    }
                }
                shown?.let { (_, times) ->
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(top = 8.dp).testTag("own_booking_times"),
                    ) {
                        times.forEach { at ->
                            FilterChip(
                                selected = at == b.slot,
                                onClick = { model.pickBookingSlot(at) },
                                label = { Text(OwnText.slotLabel(at, zone)) },
                                modifier = Modifier.testTag("own_booking_slot"),
                            )
                        }
                    }
                }
            }

            item(key = "reason") {
                OwnLabel("What's it about? (optional)")
                OutlinedTextField(
                    value = b.reason,
                    onValueChange = { model.setBookingReason(it) },
                    singleLine = true,
                    supportingText = { Text("${b.reason.length}/${OwnBooking.REASON_MAX}") },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag("own_booking_about"),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pack.reasons(b.agent).forEach { r -> SuggestionChip(onClick = { model.setBookingReason(r) }, label = { Text(r) }) }
                }
            }

            item(key = "book") {
                Button(onClick = { model.bookOwn() }, enabled = b.canBook, modifier = Modifier.fillMaxWidth().testTag("own_booking_book")) {
                    Text(
                        when {
                            b.busy -> "Booking…"
                            b.slot != null -> "Book ${DemoFormat.time(b.slot, nowMs())}"
                            else -> "Pick a time"
                        },
                    )
                }
                b.refusal?.let { WarningText(it, Modifier.padding(top = 6.dp).testTag("own_booking_refusal")) }
                b.booked?.let {
                    Text(
                        "Booked: ${DemoFormat.time(it.at, nowMs())}. $name will call you then.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp).testTag("own_booking_done"),
                    )
                }
            }

            val mine = model.upcomingCalls.filter { it.agent == b.agent }
            item(key = "mine") {
                HorizontalDivider()
                OwnLabel("Your calls with $name", Modifier.padding(top = 10.dp))
                if (mine.isEmpty()) {
                    Text(if (model.callsLoading) "Loading…" else "No calls booked.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }
            items(mine, key = { "call-" + it.id }) { item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag("own_booking_call")) {
                    Column(Modifier.weight(1f)) {
                        Text(DemoFormat.time(item.call.at, nowMs()), fontWeight = FontWeight.SemiBold)
                        item.call.reason?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    StatusChip(CallText.label(item.call.status), CallText.tone(item.call.status))
                    if (item.call.canCancel) TextButton(onClick = { model.cancelCall(item) }) { Text("Cancel") }
                }
            }
        }
    }
}
