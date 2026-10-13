import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:graine_demo/logic/actions.dart';
import 'package:graine_demo/usecases.dart';

UseCasePack load(String id) => UseCasePack.fromJson(jsonDecode(File('assets/usecases/$id.json').readAsStringSync()) as Map<String, dynamic>);

void main() {
  final packs = [for (final id in packIds) load(id)];

  group('the generic actions, on every pack', () {
    for (final p in packs) {
      final user = p.users.first;
      final rows = p.rowsFor(user.id);
      final openScreen = p.actions.firstWhere((a) => a.effect == 'open_screen');
      final openItem = p.actions.firstWhere((a) => a.effect == 'open_item');

      test('${p.id}: opens every screen by its reported name and its generic key', () {
        for (final name in ['home', 'chat', p.itemsScreenName, 'items', 'calls', 'memory', 'profile']) {
          final o = runPackAction(p, openScreen, {'screen': name.toUpperCase()}, rows, const {});
          expect(o.answer.status, 'ok');
          expect(o.effect, isA<NavigateEffect>());
        }
        final viaPath = runPackAction(p, openScreen, {'path': '/${p.itemsScreenName}?from=card'}, rows, const {});
        expect((viaPath.effect! as NavigateEffect).screen, ScreenKey.items);
        final refused = runPackAction(p, openScreen, {'screen': 'settings'}, rows, const {});
        expect(refused.answer.status, 'refused');
        expect(refused.answer.message, contains(p.itemsScreenName));
      });

      test("${p.id}: opens one of the person's own rows, and nobody else's", () {
        final o = runPackAction(p, openItem, {'item': rows.first.title}, rows, const {});
        expect(o.answer.message, 'Showing ${rows.first.title}.');
        expect((o.effect! as OpenItemEffect).rowId, rows.first.id);
        final someoneElses = p.rows.firstWhere((r) => r.userIds != null && !r.userIds!.contains(user.id));
        expect(runPackAction(p, openItem, {'item': someoneElses.id}, rows, const {}).answer.status, 'refused');
      });
    }
  });

  test('finds by id, then title, then the one row containing the words', () {
    final p = load('banking-support');
    final rows = p.rowsFor('CUST-10041');
    expect(findRow(rows, 'credit-7730').row, rows[1]);
    expect(findRow(rows, 'Travel credit card ···· 7730').row, rows[1]);
    expect(findRow(rows, '7730').row, rows[1]);
    expect(findRow(rows, 'card').ambiguous, rows);
    final freeze = p.action('freeze_card')!;
    expect(runPackAction(p, freeze, {'item': 'card'}, rows, const {}).answer.message, 'Which one? ${rows.map((r) => r.title).join(', ')}.');
    expect(runPackAction(p, freeze, {'item': '4444'}, rows, const {}).answer.message, 'No card called "4444". Cards: ${rows.map((r) => r.title).join(', ')}.');
  });

  test('sets a status once, then says it is already set; refuses what a row does not offer', () {
    final health = load('health-coach');
    final rows = health.rowsFor('MRN-000981');
    final markDone = health.action('mark_done')!;
    final first = runPackAction(health, markDone, {'item': 'metformin'}, rows, const {});
    expect(first.answer.message, 'Metformin 500 mg: Taken today.');
    expect((first.effect! as SetStatusEffect).status, 'Taken today');
    final again = runPackAction(health, markDone, {'item': 'Metformin'}, rows, const {'metformin': 'Taken today'});
    expect(again.answer.message, 'Metformin 500 mg is already Taken today.');
    expect(again.effect, isNull);

    final retail = load('retail-shopping');
    final refused = runPackAction(retail, retail.action('request_return')!, {'item': 'GD-55120'}, retail.rowsFor('SHP-20031'), const {});
    expect(refused.answer.message, 'Return is not available for Running shoes, size 7.');
  });

  test('hides the button for the status a row already has', () {
    final p = load('banking-support');
    final frozen = p.rowsFor('CUST-10043').firstWhere((r) => r.status == 'Frozen');
    expect(rowButtons(p, frozen, const {}).map((a) => a.name), ['unfreeze_card']);
    expect(rowButtons(p, frozen, {frozen.id: 'Active'}).map((a) => a.name), ['freeze_card']);
  });
}
