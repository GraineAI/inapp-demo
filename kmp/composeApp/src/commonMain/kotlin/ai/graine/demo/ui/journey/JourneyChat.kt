package ai.graine.demo.ui.journey

import ai.graine.demo.domain.DemoFormat
import ai.graine.demo.domain.OwnText
import ai.graine.demo.journey.JourneyAgent
import ai.graine.demo.journey.JourneyModel
import ai.graine.demo.journey.JourneyText
import ai.graine.demo.ui.own.AnsweredLine
import ai.graine.demo.ui.own.Bubble
import ai.graine.demo.ui.own.Composer
import ai.graine.demo.ui.own.OwnComponentCard
import ai.graine.demo.ui.own.Separator
import ai.graine.demo.ui.own.TypingBubble
import ai.graine.demo.ui.own.rememberAnswered
import ai.graine.inapp.kmp.Graine
import ai.graine.inapp.kmp.GraineChatRow
import ai.graine.inapp.kmp.GraineChatRows
import ai.graine.inapp.kmp.GraineChatStatus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A CHAT OF THE JOURNEY, drawn by the app from the agent's `chat` state: the coach's (the main screen, with the call
 * button and the plan card), the first call's chat version, and support's. It says when it is on screen
 * (`chat.shown(true/false)`), lays the turns out with `GraineChatRows`, draws the agent's component — "Select all that
 * apply" among them — in its look (`component.style`), answers it with `chat.answerWidget(id, data, summary)`, and keeps
 * the answer on show while the agent answers it (`componentAwaitingReply`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JourneyChat(
    journey: JourneyModel,
    agent: JourneyAgent,
    title: String,
    onBack: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    starters: List<String> = emptyList(),
    above: @Composable (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val sdk = Graine.agent(agent.sdkName)
    if (sdk == null) {
        JourneyScreen(title, "Not set up in this build", onBack = onBack, onMenu = onMenu) {
            Text(
                "This agent has no publishable key in this build: " + when (agent) {
                    JourneyAgent.COACH -> "graine.publishableKey (GRAINE_PUBLISHABLE_KEY on iOS)."
                    JourneyAgent.FIRST_CALL -> "graine.consultationPublishableKey (GRAINE_CONSULTATION_PUBLISHABLE_KEY on iOS)."
                    JourneyAgent.SUPPORT -> "graine.helpPublishableKey (GRAINE_HELP_PUBLISHABLE_KEY on iOS)."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    val chat = sdk.chat
    val turns by chat.turns.collectAsState()
    val status by chat.status.collectAsState()
    val failed by chat.failed.collectAsState()
    val typing by chat.typing.collectAsState()
    val quickReplies by chat.quickReplies.collectAsState()
    val newConversationBefore by chat.newConversationBefore.collectAsState()
    val component by sdk.component.collectAsState()
    val pending by sdk.componentPending.collectAsState()
    // The user's answer, kept where the card was while the agent answers it (componentAwaitingReply, SDK 0.14.8).
    var answered by rememberAnswered(sdk, component)
    val chose = answered.line

    // On screen and gone, in pairs, as the SDK's own chat window says it.
    DisposableEffect(chat) {
        chat.shown(true)
        onDispose { chat.shown(false) }
    }

    val rows = remember(turns, newConversationBefore) { GraineChatRows.build(turns, newConversationBefore) }
    val list = rememberLazyListState()
    val offered = starters.takeIf { GraineChatRows.offersStarters(turns) }.orEmpty()
    val count = (if (above != null) 1 else 0) + rows.size +
        listOf(typing, component != null || pending || chose != null, quickReplies.isNotEmpty(), offered.isNotEmpty()).count { it }
    LaunchedEffect(count, turns.lastOrNull()?.text?.length) {
        if (count > 0) list.animateScrollToItem(count - 1)
    }
    val draft = journey.chatDraft.takeIf { agent == JourneyAgent.COACH }.orEmpty()
    LaunchedEffect(draft) { if (draft.isNotEmpty()) journey.chatDraft = null }

    JourneyScreen(
        title = title,
        subtitle = OwnText.chatStatus(status, typing, failed),
        onBack = onBack,
        onMenu = onMenu,
        scroll = false,
        actions = {
            if (failed) TextButton(onClick = { chat.retry() }) { Text("Retry") }
            actions()
        },
    ) {
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("journey_chat_${agent.sdkName}"),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (above != null) item(key = "above") { above() }
            if (rows.isEmpty() && !typing) {
                item(key = "empty") {
                    Text(
                        if (status == GraineChatStatus.CONNECTING) "Connecting…" else "Say hello to start.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            items(rows, key = { it.key }) { row ->
                when (row) {
                    is GraineChatRow.Day -> Separator(GraineChatRows.dayLabel(row))
                    is GraineChatRow.VoiceCall -> Separator(GraineChatRows.callLabel(DemoFormat.clock(row.at.toEpochMilliseconds())))
                    is GraineChatRow.NewConversation -> Separator(row.label)
                    is GraineChatRow.Turn -> Bubble(row.turn, onRetry = { chat.retry() })
                }
            }
            if (typing) item(key = "typing") { TypingBubble(title) }
            if (component != null || pending) {
                item(key = "component-${component?.id ?: "pending"}") {
                    OwnComponentCard(sdk, component, pending, onAnswered = { answered = answered.answered(it.summary) })
                }
            } else if (chose != null) {
                item(key = "answered") { AnsweredLine(chose, title) }
            }
            if (quickReplies.isNotEmpty()) {
                item(key = "replies") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        quickReplies.forEach { r -> SuggestionChip(onClick = { chat.sendQuickReply(r) }, label = { Text(r.label) }) }
                    }
                }
            }
            if (offered.isNotEmpty()) {
                item(key = "starters") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        offered.forEach { s -> AssistChip(onClick = { chat.send(s) }, label = { Text(s) }, modifier = Modifier.testTag("journey_starter")) }
                    }
                }
            }
        }
        HorizontalDivider()
        Column(Modifier.imePadding()) { Composer(onSend = { chat.send(it) }, initial = draft) }
    }
}

/** THE COACH'S CHAT, the journey's main screen: the call button in the header, the plan card above the thread. */
@Composable
fun CoachChat(journey: JourneyModel, onMenu: () -> Unit) {
    val j = journey.journey
    JourneyChat(
        journey = journey,
        agent = JourneyAgent.COACH,
        title = j.agents.coach.name,
        onMenu = onMenu,
        starters = j.coach.starters,
        above = { PlanCard(journey) },
        actions = {
            IconButton(onClick = { journey.callCoach() }, modifier = Modifier.testTag("journey_call_coach")) {
                Icon(JourneyIcons.Call, contentDescription = "Call ${j.agents.coach.name}")
            }
        },
    )
}

/** "Your plan is ready": the card in the chat once the coach saved the week — My week from it. */
@Composable
fun PlanCard(journey: JourneyModel) {
    val p = journey.plan ?: return
    val r = journey.journey.plan.ready
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        if (journey.planFresh) Text(r.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        JourneyCard(accent = true, modifier = Modifier.testTag("journey_plan_card")) {
            Text(r.card, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(p.title, style = MaterialTheme.typography.bodyMedium)
            Text(JourneyText.planLine(r.line, p), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { journey.go(ai.graine.demo.journey.JourneyRoute.WEEK) }, modifier = Modifier.testTag("journey_open_week")) {
                    Text(r.open)
                    Icon(JourneyIcons.Chevron, contentDescription = null)
                }
            }
        }
    }
}

/** The first call's chat version: the same questions, from the first-call agent; its save builds the plan. */
@Composable
fun FirstCallChat(journey: JourneyModel) {
    val f = journey.journey.firstCall
    JourneyChat(
        journey = journey,
        agent = JourneyAgent.FIRST_CALL,
        title = f.chat.title,
        onBack = { journey.leaveFirstCallChat() },
        starters = listOf(f.chatStart),
        above = { Text(f.chat.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}

/** Help & support: the support agent's chat. */
@Composable
fun SupportChat(journey: JourneyModel, onMenu: () -> Unit) {
    val j = journey.journey
    JourneyChat(journey = journey, agent = JourneyAgent.SUPPORT, title = j.agents.support.name, onMenu = onMenu, starters = j.support.starters)
}
