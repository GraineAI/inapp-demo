import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:graine_demo/graine_service.dart';
import 'package:graine_demo/usecases.dart';

/// The adapter, faked: records what the app asked the SDK for, answers like the SDK would. Two agents: the
/// help agent exists when [hasHelpAgent].
class FakeGraineService implements GraineService {
  FakeGraineService({this.publishableKey = 'pk_test_demo', this.hasHelpAgent = false});

  @override
  final String publishableKey;

  @override
  final bool hasHelpAgent;

  final List<(String packId, Map<String, Object?> traits, bool memory)> signIns = <(String, Map<String, Object?>, bool)>[];
  int signOuts = 0;
  final List<bool> consentChanges = <bool>[];
  final List<GraineScreen> screens = <GraineScreen>[];
  final List<String> registered = <String>[];
  List<GraineMemory> remembered = <GraineMemory>[
    GraineMemory(id: 'm1', text: 'Takes metformin at 8 pm, after an evening walk.', createdAt: DateTime.now().subtract(const Duration(days: 1))),
    GraineMemory(id: 'm2', text: 'Feels dizzy when skipping breakfast.', createdAt: DateTime.now().subtract(const Duration(days: 2))),
  ];
  int forgets = 0;
  final Map<DemoAgent, List<GraineScheduledCall>> calls = <DemoAgent, List<GraineScheduledCall>>{DemoAgent.main: [], DemoAgent.help: []};
  final List<(String reason, DemoAgent agent)> placed = <(String, DemoAgent)>[];

  /// Whether each agent can be booked now, as the SDK would say; a test flips them.
  final Map<DemoAgent, ValueNotifier<bool>> bookable = {DemoAgent.main: ValueNotifier<bool>(true), DemoAgent.help: ValueNotifier<bool>(true)};

  /// Every booking sheet opened: the reason it started with, and the agent.
  final List<(String? reason, DemoAgent agent)> bookingSheets = <(String?, DemoAgent)>[];

  /// The sheet books this (and Graine then lists it); null: the person closes it without booking.
  DateTime? bookAt = DateTime.now().add(const Duration(days: 1));
  GraineSchedulingRules rules = const GraineSchedulingRules(usersCanBook: true, minLeadMinutes: 5, maxDaysAhead: 14, callName: 'Asha · Graine Care');

  @override
  void signIn(UseCasePack pack, PackUser user, {required bool memory}) => signIns.add((pack.id, pack.identifyTraits(user), memory));

  @override
  void signOut() => signOuts++;

  @override
  void setMemoryConsent(bool enabled) => consentChanges.add(enabled);

  @override
  Future<List<GraineMemory>> memories() async => List<GraineMemory>.of(remembered);

  @override
  Future<int> forgetMemory() async {
    forgets++;
    final n = remembered.length;
    remembered = <GraineMemory>[];
    return n;
  }

  @override
  Future<String> installId() async => '3f2b8c1e-8d4a-4e1b-9a55-0c2f9b7d6e11';

  @override
  bool? get agentRemembers => true;

  @override
  Future<void> loadSession() async {}

  @override
  GraineSchedulingRules rulesOf(DemoAgent agent) => rules;

  @override
  ValueListenable<bool> bookingAvailable(DemoAgent agent) => bookable[agent]!;

  @override
  Future<GraineScheduledCall?> openBooking(BuildContext context, {String? reason, DemoAgent agent = DemoAgent.main}) async {
    bookingSheets.add((reason, agent));
    final at = bookAt;
    if (at == null) return null;
    final list = calls[agent]!;
    final call = GraineScheduledCall(
        id: '${agent == DemoAgent.help ? 'h' : 'c'}${list.length + 1}', at: at.toUtc(), reason: reason, source: GraineCallSource.app, sourceName: 'sdk');
    list.insert(0, call);
    return call;
  }

  @override
  Future<List<GraineScheduledCall>> scheduledCalls({DemoAgent agent = DemoAgent.main}) async =>
      agent == DemoAgent.help && !hasHelpAgent ? const <GraineScheduledCall>[] : List<GraineScheduledCall>.of(calls[agent]!);

  @override
  Future<GraineCallStatus> cancelScheduledCall(String id, {DemoAgent agent = DemoAgent.main}) async {
    final list = calls[agent]!;
    final i = list.indexWhere((c) => c.id == id);
    if (i < 0) throw const GraineException('Not yours.', code: GraineErrorCode.notFound);
    final c = list[i];
    list[i] = GraineScheduledCall(id: c.id, at: c.at, status: GraineCallStatus.cancelled, statusName: 'cancelled', reason: c.reason);
    return GraineCallStatus.cancelled;
  }

  @override
  void reportScreen(GraineScreen screen) => screens.add(screen);

  ActionRunner? runner;

  @override
  void Function() registerActions(UseCasePack pack, ActionRunner run) {
    registered
      ..clear()
      ..addAll(pack.actions.map((a) => a.name));
    runner = run;
    return () => registered.clear();
  }

  @override
  bool get callsSupported => false;

  @override
  Future<void> setUpCalls(String callName) async {}

  @override
  Future<String?> placeCall(String reason, {DemoAgent agent = DemoAgent.main}) async {
    placed.add((reason, agent));
    return null;
  }

  /// What the call engine reports, as a test hands it in.
  final StreamController<CallEvent> callEventsIn = StreamController<CallEvent>.broadcast();

  @override
  Stream<CallEvent> get callEvents => callEventsIn.stream;

  final List<DemoAgent> voiceStarts = <DemoAgent>[];

  @override
  Future<void> startVoice({DemoAgent agent = DemoAgent.main}) async => voiceStarts.add(agent);

  @override
  void endVoice({DemoAgent agent = DemoAgent.main}) {}

  @override
  Widget helpChat(String agentName) => Text('Chat with $agentName', key: const Key('fake-help-chat'));

  @override
  Widget helpVoice({required Widget child, required bool talking, required String agentName, required VoidCallback onClose}) =>
      Stack(children: [child, if (talking) Text('$agentName is listening', key: const Key('fake-help-voice'))]);

  @override
  Future<void> requestPermissions() async {}
}
