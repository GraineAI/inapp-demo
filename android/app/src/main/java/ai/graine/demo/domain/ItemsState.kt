package ai.graine.demo.domain

/** Which row the agent (or a button) named. */
sealed class RowMatch {
    data class Found(val row: UseCasePack.Row) : RowMatch()
    data class Ambiguous(val rows: List<UseCasePack.Row>) : RowMatch()
    object NoMatch : RowMatch()
}

/**
 * THE ITEMS SCREEN'S STATE for the signed-in user: their rows and each row's
 * status chip. Statuses live for the app session (the pack's rule): they
 * reset when the app restarts, and each user has their own.
 */
data class ItemsState(
    val rows: List<UseCasePack.Row>,
    /** Statuses set by set_status actions, by row id. A row without one shows its starting status. */
    val statuses: Map<String, String> = emptyMap(),
) {
    /** The row's status chip now: the one set, else the pack's starting one. */
    fun statusOf(row: UseCasePack.Row): String? = statuses[row.id] ?: row.status

    /** What the agent reads as the row's value: its status, else its subtitle. */
    fun valueOf(row: UseCasePack.Row): String = statusOf(row) ?: row.subtitle

    /**
     * The pack's rule for finding the row a name refers to (case-insensitive,
     * trimmed): a row whose id equals it; else whose title equals it; else the
     * ONE row whose title or any field value contains it ("7730",
     * "GD-55120"). Two or more is ambiguous.
     */
    fun find(query: String?): RowMatch {
        val q = norm(query ?: "")
        if (q.isEmpty()) return RowMatch.NoMatch
        rows.firstOrNull { norm(it.id) == q }?.let { return RowMatch.Found(it) }
        rows.firstOrNull { norm(it.title) == q }?.let { return RowMatch.Found(it) }
        val containing = rows.filter { row -> norm(row.title).contains(q) || row.fields.any { norm(it.value).contains(q) } }
        return when (containing.size) {
            0 -> RowMatch.NoMatch
            1 -> RowMatch.Found(containing[0])
            else -> RowMatch.Ambiguous(containing)
        }
    }

    /** The set_status buttons a row shows: its own actions, hidden while the row already has that status. */
    fun buttons(row: UseCasePack.Row, pack: UseCasePack): List<UseCasePack.Action> =
        row.actions.mapNotNull { pack.action(it) }.filter {
            it.knownEffect == UseCasePack.Effect.SET_STATUS && it.status != null && it.status != statusOf(row)
        }

    /** The same rows with one new status. */
    fun withStatus(status: String, row: UseCasePack.Row): ItemsState = copy(statuses = statuses + (row.id to status))

    private fun norm(s: String) = s.trim().lowercase()
}
