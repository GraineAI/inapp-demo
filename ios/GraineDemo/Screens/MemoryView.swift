import GraineInApp
import SwiftUI

/**
 * Memory: what the assistant remembers about the signed-in user, the consent
 * switch, and erasing it all. Another user sees another list — memories
 * never cross people.
 */
struct MemoryView: View {
    @EnvironmentObject private var model: AppModel
    @State private var confirmErase = false
    @State private var erasing = false

    var body: some View {
        let agent = model.agentName
        List {
            Section {
                Toggle("Let \(agent) remember our conversations", isOn: Binding(
                    get: { model.memoryOn },
                    set: { model.setMemory($0) }
                ))
                .accessibilityIdentifier("memory_switch")
            } footer: {
                if model.agentMemoryEnabled == false {
                    Text("Memory is turned off for this assistant, so nothing is remembered whatever you choose here.")
                } else {
                    Text("Applies from your next conversation. What \(agent) remembers about you is never used for anyone else.")
                }
            }

            Section {
                if let error = model.memoriesError {
                    Label(error, systemImage: "exclamationmark.triangle")
                        .font(.footnote)
                        .foregroundColor(DemoTheme.warning)
                } else if model.memories.isEmpty {
                    Text(model.memoriesLoading ? "Loading…" : emptyText(agent))
                        .foregroundColor(.secondary)
                        .accessibilityIdentifier("memory_empty")
                }
                ForEach(model.memories) { memory in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(memory.text)
                        if let when = memory.when {
                            Text(DemoFormat.time(when, now: Date()))
                                .font(.footnote)
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(.vertical, 2)
                }
            } header: {
                Text("What \(agent) remembers about you")
            }

            Section {
                Button(role: .destructive) {
                    confirmErase = true
                } label: {
                    Text(erasing ? "Erasing…" : "Erase everything \(agent) remembers")
                }
                .disabled(erasing)
                .accessibilityIdentifier("erase_memory")
            } footer: {
                Text("This cannot be undone. Your \(model.pack?.items.title.lowercased() ?? "list") and scheduled calls are not affected.")
            }
        }
        .navigationTitle("Memory")
        .refreshable { await model.refreshMemories() }
        .task(id: model.user?.id) { await model.refreshMemories() }
        .confirmationDialog("Erase everything \(agent) remembers about you?", isPresented: $confirmErase, titleVisibility: .visible) {
            Button("Erase", role: .destructive) {
                erasing = true
                Task {
                    await model.forgetMemory()
                    erasing = false
                }
            }
            Button("Keep", role: .cancel) {}
        } message: {
            Text("\(agent) will no longer recall anything from your earlier conversations.")
        }
    }

    /// The empty state, with one of the pack's examples of what to say.
    private func emptyText(_ agent: String) -> String {
        if let example = model.pack?.memoryExamples.first {
            return "Nothing yet. Tell \(agent) something like “\(example.tell)” and it will be remembered next time."
        }
        return "Nothing yet. Tell \(agent) about yourself and it will be remembered next time."
    }
}
