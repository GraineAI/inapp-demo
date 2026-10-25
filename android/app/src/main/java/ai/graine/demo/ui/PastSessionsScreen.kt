package ai.graine.demo.ui

import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.DemoFormat
import ai.graine.demo.domain.PastSession
import ai.graine.demo.domain.PastSessionsText
import ai.graine.demo.domain.SessionTranscript
import ai.graine.demo.domain.Tone
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat

/**
 * PAST SESSIONS, DRAWN BY THE APP — the signed-in user's closed sessions with
 * the agent, newest first, from the SDK's `chat.pastSessions`: when, chat or
 * call, how long, how it ended, how many messages, the first words, and the
 * session id shortened with Copy. The next page loads at the end of the list
 * (`loadMorePastSessions`); empty, loading and refused lists say so in plain
 * words. A tap opens the session's transcript (`sessionTranscript`), read only.
 * Opened from the chat's header.
 */
@Composable
fun PastSessionsScreen(model: AppModel, agent: DemoAgent) {
    Surface(Modifier.fillMaxSize().testTag("past_sessions"), color = MaterialTheme.colorScheme.background) {
        val transcript = model.transcript
        if (transcript != null) TranscriptView(model, agent, transcript) else SessionList(model, agent)
    }
}

@Composable
private fun SessionList(model: AppModel, agent: DemoAgent) {
    val state = model.pastSessions
    val list = rememberLazyListState()
    // The end of the list is in view: the next page, while there is one and nothing is on its way.
    val atEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index == list.layoutInfo.totalItemsCount - 1 } }
    LaunchedEffect(atEnd, state.sessions.size, state.hasMore) {
        if (atEnd && state.hasMore && !state.loading && state.error == null && state.sessions.isNotEmpty()) model.loadMorePastSessions()
    }

    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                TextButton(onClick = { model.closePastSessions() }) { Text("Back") }
                Text("Past sessions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { model.refreshPastSessions() }, modifier = Modifier.testTag("past_sessions_refresh")) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
            Text(
                "With ${model.nameOf(agent)} · newest first · drawn by this app",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (state.sessions.isEmpty()) {
                    item(key = "state") {
                        val words = PastSessionsText.error(state.error)
                        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            when {
                                words != null -> {
                                    Text(words, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("past_sessions_error"))
                                    if (PastSessionsText.canRetry(state.error)) {
                                        OutlinedButton(onClick = { model.refreshPastSessions() }) { Text("Try again") }
                                    }
                                }
                                state.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text("  ${PastSessionsText.LOADING}", style = MaterialTheme.typography.bodyMedium)
                                }
                                else -> Text(
                                    PastSessionsText.EMPTY,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("past_sessions_empty"),
                                )
                            }
                        }
                    }
                }
                items(state.sessions, key = { it.id }) { session ->
                    SessionRow(session, opening = model.transcriptLoading == session.id, onOpen = { model.openTranscript(session.id) }, onCopied = { model.notice = "Session id copied." })
                    HorizontalDivider()
                }
                if (state.sessions.isNotEmpty()) {
                    item(key = "footer") {
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            when {
                                state.loading -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                state.error != null -> {
                                    Text(PastSessionsText.error(state.error).orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    TextButton(onClick = { model.loadMorePastSessions() }) { Text("Try again") }
                                }
                                state.hasMore -> TextButton(onClick = { model.loadMorePastSessions() }, modifier = Modifier.testTag("past_sessions_more")) { Text("Load more") }
                                else -> Text("No earlier sessions.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        model.transcriptError?.let { why ->
            Text(
                why,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.errorContainer).padding(12.dp).testTag("transcript_error"),
            )
        }
    }
}

@Composable
private fun SessionRow(session: PastSession, opening: Boolean, onOpen: () -> Unit, onCopied: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 10.dp).testTag("past_session"), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(DemoFormat.time(session.startedAt, System.currentTimeMillis()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(PastSessionsText.summary(session), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            session.preview?.takeIf { it.isNotBlank() }?.let {
                Text("“$it”", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = { clipboard.setText(AnnotatedString(session.id)); onCopied() }, contentPadding = PaddingValues(0.dp)) {
                Text(PastSessionsText.shortId(session.id), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                Text("  Copy", style = MaterialTheme.typography.labelSmall)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip(PastSessionsText.status(session.status), Tone.NEUTRAL)
            if (opening) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        }
    }
}

/** One past session, read only: its lines oldest first, each with its time; who said it by the side it sits on. */
@Composable
private fun TranscriptView(model: AppModel, agent: DemoAgent, t: SessionTranscript) {
    val clipboard = LocalClipboardManager.current
    val clock = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("transcript")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            TextButton(onClick = { model.closeTranscript() }) { Text("Back") }
            Text(DemoFormat.time(t.startedAt, System.currentTimeMillis()), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { clipboard.setText(AnnotatedString(t.id)); model.notice = "Session id copied." }) {
                Text(PastSessionsText.shortId(t.id), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(
            PastSessionsText.transcriptSummary(t),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (t.turns.isEmpty()) item { Text("This session has no messages.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(t.turns.size) { i ->
                val line = t.turns[i]
                val mine = PastSessionsText.isCustomer(line)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 300.dp)) {
                        Text(
                            line.text,
                            color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clip(RoundedCornerShape(14.dp))
                                .background(if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        Text(
                            (if (mine) "You" else model.nameOf(agent)) + (line.at?.let { " · " + clock.format(it) } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
