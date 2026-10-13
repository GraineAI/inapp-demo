import SwiftUI

/**
 * Graine Demo for iOS, from the shared Kotlin code: every screen, rule and SDK
 * call is in ../composeApp. This shell only shows it. The shared code starts
 * the SDK — its calls and VoIP pushes included — when the first screen is made.
 */
@main
struct GraineDemoApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView().ignoresSafeArea()
        }
    }
}
