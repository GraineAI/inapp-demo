import SwiftUI

/**
 * Help: a second agent in the same app — the pack's help desk, with its own
 * publishable key. Its chat, voice and calls go to that agent; the person,
 * their memory choice and the screen are the same as on Home.
 */
struct HelpView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        let profile = model.profile(.help)
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if model.isAvailable(.help) {
                    Card(profile?.name ?? "Help") {
                        if let role = profile?.role, !role.isEmpty {
                            Text(role).font(.subheadline).foregroundColor(.secondary)
                        }
                        HStack(spacing: 12) {
                            ActionTile(title: "Talk", systemImage: "waveform") { model.talk(.help) }
                            ActionTile(title: "Chat", systemImage: "bubble.left.and.bubble.right") { model.openChat(.help) }
                            ActionTile(title: "Call", systemImage: "phone") { model.callAgent(.help) }
                        }
                    }
                    if let starters = profile?.starters, !starters.isEmpty {
                        Card("Ask \(profile?.name ?? "the help desk")") {
                            ForEach(starters, id: \.self) { starter in
                                Button { model.startChat(with: starter, agent: .help) } label: {
                                    HStack {
                                        Text(starter).font(.subheadline).multilineTextAlignment(.leading)
                                        Spacer()
                                        Image(systemName: "arrow.up.right").font(.footnote)
                                    }
                                    .padding(.vertical, 4)
                                }
                                .accessibilityIdentifier("help_starter_\(starter)")
                            }
                        }
                    }
                    Card("Book a call") {
                        Text("\(profile?.name ?? "The help desk") can call you at a time you choose. Its calls are listed with \(model.agentName)'s on the Calls tab.")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                        // Booked with the help desk's own agent, in the SDK's sheet.
                        Button(model.pack?.bookLabel(.help) ?? "Book a call") { Task { await model.book(.help) } }
                            .font(.subheadline.weight(.semibold))
                            .disabled(!model.canBook(.help))
                            .accessibilityIdentifier("help_book")
                        if !model.canBook(.help) {
                            Text(CallText.bookingUnavailable)
                                .font(.footnote)
                                .foregroundColor(.secondary)
                                .accessibilityIdentifier("help_booking_unavailable")
                        }
                    }
                    Text("Signed in once: \(profile?.name ?? "the help desk") knows who you are, and \(model.agentName) does too.")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                } else {
                    Card(profile?.name ?? "Help") {
                        if let role = profile?.role, !role.isEmpty {
                            Text(role).font(.subheadline).foregroundColor(.secondary)
                        }
                        Text("This screen talks to a second agent, with its own publishable key, in the same app. To try it, add the help agent's key as GRAINE_HELP_PUBLISHABLE_KEY in Config.xcconfig and build again.")
                            .font(.subheadline)
                            .accessibilityIdentifier("help_not_configured")
                    }
                }
            }
            .padding(16)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle(profile?.name ?? "Help")
    }
}
