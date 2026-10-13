import SwiftUI

/// Profile: who is signed in, their memory choice, this device, and signing out.
struct ProfileView: View {
    @EnvironmentObject private var model: AppModel
    @State private var confirmErase = false

    var body: some View {
        let agent = model.agentName
        List {
            if let pack = model.pack, let user = model.user {
                Section("Signed in as") {
                    row("Name", user.name)
                    row(pack.identifierLabel, user.id)
                    ForEach(user.traits.keys.sorted(), id: \.self) { key in
                        row(label(key), user.traits[key] ?? "")
                    }
                    row("Use case", pack.appTitle)
                }
            }

            Section {
                Toggle("Let \(agent) remember our conversations", isOn: Binding(
                    get: { model.memoryOn },
                    set: { model.setMemory($0) }
                ))
                Button("What \(agent) remembers") { model.open(.memory) }
                Button("Erase everything \(agent) remembers", role: .destructive) { confirmErase = true }
            } header: {
                Text("Memory")
            }

            Section("Calls") {
                Button("Call activity") { model.open(.callLog) }
            }

            Section {
                row("Install ID", model.graine.installId, monospaced: true)
                row("Server", model.config.baseURL)
            } header: {
                Text("This device")
            } footer: {
                Text("The install ID identifies this installation of the app. It is made on this device and is not linked to your phone number.")
            }

            Section {
                Button("Sign out", role: .destructive) { model.signOut() }
                    .accessibilityIdentifier("sign_out")
            }
        }
        .navigationTitle("Profile")
        .confirmationDialog("Erase everything \(agent) remembers about you?", isPresented: $confirmErase, titleVisibility: .visible) {
            Button("Erase", role: .destructive) { Task { await model.forgetMemory() } }
            Button("Keep", role: .cancel) {}
        } message: {
            Text("\(agent) will no longer recall anything from your earlier conversations.")
        }
    }

    /// "patient_id" → "Patient id".
    private func label(_ key: String) -> String {
        let words = key.replacingOccurrences(of: "_", with: " ")
        return words.prefix(1).uppercased() + words.dropFirst()
    }

    private func row(_ label: String, _ value: String, monospaced: Bool = false) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label)
            Spacer(minLength: 16)
            Text(value)
                .font(monospaced ? .footnote.monospaced() : .body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.trailing)
                .textSelection(.enabled)
        }
    }
}
