package ai.graine.demo.ui.journey

import ai.graine.demo.domain.DemoFormat
import ai.graine.demo.journey.JourneyModel
import ai.graine.demo.journey.JourneyTime
import ai.graine.demo.journey.NoConnect
import ai.graine.demo.journey.PickerState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

/** THE FIRST CALL: Start now (`startCall(reason, agent = "consultation")`) or Choose a time (the picker). */
@Composable
fun FirstCallScreen(journey: JourneyModel) {
    val f = journey.journey.firstCall
    StepScreen(
        title = f.title,
        body = f.body,
        footer = {
            PrimaryButton(f.startNow, { journey.startFirstCall() }, tag = "journey_start_now")
            SecondaryButton(f.chooseTime, { journey.openPicker() }, tag = "journey_choose_time")
            if (!journey.firstCallReady) NotSetUp()
        },
    ) {
        Topics(journey)
    }
}

/** What the first call covers, from the pack's flow. */
@Composable
fun Topics(journey: JourneyModel) {
    val topics = journey.journey.firstCall.topics
    if (topics.isEmpty()) return
    JourneyCard {
        topics.forEachIndexed { i, t ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(24.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(t, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun NotSetUp() {
    Text(
        "The first-call agent is not set up in this build: add its publishable key (graine.consultationPublishableKey, or GRAINE_CONSULTATION_PUBLISHABLE_KEY on iOS).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

/** A first call booked for later: when it rings, and the way to change it or call now. Its fate is read again every minute. */
@Composable
fun BookedScreen(journey: JourneyModel) {
    val b = journey.journey.firstCall.booked
    val at = journey.booked?.at
    // Nothing to ask before its time (a ring arrives by itself). Past it without a ring or an outcome here (a closed
    // app, a lost push), Graine is asked what became of it, once a minute while this screen is open.
    LaunchedEffect(at) {
        if (at == null) return@LaunchedEffect
        while (true) {
            val wait = at + 60_000 - journey.nowMs()
            delay(if (wait > 0) wait else 60_000)
            journey.onForeground()
        }
    }
    StepScreen(
        title = b.title,
        body = b.body.replace("{time}", at?.let { DemoFormat.time(kotlinx.datetime.Instant.fromEpochMilliseconds(it), journey.nowMs()).lowercaseFirstWord() } ?: "soon"),
        footer = {
            SecondaryButton(b.change, { journey.openPicker() }, tag = "journey_change_time")
            QuietButton(b.startNow, { journey.startFirstCall() }, tag = "journey_start_now_instead")
        },
    ) {
        Topics(journey)
    }
}

/**
 * "WE COULDN'T CONNECT": a missed, declined, failed, busy or cut-short first call, or a booked one that could not ring.
 * Call me now (`Graine.calls.callBack()`, else a new call), Choose another time (the picker), Continue in chat.
 */
@Composable
fun CouldNotConnectScreen(journey: JourneyModel) {
    val c = journey.journey.firstCall.couldNotConnect
    val body = when (journey.noConnect) {
        NoConnect.DECLINED -> c.declined
        NoConnect.ENDED_EARLY -> c.endedEarly
        NoConnect.BUSY -> c.busy
        NoConnect.NOT_REACHED -> c.notReached
        NoConnect.MISSED, NoConnect.FAILED, null -> c.body
    }
    StepScreen(
        title = c.title,
        body = body,
        footer = {
            PrimaryButton(c.callNow, { journey.callMeNow() }, tag = "journey_call_me_now")
            SecondaryButton(c.chooseTime, { journey.openPicker() }, tag = "journey_choose_another_time")
            SecondaryButton(c.chat, { journey.continueInChat() }, tag = "journey_continue_in_chat")
        },
    ) {
        Topics(journey)
    }
}

/** The first call is ringing or live: the call screen is in front; this is what is behind it. */
@Composable
fun InCallScreen(journey: JourneyModel) {
    val f = journey.journey.firstCall
    StepScreen(title = f.incomingTitle, body = "${journey.journey.agents.firstCall.name} · ${journey.journey.agents.firstCall.role}") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("The call is on the call screen.", style = MaterialTheme.typography.bodyMedium)
        }
        Topics(journey)
    }
}

/**
 * THE TIME PICKER: day chips, then the few times the pack prefers on that day out of every time the first-call
 * agent's rules allow (`bookingSlots(day)`), then Confirm (`scheduleCall(at, reason)`). A refusal is the SDK's own line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PickerScreen(journey: JourneyModel, picker: PickerState) {
    val p = journey.journey.firstCall.picker
    val zone = TimeZone.currentSystemDefault()
    val booking = journey.firstCallBooking
    JourneyScreen(title = p.title, onBack = { journey.closePicker() }, modifier = Modifier.testTag("journey_picker")) {
        Text(p.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!booking.available) {
            Text(bookingReason(booking.reason), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("journey_picker_unavailable"))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(picker.days, key = { it.date.toString() }) { day ->
                val today = journey.todayDate()
                val label = when (day.date) {
                    today -> "Today"
                    today.plus(1, DateTimeUnit.DAY) -> "Tomorrow"
                    else -> "${day.date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${day.date.dayOfMonth} ${MONTHS[day.date.monthNumber - 1]}"
                }
                FilterChip(
                    selected = picker.day == day.date,
                    onClick = { journey.pickDay(day.date) },
                    enabled = day.times.isNotEmpty(),
                    label = { Text(label) },
                    modifier = Modifier.testTag("journey_day_${day.date}"),
                )
            }
        }
        if (picker.times.isEmpty()) {
            Text(p.none, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                picker.times.forEach { at ->
                    FilterChip(
                        selected = picker.time == at,
                        onClick = { journey.pickTime(at) },
                        label = { Text(JourneyTime.spoken(JourneyTime.of(at, zone)), fontWeight = FontWeight.Medium) },
                        modifier = Modifier.testTag("journey_time"),
                    )
                }
            }
        }
        picker.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("journey_picker_error")) }
        PrimaryButton(p.confirm, { journey.confirmPicker() }, enabled = picker.time != null && booking.available, busy = picker.busy, tag = "journey_confirm_time")
    }
}

/** Why the first-call agent cannot be booked now, in plain words (the SDK's bookingState reason). */
fun bookingReason(reason: String?): String = when (reason) {
    "not_loaded", null -> "Loading the times…"
    "rings_not_set_up" -> "Calls can't ring this phone yet: the workspace's in-app calling credentials are missing for this app."
    "scheduling_off" -> "This agent doesn't take bookings from the app (Calls & scheduling → Users, from your app)."
    "not_identified" -> "Finish your profile first: the call is booked for you."
    else -> "Booking isn't available right now."
}

/** "Today, 10:30 am" → "today, 10:30 am", for the middle of a sentence. */
private fun String.lowercaseFirstWord(): String = if (startsWith("Today") || startsWith("Tomorrow")) replaceFirstChar { it.lowercase() } else "on $this"
