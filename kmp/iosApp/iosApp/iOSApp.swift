import ComposeApp
import GraineInAppRTC
import SwiftUI

/**
 * Graine Demo for iOS, from the shared Kotlin code: every screen, rule and SDK
 * call is in ../composeApp. This shell only shows it. The shared code starts
 * the SDK — its calls and VoIP pushes included — when the first screen is made.
 */
@main
struct GraineDemoApp: App {
    init() {
        GraineRTC.install()   // WebRTC calls: keeps the module in the build and registers it
        // The Journey's check-in reminders: a tap that starts the app reaches it (the delegate is set before launch ends).
        MainViewControllerKt.installJourneyNotifications()
    }

    var body: some Scene {
        WindowGroup {
            ContentView().ignoresSafeArea()
        }
    }
}
