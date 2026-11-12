package ai.graine.demo.ui

import ai.graine.demo.domain.CallText
import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.DemoFormat
import ai.graine.demo.domain.DemoScreen
import ai.graine.demo.domain.Storefront
import ai.graine.demo.domain.Tone
import ai.graine.demo.domain.UseCasePack
import ai.graine.demo.platform.CallReadiness
import ai.graine.demo.ui.own.DesignSwitch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * HOME AS A STOREFRONT (a pack with `store`): where tomorrow's delivery goes and what the wallet holds; the assistant
 * one tap away — chat, voice and a call, with the face switch; the week; tomorrow's basket and its total; one-time
 * add-ons by category; one banner; the next call. Every button runs the pack's own set_status action, the one the
 * agent runs, so a tap and the agent change the same status — and the agent reads this screen as it is (screen
 * context). The SDK's showcase (design switch, call readiness, memory) stays at the bottom.
 */
@Composable
fun StoreHomeScreen(model: AppModel) {
    val pack = model.pack ?: return
    val user = model.user ?: return
    val store = pack.store ?: return
    val state = model.items ?: return
    val view = Storefront.view(pack, state) ?: return
    val agentName = model.nameOf(DemoAgent.MAIN)
    Column(
        Modifier.fillMaxSize().background(storePage()).verticalScroll(rememberScrollState()).testTag("store_home"),
    ) {
        StoreHeader(model, pack, user, view)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AskBar(model, agentName)
            WeekStrip(Storefront.week(Clock.System.todayIn(TimeZone.currentSystemDefault()), view), "Next", "No delivery")
            BasketCard(model, pack, store, view)
            AddOns(model, pack, store, view)
            store.banner?.let { banner ->
                Banner(banner) { model.openTarget(banner.target) }
            }
            NextCallCard(model, pack, agentName)
            DemoControls(model)
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun StoreHeader(model: AppModel, pack: UseCasePack, user: UseCasePack.User, view: Storefront.View) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxWidth().background(accent.copy(alpha = 0.07f)).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pack.appTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = accent)
                Storefront.deliveringTo(pack, user)?.let { place ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(DemoIcons.Place, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "Delivering to $place",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 3.dp),
                        )
                    }
                }
            }
            view.wallet?.row?.price?.let { balance ->
                WalletChip(balance, onClick = { model.openItem(view.wallet.row) }, modifier = Modifier.padding(end = 8.dp))
            }
            InitialAvatar(user.firstName) { model.open(DemoScreen.PROFILE) }
        }
        Text("Hello, ${user.firstName}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
}

/** The assistant one tap away: the bar opens the chat; the mic, voice; the phone, a call. The face switch beneath. */
@Composable
private fun AskBar(model: AppModel, agentName: String) {
    StoreCard(padding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { model.open(DemoScreen.CHAT) }.padding(vertical = 8.dp).testTag("chat"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(DemoIcons.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.padding(start = 10.dp)) {
                    Text("Ask $agentName", fontWeight = FontWeight.SemiBold)
                    Text("Pause, add, check your wallet…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { model.talk() }, modifier = Modifier.testTag("talk")) {
                Icon(DemoIcons.Mic, contentDescription = "Talk to $agentName", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { model.callAgent() }, modifier = Modifier.testTag("call")) {
                Icon(DemoIcons.Call, contentDescription = "Call $agentName", tint = MaterialTheme.colorScheme.primary)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Box(Modifier.padding(end = 10.dp, bottom = 4.dp)) { FaceSwitch(model) }
    }
}

@Composable
private fun BasketCard(model: AppModel, pack: UseCasePack, store: UseCasePack.Store, view: Storefront.View) {
    StoreCard(modifier = Modifier.testTag("store_basket")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(store.basketTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                store.basketNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            TextButton(onClick = { model.open(DemoScreen.ITEMS) }, modifier = Modifier.testTag("store_basket_edit")) { Text("Edit") }
        }
        if (view.nothingComing) {
            Text("Nothing is coming. Resume a subscription or add something.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        view.basket.forEach { line -> BasketLine(pack, line, dimmed = false) }
        view.notComing.forEach { line -> BasketLine(pack, line, dimmed = true) }
        view.total?.let { total ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${view.basket.size} ${if (view.basket.size == 1) "item" else "items"} · paid from your wallet", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(total, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("store_total"))
            }
        }
    }
}

@Composable
private fun BasketLine(pack: UseCasePack, line: Storefront.Line, dimmed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("basket_${line.row.id}")) {
        EmojiTile(line.row.icon, size = 40.dp, dimmed = dimmed)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(line.row.title, fontWeight = FontWeight.SemiBold, color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            if (dimmed) {
                line.status?.let { StatusChip(it, Storefront.tone(line, pack), Modifier.padding(top = 2.dp)) }
            } else {
                Text(line.row.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!dimmed) line.row.price?.let { Text(it, fontWeight = FontWeight.SemiBold) }
    }
}

/** One-time add-ons: chips by category, and a two-column grid of tiles, each with its own Add / Added. */
@Composable
private fun AddOns(model: AppModel, pack: UseCasePack, store: UseCasePack.Store, view: Storefront.View) {
    if (view.addOns.isEmpty()) return
    var chosen by remember { mutableStateOf<String?>(null) }
    val categories = Storefront.categoriesWithAddOns(pack, view)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StoreSectionTitle(store.addOnsTitle)
        if (categories.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(selected = chosen == null, onClick = { chosen = null }, label = { Text("All") }, colors = storeChipColors())
                }
                items(categories, key = { it.id }) { c ->
                    FilterChip(
                        selected = chosen == c.id,
                        onClick = { chosen = if (chosen == c.id) null else c.id },
                        label = { Text(c.label) },
                        colors = storeChipColors(),
                        modifier = Modifier.testTag("store_category_${c.id}"),
                    )
                }
            }
        }
        val shown = Storefront.addOnsIn(view, chosen)
        shown.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { line -> AddOnTile(model, pack, line, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun storeChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    containerColor = MaterialTheme.colorScheme.surface,
)

@Composable
private fun AddOnTile(model: AppModel, pack: UseCasePack, line: Storefront.Line, modifier: Modifier) {
    val store = pack.store ?: return
    val added = Storefront.inBasket(line, pack, store)
    val button = Storefront.addOnButton(line, pack)
    StoreCard(modifier = modifier.testTag("addon_${line.row.id}"), padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { EmojiTile(line.row.icon, size = 64.dp) }
        Text(line.row.title, fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(line.row.price ?: "", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (button != null) {
                if (added) {
                    OutlinedButton(
                        onClick = { model.press(button, line.row) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp).testTag("action_${button.name}_${line.row.id}"),
                    ) {
                        Icon(DemoIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Added", modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Button(
                        onClick = { model.press(button, line.row) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp).testTag("action_${button.name}_${line.row.id}"),
                    ) {
                        Icon(DemoIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Add", modifier = Modifier.padding(start = 2.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun Banner(banner: UseCasePack.Store.Banner, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.fillMaxWidth().testTag("store_banner"),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🧳", style = MaterialTheme.typography.headlineMedium)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(banner.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(banner.body, style = MaterialTheme.typography.bodySmall)
            }
            Icon(DemoIcons.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun NextCallCard(model: AppModel, pack: UseCasePack, agentName: String) {
    StoreCard(modifier = Modifier.testTag("store_next_call")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) { Icon(DemoIcons.Calendar, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(pack.call.title, fontWeight = FontWeight.SemiBold)
                val next = model.nextCall
                Text(
                    when {
                        next != null -> "${DemoFormat.time(next.call.at, nowMs())} · with ${model.nameOf(next.agent)}"
                        model.callsLoading -> "Loading…"
                        else -> "$agentName can call you at a time you choose."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            model.nextCall?.let { StatusChip(CallText.label(it.call.status), CallText.tone(it.call.status)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { model.book(DemoAgent.MAIN) }, enabled = !model.bookingOpen, modifier = Modifier.testTag("home_book")) {
                Text(pack.bookLabel(DemoAgent.MAIN))
            }
            TextButton(onClick = { model.open(DemoScreen.CALLS) }) { Text("See calls") }
        }
    }
}

/** The demo's own controls, as on the generic Home: Graine's screens or your own design, call readiness, memory. */
@Composable
private fun DemoControls(model: AppModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
        Text("This demo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StoreCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Memory", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                val (label, on) = model.memoryStatus
                TextButton(onClick = { model.open(DemoScreen.MEMORY) }, modifier = Modifier.testTag("memory_chip")) {
                    StatusChip(label, if (on) Tone.POSITIVE else Tone.NEUTRAL)
                }
            }
            DesignSwitch(model)
            CallReadiness()
        }
        Spacer(Modifier.width(1.dp))
    }
}
