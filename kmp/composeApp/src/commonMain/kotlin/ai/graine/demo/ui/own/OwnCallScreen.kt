package ai.graine.demo.ui.own

import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.EndedCall
import ai.graine.demo.domain.OwnText
import ai.graine.demo.ui.AppModel
import ai.graine.inapp.kmp.Graine
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

/**
 * The app's own call screen while there is something to show — a ring, a call, a missed call, a call that just
 * ended — and nothing otherwise. A call that ends stays on screen a moment, so its outcome (with the final
 * connection report) can arrive; [onGone] hears when there is nothing left to show (Android's call activity
 * closes itself then). It also hears it once if nothing comes at all — an activity opened for a call already over.
 */
@Composable
fun OwnCallHost(model: AppModel, modifier: Modifier = Modifier, onGone: () -> Unit = {}) {
    val info by Graine.calls.state.collectAsState()
    val ended = model.endedCall
    val showing = info.showsCallScreen || ended != null
    var last by remember { mutableStateOf(info) }
    if (info.state != GraineCallState.IDLE || info.missedCall != null) last = info
    var lingering by remember { mutableStateOf(false) }
    var everShown by remember { mutableStateOf(false) }
    LaunchedEffect(showing) {
        if (showing) {
            everShown = true
            lingering = false
        } else {
            if (everShown) lingering = true
            delay(LINGER_MS)
            lingering = false
            onGone()
        }
    }
    // Close is the user's: nothing lingers after it.
    val close = {
        everShown = false
        lingering = false
        onGone()
    }
    if (showing || lingering) OwnCallScreen(model, if (showing) info else last, ended, modifier, onClose = close)
}

/** How long a call that just ended stays on screen, waiting for its outcome. */
private const val LINGER_MS = 2_000L

private val CallBackground = Color(0xFF101418)
private val CallText = Color(0xFFF1F5F9)
private val CallMuted = Color(0xFF94A3B8)
private val Accept = Color(0xFF16A34A)
private val Hangup = Color(0xFFDC2626)

/**
 * THE CALL SCREEN, DRAWN BY THE APP — from `Graine.calls.state` only: where the call is, who and why, its length
 * from `startedAt`, the agent's level, the caption, mute and speaker, and Answer / Decline / End / Call back; and
 * the Connection card, live while the call goes (`state.connection`) and final once it is over (the outcome's).
 */
@Composable
fun OwnCallScreen(model: AppModel, info: GraineCallInfo, ended: EndedCall?, modifier: Modifier = Modifier, onClose: () -> Unit = {}) {
    val callAgent = DemoAgent.ofSdkName(info.agent) ?: ended?.agent ?: DemoAgent.MAIN
    val agentName = model.nameOf(callAgent)
    val live = info.state != GraineCallState.IDLE && info.state != GraineCallState.ENDED
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(info.startedAt) {
        while (true) {
            now = Clock.System.now()
            delay(1_000)
        }
    }

    Surface(modifier.fillMaxSize().testTag("own_call"), color = CallBackground) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Drawn by this app", color = CallMuted, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            val headline = when {
                live || info.missedCall != null || info.state == GraineCallState.ENDED -> OwnText.callStatus(info)
                ended?.outcome == "failed" -> "Couldn't connect"
                else -> "Call ended"
            }
            Text(headline, color = CallMuted, style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("own_call_status"))
            Avatar(info.callerName ?: agentName)
            Text(info.callerName ?: agentName, color = CallText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            (info.reason ?: info.missedCall?.reason)?.let {
                Text("About: ${it.replace('_', ' ')}", color = CallMuted, style = MaterialTheme.typography.bodySmall)
            }

            when {
                info.state == GraineCallState.ACTIVE -> {
                    Text(
                        if (info.reconnecting) "Reconnecting…" else OwnText.elapsed(info.startedAt, now) ?: "0:00",
                        color = CallText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 28.sp,
                        modifier = Modifier.testTag("own_call_duration"),
                    )
                    LevelBar(info.agentLevel, info.agentSpeaking)
                }
                info.state == GraineCallState.ENDED -> {
                    Text(OwnText.endReason(info.endReason, agentName), color = CallText, style = MaterialTheme.typography.bodyLarge)
                    OwnText.endCause(info.endCause, agentName)?.let { Text("$it (${info.endCause})", color = CallMuted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
                }
                !live && info.missedCall == null && ended != null -> {
                    Text(
                        if (ended.outcome == "failed") ended.reason?.let { "Reason: ${it.replace('_', ' ')}" } ?: "It never connected."
                        else OwnText.endReason(ended.reason, agentName),
                        color = CallText,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    ended.durationSeconds?.let { Text("Length ${OwnText.duration(it.toLong())}", color = CallMuted, fontFamily = FontFamily.Monospace) }
                    OwnText.endCause(ended.endCause, agentName)?.let {
                        Text("$it (${ended.endCause})", color = CallMuted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.testTag("own_call_end_cause"))
                    }
                }
            }

            info.caption?.takeIf { live }?.let { caption ->
                Text(
                    (if (caption.role == GraineRole.AGENT) "$agentName: " else "You: ") + caption.text,
                    color = CallText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().alpha(if (caption.live) 0.75f else 1f).testTag("own_call_caption"),
                )
            }

            Spacer(Modifier.height(4.dp))
            Controls(model, info, ended, onClose)

            val final = !live && ended != null && info.missedCall == null
            Readout(
                title = if (final) "Connection · final" else "Connection · live",
                rows = OwnText.connectionRows(if (final) ended?.connection else info.connection),
                note = info.transportFallbackReason?.let { "fell back to websocket: $it" },
                modifier = Modifier.testTag("own_call_connection"),
            )
        }
    }
}

@Composable
private fun Controls(model: AppModel, info: GraineCallInfo, ended: EndedCall?, onClose: () -> Unit) {
    val calls = Graine.calls
    when {
        info.state == GraineCallState.RINGING -> Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            RoundButton("Decline", OwnIcons.CallEnd, Hangup, "own_call_decline") { calls.decline() }
            RoundButton("Answer", OwnIcons.Call, Accept, "own_call_answer") { calls.answer() }
        }
        info.state == GraineCallState.CONNECTING || info.state == GraineCallState.ACTIVE -> Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Toggle("Mute", if (info.muted) OwnIcons.MicOff else OwnIcons.Mic, info.muted, "own_call_mute") { calls.setMuted(it) }
            RoundButton("End", OwnIcons.CallEnd, Hangup, "own_call_end") { calls.end() }
            Toggle("Speaker", OwnIcons.Speaker, info.speakerOn, "own_call_speaker") { calls.setSpeaker(it) }
        }
        info.missedCall != null -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = { calls.dismissMissedCall(); onClose() }, modifier = Modifier.testTag("own_call_close")) { Text("Close", color = CallText) }
            RoundButton("Call back", OwnIcons.Call, Accept, "own_call_back") { calls.callBack() }
        }
        info.state == GraineCallState.ENDED -> Unit
        ended != null -> OutlinedButton(onClick = { model.closeEndedCall(); onClose() }, modifier = Modifier.testTag("own_call_close")) { Text("Close", color = CallText) }
    }
}

@Composable
private fun Avatar(name: String) {
    Box(Modifier.size(96.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimary, fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The agent's voice as it is heard: a bar that follows `agentLevel`, brighter while the agent speaks. */
@Composable
private fun LevelBar(level: Float, speaking: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(32.dp)) {
            val bars = 12
            for (i in 0 until bars) {
                val shape = 1f - kotlin.math.abs(i - (bars - 1) / 2f) / bars
                val h = (6 + 26 * (level.coerceIn(0f, 1f) * shape)).dp
                Box(Modifier.size(width = 6.dp, height = h).background(if (speaking) Accept else CallMuted, CircleShape))
            }
        }
        Text(if (speaking) "Agent speaking" else "Agent level ${OwnText.level(level)}", color = CallMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RoundButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, tag: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = Color.White),
            modifier = Modifier.size(64.dp).testTag(tag),
        ) { Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp)) }
        Text(label, color = CallText, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Toggle(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilledTonalIconToggleButton(checked = on, onCheckedChange = onChange, modifier = Modifier.size(52.dp).testTag(tag)) {
            Icon(icon, contentDescription = label)
        }
        Text(if (on) "$label on" else label, color = CallText, style = MaterialTheme.typography.labelMedium)
    }
}
