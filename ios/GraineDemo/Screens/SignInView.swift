import SwiftUI

/// Sign in (demo): pick the use case and a fictional user; consent to memory.
struct SignInView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.demoAccent) private var accent
    @State private var selected: UseCasePack.User?
    @State private var remember = true

    var body: some View {
        NavigationStack {
            List {
                if model.packs.count > 1 {
                    Section {
                        Picker("Use case", selection: Binding(
                            get: { model.pack?.id ?? "" },
                            set: { id in
                                selected = nil
                                model.choosePack(id)
                            }
                        )) {
                            ForEach(model.packs) { pack in
                                Text("\(pack.appTitle) — \(pack.agentRole)").tag(pack.id)
                            }
                        }
                        .accessibilityIdentifier("use_case")
                    } footer: {
                        if let tagline = model.pack?.tagline, !tagline.isEmpty { Text(tagline) }
                    }
                }

                if let pack = model.pack {
                    Section {
                        ForEach(pack.users) { user in
                            Button {
                                selected = user
                            } label: {
                                HStack(spacing: 12) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(user.name).font(.body.weight(.semibold)).foregroundColor(.primary)
                                        Text(subtitle(user)).font(.subheadline).foregroundColor(.secondary)
                                    }
                                    Spacer()
                                    Image(systemName: selected == user ? "checkmark.circle.fill" : "circle")
                                        .foregroundColor(selected == user ? accent : .secondary)
                                        .imageScale(.large)
                                }
                                .contentShape(Rectangle())
                            }
                            .accessibilityIdentifier("user_\(user.id)")
                        }
                    } header: {
                        Text("Choose a \(pack.userNoun)")
                    } footer: {
                        Text("Demo sign-in. Every person and record in this app is fictional.")
                    }

                    Section {
                        Toggle("Let \(pack.agentName) remember our conversations", isOn: $remember)
                            .accessibilityIdentifier("memory_consent")
                    } footer: {
                        Text("\(pack.agentName) uses what you said before to pick up where you left off. You can turn this off, or erase what is remembered, at any time.")
                    }
                }

                Section {
                    Button {
                        if let user = selected { model.signIn(user, memory: remember) }
                    } label: {
                        Text("Continue").frame(maxWidth: .infinity).font(.body.weight(.semibold))
                    }
                    .disabled(selected == nil)
                    .accessibilityIdentifier("continue")
                }

                if !model.config.hasKey {
                    Section {
                        Label("No publishable key is set. Copy Config.example.xcconfig to Config.xcconfig, add the key, and build again.", systemImage: "exclamationmark.triangle")
                            .font(.footnote)
                            .foregroundColor(DemoTheme.warning)
                    }
                }
            }
            .navigationTitle("Graine Demo")
        }
        .onAppear { model.describeScreen() }
    }

    /// "MRN-000981 · Diabetes care": the identifier and the first extra trait.
    private func subtitle(_ user: UseCasePack.User) -> String {
        let extra = user.traits.keys.sorted().first.flatMap { user.traits[$0] }
        return [user.id, extra].compactMap { $0 }.joined(separator: " · ")
    }
}
