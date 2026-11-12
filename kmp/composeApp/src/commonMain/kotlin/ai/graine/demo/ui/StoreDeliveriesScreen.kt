package ai.graine.demo.ui

import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.Storefront
import ai.graine.demo.domain.UseCasePack
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * THE LIST SCREEN AS A STOREFRONT (a pack with `store`): the user's subscriptions with Skip, Pause and Resume; the
 * one-time add-ons with Add and Remove; recent deliveries with Report; the wallet. A card opens in place to show its
 * details; the agent's open_item opens one here and scrolls to it. Every button is the pack's set_status action — the
 * one the agent runs — and the agent reads this screen's rows and their statuses as its screen context.
 */
@Composable
fun StoreDeliveriesScreen(model: AppModel) {
    val pack = model.pack ?: return
    val store = pack.store ?: return
    val state = model.items ?: return
    val view = Storefront.view(pack, state) ?: return
    val listState = rememberLazyListState()
    // The row the agent (or a tap) opened: brought into view.
    val order = buildList {
        add("title")
        if (view.subscriptions.isNotEmpty()) { add("subs"); view.subscriptions.forEach { add(it.row.id) } }
        if (view.addOns.isNotEmpty()) { add("addons"); view.addOns.forEach { add(it.row.id) } }
        if (view.deliveries.isNotEmpty()) { add("deliveries"); view.deliveries.forEach { add(it.row.id) } }
        view.wallet?.let { add("wallet_title"); add(it.row.id) }
    }
    LaunchedEffect(model.expandedRowId) {
        val index = model.expandedRowId?.let { order.indexOf(it) } ?: -1
        if (index >= 0) listState.animateScrollToItem(index)
    }
    LazyColumn(
        Modifier.fillMaxSize().background(storePage()).testTag("store_deliveries"),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "title") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pack.items.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                view.wallet?.row?.price?.let { WalletChip(it, onClick = { model.openItem(view.wallet.row) }) }
            }
        }
        if (view.subscriptions.isNotEmpty()) {
            item(key = "subs") { StoreSectionTitle(store.subscriptionsTitle ?: "Subscriptions") }
            items(view.subscriptions, key = { it.row.id }) { line ->
                ProductCard(model, pack, line, Storefront.subscriptionButtons(line, pack))
            }
        }
        if (view.addOns.isNotEmpty()) {
            item(key = "addons") { StoreSectionTitle(store.addOnsTitle) }
            items(view.addOns, key = { it.row.id }) { line ->
                ProductCard(model, pack, line, listOfNotNull(Storefront.addOnButton(line, pack)))
            }
        }
        if (view.deliveries.isNotEmpty()) {
            item(key = "deliveries") { StoreSectionTitle(store.deliveriesTitle ?: "Deliveries") }
            items(view.deliveries, key = { it.row.id }) { line ->
                ProductCard(model, pack, line, listOfNotNull(Storefront.deliveryButton(line, pack)), alwaysOpen = true)
            }
        }
        view.wallet?.let { wallet ->
            item(key = "wallet_title") { StoreSectionTitle(store.walletTitle ?: wallet.row.title) }
            item(key = wallet.row.id) { WalletCard(model, pack, store, wallet) }
        }
    }
}

/**
 * One product or delivery: its tile, name, schedule and price, and its status chip; open, its details and buttons.
 * [alwaysOpen]: a delivery shows its details without a tap.
 */
@Composable
private fun ProductCard(
    model: AppModel,
    pack: UseCasePack,
    line: Storefront.Line,
    buttons: List<UseCasePack.Action>,
    alwaysOpen: Boolean = false,
) {
    val row = line.row
    val open = alwaysOpen || model.expandedRowId == row.id
    StoreCard(
        modifier = Modifier.testTag("item_${row.id}"),
        highlighted = model.expandedRowId == row.id,
        onClick = { model.toggleExpanded(row) },
        padding = PaddingValues(14.dp),
    ) {
        val stopped = row.kind == Storefront.SUBSCRIPTION && !Storefront.delivering(line, pack, pack.store!!)
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(row.icon, size = 48.dp, dimmed = stopped)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(row.title, fontWeight = FontWeight.SemiBold)
                if (row.subtitle.isNotEmpty()) {
                    Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            line.status?.let { StatusChip(it, Storefront.tone(line, pack)) }
        }
        if (open && row.fields.isNotEmpty()) {
            Column(Modifier.padding(start = 60.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                row.fields.forEach { field ->
                    Row {
                        Text(field.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(field.value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        if (buttons.isNotEmpty() || open) {
            Row(
                Modifier.padding(start = 60.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                buttons.forEachIndexed { i, action ->
                    val tag = Modifier.height(36.dp).testTag("action_${action.name}_${row.id}")
                    val padding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                    if (i == 0) {
                        FilledTonalButton(onClick = { model.press(action, row) }, modifier = tag, contentPadding = padding) {
                            Text(action.label, style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        OutlinedButton(onClick = { model.press(action, row) }, modifier = tag, contentPadding = padding) {
                            Text(action.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                if (open) {
                    TextButton(onClick = { model.startChat("I have a question about ${row.title}.") }) {
                        Text("Ask ${model.nameOf(DemoAgent.MAIN)}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** The wallet: its balance large, its details, and top-up chips — a demo takes no payment, so a chip says so. */
@Composable
private fun WalletCard(model: AppModel, pack: UseCasePack, store: UseCasePack.Store, wallet: Storefront.Line) {
    val row = wallet.row
    StoreCard(modifier = Modifier.testTag("item_${row.id}"), highlighted = model.expandedRowId == row.id) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(row.icon, size = 48.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Balance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(row.price ?: row.subtitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            wallet.status?.let { StatusChip(it, Storefront.tone(wallet, pack)) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        row.fields.filter { it.name != "balance" }.forEach { field ->
            Row {
                Text(field.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(field.value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
        if (store.topUps.isNotEmpty()) {
            Text("Add money", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(store.topUps) { amount ->
                    AssistChip(
                        onClick = { model.notice = store.topUpNotice ?: "Payments aren't part of this demo." },
                        label = { Text("+ $amount", fontWeight = FontWeight.SemiBold) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("topup_$amount"),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { model.startChat("I have a question about ${row.title}.") }, modifier = Modifier.height(38.dp)) {
                Icon(DemoIcons.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Ask ${model.nameOf(DemoAgent.MAIN)}", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
