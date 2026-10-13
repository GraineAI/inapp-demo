package ai.graine.demo.ui

import ai.graine.demo.domain.Tone
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** "#0F766E" → a colour; null for anything else. */
fun parseHex(hex: String?): Color? {
    if (hex == null || hex.length != 7 || !hex.startsWith("#")) return null
    val v = hex.drop(1).toLongOrNull(16) ?: return null
    return Color(0xFF000000 or v)
}

private val Positive = Color(0xFF1F8C54)
private val Warning = Color(0xFFBF730D)
private val Negative = Color(0xFFC72E2E)

/**
 * The demo's own chrome, in the pack's accent. The agent's surfaces (the bar,
 * the chat, the call screen) draw the brand's design from the dashboard instead.
 */
@Composable
fun DemoTheme(accent: String?, content: @Composable () -> Unit) {
    val primary = parseHex(accent) ?: Color(0xFF0F766E)
    val scheme = if (isSystemInDarkTheme()) darkColorScheme(primary = primary, secondary = primary)
    else lightColorScheme(primary = primary, secondary = primary)
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun toneColor(tone: Tone): Color = when (tone) {
    Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    Tone.INFO -> MaterialTheme.colorScheme.primary
    Tone.POSITIVE -> Positive
    Tone.WARNING -> Warning
    Tone.NEGATIVE -> Negative
}

/** A small status label: "Scheduled", "Taken today", "Missed". */
@Composable
fun StatusChip(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val color = toneColor(tone)
    Text(
        text,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

/** A grouped block on the home screen. */
@Composable
fun DemoCard(title: String?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

/** A warning line: something the user can fix, in words. */
@Composable
fun WarningText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = Warning, style = MaterialTheme.typography.bodySmall)
}

/** The tab bar's icons, drawn from Material's 24-unit paths, so the app needs no icon library. */
object DemoIcons {
    val Home = icon("Home", "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z")
    val List = icon("List", "M3,13h2v-2H3v2zM3,17h2v-2H3v2zM3,9h2V7H3v2zM7,13h14v-2H7v2zM7,17h14v-2H7v2zM7,7v2h14V7H7z")
    val Calendar = icon(
        "Calendar",
        "M9,11H7v2h2v-2zM13,11h-2v2h2v-2zM17,11h-2v2h2v-2zM19,4h-1V2h-2v2H8V2H6v2H5c-1.11,0 -1.99,0.9 -1.99,2L3,20c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2zM19,20H5V9h14v11z",
    )
    val Call = icon(
        "Call",
        "M6.62,10.79c1.44,2.83 3.76,5.14 6.59,6.59l2.2,-2.2c0.27,-0.27 0.67,-0.36 1.02,-0.24 1.12,0.37 2.33,0.57 3.57,0.57 0.55,0 1,0.45 1,1V20c0,0.55 -0.45,1 -1,1 -9.39,0 -17,-7.61 -17,-17 0,-0.55 0.45,-1 1,-1h3.5c0.55,0 1,0.45 1,1 0,1.25 0.2,2.45 0.57,3.57 0.11,0.35 0.03,0.74 -0.25,1.02l-2.2,2.2z",
    )
    val Info = icon("Info", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2V7h2v2z")

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black))
            .build()
}
