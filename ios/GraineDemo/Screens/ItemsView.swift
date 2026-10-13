import SwiftUI

/**
 * Items: the pack's list ("Medicines", "Cards", "Orders") for the signed-in
 * user. A row opens in place to show its fields and its buttons; the agent
 * works on the same rows (`open_item`, `set_status`) and reads them as the
 * screen's fields.
 */
struct ItemsView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        List {
            if let pack = model.pack, let state = model.items {
                if state.rows.isEmpty {
                    Text(pack.items.emptyText).foregroundColor(.secondary)
                }
                ForEach(state.rows) { row in
                    rowView(row, state: state, pack: pack)
                }
            }
        }
        .navigationTitle(model.pack?.items.title ?? "Items")
        .animation(.easeInOut(duration: 0.2), value: model.expandedRowId)
    }

    private func rowView(_ row: UseCasePack.Row, state: ItemsState, pack: UseCasePack) -> some View {
        let expanded = model.expandedRowId == row.id
        return VStack(alignment: .leading, spacing: 10) {
            Button { model.toggleExpanded(row) } label: {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(row.title).font(.body.weight(.semibold)).foregroundColor(.primary)
                        if !row.subtitle.isEmpty {
                            Text(row.subtitle).font(.subheadline).foregroundColor(.secondary)
                        }
                    }
                    Spacer()
                    if let status = state.status(of: row) {
                        StatusChip(text: status, tone: status == row.status ? .neutral : .positive)
                    }
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("item_\(row.id)")

            if expanded {
                ForEach(row.fields, id: \.self) { field in
                    HStack(alignment: .firstTextBaseline) {
                        Text(field.label).font(.subheadline)
                        Spacer(minLength: 16)
                        Text(field.value).font(.subheadline).foregroundColor(.secondary).multilineTextAlignment(.trailing)
                    }
                }
                let buttons = state.buttons(for: row, pack: pack)
                if !buttons.isEmpty {
                    HStack {
                        ForEach(buttons) { action in
                            Button(action.label) { model.press(action, on: row) }
                                .buttonStyle(.borderedProminent)
                                .accessibilityIdentifier("action_\(action.name)_\(row.id)")
                        }
                    }
                }
                Button("Ask \(pack.agentName) about this") {
                    model.startChat(with: "I have a question about \(row.title).")
                }
                .font(.subheadline)
                .buttonStyle(.borderless)
            }
        }
        .padding(.vertical, 4)
    }
}
