/// The pack's actions, run generically: every platform implements the same three effects with the
/// same answers (usecases/README.md, "Actions"). Pure, so the rules are tested once and the screens only
/// apply the effect.
library;

import '../usecases.dart';

class ActionAnswer {
  const ActionAnswer(this.status, this.message, [this.data = const <String, Object?>{}]);

  /// `ok` or `refused`.
  final String status;
  final String message;
  final Map<String, Object?> data;
}

sealed class ActionEffect {
  const ActionEffect();
}

class NavigateEffect extends ActionEffect {
  const NavigateEffect(this.screen);
  final ScreenKey screen;
}

class OpenItemEffect extends ActionEffect {
  const OpenItemEffect(this.rowId);
  final String rowId;
}

class SetStatusEffect extends ActionEffect {
  const SetStatusEffect(this.rowId, this.status);
  final String rowId;
  final String status;
}

class ActionOutcome {
  const ActionOutcome(this.answer, [this.effect]);
  final ActionAnswer answer;
  final ActionEffect? effect;
}

/// The row named — `row` — or the candidates when several match, or neither when none does.
class RowMatch {
  const RowMatch({this.row, this.ambiguous = const <PackRow>[]});
  final PackRow? row;
  final List<PackRow> ambiguous;
}

String _norm(Object? v) => v is String || v is num ? '$v'.trim().toLowerCase() : '';

/// The row a person or the agent named: by id, else by title, else the ONE row whose title or a field contains it.
RowMatch findRow(List<PackRow> rows, Object? query) {
  final q = _norm(query);
  if (q.isEmpty) return const RowMatch();
  for (final r in rows) {
    if (r.id.toLowerCase() == q) return RowMatch(row: r);
  }
  for (final r in rows) {
    if (r.title.toLowerCase() == q) return RowMatch(row: r);
  }
  final containing = rows
      .where((r) => r.title.toLowerCase().contains(q) || r.fields.any((f) => f.value.toLowerCase().contains(q)))
      .toList();
  if (containing.length == 1) return RowMatch(row: containing.first);
  return RowMatch(ambiguous: containing);
}

String? currentStatus(PackRow row, Map<String, String> statuses) => statuses[row.id] ?? row.status;

/// A screen named by the agent (`screen`) or by a component's tap (`path`, e.g. "orders?id=1" or "/calls").
String _screenArg(Map<String, Object?> args) {
  final screen = args['screen'];
  final raw = screen is String && screen.trim().isNotEmpty ? screen : (args['path'] is String ? args['path']! as String : '');
  return raw.split('?').first.split('/').where((s) => s.isNotEmpty).firstOrNull ?? '';
}

/// Run one of the pack's actions against the signed-in person's rows. [statuses] are the statuses set so
/// far this session (row id → status). Never throws: a wrong argument is a refusal the agent can repeat.
ActionOutcome runPackAction(UseCasePack pack, PackAction action, Map<String, Object?> args, List<PackRow> rows, Map<String, String> statuses) {
  if (action.effect == 'open_screen') {
    final asked = _screenArg(args);
    final key = pack.screenKeyFor(asked);
    if (key == null) {
      final names = ScreenKey.values.map(pack.screenName).join(', ');
      return ActionOutcome(ActionAnswer('refused', 'There is no screen called "$asked". Screens: $names.'));
    }
    return ActionOutcome(
      ActionAnswer('ok', 'Opened ${pack.screenTitle(key)}.', <String, Object?>{'screen': pack.screenName(key)}),
      NavigateEffect(key),
    );
  }

  final titles = rows.map((r) => r.title).join(', ');
  final match = findRow(rows, args['item']);
  if (match.row == null && match.ambiguous.isNotEmpty) {
    return ActionOutcome(ActionAnswer('refused', 'Which one? ${match.ambiguous.map((r) => r.title).join(', ')}.'));
  }
  final row = match.row;
  if (row == null) {
    final asked = _norm(args['item']).isEmpty ? '' : '${args['item']}'.trim();
    final list = titles.isEmpty ? 'none' : titles;
    return ActionOutcome(ActionAnswer(
      'refused',
      asked.isEmpty ? 'Say which ${pack.itemNoun}. ${pack.itemsTitle}: $list.' : 'No ${pack.itemNoun} called "$asked". ${pack.itemsTitle}: $list.',
    ));
  }
  final status = currentStatus(row, statuses);

  if (action.effect == 'open_item') {
    return ActionOutcome(
      ActionAnswer('ok', 'Showing ${row.title}.', <String, Object?>{'item': row.id, if (status != null) 'status': status}),
      OpenItemEffect(row.id),
    );
  }

  // set_status
  final target = action.status ?? '';
  if (!row.actions.contains(action.name) || target.isEmpty) {
    return ActionOutcome(ActionAnswer('refused', '${action.label} is not available for ${row.title}.'));
  }
  if (status == target) {
    return ActionOutcome(ActionAnswer('ok', '${row.title} is already $target.', <String, Object?>{'item': row.id, 'status': target}));
  }
  return ActionOutcome(
    ActionAnswer('ok', '${row.title}: $target.', <String, Object?>{'item': row.id, 'status': target}),
    SetStatusEffect(row.id, target),
  );
}

/// The buttons a row shows: its set_status actions, minus the one whose status it already has.
List<PackAction> rowButtons(UseCasePack pack, PackRow row, Map<String, String> statuses) {
  final status = currentStatus(row, statuses);
  return [
    for (final name in row.actions)
      if (pack.action(name) case final a? when a.effect == 'set_status' && a.status != status) a,
  ];
}
