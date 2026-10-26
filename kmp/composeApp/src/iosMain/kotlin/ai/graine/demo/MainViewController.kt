package ai.graine.demo

import ai.graine.demo.journey.JourneyNotifications
import ai.graine.demo.ui.DemoApp
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** The app's one screen for SwiftUI (iosApp/iosApp/ContentView.swift): the shared Compose UI. */
fun MainViewController(): UIViewController {
    // At launch, before any UI: the SDK, its calls and VoIP pushes, and the app's state.
    installJourneyNotifications()
    val model = DemoBootstrap.start()
    return ComposeUIViewController { DemoApp(model) }
}

/**
 * The app's notification delegate, for the Journey's check-in reminders: a tap reaches the journey. Called from the
 * app's init (iosApp/iOSApp.swift), before launch finishes, so a tap that starts the app is not lost; idempotent.
 */
fun installJourneyNotifications() = JourneyNotifications.install()
