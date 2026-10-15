import GraineInApp
import SwiftUI

/// Scheduled calls: every agent's calls, each with the agent's name, its status chip and Cancel; and booking one with the SDK's sheet.
struct ScheduledCallsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var cancelling: AgentCall?

    var body: some View {
        let canBook = model.canBook(.main)
        List {
            Section {
                Button {
                    Task { await model.book(.main) }
                } label: {
                    Label(model.pack?.bookLabel(.main) ?? "Book a call", systemImage: "plus.circle.fill")
                }
                .disabled(!canBook)
                .accessibilityIdentifier("schedule_call")
            } footer: {
                if canBook {
                    Text("The assistant calls you at the time you choose. It rings like a phone call, even when the app is closed.")
                } else {
                    Text(CallText.bookingUnavailable).accessibilityIdentifier("booking_unavailable")
                }
            }

            if let error = model.callsError {
                Section {
                    Label(error, systemImage: "exclamationmark.triangle")
                        .font(.footnote)
                        .foregroundColor(DemoTheme.warning)
                }
            }

            Section("Upcoming") {
                if model.upcomingCalls.isEmpty {
                    Text(model.callsLoading ? "Loading…" : "No upcoming calls.")
                        .foregroundColor(.secondary)
                }
                ForEach(model.upcomingCalls) { item in
                    row(item)
                        .swipeActions {
                            if item.call.canCancel {
                                Button("Cancel", role: .destructive) { cancelling = item }
                            }
                        }
                }
            }

            if !model.pastCalls.isEmpty {
                Section("Earlier") {
                    ForEach(model.pastCalls) { row($0) }
                }
            }

            Section {
                NavigationLink(value: CallsRoute.callLog) {
                    Label("Call activity", systemImage: "list.bullet.rectangle")
                }
            }
        }
        .navigationTitle(model.pack?.call.title ?? "Scheduled calls")
        .refreshable { await model.refreshCalls() }
        .task { await model.refreshCalls() }
        .confirmationDialog(
            "Cancel this call?",
            isPresented: Binding(get: { cancelling != nil }, set: { if !$0 { cancelling = nil } }),
            titleVisibility: .visible,
            presenting: cancelling
        ) { item in
            Button("Cancel call", role: .destructive) {
                Task { await model.cancelCall(item) }
            }
            Button("Keep it", role: .cancel) {}
        } message: { item in
            Text("\(model.name(of: item.agent)) · \(DemoFormat.time(item.call.at, now: Date()))")
        }
    }

    private func row(_ item: AgentCall) -> some View {
        let call = item.call
        return HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 3) {
                Text(DemoFormat.time(call.at, now: Date())).font(.body.weight(.semibold))
                Text(model.name(of: item.agent)).font(.footnote.weight(.medium)).accessibilityIdentifier("call_agent_\(call.id)")
                if let reason = call.reason, !reason.isEmpty {
                    Text(reason).font(.footnote).foregroundColor(.secondary).lineLimit(2)
                }
                if let by = CallText.bookedBy(call.source, agentName: model.name(of: item.agent)) {
                    Text(by).font(.footnote).foregroundColor(.secondary)
                }
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 6) {
                StatusChip(text: CallText.label(call.status), tone: CallText.tone(call.status))
                if call.canCancel {
                    Button("Cancel") { cancelling = item }
                        .font(.footnote)
                        .buttonStyle(.borderless)
                        .accessibilityIdentifier("cancel_\(call.id)")
                }
            }
        }
        .padding(.vertical, 2)
    }
}
