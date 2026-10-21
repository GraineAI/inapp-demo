package ai.graine.demo.ui.own

import ai.graine.demo.ui.AppModel
import ai.graine.demo.ui.DemoCard
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * HOME'S SWITCH: "Graine's screens" — the SDK's own chat, voice bar, call screen and booking sheet, in the brand's
 * Garage design — or "Your own design", where this app draws every one of them from the SDK's public state. The
 * same buttons everywhere in the app (Talk, Chat, Call, booking) follow it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignSwitch(model: AppModel) {
    DemoCard(title = null, modifier = Modifier.testTag("design_switch")) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !model.ownDesign,
                onClick = { model.chooseOwnDesign(false) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                modifier = Modifier.testTag("design_graine"),
            ) { Text("Graine's screens", maxLines = 1) }
            SegmentedButton(
                selected = model.ownDesign,
                onClick = { model.chooseOwnDesign(true) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                modifier = Modifier.testTag("design_own"),
            ) { Text("Your own design", maxLines = 1) }
        }
        Text(
            if (model.ownDesign) "Chat, Talk, calls, booking and the agent's components are drawn by this app from the SDK's public state. No Graine screen is used."
            else "Chat, Talk, calls and booking use the SDK's own screens, drawn from the brand's design in Graine UI.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { model.openPublicState() }, modifier = Modifier.testTag("open_public_state")) { Text("Public state") }
    }
}
