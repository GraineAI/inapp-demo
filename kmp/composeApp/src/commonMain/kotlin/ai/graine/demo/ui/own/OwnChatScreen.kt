package ai.graine.demo.ui.own

import ai.graine.demo.domain.CallText
import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.DemoFormat
import ai.graine.demo.domain.OwnText
import ai.graine.demo.ui.AppModel
import ai.graine.demo.ui.StatusChip
import ai.graine.demo.ui.nowMs
import ai.graine.inapp.kmp.Graine
import ai.graine.inapp.kmp.GraineChat
import ai.graine.inapp.kmp.GraineChatRating
import ai.graine.inapp.kmp.GraineChatRow
import ai.graine.inapp.kmp.GraineChatRows
import ai.graine.inapp.kmp.GraineChatStatus
import ai.graine.inapp.kmp.GraineChatTurn
import ai.graine.inapp.kmp.GraineDelivery
import ai.graine.inapp.kmp.GraineException
import ai.graine.inapp.kmp.GraineRole
import ai.graine.inapp.kmp.GraineScheduledCall
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * THE CHAT, DRAWN BY THE APP — from `agent.chat`'s public state only:
 * `turns` laid out by `GraineChatRows.build(turns, newConversationBefore)` (day
 * separators, one row per earlier call, "Disconnected · new conversation"),
 * `status` (Disconnected included) and `typing` in the header, the agent's
 * component, `quickReplies`, the end-of-chat `rating`, the `upcomingCalls`
 * card, and the composer (`send`). The screen says when it appears and goes
 * (`shown(true/false)`), so the conversation lives as the SDK's own window's does.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun OwnChatScreen(model: AppModel, agent: DemoAgent) {
    val name = model.nameOf(agent)
    val sdk = Graine.agent(agent.sdkName)
    if (sdk == null) {
        OwnScreen(name, "Not set up on this build", onBack = { model.closeChat() }) {
            Text("This agent has no publishable key in this build.", Modifier.padding(16.dp))
        }
        return
    }
    val chat = sdk.chat
    val turns by chat.turns.collectAsState()
    val status by chat.status.collectAsState()
    val failed by chat.failed.collectAsState()
    val typing by chat.typing.collectAsState()
    val quickReplies by chat.quickReplies.collectAsState()
    val ended by chat.ended.collectAsState()
    val rating by chat.rating.collectAsState()
    val pinned by chat.pinned.collectAsState()
    val upcoming by chat.upcomingCalls.collectAsState()
    val newConversationBefore by chat.newConversationBefore.collectAsState()
    val sessionId by chat.sessionId.collectAsState()
    val sessions by chat.sessions.collectAsState()
    var showSessions by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val component by sdk.component.collectAsState()
    val componentPending by sdk.componentPending.collectAsState()
    // The user's answer, kept where the card was while the agent answers it (componentAwaitingReply, SDK 0.14.8).
    var answered by rememberAnswered(sdk, component)
    val chose = answered.line

    // On screen, and gone: in pairs, as the SDK's own chat window says it.
    DisposableEffect(chat) {
        chat.shown(true)
        onDispose { chat.shown(false) }
    }

    val scope = rememberCoroutineScope()
    val rows = remember(turns, newConversationBefore) { GraineChatRows.build(turns, newConversationBefore) }
    val list = rememberLazyListState()
    // Keep the newest in view as the thread grows — a message, the agent typing, a component, the replies, the rating:
    // the index of the last item the list below draws, counted as it draws them.
    val starters = model.profile(agent)?.starters.orEmpty().takeIf { GraineChatRows.offersStarters(turns) }.orEmpty()
    val lastIndex = listOf(
        pinned != null, upcoming.isNotEmpty(), rows.isEmpty() && !typing,
    ).count { it } + rows.size + listOf(
        typing, component != null || componentPending || chose != null, quickReplies.isNotEmpty(), ended, starters.isNotEmpty(),
    ).count { it } - 1
    LaunchedEffect(lastIndex, turns.lastOrNull()?.text?.length) {
        if (lastIndex >= 0) list.animateScrollToItem(lastIndex)
    }

    OwnScreen(
        title = name,
        subtitle = OwnText.chatStatus(status, typing, failed),
        onBack = { model.closeChat() },
        modifier = Modifier.testTag("own_chat"),
        actions = {
            if (OwnText.chatCanRetry(failed)) TextButton(onClick = { chat.retry() }, modifier = Modifier.testTag("own_chat_retry")) { Text("Retry") }
            // The conversation's own sessions, and the user's past sessions with this agent.
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("own_chat_menu")) { Icon(OwnIcons.More, contentDescription = "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (sessions.isNotEmpty()) {
                        DropdownMenuItem(text = { Text("Sessions in this chat") }, onClick = { menu = false; showSessions = true }, modifier = Modifier.testTag("own_sessions_open"))
                    }
                    DropdownMenuItem(text = { Text("Past sessions") }, onClick = { menu = false; model.openPastSessions(agent) }, modifier = Modifier.testTag("past_sessions_open"))
                }
            }
            if (GraineChatRows.offersNewConversation(turns)) {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            chat.newConversation()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            model.notice = (e as? GraineException)?.message ?: CallText.message(e)
                        }
                    }
                }, modifier = Modifier.testTag("own_new_conversation")) { Text("New conversation") }
            }
        },
    ) {
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            pinned?.let { pin ->
                item(key = "pin") {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(pin.title, fontWeight = FontWeight.SemiBold)
                                pin.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                            pin.badge?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }
            if (upcoming.isNotEmpty()) {
                item(key = "upcoming") { UpcomingCard(chat, upcoming) }
            }
            if (rows.isEmpty() && !typing) {
                item(key = "empty") {
                    Text(
                        if (status == GraineChatStatus.CONNECTING) "Connecting to $name…" else "Ask $name anything.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            items(rows, key = { it.key }) { row ->
                when (row) {
                    is GraineChatRow.Day -> Separator(GraineChatRows.dayLabel(row))
                    is GraineChatRow.VoiceCall -> Separator(GraineChatRows.callLabel(DemoFormat.clock(row.at.toEpochMilliseconds())))
                    is GraineChatRow.NewConversation -> Separator(row.label, Modifier.semantics { contentDescription = row.spoken }.testTag("own_new_conversation_row"))
                    is GraineChatRow.Turn -> Bubble(row.turn, onRetry = { chat.retry() })
                }
            }
            if (typing) item(key = "typing") { TypingBubble(name) }
            if (component != null || componentPending) {
                item(key = "component-${component?.id ?: "pending"}") {
                    OwnComponentCard(sdk, component, componentPending, onAnswered = { answered = answered.answered(it.summary) })
                }
            } else if (chose != null) {
                item(key = "answered") { AnsweredLine(chose, name) }
            }
            if (quickReplies.isNotEmpty()) {
                item(key = "replies") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        quickReplies.forEach { reply ->
                            SuggestionChip(onClick = { chat.sendQuickReply(reply) }, label = { Text(reply.label) }, modifier = Modifier.testTag("own_quick_reply"))
                        }
                    }
                }
            }
            if (ended) item(key = "rating") { RatingCard(rating) { up -> chat.rate("thumbs", if (up) 1 else 0) } }
            if (starters.isNotEmpty()) {
                item(key = "starters") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        starters.forEach { s -> AssistChip(onClick = { chat.send(s) }, label = { Text(s) }, modifier = Modifier.testTag("own_starter")) }
                    }
                }
            }
        }
        HorizontalDivider()
        Composer(onSend = { chat.send(it) })
    }

    if (showSessions) {
        OwnSessionsSheet(
            sessions = sessions,
            sessionId = sessionId,
            onPick = { index ->
                showSessions = false
                // The items above the thread, then the session's first row.
                val above = listOf(pinned != null, upcoming.isNotEmpty()).count { it }
                val row = OwnText.sessionRow(rows, OwnText.sessionStart(sessions, index))
                scope.launch { list.animateScrollToItem(above + row) }
            },
            onCopied = { model.notice = "Session id copied." },
            onDismiss = { showSessions = false },
        )
    }
}

@Composable
internal fun Bubble(turn: GraineChatTurn, onRetry: () -> Unit) {
    val mine = turn.role == GraineRole.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 300.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (mine) 16.dp else 4.dp, bottomEnd = if (mine) 4.dp else 16.dp))
                    .background(if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag(if (mine) "own_bubble_user" else "own_bubble_agent"),
            ) {
                Text(
                    turn.text,
                    color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.alpha(if (turn.live) 0.75f else 1f),
                )
            }
            val note = when {
                mine && turn.delivery == GraineDelivery.SENDING -> "Sending…"
                mine && turn.delivery == GraineDelivery.FAILED -> "Not sent · tap to retry"
                else -> DemoFormat.clock(turn.at.toEpochMilliseconds())
            }
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = if (turn.delivery == GraineDelivery.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                    .let { if (turn.delivery == GraineDelivery.FAILED) it.clickable(onClick = onRetry) else it },
            )
        }
    }
}

@Composable
internal fun TypingBubble(name: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("own_typing")) {
        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("$name is typing…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UpcomingCard(chat: GraineChat, calls: List<GraineScheduledCall>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OwnLabel("Upcoming calls")
            calls.forEach { call ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(DemoFormat.time(call.at, nowMs()), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    StatusChip(CallText.label(call.status), CallText.tone(call.status))
                    if (call.canCancel) TextButton(onClick = { chat.cancelUpcomingCall(call.id) }) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun RatingCard(rating: GraineChatRating?, onRate: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("own_rating"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("The conversation has ended.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (rating == null) {
                Text("Was this chat helpful?", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onRate(true) }, modifier = Modifier.testTag("own_rate_up")) {
                        Icon(OwnIcons.ThumbUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Yes")
                    }
                    OutlinedButton(onClick = { onRate(false) }, modifier = Modifier.testTag("own_rate_down")) {
                        Icon(OwnIcons.ThumbDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("No")
                    }
                }
            } else {
                val said = when (rating.style) {
                    "thumbs" -> if (rating.value >= 1) "helpful" else "not helpful"
                    else -> "${rating.value} of ${rating.scale}"
                }
                Text("Thanks for your feedback: $said.", style = MaterialTheme.typography.bodySmall)
            }
            Text("Send a message to start again.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The message box; [initial] starts it with a draft (a screen's "Ask about this"). */
@Composable
internal fun Composer(onSend: (String) -> Unit, initial: String = "") {
    var text by remember(initial) { mutableStateOf(initial) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Message") },
            maxLines = 4,
            modifier = Modifier.weight(1f).testTag("own_composer"),
        )
        IconButton(
            onClick = {
                val t = text.trim()
                if (t.isNotEmpty()) {
                    onSend(t)
                    text = ""
                }
            },
            enabled = text.isNotBlank(),
            modifier = Modifier.testTag("own_send"),
        ) { Icon(OwnIcons.Send, contentDescription = "Send", tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
