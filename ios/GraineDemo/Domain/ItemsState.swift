import Foundation

/// Which row the agent (or a button) named.
enum RowMatch: Equatable {
    case found(UseCasePack.Row)
    case ambiguous([UseCasePack.Row])
    case noMatch
}

/**
 * THE ITEMS SCREEN'S STATE for the signed-in user: their rows, each row's
 * status chip, and the row shown expanded. Statuses live for the app session
 * (the pack's rule): they reset when the app restarts, and each user has their own.
 */
struct ItemsState: Equatable {
    let rows: [UseCasePack.Row]
    /// Statuses set by `set_status` actions, by row id. A row without one shows its starting status.
    private(set) var statuses: [String: String] = [:]

    init(rows: [UseCasePack.Row]) {
        self.rows = rows
    }

    /// The row's status chip now: the one set, else the pack's starting one.
    func status(of row: UseCasePack.Row) -> String? {
        statuses[row.id] ?? row.status
    }

    /// What the agent reads as the row's value: its status, else its subtitle.
    func value(of row: UseCasePack.Row) -> String {
        status(of: row) ?? row.subtitle
    }

    /**
     * The pack's rule for finding the row a name refers to (case-insensitive,
     * trimmed): a row whose id equals it; else whose title equals it; else the
     * ONE row whose title or any field value contains it ("7730",
     * "GD-55120"). Two or more is ambiguous.
     */
    func find(_ query: String?) -> RowMatch {
        let q = ItemsState.normalise(query ?? "")
        guard !q.isEmpty else { return .noMatch }
        if let byId = rows.first(where: { ItemsState.normalise($0.id) == q }) { return .found(byId) }
        if let byTitle = rows.first(where: { ItemsState.normalise($0.title) == q }) { return .found(byTitle) }
        let containing = rows.filter { row in
            ItemsState.normalise(row.title).contains(q) || row.fields.contains { ItemsState.normalise($0.value).contains(q) }
        }
        switch containing.count {
        case 0: return .noMatch
        case 1: return .found(containing[0])
        default: return .ambiguous(containing)
        }
    }

    /// The `set_status` buttons a row shows: its own actions, hidden while the row already has that status.
    func buttons(for row: UseCasePack.Row, pack: UseCasePack) -> [UseCasePack.Action] {
        row.actions.compactMap { pack.action(named: $0) }.filter { action in
            action.knownEffect == .setStatus && action.status != nil && action.status != status(of: row)
        }
    }

    /// Give a row a new status. Nothing else about it changes.
    mutating func setStatus(_ status: String, of row: UseCasePack.Row) {
        statuses[row.id] = status
    }

    private static func normalise(_ s: String) -> String {
        s.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }
}
