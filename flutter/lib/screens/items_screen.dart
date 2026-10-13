import 'package:flutter/material.dart';

import '../graine_service.dart';
import '../logic/actions.dart';
import '../state.dart';
import '../ui.dart';

/// The pack's list screen (medicines, cards, orders…): the signed-in person's rows, their status chips and the
/// actions each row offers. The agent sees the same list as screen context and runs the same actions; a button
/// here goes through the same rules.
class ItemsScreen extends StatelessWidget {
  const ItemsScreen({super.key, required this.active});

  final bool active;

  @override
  Widget build(BuildContext context) {
    final state = DemoScope.of(context);
    final pack = state.pack;
    final rows = state.rows;
    final statuses = state.statuses;
    final theme = Theme.of(context);

    return ScreenReport(
      active: active,
      screen: GraineScreen(pack.itemsScreenName, title: pack.itemsTitle, fields: [
        for (final r in rows) GraineField(r.id, label: r.title, value: currentStatus(r, statuses) ?? r.subtitle, status: FieldStatus.filled),
      ]),
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 140),
        children: [
          if (rows.isEmpty) Text(pack.emptyText),
          for (final row in rows)
            Card(
              key: Key('row-${row.id}'),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
                side: BorderSide(color: state.openRowId == row.id ? theme.colorScheme.primary : Colors.transparent, width: 2),
              ),
              child: InkWell(
                borderRadius: BorderRadius.circular(12),
                onTap: () => state.setOpenRow(state.openRowId == row.id ? null : row.id),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(children: [
                        Expanded(child: Text(row.title, style: theme.textTheme.titleMedium)),
                        if (currentStatus(row, statuses) case final s?) ToneChip(s),
                      ]),
                      if (row.subtitle.isNotEmpty) Text(row.subtitle, style: theme.textTheme.bodySmall),
                      if (state.openRowId == row.id) ...[
                        const SizedBox(height: 10),
                        for (final f in row.fields)
                          Padding(
                            padding: const EdgeInsets.symmetric(vertical: 2),
                            child: Row(children: [
                              SizedBox(width: 110, child: Text(f.label, style: theme.textTheme.bodySmall)),
                              Expanded(child: Text(f.value)),
                            ]),
                          ),
                        const SizedBox(height: 6),
                        Wrap(spacing: 8, children: [
                          for (final action in rowButtons(pack, row, statuses))
                            OutlinedButton(
                              key: Key('${row.id}-${action.name}'),
                              onPressed: () {
                                final outcome = runPackAction(pack, action, <String, Object?>{'item': row.id}, rows, statuses);
                                if (outcome.effect case SetStatusEffect(:final rowId, :final status)) state.setRowStatus(rowId, status);
                              },
                              child: Text(action.label),
                            ),
                        ]),
                      ],
                    ],
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
