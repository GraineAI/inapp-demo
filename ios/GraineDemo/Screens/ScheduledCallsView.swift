import GraineInApp
import SwiftUI

/// Scheduled calls: every agent's calls, each with the agent's name, its status chip and Cancel; and booking one.
struct ScheduledCallsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var booking = false
    @State private var cancelling: AgentCall?

    var body: some View {
        let canBook = model.graine.agents.contains { model.rules(for: $0).usersCanBook }
        List {
            Section {
                Button {
                    booking = true
                } label: {
                    Label(model.pack?.call.bookLabel ?? "Schedule a call", systemImage: "plus.circle.fill")
                }
                .disabled(!canBook)
                .accessibilityIdentifier("schedule_call")
            } footer: {
                if !canBook {
                    Text(CallText.message(code: .schedulingOff, rules: model.rules, fallback: nil))
                } else {
                    Text("The assistant calls you at the time you choose. It rings like a phone call, even when the app is closed.")
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
        .sheet(isPresented: $booking) {
            ScheduleCallSheet(agent: .main)
        }
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

/**
 * The booking sheet: which agent calls (when two are configured), one of the
 * pack's reasons, its quick-time chips, and the system's own date and time
 * pickers in 5-minute steps, bounded by that agent's earliest and latest from
 * the SDK, in the device's time zone. The SDK checks the time again before it
 * books; whatever it or the server refuses comes back in plain words.
 */
struct ScheduleCallSheet: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.demoAccent) private var accent
    @State private var agent: DemoAgent
    @State private var when = Date()
    @State private var reason = ""
    @State private var error: String?
    @State private var sending = false

    init(agent: DemoAgent) {
        _agent = State(initialValue: agent)
        // SwiftUI's DatePicker has no step; the wheel it draws is a UIDatePicker, which has.
        UIDatePicker.appearance().minuteInterval = Booking.stepMinutes
    }

    var body: some View {
        let pack = model.pack
        let reasons = pack?.reasons(agent) ?? []
        let now = Date()
        let rules = model.rules(for: agent)
        let range = model.bookingRange(for: agent)
        NavigationStack {
            Form {
                if model.graine.agents.count > 1 {
                    Section("Who should call you") {
                        Picker("Agent", selection: $agent) {
                            ForEach(model.graine.agents) { Text(model.name(of: $0)).tag($0) }
                        }
                        .pickerStyle(.segmented)
                        .accessibilityIdentifier("booking_agent")
                    }
                }

                if !reasons.isEmpty {
                    Section("Reason") {
                        Picker("Reason", selection: $reason) {
                            ForEach(reasons, id: \.self) { Text($0).tag($0) }
                        }
                        .pickerStyle(.inline)
                        .labelsHidden()
                        .accessibilityIdentifier("reason")
                    }
                }

                if let quick = pack?.call.quickTimes, !quick.isEmpty {
                    Section("Quick times") {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(quick) { q in
                                    let time = Booking.resolve(q, now: now)
                                    let usable = time.map { Booking.isBookable($0, now: now, rules: rules) } ?? false
                                    Button(q.label) { if let t = time { when = t } }
                                        .font(.subheadline.weight(.medium))
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 7)
                                        .background((time == when ? accent.opacity(0.25) : accent.opacity(0.1)), in: Capsule())
                                        .foregroundColor(usable ? accent : .secondary)
                                        .disabled(!usable)
                                        .accessibilityIdentifier("quick_\(q.label)")
                                }
                            }
                            .padding(.vertical, 2)
                        }
                    }
                }

                Section {
                    DatePicker("Date", selection: $when, in: range, displayedComponents: .date)
                        .datePickerStyle(.graphical)
                    DatePicker("Time", selection: $when, in: range, displayedComponents: .hourAndMinute)
                        .datePickerStyle(.wheel)
                        .labelsHidden()
                        .frame(maxWidth: .infinity)
                    HStack {
                        Image(systemName: "globe")
                        Text(Booking.zoneLabel(when)).accessibilityIdentifier("time_zone")
                    }
                    .font(.footnote)
                    .foregroundColor(.secondary)
                } header: {
                    Text("Date and time")
                } footer: {
                    Text("Between \(rules.minLeadMinutes) minutes and \(rules.maxDaysAhead) days from now, within the assistant's calling hours.")
                }

                if let error = error {
                    Section {
                        Label(error, systemImage: "exclamationmark.triangle")
                            .foregroundColor(DemoTheme.warning)
                            .accessibilityIdentifier("booking_error")
                    }
                }
            }
            .navigationTitle(pack?.bookLabel(agent) ?? "Schedule a call")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(sending ? "Booking…" : "Book") {
                        sending = true
                        error = nil
                        let chosen = reason.isEmpty ? reasons.first : reason
                        let at = Booking.snapped(when)
                        let who = agent
                        Task {
                            let problem = await model.scheduleCall(at: at, reason: chosen, agent: who)
                            sending = false
                            if let problem = problem { error = problem } else { dismiss() }
                        }
                    }
                    .disabled(sending || !rules.usersCanBook)
                    .accessibilityIdentifier("book")
                }
            }
            .onAppear {
                when = min(max(model.suggestedCallTime(for: agent), range.lowerBound), range.upperBound)
                if reason.isEmpty { reason = reasons.first ?? "" }
            }
            .onChange(of: agent) { next in
                let bounds = model.bookingRange(for: next)
                when = min(max(when, bounds.lowerBound), bounds.upperBound)
                // Each agent books with its own reasons.
                let options = model.pack?.reasons(next) ?? []
                if !options.contains(reason) { reason = options.first ?? "" }
            }
        }
    }
}
