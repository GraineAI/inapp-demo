package ai.graine.demo.ui

import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.graine.GraineService
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Chat: the SDK's chat window (GraineChatView, through AndroidView), full
 * screen, under the pack's notice, **Past sessions** and its starter chips.
 * The chat window draws the brand's design and the agent's components itself;
 * the app only decides when it is open. Past sessions — the user's earlier
 * sessions with this agent — is the app's own screen (PastSessionsScreen).
 */
@Composable
fun ChatScreen(model: AppModel, agent: DemoAgent) {
    val pack = model.pack ?: return
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) {
                        if (agent == DemoAgent.MAIN) {
                            pack.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    TextButton(onClick = { model.openPastSessions(agent) }, modifier = Modifier.testTag("past_sessions_open")) { Text("Past sessions") }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (model.profile(agent)?.starters ?: emptyList()).forEach { starter ->
                        AssistChip(onClick = { model.startChat(starter, agent) }, label = { Text(starter) }, modifier = Modifier.testTag("starter"))
                    }
                }
            }
            AndroidView(
                factory = { ctx -> GraineService.get().chatView(ctx, agent) { model.closeChat() } },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
