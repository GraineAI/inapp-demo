package ai.graine.demo.ui.own

import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.OwnText
import ai.graine.demo.domain.Tone
import ai.graine.demo.ui.AppModel
import ai.graine.demo.ui.StatusChip
import ai.graine.demo.ui.WarningText
import ai.graine.inapp.kmp.Graine
import ai.graine.inapp.kmp.GraineCaption
import ai.graine.inapp.kmp.GraineRole
import ai.graine.inapp.kmp.GraineVoiceInfo
import ai.graine.inapp.kmp.GraineVoiceStatus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * TALK, DRAWN BY THE APP — the voice conversation with the agent, beside the
 * screen it talks about, from `agent.voice.state` only: the status word (and
 * the error sentence when it failed), the live captions, the agent's and the
 * microphone's levels (Android's voice conversation does not report them yet:
 * they stay at 0 there), what carries the audio, and Start, End, Mute and
 * captions on or off. Nothing starts until Start is tapped; the microphone with it.
 */
@Composable
fun OwnTalkPanel(model: AppModel, agent: DemoAgent, modifier: Modifier = Modifier) {
    val sdk = Graine.agent(agent.sdkName) ?: return
    val info by sdk.voice.state.collectAsState()
    val component by sdk.component.collectAsState()
    val componentPending by sdk.componentPending.collectAsState()
    // The user's answer, kept where the card was while the agent answers it (componentAwaitingReply, SDK 0.14.8).
    var answered by rememberAnswered(sdk, component)
    val live = OwnText.voiceLive(info)
    val word = OwnText.voiceWord(info)

    Card(
        modifier.fillMaxWidth().testTag("own_talk"),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(model.nameOf(agent), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Talk · drawn by this app", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusChip(OwnText.voiceStatus(word), tone(word), Modifier.testTag("own_talk_status"))
                IconButton(onClick = { model.closeTalk() }, modifier = Modifier.testTag("own_talk_close")) { Icon(OwnIcons.Close, contentDescription = "Close") }
            }

            if (word == GraineVoiceStatus.FAILED) info.error?.let { WarningText(it) }

            LevelMeter("Agent", info.agentLevel)
            LevelMeter("You", info.micLevel, color = MaterialTheme.colorScheme.tertiary)

            Captions(info)

            if (component != null || componentPending) {
                OwnComponentCard(
                    sdk, component, componentPending,
                    onAnswered = { answered = answered.answered(it.summary) },
                    onTap = { model.runTap(it) },
                )
            } else {
                answered.line?.let { AnsweredLine(it, model.nameOf(agent)) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (live) {
                    Button(
                        onClick = { model.endTalk() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("own_talk_end"),
                    ) {
                        Icon(OwnIcons.CallEnd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("End")
                    }
                    FilledTonalIconToggleButton(checked = info.muted, onCheckedChange = { sdk.voice.setMuted(it) }, modifier = Modifier.testTag("own_talk_mute")) {
                        Icon(if (info.muted) OwnIcons.MicOff else OwnIcons.Mic, contentDescription = if (info.muted) "Unmute" else "Mute")
                    }
                } else {
                    Button(onClick = { model.startTalk() }, modifier = Modifier.testTag("own_talk_start")) {
                        Icon(OwnIcons.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(if (word == GraineVoiceStatus.FAILED || word == GraineVoiceStatus.ENDED) "Start again" else "Start")
                    }
                }
                FilledTonalIconToggleButton(checked = info.captionsOn, onCheckedChange = { sdk.voice.setCaptionsOn(it) }, modifier = Modifier.testTag("own_talk_cc")) {
                    Icon(OwnIcons.Captions, contentDescription = if (info.captionsOn) "Hide captions" else "Show captions")
                }
            }

            val road = info.transport?.let { t ->
                "audio: ${t.name.lowercase()}" + (info.transportFallbackReason?.let { " · fell back: $it" } ?: "")
            } ?: if (live) "audio: not reported yet" else "audio: chosen when the conversation starts"
            Text(
                road,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Captions(info: GraineVoiceInfo) {
    val scroll = rememberScrollState()
    val lines: List<GraineCaption> = info.captions.takeLast(6)
    LaunchedEffect(lines.size, lines.lastOrNull()?.text?.length) { scroll.animateScrollTo(scroll.maxValue) }
    Column(
        Modifier.fillMaxWidth().heightIn(max = 140.dp).verticalScroll(scroll).testTag("own_talk_captions"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when {
            !info.captionsOn -> Text("Captions are off.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            lines.isEmpty() -> Text(
                if (OwnText.voiceLive(info)) "Captions appear here as you talk." else "Tap Start, then speak.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> lines.forEach { line ->
                Row {
                    Text(
                        if (line.role == GraineRole.AGENT) "Agent  " else "You  ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(line.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alpha(if (line.live) 0.7f else 1f))
                }
            }
        }
    }
}

private fun tone(status: GraineVoiceStatus): Tone = when (status) {
    GraineVoiceStatus.LISTENING, GraineVoiceStatus.SPEAKING, GraineVoiceStatus.THINKING -> Tone.POSITIVE
    GraineVoiceStatus.CONNECTING, GraineVoiceStatus.RECONNECTING, GraineVoiceStatus.MUTED -> Tone.WARNING
    GraineVoiceStatus.FAILED -> Tone.NEGATIVE
    GraineVoiceStatus.IDLE, GraineVoiceStatus.ENDED -> Tone.NEUTRAL
}
