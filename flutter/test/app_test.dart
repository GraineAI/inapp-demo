import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:graine_demo/app.dart';
import 'package:graine_demo/graine_service.dart';
import 'package:graine_demo/state.dart';
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
    expect(traits['name'], 'Rahul Verma');
    expect(memory, isFalse);
    expect(service.registered, ['open_screen', 'open_item', 'freeze_card', 'unfreeze_card']);
    expect(find.text('Hello, Rahul'), findsOneWidget);
    expect(find.text('Memory off'), findsOneWidget);
    expect(service.screens.last.screen, 'home');
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

  testWidgets('booking: reason, quick time, the zone line, scheduleCall, then the call with its chip and Cancel', (tester) async {
    final (_, service) = await start(tester);
    await signInFirst(tester);
    await tester.tap(find.text('Calls'));
    await tester.pumpAndSettle();
    expect(find.text('Nothing booked.'), findsOneWidget);

    await tester.tap(find.byKey(const Key('open-booking')));
    await tester.pumpAndSettle();
    expect(find.text('Schedule a consultation call'), findsWidgets);
    expect(find.byKey(const Key('reason-Medication review')), findsOneWidget);
    await tester.tap(find.byKey(const Key('reason-Medication review')));
    await tester.tap(find.byKey(const Key('quick-Tomorrow 10:00')));
    await tester.pumpAndSettle();
    expect(find.text('Asia/Kolkata · Tomorrow, 10:00 am'), findsOneWidget);

    // The SDK's refusal, in plain words; the sheet stays open.
    service.failNextBooking = const GraineException('outside hours', code: GraineErrorCode.outsideWindow);
    await tester.tap(find.byKey(const Key('book')));
    await tester.pumpAndSettle();
    expect(find.text("That time is outside Asha's calling hours. Pick another time."), findsOneWidget);

    await tester.tap(find.byKey(const Key('book')));
    await tester.pumpAndSettle();
    final (at, reason, agent) = service.bookings.single;
    expect(agent, DemoAgent.main);
    final tomorrow = DateTime.now().add(const Duration(days: 1));
    expect((at.year, at.month, at.day, at.hour, at.minute), (tomorrow.year, tomorrow.month, tomorrow.day, 10, 0));
    expect(reason, 'Medication review');
    expect(find.byKey(const Key('call-c1')), findsOneWidget);
    expect(find.text('Scheduled'), findsOneWidget);
    expect(find.textContaining('Booked: Asia/Kolkata · Tomorrow, 10:00 am'), findsOneWidget);

    await tester.tap(find.byKey(const Key('cancel-c1')));
    await tester.pumpAndSettle();
    expect(find.text('Cancelled'), findsOneWidget);
    expect(find.byKey(const Key('cancel-c1')), findsNothing);
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

  testWidgets('help: the second agent chats, talks and books with its own reasons; both agents\' calls are listed by name', (tester) async {
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

    await tester.tap(find.byKey(const Key('help-book')));
    await tester.pumpAndSettle();
    expect(find.text('Book a call with billing'), findsWidgets);
    await tester.tap(find.byKey(const Key('reason-Change an appointment')));
    await tester.tap(find.byKey(const Key('quick-Tomorrow 10:00')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('book')));
    await tester.pumpAndSettle();
    final (_, reason, agent) = service.bookings.single;
    expect((reason, agent), ('Change an appointment', DemoAgent.help));
    expect(find.byKey(const Key('help-message')), findsOneWidget);

    // A main-agent booking too; the list shows both, each named.
    service.calls[DemoAgent.main]!.add(GraineScheduledCall(id: 'c9', at: DateTime.now().add(const Duration(days: 2)).toUtc(), reason: 'Consultation'));
    await tester.pageBack();
    await tester.pumpAndSettle();
    await tester.tap(find.text('Calls'));
    await tester.pumpAndSettle();
    expect(find.text('Billing & appointments help · Change an appointment'), findsOneWidget);
    expect(find.text('Asha · Consultation'), findsOneWidget);
    await tester.tap(find.byKey(const Key('cancel-h1')));
    await tester.pumpAndSettle();
    expect(service.calls[DemoAgent.help]!.single.status, GraineCallStatus.cancelled);
  });
}
