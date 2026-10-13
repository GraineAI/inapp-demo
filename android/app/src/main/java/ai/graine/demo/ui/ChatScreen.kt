package ai.graine.demo.ui

import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.graine.GraineService
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Chat: the SDK's chat window (GraineChatView, through AndroidView), full
 * screen, under the pack's notice and its starter chips. The chat window
 * draws the brand's design and the agent's components itself; the app only
 * decides when it is open.
 */
@Composable
fun ChatScreen(model: AppModel, agent: DemoAgent) {
    val pack = model.pack ?: return
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (agent == DemoAgent.MAIN) {
                    pack.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
