package ai.graine.demo.ui.own

import ai.graine.demo.domain.DemoFormat
import ai.graine.demo.domain.OwnText
import ai.graine.demo.domain.Tone
import ai.graine.demo.ui.StatusChip
import ai.graine.demo.ui.nowMs
import ai.graine.inapp.kmp.GraineChatSession
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * THE CHAT'S SESSIONS, DRAWN BY THE APP — `chat.sessions`, oldest first: each
 * run of the conversation (a chat connection's, or a call's) with when it
 * began, chat or voice call, how many messages, its id shortened with Copy, and
 * the current one marked. Tapping one goes to it in the thread ([onPick], with
 * its place in the list). A session with no id is the messages kept before
 * Graine sent session ids — while it sends none, the whole conversation is one:
 * "Earlier messages".
 */
@Suppress("DEPRECATION") // LocalClipboardManager: plain text on both platforms, without a platform clip entry.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnSessionsSheet(
    sessions: List<GraineChatSession>,
    sessionId: String?,
    onPick: (index: Int) -> Unit,
    onCopied: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("own_sessions")) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Sessions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Each run of this conversation, oldest first: a chat connection or a call. Tap one to go to it." +
                    if (sessionId == null) " This connection has no session id yet." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (sessions.isEmpty()) {
                Text("No messages yet.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
            }
            sessions.forEachIndexed { index, s ->
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(index) }.padding(vertical = 6.dp).testTag("own_session"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            if (s.id == null) OwnText.UNNUMBERED_SESSION else DemoFormat.time(s.startedAt, nowMs()),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (s.id == null) "${OwnText.sessionKind(s)} · from ${DemoFormat.time(s.startedAt, nowMs())}" else OwnText.sessionKind(s),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (s.current) StatusChip("Current", Tone.POSITIVE)
                    s.id?.let { id ->
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(id))
                            onCopied()
                        }, modifier = Modifier.testTag("own_session_copy")) {
                            Text(OwnText.shortId(id), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
                            Text("  Copy", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}
