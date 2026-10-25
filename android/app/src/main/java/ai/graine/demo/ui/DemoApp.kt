package ai.graine.demo.ui

import ai.graine.demo.graine.GraineService
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

/**
 * The layers, bottom to top: the app (sign-in or the tabs), the chat, the
 * voice bar, Past sessions and a notice. The call screen is the SDK's own
 * full-screen activity, so it comes up over everything by itself.
 */
@Composable
fun DemoApp(model: AppModel) {
    DemoTheme(accent = model.pack?.accent) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                when {
                    model.pack == null -> NoPacks()
                    model.user == null -> SignInScreen(model)
                    else -> MainTabs(model)
                }

                model.chatAgent?.let { agent ->
                    BackHandler { model.closeChat() }
                    key(agent) { ChatScreen(model, agent) }
                }

                val voice = model.voiceAgent
                if (voice != null && !model.chatOpen) {
                    key(voice) {
                        AndroidView(
                            factory = { ctx -> GraineService.get().voiceBar(ctx, voice, model.nameOf(voice)) { model.voiceBarClosed() } },
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 80.dp),
                        )
                    }
                }

                // Past sessions, over the chat it was opened from; Back closes the transcript first.
                model.pastSessionsAgent?.let { agent ->
                    BackHandler { if (model.transcript != null) model.closeTranscript() else model.closePastSessions() }
                    PastSessionsScreen(model, agent)
                }

                model.notice?.let { text ->
                    LaunchedEffect(text) {
                        delay(4_000)
                        if (model.notice == text) model.notice = null
                    }
                    Snackbar(
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    ) { Text(text) }
                }
            }
        }
    }
}

@Composable
private fun MainTabs(model: AppModel) {
    val pack = model.pack ?: return
    if (model.callLogOpen) BackHandler { model.closeCallLog() }
    if (model.profileOpen) BackHandler { model.closeProfile() }
    Scaffold(
        bottomBar = {
            NavigationBar {
                tab(model, DemoTab.HOME, "Home") { Icon(Icons.Filled.Home, contentDescription = null) }
                tab(model, DemoTab.ITEMS, pack.items.title) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) }
                tab(model, DemoTab.CALLS, "Calls") { Icon(Icons.Filled.DateRange, contentDescription = null) }
                tab(model, DemoTab.HELP, "Help") { Icon(Icons.Filled.Call, contentDescription = null) }
                tab(model, DemoTab.MEMORY, "Memory") { Icon(Icons.Filled.Info, contentDescription = null) }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (model.tab) {
                DemoTab.HOME -> if (model.profileOpen) ProfileScreen(model) else HomeScreen(model)
                DemoTab.ITEMS -> ItemsScreen(model)
                DemoTab.CALLS -> if (model.callLogOpen) CallLogScreen(model) else ScheduledCallsScreen(model)
                DemoTab.HELP -> HelpScreen(model)
                DemoTab.MEMORY -> MemoryScreen(model)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.tab(model: AppModel, tab: DemoTab, label: String, icon: @Composable () -> Unit) {
    NavigationBarItem(
        selected = model.tab == tab,
        onClick = { model.select(tab) },
        icon = icon,
        label = { Text(label, maxLines = 1) },
    )
}

/** Shown only when the build carries no use-case pack (the usecases folder was not bundled). */
@Composable
private fun NoPacks() {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Text("No use-case packs in this build", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(
            "The app bundles demo/usecases/*.json. Check that the folder exists and build again.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}
