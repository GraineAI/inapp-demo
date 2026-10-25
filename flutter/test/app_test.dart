import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:graine_demo/app.dart';
import 'package:graine_demo/graine_service.dart';
import 'package:graine_demo/state.dart';
import 'package:graine_demo/ui.dart';
import 'package:graine_demo/usecases.dart';

import 'fake_service.dart';

List<UseCasePack> loadAll() => [
      for (final id in packIds) UseCasePack.fromJson(jsonDecode(File('assets/usecases/$id.json').readAsStringSync()) as Map<String, dynamic>),
    ];

Future<(DemoState, FakeGraineService)> start(WidgetTester tester, {bool help = false}) async {
  // A tall phone, so every row of the longer screens is laid out without scrolling.
  tester.view.physicalSize = const Size(1200, 8000);
  tester.view.devicePixelRatio = 2.0;
  addTearDown(tester.view.reset);
  final service = FakeGraineService(hasHelpAgent: help);
  final state = DemoState(packs: loadAll(), service: service, keyFor: (_) => 'pk_test_demo');
  await state.restore();
  await tester.pumpWidget(GraineDemoApp(state: state, withEngine: false));
  await tester.pumpAndSettle();
  return (state, service);
}

Future<void> signInFirst(WidgetTester tester) async {
  await tester.tap(find.byKey(const Key('sign-in')));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('sign in: the pack picker changes the people, and sign-in identifies with the pack trait and consent', (tester) async {
    final (state, service) = await start(tester);
    expect(find.text('Riya Sharma'), findsOneWidget);

    await tester.tap(find.byKey(const Key('pack-banking-support')));
    await tester.pumpAndSettle();
    expect(state.pack.id, 'banking-support');
    expect(find.text('Priya Nair'), findsOneWidget);
    expect(find.text('Riya Sharma'), findsNothing);

    await tester.tap(find.byKey(const Key('user-CUST-10042')));
    await tester.tap(find.byKey(const Key('consent')));
    await tester.pumpAndSettle();
    await signInFirst(tester);

    expect(service.signIns, hasLength(1));
    final (packId, traits, memory) = service.signIns.single;
    expect(packId, 'banking-support');
    expect(traits['customer_id'], 'CUST-10042');
    // The id rides under the pack's field and under user_id, so either "Who is the user?" choice recognises the user.
    expect(traits['user_id'], 'CUST-10042');
    expect(traits['name'], 'Rahul Verma');
    expect(memory, isFalse);
    expect(service.registered, ['open_screen', 'open_item', 'freeze_card', 'unfreeze_card']);
    expect(find.text('Hello, Rahul'), findsOneWidget);
    expect(find.text('Memory off'), findsOneWidget);
    expect(service.screens.last.screen, 'home');
  });

  testWidgets("sign in sets the agents' variables through the SDK, as an API call sends them; sign out clears them", (tester) async {
    final (state, service) = await start(tester);
    await signInFirst(tester);
    expect(service.variables, [
      {'callee_name': 'Riya Sharma', 'age': '34'},
    ]);

    await state.signOut();
    await tester.pumpAndSettle();
    expect(service.variables.last, {'callee_name': null, 'age': null}, reason: "nothing of Riya's reaches the next person");
  });

  testWidgets('items: only the signed-in person rows, a row action sets its status, and the agent can do the same', (tester) async {
    final (state, service) = await start(tester);
    await signInFirst(tester);

    await tester.tap(find.text('Medicines'));
    await tester.pumpAndSettle();
    expect(service.screens.last.screen, 'medicines');
    expect(find.text('Metformin 500 mg'), findsOneWidget);
    expect(find.text('Amlodipine 5 mg'), findsNothing, reason: "another patient's medicine");

    await tester.tap(find.text('Metformin 500 mg'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('metformin-mark_done')));
    await tester.pumpAndSettle();
    expect(find.text('Taken today'), findsOneWidget);
    expect(service.screens.last.fields.firstWhere((f) => f.name == 'metformin').value, 'Taken today');

    final r = state.runActionForTest('mark_done', {'item': 'Atorvastatin'});
    expect(r.message, 'Atorvastatin 10 mg: Taken today.');
    final opened = state.runActionForTest('open_screen', {'screen': 'calls'});
    await tester.pumpAndSettle();
    expect(opened.message, 'Opened Check-in calls.');
    expect(state.tab, ScreenKey.calls);
  });

  testWidgets("booking: the pack's button opens the SDK's sheet with the pack's reason, then the call with its chip and Cancel", (tester) async {
    final (_, service) = await start(tester);
    await signInFirst(tester);
    await tester.tap(find.text('Calls'));
    await tester.pumpAndSettle();
    expect(find.text('Nothing booked.'), findsOneWidget);

    await tester.tap(find.byKey(const Key('open-booking')));
    await tester.pumpAndSettle();
    expect(service.bookingSheets.single, ('Consultation', DemoAgent.main));
    // The list is read again: the booked call, with its chip and Cancel.
    expect(find.byKey(const Key('call-c1')), findsOneWidget);
    expect(find.text('Scheduled'), findsOneWidget);
    expect(find.textContaining('Call with Asha booked for Tomorrow, '), findsOneWidget);

    await tester.tap(find.byKey(const Key('cancel-c1')));
    await tester.pumpAndSettle();
    expect(find.text('Cancelled'), findsOneWidget);
    expect(find.byKey(const Key('cancel-c1')), findsNothing);
  });

  testWidgets("booking: closing the sheet books nothing; while the SDK says no, the line says why and a tap still opens the SDK's sheet", (tester) async {
    final (_, service) = await start(tester);
    await signInFirst(tester);
    await tester.tap(find.text('Calls'));
    await tester.pumpAndSettle();
    service.bookAt = null;
    await tester.tap(find.byKey(const Key('open-booking')));
    await tester.pumpAndSettle();
    expect(service.bookingSheets, hasLength(1));
    expect(find.text('Nothing booked.'), findsOneWidget);
    expect(find.text(bookingUnavailable), findsNothing);

    service.bookable[DemoAgent.main]!.value = false;
    await tester.pumpAndSettle();
    expect(tester.widget<FilledButton>(find.byKey(const Key('open-booking'))).onPressed, isNotNull);
    expect(find.text("Booking needs, in Graine UI: users may book (Calls & scheduling), this app's push keys (Workspace settings → In-app calling), and Who is the user? set to this pack's ID."), findsOneWidget);
    await tester.tap(find.byKey(const Key('open-booking')));
    await tester.pumpAndSettle();
    expect(service.bookingSheets, hasLength(2), reason: "the SDK's sheet opens, and says why itself");
  });

  testWidgets("booking from Home: the pack's button opens the same sheet, then the next call shows", (tester) async {
    final (_, service) = await start(tester);
    await signInFirst(tester);
    expect(find.textContaining('No calls booked.'), findsOneWidget);
    await tester.tap(find.byKey(const Key('book-from-home')));
    await tester.pumpAndSettle();
    expect(service.bookingSheets.single, ('Consultation', DemoAgent.main));
    expect(find.textContaining('No calls booked.'), findsNothing);
    expect(find.textContaining('Asha · Consultation'), findsOneWidget);
  });

  testWidgets('memory: the list, then erase with an in-app confirmation', (tester) async {
    final (_, service) = await start(tester);
    await signInFirst(tester);
    await tester.tap(find.byKey(const Key('memory-chip')));
    await tester.pumpAndSettle();
    expect(find.text('What Asha remembers about you'), findsOneWidget);
    expect(find.text('Takes metformin at 8 pm, after an evening walk.'), findsOneWidget);
    expect(service.screens.last.screen, 'memory');

    await tester.tap(find.byKey(const Key('erase')));
    await tester.pumpAndSettle();
    expect(find.text('Erase everything Asha remembers?'), findsOneWidget);
    await tester.tap(find.byKey(const Key('confirm-erase')));
    await tester.pumpAndSettle();
    expect(service.forgets, 1);
    expect(find.textContaining('2 memories erased.'), findsOneWidget);
    expect(find.textContaining('Nothing yet. Tell Asha'), findsOneWidget);

    await tester.tap(find.byKey(const Key('memory-consent')));
    await tester.pumpAndSettle();
    expect(service.consentChanges, [false]);
  });

  testWidgets('help: without a help key, the Help screen says how to add one', (tester) async {
    await start(tester);
    await signInFirst(tester);
    await tester.tap(find.byKey(const Key('open-help')));
    await tester.pumpAndSettle();
    expect(find.text('Billing & appointments help'), findsOneWidget);
    expect(find.textContaining('This app has no key for Billing & appointments help yet'), findsOneWidget);
  });

  testWidgets('help: the second agent chats, talks and books through its own agent; both agents\' calls are listed by name', (tester) async {
    final (_, service) = await start(tester, help: true);
    await signInFirst(tester);
    expect(service.signIns, hasLength(1), reason: 'one sign-in for both agents');

    await tester.tap(find.byKey(const Key('open-help')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('fake-help-chat')), findsOneWidget);
    expect(service.screens.last.screen, 'help');

    await tester.tap(find.byKey(const Key('help-talk')));
    await tester.pumpAndSettle();
    expect(service.voiceStarts, [DemoAgent.help]);
    expect(find.byKey(const Key('fake-help-voice')), findsOneWidget);

    expect(find.text('Book a call with billing'), findsOneWidget);
    await tester.tap(find.byKey(const Key('help-book')));
    await tester.pumpAndSettle();
    expect(service.bookingSheets.single, ('Billing question', DemoAgent.help));
    expect(find.byKey(const Key('help-message')), findsOneWidget);

    // A main-agent booking too; the list shows both, each named.
    service.calls[DemoAgent.main]!.add(GraineScheduledCall(id: 'c9', at: DateTime.now().add(const Duration(days: 2)).toUtc(), reason: 'Consultation'));
    await tester.pageBack();
    await tester.pumpAndSettle();
    await tester.tap(find.text('Calls'));
    await tester.pumpAndSettle();
    expect(find.text('Billing & appointments help · Billing question'), findsOneWidget);
    expect(find.text('Asha · Consultation'), findsOneWidget);
    await tester.tap(find.byKey(const Key('cancel-h1')));
    await tester.pumpAndSettle();
    expect(service.calls[DemoAgent.help]!.single.status, GraineCallStatus.cancelled);

    // Call activity: each ring and outcome with the agent the call engine says the call was with.
    service.callEventsIn
      ..add(CallEvent('call_h1', 'incoming', 'Graine Care billing', DateTime(2026, 10, 14, 18, 5), agent: DemoAgent.help))
      ..add(CallEvent('call_m1', 'ended', 'remote', DateTime(2026, 10, 14, 18, 9), agent: DemoAgent.main));
    await tester.pumpAndSettle();
    expect(find.text('6:05 pm · Billing & appointments help · incoming (Graine Care billing) · call_h1'), findsOneWidget);
    expect(find.text('6:09 pm · Asha · ended (remote) · call_m1'), findsOneWidget);
  });
}
