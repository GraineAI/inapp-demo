package ai.graine.demo

import ai.graine.demo.ui.DemoApp
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** The app's one screen for SwiftUI (iosApp/iosApp/ContentView.swift): the shared Compose UI. */
fun MainViewController(): UIViewController {
    // At launch, before any UI: the SDK, its calls and VoIP pushes, and the app's state.
    val model = DemoBootstrap.start()
    return ComposeUIViewController { DemoApp(model) }
}
