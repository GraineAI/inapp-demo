import GraineInApp
import SwiftUI

/// Home: the memory chip, the three ways to reach the assistant, the next scheduled call and the pack's cards.
struct HomeView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if let pack = model.pack, let user = model.user {
                    header(pack, user)
                    if let notice = pack.notice { NoticeCard(text: notice) }
                    agentCard(pack)
                    nextCallCard(pack)
                    ForEach(pack.home.cards) { card in
                        Button { model.open(target: card.target) } label: {
                            Card(card.title) {
                                HStack {
                                    Text(card.body).font(.subheadline).foregroundColor(.secondary).multilineTextAlignment(.leading)
                                    Spacer()
                                    Image(systemName: "chevron.right").font(.footnote).foregroundColor(.secondary)
                                }
                            }
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("card_\(card.id)")
                    }
                }
            }
            .padding(16)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle(model.pack?.home.title ?? "Home")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button { model.open(.profile) } label: {
                    Image(systemName: "person.crop.circle")
                }
                .accessibilityLabel("Profile")
                .accessibilityIdentifier("profile")
            }
        }
        .refreshable { await model.refreshCalls() }
    }

    private func header(_ pack: UseCasePack, _ user: UseCasePack.User) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Hello, \(user.firstName)").font(.title2.weight(.semibold))
            HStack(spacing: 8) {
                Text(pack.appTitle).font(.subheadline).foregroundColor(.secondary)
                let memory = model.memoryStatus
                Button { model.open(.memory) } label: {
                    StatusChip(text: memory.label, tone: memory.on ? .positive : .neutral)
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("memory_chip")
            }
        }
    }

    private func agentCard(_ pack: UseCasePack) -> some View {
        Card("\(model.name(of: .main)), your \(model.profile(.main)?.role ?? pack.agentRole)") {
            HStack(spacing: 12) {
                ActionTile(title: "Talk", systemImage: "waveform") { model.talk() }
                ActionTile(title: "Chat", systemImage: "bubble.left.and.bubble.right") { model.open(.chat) }
                ActionTile(title: "Call", systemImage: "phone") { model.callAgent() }
            }
        }
    }

    private func nextCallCard(_ pack: UseCasePack) -> some View {
        Card("Next scheduled call") {
            if let next = model.nextCall {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(DemoFormat.time(next.call.at, now: Date())).font(.body.weight(.semibold))
                        Text("With \(model.name(of: next.agent))").font(.footnote).foregroundColor(.secondary)
                    }
                    Spacer()
                    StatusChip(text: CallText.label(next.call.status), tone: CallText.tone(next.call.status))
                }
            } else {
                Text(model.callsLoading ? "Loading…" : "No call booked.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
            HStack(spacing: 16) {
                Button(pack.bookLabel(.main)) { Task { await model.book(.main) } }
                    .buttonStyle(.borderedProminent)
                    .accessibilityIdentifier("home_book")
                Button(pack.call.title) { model.open(.calls) }
            }
            .font(.subheadline.weight(.semibold))
        }
    }
}
