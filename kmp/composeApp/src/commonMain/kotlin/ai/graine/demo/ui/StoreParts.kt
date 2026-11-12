package ai.graine.demo.ui

import ai.graine.demo.domain.Storefront
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * THE STOREFRONT'S PARTS (StoreHomeScreen, StoreDeliveriesScreen): white cards with a hairline on a pale page, an
 * emoji tile on a tint of the pack's accent for every product, and the pack's accent for what the user can do. The
 * look is the app's own; the agent's surfaces (the chat, the voice bar, the call screen) draw the brand's design from
 * the dashboard, as on every screen.
 */

/** The page behind the cards: a pale grey in light mode, the theme's background in dark. */
@Composable
internal fun storePage(): Color = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.background else Color(0xFFF4F6F4)

/** A white card with a hairline; [highlighted] (the item the agent opened) draws it in the accent. */
@Composable
internal fun StoreCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = if (highlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    Card(
        modifier = modifier.fillMaxWidth().let { if (onClick != null) it.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(18.dp),
        border = border,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** A section's title, with an optional link on the right ("Edit", "See all"). */
@Composable
internal fun StoreSectionTitle(title: String, modifier: Modifier = Modifier, link: String? = null, onLink: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (link != null && onLink != null) {
            Text(
                link,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onLink).padding(horizontal = 6.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** A product's emoji on a rounded tile tinted with the accent. */
@Composable
internal fun EmojiTile(icon: String?, size: Dp = 44.dp, dimmed: Boolean = false) {
    val tint = MaterialTheme.colorScheme.primary.copy(alpha = if (dimmed) 0.05f else 0.10f)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Text(icon ?: "•", fontSize = (size.value * 0.5f).sp, modifier = Modifier.alpha(if (dimmed) 0.45f else 1f))
    }
}

/** The wallet's balance as a pill: the header's, and a tap opens the wallet. */
@Composable
internal fun WalletChip(balance: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.testTag("store_wallet_chip"),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(DemoIcons.Wallet, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(balance, modifier = Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/** The user's initial in a circle: Profile. */
@Composable
internal fun InitialAvatar(name: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable(onClick = onClick).testTag("profile"),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
    }
}

/** Seven days from today, the next delivery marked: "Next", or the [nothingLabel] when nothing comes. */
@Composable
internal fun WeekStrip(days: List<Storefront.Day>, nextLabel: String, nothingLabel: String) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
        items(days, key = { it.date.toString() }) { day ->
            val next = day.next
            val bg = when {
                next && day.nothing -> MaterialTheme.colorScheme.surface
                next -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surface
            }
            val fg = if (next && !day.nothing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            val stroke = when {
                next && day.nothing -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                day.today -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            }
            Column(
                Modifier.width(56.dp).clip(RoundedCornerShape(14.dp)).background(bg)
                    .border(1.dp, stroke, RoundedCornerShape(14.dp)).padding(vertical = 8.dp)
                    .testTag(if (next) "store_week_next" else "store_week_${day.date}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(day.weekday, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.8f))
                Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = fg)
                val note = when {
                    next && day.nothing -> nothingLabel
                    next -> nextLabel
                    day.today -> "Today"
                    else -> ""
                }
                Text(
                    note,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (next && day.nothing) MaterialTheme.colorScheme.error else fg,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.height(16.dp),
                )
            }
        }
    }
}

/** A thin line of space between groups on the storefront. */
@Composable
internal fun StoreGap(height: Dp = 4.dp) = Box(Modifier.height(height))
