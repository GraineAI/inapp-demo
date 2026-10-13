import SwiftUI

/// Call activity: every native call the SDK reported on this device, and what became of it.
struct CallLogView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        List {
            if model.callLog.entries.isEmpty {
                Text("No calls yet. Calls you place, and calls \(model.agentName) makes to you, appear here with their outcome.")
                    .foregroundColor(.secondary)
            }
            ForEach(model.callLog.entries) { entry in
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(entry.title(agentName: model.name(of: entry.agent.flatMap(DemoAgent.init(rawValue:)) ?? .main)))
                        Text("Call \(String(entry.callId.suffix(8)))").font(.caption.monospaced()).foregroundColor(.secondary)
                    }
                    Spacer()
                    Text(DemoFormat.time(entry.at, now: Date())).font(.footnote).foregroundColor(.secondary)
                }
                .padding(.vertical, 2)
            }
        }
        .navigationTitle("Call activity")
        .toolbar {
            if !model.callLog.entries.isEmpty {
                Button("Clear") { model.clearCallLog() }
            }
        }
    }
}
