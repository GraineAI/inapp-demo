package ai.graine.demo.ui.journey

import ai.graine.demo.domain.OwnText
import ai.graine.demo.journey.JourneyAgent
import ai.graine.demo.journey.JourneyModel
import ai.graine.demo.ui.own.OwnIcons
import ai.graine.inapp.kmp.GraineCallInfo
import ai.graine.inapp.kmp.GraineCallState
import ai.graine.inapp.kmp.GraineRole
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

private val Background = Color(0xFF0E1512)
private val OnBackground = Color(0xFFF1F5F3)
private val Muted = Color(0xFF9AA9A2)
private val Accept = Color(0xFF16A34A)
private val Hangup = Color(0xFFDC2626)

/**
 * THE JOURNEY'S CALL SCREEN, while a call rings, connects or is on — drawn by the app from `Graine.calls.state`. iOS
 * draws it in place over the app (CallKit rings first); on Android it is the app's own call activity
 * (`GraineCallsConfig(androidCallActivity = …)`), which the SDK opens for every call. A call that ends stays a
 * moment, then [onGone] hears that nothing is left. A missed ring is the journey's own screen, not this one:
 * [onMissed] hears it (Android's activity opens the app for its choices).
 */
@Composable
fun JourneyCallHost(journey: JourneyModel, modifier: Modifier = Modifier, onGone: () -> Unit = {}, onMissed: () -> Unit = {}) {
    val info = journey.call
    val live = info.state == GraineCallState.RINGING || info.state == GraineCallState.CONNECTING || info.state == GraineCallState.ACTIVE
    var last by remember { mutableStateOf(info) }
    if (live) last = info
    var lingering by remember { mutableStateOf(false) }
    var everShown by remember { mutableStateOf(false) }
    LaunchedEffect(live, info.missedCall) {
        when {
            live -> { everShown = true; lingering = false }
            info.missedCall != null -> { lingering = false; onMissed() }
            else -> {
                if (everShown) lingering = true
                delay(1_500)
                lingering = false
                onGone()
            }
        }
    }
    if (live || lingering) JourneyCallScreen(journey, if (live) info else last.copy(state = GraineCallState.ENDED), modifier)
}

@Composable
fun JourneyCallScreen(journey: JourneyModel, info: GraineCallInfo, modifier: Modifier = Modifier) {
    val j = journey.journey
    val firstCall = info.agent == JourneyAgent.FIRST_CALL.sdkName
    val name = when (JourneyAgent.ofSdkName(info.agent)) {
        JourneyAgent.FIRST_CALL -> j.agents.firstCall.name
        JourneyAgent.SUPPORT -> j.agents.support.name
        else -> info.callerName ?: j.agents.coach.name
    }
    val title = if (firstCall) j.firstCall.incomingTitle else info.reason?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: j.agents.coach.role
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(info.startedAt) {
        while (true) {
            now = Clock.System.now()
            delay(1_000)
        }
    }
    Surface(modifier.fillMaxSize().testTag("journey_call"), color = Background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                when (info.state) {
                    GraineCallState.RINGING -> "Incoming call"
                    GraineCallState.CONNECTING -> if (info.incoming) "Connecting…" else "Calling…"
                    GraineCallState.ACTIVE -> if (info.reconnecting) "Reconnecting…" else OwnText.elapsed(info.startedAt, now) ?: "0:00"
                    else -> "Call ended"
                },
                color = Muted,
                fontFamily = if (info.state == GraineCallState.ACTIVE) FontFamily.Monospace else FontFamily.Default,
                fontSize = 16.sp,
                modifier = Modifier.testTag("journey_call_status"),
            )
            Spacer(Modifier.height(28.dp))
            Box(Modifier.size(104.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimary, fontSize = 44.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
            Text(name, color = OnBackground, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(title, color = Muted, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.testTag("journey_call_title"))

            Spacer(Modifier.weight(1f))
            // The current topic: the agent's last line on the call (its caption).
            val topic = info.captions.lastOrNull { it.role == GraineRole.AGENT }?.text ?: info.caption?.takeIf { it.role == GraineRole.AGENT }?.text
            if (info.state == GraineCallState.ACTIVE && topic != null) {
                Column(
                    Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)).padding(16.dp).testTag("journey_call_topic"),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Current topic", color = Muted, style = MaterialTheme.typography.labelMedium)
                    Text(topic, color = OnBackground, style = MaterialTheme.typography.bodyLarge, maxLines = 4)
                }
            }
            Spacer(Modifier.height(32.dp))
            when (info.state) {
                GraineCallState.RINGING -> Row(horizontalArrangement = Arrangement.spacedBy(64.dp)) {
                    Round("Decline", OwnIcons.CallEnd, Hangup, "journey_call_decline") { journey.declineCall() }
                    Round("Answer", OwnIcons.Call, Accept, "journey_call_answer") { journey.answerCall() }
                }
                GraineCallState.CONNECTING, GraineCallState.ACTIVE -> Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                    Toggle(if (info.muted) "Unmute" else "Mute", if (info.muted) OwnIcons.MicOff else OwnIcons.Mic, info.muted, "journey_call_mute") { journey.setMuted(it) }
                    Round("End", OwnIcons.CallEnd, Hangup, "journey_call_end") { journey.endCall() }
                    Toggle("Speaker", OwnIcons.Speaker, info.speakerOn, "journey_call_speaker") { journey.setSpeaker(it) }
                }
                else -> Spacer(Modifier.height(88.dp))
            }
        }
    }
}

@Composable
private fun Round(label: String, icon: ImageVector, color: Color, tag: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = Color.White),
            modifier = Modifier.size(68.dp).testTag(tag),
        ) { Icon(icon, contentDescription = label, modifier = Modifier.size(30.dp)) }
        Text(label, color = OnBackground, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Toggle(label: String, icon: ImageVector, on: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalIconToggleButton(checked = on, onCheckedChange = onChange, modifier = Modifier.size(56.dp).testTag(tag)) {
            Icon(icon, contentDescription = label)
        }
        Text(label, color = OnBackground, style = MaterialTheme.typography.labelMedium)
    }
}
