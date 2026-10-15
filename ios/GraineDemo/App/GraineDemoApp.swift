import GraineInAppRTC
import SwiftUI

@main
struct GraineDemoApp: App {
    @StateObject private var model: AppModel

    init() {
        GraineRTC.install()   // WebRTC calls: keeps the module in the build and registers it
        let config = AppConfig.current
        let packs = UseCasePacks.load()
        let pack = UseCasePacks.chosen(packs, defaults: .standard)
        // The SDK and its calls are set up here, on every launch: a VoIP push
        // that wakes a closed app is delivered only once calls are configured.
        // While the app hosts its unit tests, nothing reaches a server.
        let hostingTests = ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
        let graine: GraineClient = hostingTests
            ? OfflineGraineClient()
            : GraineService.start(config: config, callName: pack?.call.callName ?? "Graine Demo")
        _model = StateObject(wrappedValue: AppModel(graine: graine, config: config, packs: packs))
    }

    var body: some Scene {
        WindowGroup {
            let accent = Color(hex: model.pack?.accent) ?? DemoTheme.fallbackAccent
            RootView()
                .environmentObject(model)
                .environment(\.demoAccent, accent)
                .tint(accent)
        }
    }
}
