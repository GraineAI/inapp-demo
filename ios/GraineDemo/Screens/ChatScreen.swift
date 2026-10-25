import SwiftUI

/**
 * Chat: the SDK's chat window, full screen, under the pack's notice,
 * **Past sessions** and its starter chips. The chat window draws the brand's
 * design and the agent's components itself; the app only decides when it is
 * open. Past sessions — the user's earlier sessions with this agent — is the
 * app's own screen (`PastSessionsView`).
 */
struct ChatScreen: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.demoAccent) private var accent
    /// Whose chat: the main agent's (Chat) or the help desk's (Help).
    let agent: DemoAgent

    var body: some View {
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .center, spacing: 8) {
                    if agent == .main, let notice = model.pack?.notice {
                        Text(notice).font(.caption).foregroundColor(.secondary)
                    }
                    Spacer(minLength: 0)
                    Button("Past sessions") { model.openPastSessions(agent) }
                        .font(.footnote.weight(.semibold))
                        .accessibilityIdentifier("past_sessions_open")
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(model.profile(agent)?.starters ?? [], id: \.self) { starter in
                            Button(starter) { model.startChat(with: starter, agent: agent) }
                                .font(.footnote.weight(.medium))
                                .padding(.horizontal, 12)
                                .padding(.vertical, 7)
                                .background(accent.opacity(0.1), in: Capsule())
                                .accessibilityIdentifier("starter_\(starter)")
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            model.graine.chatView(agent: agent) { model.closeChat() }
        }
        .background(Color(.systemBackground).ignoresSafeArea())
    }
}
