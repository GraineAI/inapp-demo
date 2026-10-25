import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:graine_demo/app.dart';
import 'package:graine_demo/graine_service.dart';
import 'package:graine_demo/state.dart';
import 'package:graine_demo/usecases.dart';
import 'package:graine_inapp/graine_inapp.dart'
    show GrainePastSession, GrainePastSessionChannel, GrainePastSessionDirection, GrainePastSessionRole, GrainePastSessionTranscript, GrainePastSessionTurn, GrainePastSessions;

import 'fake_service.dart';

/// Past sessions: how the SDK's state reads in the app, and the screen's flow over a fake SDK.
void main() {
  DateTime at(String iso) => DateTime.parse(iso);

  // The contract's first page (sdk/contract/past-sessions.json), as the app holds it.
  final chat = PastSession(
      id: 'ex-7a1c0001', startedAt: at('2026-10-21T12:00:05Z'), endedAt: at('2026-10-21T12:04:41Z'), durationSeconds: 276,
      channel: 'chat', status: 'completed', turns: 9, preview: 'Can I move my check-in call to Friday?');
  final call = PastSession(
      id: 'ex-31f00002', startedAt: at('2026-10-20T08:00:00Z'), endedAt: at('2026-10-20T08:03:12Z'), durationSeconds: 192,
      channel: 'call', direction: 'outbound', status: 'completed', turns: 14, preview: 'Hi, yes I can talk now.');

  test("the SDK's state reads as the app's own", () {
    final sdk = GrainePastSessions(sessions: [
      GrainePastSession(
          id: 'ex-7a1c0001', startedAt: at('2026-10-21T12:00:05Z'), endedAt: at('2026-10-21T12:04:41Z'), durationSeconds: 276,
          status: 'completed', turns: 9, preview: 'Can I move my check-in call to Friday?'),
      GrainePastSession(
          id: 'ex-31f00002', startedAt: at('2026-10-20T08:00:00Z'), endedAt: at('2026-10-20T08:03:12Z'), durationSeconds: 192,
          channel: GrainePastSessionChannel.call, direction: GrainePastSessionDirection.outbound, status: 'completed', turns: 14, preview: 'Hi, yes I can talk now.'),
    ], hasMore: true);
    expect(pastSessionsOf(sdk), PastSessionsState(sessions: [chat, call], hasMore: true));
    // What the SDK says it does not know reads as nothing: no status, no preview, no length.
    expect(pastSessionOf(GrainePastSession(id: 'ex-loose0003', startedAt: at('2026-10-19T18:30:00Z'))),
        PastSession(id: 'ex-loose0003', startedAt: at('2026-10-19T18:30:00Z'), channel: 'chat'));
    expect(pastSessionsOf(const GrainePastSessions(error: 'device_limit')).error, 'device_limit');
    expect(pastSessionsOf(const GrainePastSessions(loading: true)).loading, isTrue);

    final t = transcriptOf(GrainePastSessionTranscript(
      id: 'ex-7a1c0001',
      startedAt: at('2026-10-21T12:00:05Z'),
      durationSeconds: 276,
      channel: GrainePastSessionChannel.voice,
      direction: GrainePastSessionDirection.inbound,
      turns: [
        GrainePastSessionTurn(role: GrainePastSessionRole.agent, text: 'Hi Meera, how can I help?', at: at('2026-10-21T12:00:05Z')),
        GrainePastSessionTurn(role: GrainePastSessionRole.customer, text: 'Move my call.', at: at('2026-10-21T12:00:20Z')),
      ],
    ));
    expect(t.turns.map((l) => l.role), ['agent', 'customer']);
    expect((t.channel, t.direction, t.status), ('voice', 'inbound', null));
    expect(PastSessionsText.transcriptSummary(t), 'Voice · inbound · 4:36 · Ended');
  });

  test('a session reads as kind, length and messages', () {
    expect(PastSessionsText.summary(chat), 'Chat · 4:36 · 9 messages');
    expect(PastSessionsText.summary(call), 'Call · outbound · 3:12 · 14 messages');
    expect(PastSessionsText.status('completed'), 'Completed');
    expect(PastSessionsText.status('no_answer'), 'No answer');
    expect(PastSessionsText.status(null), 'Ended');
    expect(PastSessionsText.messages(1), '1 message');
    expect(PastSessionsText.length(3723), '1:02:03');
    expect(PastSessionsText.length(null), isNull);
    expect(PastSessionsText.shortId('ex-7a1c0001'), '…1c0001');
    expect(PastSessionsText.shortId('a1'), 'a1');
    expect(PastSessionsText.isCustomer(const TranscriptTurn(role: 'customer', text: 'x')), isTrue);
    expect(PastSessionsText.isCustomer(const TranscriptTurn(role: 'agent', text: 'x')), isFalse);
  });

  test('refusals read in plain words', () {
    expect(PastSessionsText.error('identity_required'), 'Sign in to see past sessions.');
    expect(PastSessionsText.error('device_limit'), 'Not available on this device.');
    expect(PastSessionsText.error('rate_limited'), 'Too many requests. Try again in a few minutes.');
    expect(PastSessionsText.error('unavailable'), 'Past sessions could not be loaded. Try again.');
    expect(PastSessionsText.error('not_found'), 'Past sessions could not be loaded. Try again.', reason: 'the list itself is never "that session"');
    expect(PastSessionsText.transcriptError('not_found'), 'That session is not available.');
    expect(PastSessionsText.transcriptError('device_limit'), 'Not available on this device.');
    expect(PastSessionsText.error(null), isNull);
    expect(PastSessionsText.canRetry('identity_required'), isFalse, reason: 'signing in is the answer');
    expect(PastSessionsText.canRetry('device_limit'), isFalse, reason: 'another device is the answer');
    expect(PastSessionsText.canRetry('unavailable'), isTrue);
  });

  Future<(FakeGraineService, DemoState)> openFromChat(WidgetTester tester, {bool help = false}) async {
    tester.view.physicalSize = const Size(1200, 8000);
    tester.view.devicePixelRatio = 2.0;
    addTearDown(tester.view.reset);
    final service = FakeGraineService(hasHelpAgent: help);
    final packs = [
      for (final id in packIds) UseCasePack.fromJson(jsonDecode(File('assets/usecases/$id.json').readAsStringSync()) as Map<String, dynamic>),
    ];
    final state = DemoState(packs: packs, service: service, keyFor: (_) => 'pk_test_demo');
    await state.restore();
    await tester.pumpWidget(GraineDemoApp(state: state, withEngine: false));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('sign-in')));
    await tester.pumpAndSettle();
    state.go(ScreenKey.chat);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('past-sessions-open')));
    await tester.pumpAndSettle();
    return (service, state);
  }

  testWidgets('from the chat: Past sessions refreshes on open, lists the page and loads more only while there is more', (tester) async {
    final (service, _) = await openFromChat(tester);
    expect(find.text('Past sessions'), findsOneWidget);
    expect(find.text('With Asha · newest first · drawn by this app'), findsOneWidget);
    expect(service.pastRefreshes, [DemoAgent.main], reason: 'the first page, newest first');
    expect(find.byKey(const Key('past-sessions-empty')), findsOneWidget);

    service.past[DemoAgent.main]!.value = const PastSessionsState(loading: true);
    await tester.pump();
    expect(find.text(PastSessionsText.loading), findsOneWidget);

    service.past[DemoAgent.main]!.value = PastSessionsState(sessions: [chat], hasMore: true);
    await tester.pump();
    await tester.pump();
    expect(find.text('Chat · 4:36 · 9 messages'), findsOneWidget);
    expect(find.text('“Can I move my check-in call to Friday?”'), findsOneWidget);
    expect(find.text('Completed'), findsOneWidget);
    expect(service.pastMores, [DemoAgent.main], reason: 'the end of the list is in view: the next page');

    service.past[DemoAgent.main]!.value = PastSessionsState(sessions: [chat], hasMore: true, loading: true);
    await tester.pump();
    await tester.pump();
    expect(service.pastMores, hasLength(1), reason: 'not while a page is on its way');

    service.past[DemoAgent.main]!.value = PastSessionsState(sessions: [chat, call], hasMore: true, error: 'network');
    await tester.pump();
    await tester.pump();
    expect(service.pastMores, hasLength(1), reason: 'a page that failed waits for Try again');
    expect(find.text('Past sessions could not be loaded. Try again.'), findsOneWidget);
    await tester.tap(find.text('Try again'));
    await tester.pump();
    expect(service.pastMores, hasLength(2));

    service.past[DemoAgent.main]!.value = PastSessionsState(sessions: [chat, call]);
    await tester.pump();
    await tester.pump();
    expect(find.text('Call · outbound · 3:12 · 14 messages'), findsOneWidget);
    expect(find.text('No earlier sessions.'), findsOneWidget);
    expect(service.pastMores, hasLength(2), reason: 'not past the end');

    await tester.tap(find.byKey(const Key('past-sessions-refresh')));
    await tester.pump();
    expect(service.pastRefreshes, [DemoAgent.main, DemoAgent.main]);
  });

  testWidgets('a refused list says why: signing in or another device is the answer, else Try again', (tester) async {
    final (service, _) = await openFromChat(tester);
    service.past[DemoAgent.main]!.value = const PastSessionsState(error: 'identity_required');
    await tester.pump();
    expect(find.text('Sign in to see past sessions.'), findsOneWidget);
    expect(find.text('Try again'), findsNothing);

    service.past[DemoAgent.main]!.value = const PastSessionsState(error: 'device_limit');
    await tester.pump();
    expect(find.text('Not available on this device.'), findsOneWidget);
    expect(find.text('Try again'), findsNothing);

    service.past[DemoAgent.main]!.value = const PastSessionsState(error: 'unavailable');
    await tester.pump();
    expect(find.text('Past sessions could not be loaded. Try again.'), findsOneWidget);
    await tester.tap(find.text('Try again'));
    await tester.pump();
    expect(service.pastRefreshes, [DemoAgent.main, DemoAgent.main]);
  });

  testWidgets('a tap opens the transcript, read only, or says why not', (tester) async {
    final (service, _) = await openFromChat(tester);
    service.past[DemoAgent.main]!.value = PastSessionsState(sessions: [chat, call]);
    service.transcripts[chat.id] = SessionTranscript(id: chat.id, startedAt: chat.startedAt, durationSeconds: 276, channel: 'chat', status: 'completed', turns: [
      TranscriptTurn(role: 'agent', text: 'Hi, how can I help?', at: at('2026-10-21T12:00:05Z')),
      TranscriptTurn(role: 'customer', text: 'Move my call.', at: at('2026-10-21T12:00:20Z')),
    ]);
    service.transcriptRefusals[call.id] = const GraineException('Not this device.', code: GraineErrorCode.deviceLimit);
    await tester.pump();

    await tester.tap(find.byKey(Key('past-session-${call.id}')));
    await tester.pumpAndSettle();
    expect(find.text('Not available on this device.'), findsOneWidget);

    await tester.tap(find.byKey(Key('past-session-${chat.id}')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('transcript')), findsOneWidget);
    expect(find.text('Chat · 4:36 · Completed'), findsOneWidget);
    expect(find.text('Hi, how can I help?'), findsOneWidget);
    expect(find.text('Move my call.'), findsOneWidget);
    expect(find.textContaining('You · '), findsOneWidget);
    expect(find.textContaining('Asha · '), findsOneWidget);

    await tester.pageBack();
    await tester.pumpAndSettle();
    service.past[DemoAgent.main]!.value = PastSessionsState(sessions: [chat, call, const PastSession(id: 'someone-else', channel: 'chat')]);
    await tester.pump();
    await tester.tap(find.byKey(const Key('past-session-someone-else')));
    await tester.pumpAndSettle();
    expect(find.text('That session is not available.'), findsOneWidget);
  });

  testWidgets("Help's Past sessions are the help agent's", (tester) async {
    final (service, state) = await openFromChat(tester, help: true);
    await tester.pageBack();
    await tester.pumpAndSettle();
    state.go(ScreenKey.home);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('open-help')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('help-past-sessions-open')));
    await tester.pumpAndSettle();
    expect(find.text('With Billing & appointments help · newest first · drawn by this app'), findsOneWidget);
    expect(service.pastRefreshes, [DemoAgent.main, DemoAgent.help]);
  });
}
