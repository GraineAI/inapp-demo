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
