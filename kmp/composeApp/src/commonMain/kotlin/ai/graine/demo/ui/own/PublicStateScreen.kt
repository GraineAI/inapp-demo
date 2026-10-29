package ai.graine.demo.ui.own

import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.DemoFormat
import ai.graine.demo.domain.OwnText
import ai.graine.demo.ui.AppModel
import ai.graine.demo.ui.nowMs
import ai.graine.inapp.kmp.Graine
import ai.graine.inapp.kmp.GraineAgent
import ai.graine.inapp.kmp.GraineCaption
import ai.graine.inapp.kmp.GraineChatChannel
import ai.graine.inapp.kmp.GraineChatRow
import ai.graine.inapp.kmp.GraineChatSession
import ai.graine.inapp.kmp.GraineChatRows
import ai.graine.inapp.kmp.GraineChatTurn
import ai.graine.inapp.kmp.GraineEvent
import ai.graine.inapp.kmp.GraineRole
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * PUBLIC STATE — every value the SDK exposes to an app that draws its own
 * screens, live, grouped by surface: who, chat, voice, calls (with the
 * connection report), booking, components and events. For client meetings:
 * nothing personal is shown beyond what the app itself entered — no message,
 * caption or memory text (roles, counts and lengths instead), variable names
 * without their values, ids shortened.
 */
@Composable
fun PublicStateScreen(model: AppModel) {
    var agent by remember { mutableStateOf(DemoAgent.MAIN) }
    val sdk = Graine.agent(agent.sdkName)
    // What is read rather than collected (a getter, not a flow) is read again every second.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            tick++
        }
    }

    OwnScreen(
        title = "Public state",
        subtitle = "What the SDK exposes, live · drawn by this app",
        onBack = { model.closePublicState() },
        modifier = Modifier.testTag("public_state"),
    ) {
        if (model.isAvailable(DemoAgent.HELP)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DemoAgent.entries.filter { model.isAvailable(it) }.forEach { a ->
                    FilterChip(selected = a == agent, onClick = { agent = a }, label = { Text(model.nameOf(a)) })
                }
            }
        }
        if (sdk == null) {
            Text("This agent is not configured in this build.", Modifier.padding(16.dp))
            return@OwnScreen
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "who") { WhoState(sdk, tick) }
            item(key = "chat") { ChatState(model, agent, sdk) }
            item(key = "past") { PastSessionsState(model, agent) }
            item(key = "voice") { VoiceState(model, agent, sdk) }
            item(key = "calls") { CallsState(tick) }
            item(key = "booking") { BookingState(model, agent, sdk, tick) }
            item(key = "components") { ComponentState(model, agent, sdk) }
            item(key = "events") { EventsState(model, agent, sdk) }
        }
    }
}

@Composable
private fun WhoState(sdk: GraineAgent, tick: Int) {
    val session by sdk.session.collectAsState()
    val theme by sdk.theme.collectAsState()
    val scope = rememberCoroutineScope()
    var memories by remember(sdk) { mutableStateOf("tap Read") }
    val variables = remember(sdk, tick) { runCatching { sdk.currentVariables().keys.sorted() }.getOrDefault(emptyList()) }
    val problems = remember(tick) { runCatching { Graine.calls.callSetupProblems.map { it.code } }.getOrDefault(emptyList()) }
    Readout(
        "Who · the app's, every agent",
        listOf(
            "configured" to Graine.agents.keys.joinToString(", ").ifEmpty { "no" },
            "installId" to OwnText.shortId(runCatching { Graine.installId }.getOrNull()),
            "memoryEnabled" to runCatching { Graine.memoryEnabled.toString() }.getOrDefault("—"),
            "agent settings" to if (session == null) "not loaded" else "loaded",
            "agentId" to OwnText.shortId(session?.agentId),
            "agent memory" to (session?.memoryEnabled?.toString() ?: "—"),
            "identityField" to (session?.identityField ?: "—"),
            "devicesPerUser" to (session?.devicesPerUser?.toString() ?: "—"),
            "declaredActions" to (session?.declaredActions?.sorted()?.joinToString(", ")?.ifEmpty { "none" } ?: "—"),
            "theme" to if (theme == null) "none" else "brand tokens loaded",
            "variables (keys)" to variables.joinToString(" ") { "{$it}" }.ifEmpty { "none" },
            "callSetupProblems" to problems.joinToString(", ").ifEmpty { "none" },
            "memories()" to memories,
        ),
    )
    OutlinedButton(onClick = {
        scope.launch {
            memories = "reading…"
            memories = try {
                "${sdk.memories().size} remembered"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                "refused: ${(e as? ai.graine.inapp.kmp.GraineException)?.code?.wire ?: "error"}"
            }
        }
    }, modifier = Modifier.padding(top = 6.dp)) { Text("Read memories()") }
}

@Composable
private fun ChatState(model: AppModel, agent: DemoAgent, sdk: GraineAgent) {
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
    val fresh by chat.newConversationBefore.collectAsState()
    val sessionId by chat.sessionId.collectAsState()
    val sessions by chat.sessions.collectAsState()
    val rows = remember(turns, fresh) { GraineChatRows.build(turns, fresh) }
    Readout(
        "Chat · ${model.nameOf(agent)}",
        listOf(
            "status" to status.name.lowercase(),
            "failed" to failed.toString(),
            "typing" to typing.toString(),
            "turns" to "${turns.size} (${turns.count { it.restored }} restored)",
            "last turn" to (turns.lastOrNull()?.let { describe(it) } ?: "—"),
            "quickReplies" to quickReplies.size.toString(),
            "ended" to ended.toString(),
            "rating" to (rating?.let { "${it.style} ${it.value}/${it.scale}" } ?: "—"),
            "pinned" to if (pinned == null) "—" else "yes",
            "upcomingCalls" to upcoming.size.toString(),
            "newConversationBefore" to fresh.size.toString(),
            "sessionId" to (sessionId?.let { OwnText.shortId(it) } ?: "— (none sent yet)"),
            "sessions" to sessionsSummary(sessions),
            "rows (GraineChatRows)" to rowsSummary(rows),
        ),
    )
}

@Composable
private fun PastSessionsState(model: AppModel, agent: DemoAgent) {
    val past by model.graine.pastSessions(agent).collectAsState()
    Readout(
        "Past sessions · ${model.nameOf(agent)}",
        listOf(
            "pastSessions" to "${past.sessions.size} loaded",
            "loading" to past.loading.toString(),
            "hasMore" to past.hasMore.toString(),
            "error" to (past.error ?: "—"),
            "newest" to (past.sessions.firstOrNull()?.let { "${OwnText.shortId(it.id)} · ${it.channel} · ${it.turns} turns" } ?: "—"),
            "chat.closeDelaySeconds" to model.graine.chatCloseDelaySeconds(agent).toString(),
        ),
    )
    OutlinedButton(onClick = { model.graine.refreshPastSessions(agent) }, modifier = Modifier.padding(top = 6.dp)) { Text("refreshPastSessions()") }
}

@Composable
private fun VoiceState(model: AppModel, agent: DemoAgent, sdk: GraineAgent) {
    val info by sdk.voice.state.collectAsState()
    Readout(
        "Voice · ${model.nameOf(agent)}",
        listOf(
            "status" to info.status.wire,
            "connected" to info.connected.toString(),
            "connecting" to info.connecting.toString(),
            "muted" to info.muted.toString(),
            "captionsOn" to info.captionsOn.toString(),
            "captions" to info.captions.size.toString(),
            "last caption" to (info.captions.lastOrNull()?.let { describe(it) } ?: "—"),
            "error" to (info.error ?: "—"),
            "agentLevel" to OwnText.level(info.agentLevel),
            "micLevel" to OwnText.level(info.micLevel),
            "transport" to (info.transport?.name?.lowercase() ?: "—"),
            "fallbackReason" to (info.transportFallbackReason ?: "—"),
        ),
    )
}

@Composable
private fun CallsState(tick: Int) {
    val info by Graine.calls.state.collectAsState()
    val permissions = remember(tick) { runCatching { Graine.calls.callPermissions }.getOrNull() }
    Readout(
        "Calls · the app's, every agent",
        listOf(
            "state" to info.state.name.lowercase(),
            "callId" to OwnText.shortId(info.callId),
            "agent" to (info.agent ?: "—"),
            "callerName" to (info.callerName ?: "—"),
            "incoming" to info.incoming.toString(),
            "hosted" to info.hosted.toString(),
            "reason" to (info.reason ?: "—"),
            "muted" to info.muted.toString(),
            "speakerOn" to info.speakerOn.toString(),
            "speakerByDefault" to Graine.calls.speakerByDefault.toString(),
            "agentSpeaking" to info.agentSpeaking.toString(),
            "agentLevel" to OwnText.level(info.agentLevel),
            "reconnecting" to info.reconnecting.toString(),
            "startedAt" to (info.startedAt?.let { DemoFormat.clock(it.toEpochMilliseconds()) } ?: "—"),
            "endReason" to (info.endReason ?: "—"),
            "endCause" to (info.endCause ?: "—"),
            "caption" to (info.caption?.let { describe(it) } ?: "—"),
            "captions" to info.captions.size.toString(),
            "missedCall" to (info.missedCall?.let { OwnText.shortId(it.callId) } ?: "—"),
            "transport" to (info.transport?.wire ?: "—"),
            "fallbackReason" to (info.transportFallbackReason ?: "—"),
            "showsCallScreen" to info.showsCallScreen.toString(),
            "permissions" to (permissions?.let { "mic ${it.microphone.wire} · notifications ${it.notifications.wire} · fullScreen ${it.fullScreen.wire}" } ?: "—"),
        ),
    )
    Readout(
        "Connection · state.connection",
        OwnText.connectionRows(info.connection),
        note = if (info.connection == null) "null until the call's first fact" else null,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun BookingState(model: AppModel, agent: DemoAgent, sdk: GraineAgent, tick: Int) {
    val session by sdk.session.collectAsState()
    val scope = rememberCoroutineScope()
    var booked by remember(sdk) { mutableStateOf("tap Read") }
    val zone = TimeZone.currentSystemDefault()
    val today = Instant.fromEpochMilliseconds(nowMs()).toLocalDateTime(zone).date
    val rules = remember(session, tick) { sdk.schedulingRules }
    // Collected, never read once: it follows the session, identify and setMemory by itself (0.14.4).
    val bookingState by sdk.bookingState.collectAsState()
    val todaySlots = remember(session, tick) { sdk.bookingSlots(today) }
    val tomorrowSlots = remember(session, tick) { sdk.bookingSlots(today.plus(1, DateTimeUnit.DAY)) }
    Readout(
        "Booking · ${model.nameOf(agent)}",
        listOf(
            "bookingState.available" to bookingState.available.toString(),
            "bookingState.reason" to (bookingState.reason ?: "—"),
            "usersCanBook" to rules.usersCanBook.toString(),
            "minLeadMinutes" to rules.minLeadMinutes.toString(),
            "maxDaysAhead" to rules.maxDaysAhead.toString(),
            "maxPendingPerUser" to rules.maxPendingPerUser.toString(),
            "ringTimeoutSeconds" to rules.ringTimeoutSeconds.toString(),
            "callName" to (rules.callName ?: "—"),
            "hostedRinging" to rules.hostedRinging.toString(),
            "platforms" to rules.platforms.joinToString(", ") { it.wire }.ifEmpty { "every" },
            "bookingSlots(today)" to slotsSummary(todaySlots),
            "bookingSlots(tomorrow)" to slotsSummary(tomorrowSlots),
            "scheduledCalls()" to booked,
        ),
    )
    OutlinedButton(onClick = {
        scope.launch {
            booked = "reading…"
            booked = try {
                val list = sdk.scheduledCalls()
                "${list.size} to come" + (list.firstOrNull()?.at?.let { " · next ${DemoFormat.time(it, nowMs())}" } ?: "")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                "refused: ${(e as? ai.graine.inapp.kmp.GraineException)?.code?.wire ?: "error"}"
            }
        }
    }, modifier = Modifier.padding(top = 6.dp)) { Text("Read scheduledCalls()") }
}

@Composable
private fun ComponentState(model: AppModel, agent: DemoAgent, sdk: GraineAgent) {
    val component by sdk.component.collectAsState()
    val pending by sdk.componentPending.collectAsState()
    val awaiting by sdk.componentAwaitingReply.collectAsState()
    val c = component
    Readout(
        "Components · ${model.nameOf(agent)}",
        listOf(
            "component" to if (c == null) "—" else "on screen",
            "id" to OwnText.shortId(c?.id),
            "kind" to (c?.kind ?: "—"),
            "data (keys)" to (c?.data?.keys?.joinToString(", ")?.ifEmpty { "none" } ?: "—"),
            "fallbackText" to if (c == null) "—" else if (c.fallbackText != null) "yes (${c.fallbackText?.length} chars)" else "none",
            "style" to (c?.let { OwnText.componentLook(it.style) } ?: "—"),
            "componentPending" to pending.toString(),
            "componentAwaitingReply" to awaiting.toString(),
        ),
    )
}

@Composable
private fun EventsState(model: AppModel, agent: DemoAgent, sdk: GraineAgent) {
    val lines = remember(sdk) { mutableStateListOf<Pair<String, String>>() }
    fun add(name: String, detail: String) {
        lines.add(0, DemoFormat.clock(nowMs()) to "$name $detail".trim())
        while (lines.size > 12) lines.removeAt(lines.lastIndex)
    }
    LaunchedEffect(sdk) { sdk.events.collect { e -> describe(e).let { (n, d) -> add(n, d) } } }
    LaunchedEffect(Unit) {
        Graine.calls.outcomes.collect { o ->
            add("calls.outcome", listOfNotNull(o.outcome.wire, o.reason, o.endCause, o.durationSeconds?.let { "${it}s" }, o.agent).joinToString(" · "))
        }
    }
    LaunchedEffect(Unit) { Graine.calls.incoming.collect { i -> add("calls.incoming", listOfNotNull(if (i.hosted) "scheduled" else null, i.agent).joinToString(" · ")) } }
    Readout(
        "Events · ${model.nameOf(agent)} (since this screen opened)",
        lines.ifEmpty { listOf("—" to "none yet") },
    )
}

/** A turn without its words: who, how long, and its state. */
private fun describe(turn: GraineChatTurn): String = listOfNotNull(
    if (turn.role == GraineRole.USER) "user" else "agent",
    "${turn.text.length} chars",
    turn.delivery?.name?.lowercase(),
    "live".takeIf { turn.live },
    "restored".takeIf { turn.restored },
    turn.channel.wire.takeIf { it != "chat" },
    "proactive".takeIf { turn.proactive },
    turn.component?.kind?.let { "component $it" },
    turn.session?.let { "session ${OwnText.shortId(it)}" },
).joinToString(" · ")

/** A caption without its words. */
private fun describe(caption: GraineCaption): String =
    listOfNotNull(if (caption.role == GraineRole.USER) "user" else "agent", "${caption.text.length} chars", "live".takeIf { caption.live }).joinToString(" · ")

/** An event's name and what may be shown of it. */
private fun describe(e: GraineEvent): Pair<String, String> = when (e) {
    is GraineEvent.SessionReady -> "SessionReady" to OwnText.shortId(e.session.agentId)
    is GraineEvent.Caption -> "Caption" to listOfNotNull(if (e.role == GraineRole.USER) "user" else "agent", "live".takeIf { e.live }).joinToString(" · ")
    GraineEvent.VoiceEnded -> "VoiceEnded" to ""
    is GraineEvent.Component -> "Component" to (e.component.kind ?: "")
    is GraineEvent.ComponentPending -> "ComponentPending" to ""
    is GraineEvent.ChatMessage -> "ChatMessage" to listOfNotNull("agent", "proactive".takeIf { e.turn.proactive }).joinToString(" · ")
    is GraineEvent.ActionRequested -> "ActionRequested" to e.name
    is GraineEvent.ActionCompleted -> "ActionCompleted" to "${e.name} · ${e.result.status.wire}"
    is GraineEvent.Friction -> "Friction" to listOfNotNull(e.name, e.screen).joinToString(" · ")
    is GraineEvent.MicrophoneDenied -> "MicrophoneDenied" to ""
    is GraineEvent.Error -> "Error" to e.message.take(80)
}

/** "3: 2 chat · 1 voice · current …a1b2c3", or the one run with no id. */
private fun sessionsSummary(sessions: List<GraineChatSession>): String {
    if (sessions.isEmpty()) return "none"
    val voice = sessions.count { it.channel == GraineChatChannel.VOICE }
    val unnumbered = sessions.count { it.id == null }
    val current = sessions.firstOrNull { it.current }?.id?.let { " · current ${OwnText.shortId(it)}" } ?: ""
    return "${sessions.size}: ${sessions.size - voice} chat · $voice voice" + (if (unnumbered > 0) " · $unnumbered without an id" else "") + current
}

private fun rowsSummary(rows: List<GraineChatRow>): String {
    if (rows.isEmpty()) return "none"
    val days = rows.count { it is GraineChatRow.Day }
    val calls = rows.count { it is GraineChatRow.VoiceCall }
    val fresh = rows.count { it is GraineChatRow.NewConversation }
    val turns = rows.count { it is GraineChatRow.Turn }
    return "${rows.size}: $turns bubbles · $days days · $calls calls · $fresh new conversation"
}

private fun slotsSummary(slots: List<Instant>): String = when {
    slots.isEmpty() -> "none"
    else -> "${slots.size} · ${DemoFormat.clock(slots.first().toEpochMilliseconds())} to ${DemoFormat.clock(slots.last().toEpochMilliseconds())}"
}
