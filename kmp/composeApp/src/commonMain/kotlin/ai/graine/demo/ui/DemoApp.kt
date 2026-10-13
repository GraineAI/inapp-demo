package ai.graine.demo.ui

import ai.graine.demo.platform.PlatformBackHandler
import ai.graine.inapp.kmp.Graine
import ai.graine.inapp.kmp.compose.GraineAgentBar
import ai.graine.inapp.kmp.compose.GraineCallScreen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The layers, bottom to top: the app (sign-in or the tabs), the chat, the
 * voice bar, a notice, and the call screen. Every layer is shared code; the
 * chat, the voice bar and the call screen are the SDK's own, drawn in the
 * brand's design (ai.graine:inapp-kmp-compose).
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
                    PlatformBackHandler { model.closeChat() }
                    key(agent) { ChatScreen(model, agent) }
                }

                val voice = model.voiceAgent
                if (voice != null && !model.chatOpen) {
                    key(voice) {
                        // The bar draws itself at the bottom of the band it is given, above the tab bar.
                        GraineAgentBar(
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                                .padding(bottom = 80.dp).height(200.dp),
                            name = model.nameOf(voice),
                            onClose = { model.voiceBarClosed() },
                            agent = voice.sdkName,
                        )
                    }
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

                // A call — ringing, live, or a missed one to call back — on the SDK's call screen.
                val call by Graine.calls.state.collectAsState()
                if (call.showsCallScreen) GraineCallScreen(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun MainTabs(model: AppModel) {
    val pack = model.pack ?: return
    if (model.callLogOpen) PlatformBackHandler { model.closeCallLog() }
    if (model.profileOpen) PlatformBackHandler { model.closeProfile() }
    Scaffold(
        bottomBar = {
            NavigationBar {
                tab(model, DemoTab.HOME, "Home", DemoIcons.Home)
                tab(model, DemoTab.ITEMS, pack.items.title, DemoIcons.List)
                tab(model, DemoTab.CALLS, "Calls", DemoIcons.Calendar)
                tab(model, DemoTab.HELP, "Help", DemoIcons.Call)
                tab(model, DemoTab.MEMORY, "Memory", DemoIcons.Info)
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
private fun RowScope.tab(model: AppModel, tab: DemoTab, label: String, icon: ImageVector) {
    NavigationBarItem(
        selected = model.tab == tab,
        onClick = { model.select(tab) },
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label, maxLines = 1) },
    )
}

/** Shown only when the build carries no use-case pack. */
@Composable
private fun NoPacks() {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
        Text("No use-case packs in this build", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(
            "The app bundles demo/usecases/*.json when it is built. Check that the folder exists and build again.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}
