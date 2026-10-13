import SwiftUI

/**
 * The layers, bottom to top: the app (sign-in or the tabs), the chat, the
 * voice bar, a notice, and the call screen. Kept in one stack rather than as
 * modal covers so a call can always come up over the chat.
 */
struct RootView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        ZStack {
            Group {
                if model.pack == nil {
                    NoPacksView()
                } else if model.user == nil {
                    SignInView()
                } else {
                    MainTabs()
                }
            }

            if let agent = model.chatAgent {
                ChatScreen(agent: agent)
                    .id(agent)
                    .transition(.move(edge: .bottom))
                    .zIndex(1)
            }

            if let agent = model.voiceAgent, !model.chatOpen {
                VStack {
                    Spacer()
                    model.graine.voiceBar(agent: agent, name: model.name(of: agent), bottomInset: 64) { model.voiceBarClosed() }
                }
                .id(agent)
                .zIndex(2)
            }

            if let notice = model.notice {
                VStack {
                    Spacer()
                    NoticeBanner(text: notice)
                        .padding(.bottom, model.voiceBarVisible ? 140 : 72)
                        .onTapGesture { model.notice = nil }
                }
                .transition(.opacity)
                .zIndex(3)
                .task(id: notice) {
                    try? await Task.sleep(nanoseconds: 4_000_000_000)
                    if model.notice == notice { model.notice = nil }
                }
            }

            if model.callScreenVisible {
                model.graine.callScreen()
                    .ignoresSafeArea()
                    .transition(.move(edge: .bottom))
                    .zIndex(4)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: model.chatAgent)
        .animation(.easeInOut(duration: 0.25), value: model.callScreenVisible)
        .animation(.easeInOut(duration: 0.2), value: model.notice)
        .onAppear {
            model.graine.attachVoiceEngine()
            OfflineDemo.run(model)
        }
    }
}

struct MainTabs: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        TabView(selection: $model.tab) {
            NavigationStack(path: $model.homePath) {
                HomeView()
                    .navigationDestination(for: HomeRoute.self) { _ in ProfileView() }
            }
            .tabItem { Label("Home", systemImage: "house") }
            .tag(DemoTab.home)
            NavigationStack { ItemsView() }
                .tabItem { Label(model.pack?.items.title ?? "Items", systemImage: "list.bullet") }
                .tag(DemoTab.items)
            NavigationStack(path: $model.callsPath) {
                ScheduledCallsView()
                    .navigationDestination(for: CallsRoute.self) { _ in CallLogView() }
            }
            .tabItem { Label("Calls", systemImage: "calendar.badge.clock") }
            .tag(DemoTab.calls)
            NavigationStack { HelpView() }
                .tabItem { Label("Help", systemImage: "questionmark.bubble") }
                .tag(DemoTab.help)
            NavigationStack { MemoryView() }
                .tabItem { Label("Memory", systemImage: "brain.head.profile") }
                .tag(DemoTab.memory)
        }
    }
}

/// Shown only when the build carries no use-case pack (the `usecases` folder was not bundled).
struct NoPacksView: View {
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "shippingbox").font(.largeTitle).foregroundColor(.secondary)
            Text("No use-case packs in this build").font(.headline)
            Text("The app bundles demo/usecases/*.json. Check that the folder exists and build again.")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(32)
    }
}
