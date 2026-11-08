package ai.graine.demo.ui

import ai.graine.demo.platform.Platform
import ai.graine.demo.platform.PlatformBackHandler
import ai.graine.demo.ui.own.OwnBookingScreen
import ai.graine.demo.ui.own.OwnCallHost
import ai.graine.demo.ui.own.OwnChatScreen
import ai.graine.demo.ui.own.OwnTalkPanel
import ai.graine.demo.ui.own.PastSessionsScreen
import ai.graine.demo.ui.own.PublicStateScreen
import ai.graine.demo.ui.journey.JourneyApp
import ai.graine.demo.ui.journey.JourneyCallHost
import ai.graine.inapp.kmp.Graine
import ai.graine.inapp.kmp.compose.GraineAgentBar
import ai.graine.inapp.kmp.compose.GraineAvatarFace
import ai.graine.inapp.kmp.compose.GraineCallScreen
import androidx.compose.foundation.layout.Arrangement
import ai.graine.inapp.kmp.GraineAvatarState
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The layers, bottom to top: the app (sign-in or the tabs), the chat, the
 * voice bar, a notice, and the call screen. Every layer is shared code. With
 * Graine's screens (Home's switch, the default) the chat, the voice bar and the
 * call screen are the SDK's own, drawn in the brand's design
 * (ai.graine:inapp-kmp-compose); with "Your own design" the app draws each of
 * them — and its own booking screen and the Public state screen — from the
 * SDK's public state (ui/own). While the pack's Journey is open it is the
 * whole app (ui/journey), with its own call screen.
 */
@Composable
fun DemoApp(model: AppModel) {
    DemoTheme(accent = model.pack?.accent) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                val journey = model.journey
                if (journey != null && journey.open) {
                    JourneyApp(journey)
                    // iOS draws the call screen in place, over CallKit's; Android's is the app's call activity.
                    if (Platform.ownCallScreenInPlace) JourneyCallHost(journey, Modifier.fillMaxSize())
                    return@Box
                }
                when {
                    model.pack == null -> NoPacks()
                    model.user == null -> SignInScreen(model)
                    else -> MainTabs(model)
                }

                model.chatAgent?.let { agent ->
                    PlatformBackHandler { model.closeChat() }
                    key(agent, model.ownDesign) { if (model.ownDesign) OwnChatScreen(model, agent) else ChatScreen(model, agent) }
                }

                val voice = model.voiceAgent
                // THE FACE STAGE: while a call in the bar has a face (and the switch is on), the screen dims behind it and
                // the agent's face is shown large, as on a video call — the voice bar and its captions stay below it.
                val face by Graine.avatar.state.collectAsState()
                val faceStage = model.faceOn && voice != null && !model.chatOpen && !model.ownDesign && face.showsFace
                if (faceStage) {
                    Box(Modifier.fillMaxSize().background(Color(0xE6101014)).testTag("face_stage"))
                }
                if (voice != null && !model.chatOpen) {
                    key(voice, model.ownDesign) {
                        if (model.ownDesign) {
                            // The app's own voice panel, over the screen it talks about, above the tab bar.
                            OwnTalkPanel(
                                model,
                                voice,
                                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 88.dp),
                            )
                        } else {
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
                }

                if (model.ownDesign && model.faceOn && voice != null && !model.chatOpen && face.showsFace) {
                    // Your own design: the face where it always was, above the app's own voice panel.
                    GraineAvatarFace(
                        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 296.dp)
                            .size(200.dp, 250.dp).clip(RoundedCornerShape(24.dp)).testTag("agent_face"),
                    )
                }
                if (faceStage && voice != null) {
                    // As in the email: the face fills the room above the bar (a small margin each side, at most 1.6x as
                    // tall as wide so the square picture is never cropped past the shoulders), the agent's name and role
                    // over its top-left, LIVE and the call's time over its top-right; the bar's captions stay below.
                    BoxWithConstraints(
                        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 8.dp, bottom = 296.dp),
                    ) {
                        val width = (maxWidth - 24.dp).coerceAtMost(480.dp)
                        val height = maxHeight.coerceAtMost(width * 1.6f)
                        Box(Modifier.align(Alignment.BottomCenter).size(width, height).clip(RoundedCornerShape(28.dp))) {
                            GraineAvatarFace(Modifier.fillMaxSize().testTag("agent_face"))
                            FaceTitle(model.nameOf(voice), face.phase == GraineAvatarState.Phase.LIVE)
                        }
                    }
                }

                model.pastSessionsAgent?.let { agent ->
                    PlatformBackHandler { if (model.transcript != null) model.closeTranscript() else model.closePastSessions() }
                    PastSessionsScreen(model, agent)
                }

                if (model.ownBooking != null) {
                    PlatformBackHandler { model.closeOwnBooking() }
                    OwnBookingScreen(model)
                }

                if (model.publicStateOpen) {
                    PlatformBackHandler { model.closePublicState() }
                    PublicStateScreen(model)
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

                if (model.ownDesign) {
                    // Your own design: the app's call screen, in place (iOS); Android's is its own activity, which
                    // the SDK opens for every call, ring and missed call (GraineCallsConfig.androidCallActivity).
                    if (Platform.ownCallScreenInPlace) OwnCallHost(model, Modifier.fillMaxSize())
                } else {
                    // A call — ringing, live, or a missed one to call back — on the SDK's call screen.
                    val call by Graine.calls.state.collectAsState()
                    if (call.showsCallScreen) GraineCallScreen(Modifier.fillMaxSize())
                }
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


/** The face's title, as in the email: name and role at the top-left, LIVE and the call's time at the top-right. */
@Composable
private fun BoxScope.FaceTitle(name: String, live: Boolean) {
    var since by remember { mutableStateOf<TimeMark?>(null) }
    var seconds by remember { mutableStateOf(0L) }
    LaunchedEffect(live) {
        if (live && since == null) since = TimeSource.Monotonic.markNow()
        while (live) {
            seconds = since?.elapsedNow()?.inWholeSeconds ?: 0L
            delay(1000)
        }
    }
    Row(
        Modifier.align(Alignment.TopCenter).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
            .padding(start = 18.dp, end = 16.dp, top = 16.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            Text(name, color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Text("AI assistant", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(50), color = if (live) Color(0xFFDC2626) else Color(0x33FFFFFF)) {
                Text(
                    if (live) "LIVE" else "Connecting…",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp).testTag("face_state"),
                )
            }
            if (live) {
                val clock = "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
                Text(clock, color = Color.White, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 8.dp).testTag("face_clock"))
            }
        }
    }
}
