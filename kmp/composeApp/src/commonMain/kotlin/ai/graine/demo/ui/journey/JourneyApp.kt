package ai.graine.demo.ui.journey

import ai.graine.demo.journey.JourneyModel
import ai.graine.demo.journey.JourneyRoute
import ai.graine.demo.journey.JourneyStage
import ai.graine.demo.platform.OnAppForeground
import ai.graine.demo.platform.PlatformBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * THE JOURNEY, ON SCREEN — the whole app while its user is signed in: onboarding, the first call and its screens,
 * the plan being built, then the chat as the main screen with a sidebar (Today, My plan, Progress, Account, Help &
 * support, Log out). The call screen is drawn over it by the host (DemoApp on iOS, the call activity on Android).
 */
@Composable
fun JourneyApp(journey: JourneyModel) {
    OnAppForeground { journey.onForeground() }
    Box(Modifier.fillMaxSize()) {
        when (journey.stage) {
            JourneyStage.ONBOARDING -> OnboardingScreens(journey)
            JourneyStage.FIRST_CALL -> FirstCallScreen(journey)
            JourneyStage.BOOKED -> BookedScreen(journey)
            JourneyStage.IN_CALL -> InCallScreen(journey)
            JourneyStage.COULD_NOT_CONNECT -> CouldNotConnectScreen(journey)
            JourneyStage.FIRST_CALL_CHAT -> {
                PlatformBackHandler { journey.leaveFirstCallChat() }
                FirstCallChat(journey)
            }
            JourneyStage.BUILDING_PLAN -> BuildingPlanScreen(journey)
            JourneyStage.READY -> Main(journey)
        }

        journey.picker?.let { picker ->
            PlatformBackHandler { journey.closePicker() }
            PickerScreen(journey, picker)
        }

        journey.notice?.let { text ->
            LaunchedEffect(text) {
                delay(4_000)
                if (journey.notice == text) journey.notice = null
            }
            Snackbar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).testTag("journey_notice")) { Text(text) }
        }
    }
}

@Composable
private fun Main(journey: JourneyModel) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openMenu: () -> Unit = { scope.launch { drawer.open() } }
    fun go(route: JourneyRoute) {
        journey.go(route)
        scope.launch { drawer.close() }
    }
    val item = journey.openItem
    when {
        item != null -> PlatformBackHandler { journey.closeItem() }
        journey.shareOpen -> PlatformBackHandler { journey.openShare(false) }
        journey.route != JourneyRoute.CHAT -> PlatformBackHandler { journey.go(JourneyRoute.CHAT) }
    }
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
                    Text(journey.profile.values["name"] ?: journey.journey.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    DrawerItem(journey.journey.agents.coach.name, JourneyIcons.Call, journey.route == JourneyRoute.CHAT, "journey_nav_chat") { go(JourneyRoute.CHAT) }
                    DrawerItem("Today", JourneyIcons.Today, journey.route == JourneyRoute.TODAY, "journey_nav_today") { go(JourneyRoute.TODAY) }
                    DrawerItem("My plan", JourneyIcons.Plan, journey.route == JourneyRoute.WEEK, "journey_nav_plan") { go(JourneyRoute.WEEK) }
                    DrawerItem("Progress", JourneyIcons.Progress, journey.route == JourneyRoute.PROGRESS, "journey_nav_progress") { go(JourneyRoute.PROGRESS) }
                    DrawerItem("Account", JourneyIcons.Account, journey.route == JourneyRoute.ACCOUNT, "journey_nav_account") { go(JourneyRoute.ACCOUNT) }
                    DrawerItem(journey.journey.agents.support.name, JourneyIcons.Help, journey.route == JourneyRoute.SUPPORT, "journey_nav_support") { go(JourneyRoute.SUPPORT) }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    DrawerItem("Log out", JourneyIcons.LogOut, false, "journey_nav_log_out") {
                        scope.launch { drawer.close() }
                        journey.logOut()
                    }
                }
            }
        },
    ) {
        when (journey.route) {
            JourneyRoute.CHAT -> CoachChat(journey, openMenu)
            JourneyRoute.TODAY -> TodayScreen(journey, openMenu)
            JourneyRoute.WEEK -> WeekScreen(journey, openMenu)
            JourneyRoute.PROGRESS -> ProgressScreen(journey, openMenu)
            JourneyRoute.ACCOUNT -> AccountScreen(journey, openMenu)
            JourneyRoute.SUPPORT -> SupportChat(journey, openMenu)
        }
        if (item != null) ItemScreen(journey, item)
        if (journey.shareOpen) ShareScreen(journey)
    }
}

@Composable
private fun DrawerItem(label: String, icon: ImageVector, selected: Boolean, tag: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(icon, contentDescription = null) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.testTag(tag),
    )
}
