/// The demo's own state: which pack, who is signed in, their memory consent, the statuses set on their rows
/// this session, the tab, the voice bar and the call log. The chosen pack and the signed-in person are
/// remembered on the device and signed in again at launch (identity is the app's to restore).
library;

import 'dart:async';
import 'dart:convert';

import 'package:flutter/widgets.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'graine_service.dart';
import 'logic/actions.dart';
import 'usecases.dart';

const String _packKey = 'graine-demo.pack';
const String _signedInKey = 'graine-demo.signed-in';

class DemoState extends ChangeNotifier {
  DemoState({required this.packs, required this.service, required this.keyFor, this.helpKeyFor, this.prefs, String? packId})
      : pack = packs.firstWhere((p) => p.id == packId, orElse: () => packs.firstWhere((p) => p.id == defaultPackId, orElse: () => packs.first)) {
    _startedHelpKey = helpKeyFor?.call(pack.id);
  }

  final List<UseCasePack> packs;
  final GraineService service;

  /// The publishable key a pack's agent uses (main.dart reads them from --dart-define).
  final String Function(String packId) keyFor;

  /// The key of a pack's help agent, or null without one.
  final String? Function(String packId)? helpKeyFor;
  String? _startedHelpKey;

  /// This run's agents are the ones [packId] needs (same main key, same help key).
  bool _runs(String packId) => keyFor(packId) == service.publishableKey && helpKeyFor?.call(packId) == _startedHelpKey;
  final SharedPreferences? prefs;

  UseCasePack pack;
  PackUser? user;
  bool consent = true;
  bool ready = false;

  /// A pack whose agent needs another key than the one this run started with: chosen, applied at restart.
  String? restartFor;

  ScreenKey tab = ScreenKey.home;
  bool voiceOpen = false;
  String? openRowId;
  final List<CallEvent> callLog = <CallEvent>[];
  final Map<String, Map<String, String>> _statuses = <String, Map<String, String>>{};

  /// Set by the app: open a screen from anywhere (an action the agent ran, a card, a chip).
  void Function(ScreenKey key)? navigator;

  StreamSubscription<CallEvent>? _calls;
  void Function()? _unregister;

  List<PackRow> get rows => pack.rowsFor(user?.id);
  Map<String, String> get statuses => user == null ? const <String, String>{} : (_statuses[user!.id] ?? const <String, String>{});

  /// The remembered person signed in again, the call engine set up, the agent's session read.
  Future<void> restore() async {
    final saved = _readSignedIn();
    if (saved != null && saved.$1 == pack.id && _runs(pack.id)) {
      final person = pack.users.where((u) => u.id == saved.$2).firstOrNull;
      if (person != null) _apply(person, saved.$3);
    }
    _calls = service.callEvents.listen((e) {
      callLog.insert(0, e);
      if (callLog.length > 20) callLog.removeLast();
      debugPrint('[Graine Demo] call ${e.callId}: ${e.kind}${e.detail != null ? ' (${e.detail})' : ''}');
      notifyListeners();
    });
    ready = true;
    notifyListeners();
    unawaited(_afterSessionLoads());
    unawaited(service.setUpCalls(pack.call.callName).catchError((Object e) => debugPrint('[Graine Demo] Calls could not be set up: $e')));
  }

  Future<void> _afterSessionLoads() async {
    try {
      await service.loadSession();
    } catch (e) {
      debugPrint('[Graine Demo] The agent could not be loaded: $e');
    }
    final name = service.rulesOf(DemoAgent.main).callName;
    if (name != null && name.isNotEmpty) unawaited(service.setUpCalls(name).catchError((Object _) {}));
    notifyListeners();
  }

  (String, String, bool)? _readSignedIn() {
    final raw = prefs?.getString(_signedInKey);
    if (raw == null) return null;
    try {
      final j = jsonDecode(raw) as Map<String, dynamic>;
      return (j['packId'] as String, j['userId'] as String, j['consent'] as bool? ?? true);
    } catch (_) {
      return null;
    }
  }

  /// Switch the use case (signing the current person out). False when that pack's agent has another key:
  /// the choice is kept and applied at the next launch.
  Future<bool> choosePack(String id) async {
    final next = packs.where((p) => p.id == id).firstOrNull;
    if (next == null || next.id == pack.id) return true;
    if (user != null) await signOut();
    await prefs?.setString(_packKey, next.id);
    if (!_runs(next.id)) {
      restartFor = next.id;
      notifyListeners();
      return false;
    }
    restartFor = null;
    pack = next;
    openRowId = null;
    notifyListeners();
    return true;
  }

  Future<void> signIn(PackUser person, bool agreed) async {
    _apply(person, agreed);
    await prefs?.setString(_signedInKey, jsonEncode(<String, Object?>{'packId': pack.id, 'userId': person.id, 'consent': agreed}));
    tab = ScreenKey.home;
    notifyListeners();
    unawaited(service.requestPermissions());
  }

  void _apply(PackUser person, bool agreed) {
    service.signIn(pack, person, memory: agreed);
    user = person;
    consent = agreed;
    openRowId = null;
    _unregister?.call();
    _unregister = service.registerActions(pack, _runAction);
  }

  Future<void> signOut() async {
    service.endVoice();
    service.signOut();
    _unregister?.call();
    _unregister = null;
    user = null;
    voiceOpen = false;
    openRowId = null;
    await prefs?.remove(_signedInKey);
    notifyListeners();
  }

  Future<void> setConsent(bool on) async {
    service.setMemoryConsent(on);
    consent = on;
    if (user != null) {
      await prefs?.setString(_signedInKey, jsonEncode(<String, Object?>{'packId': pack.id, 'userId': user!.id, 'consent': on}));
    }
    notifyListeners();
  }

  void setRowStatus(String rowId, String status) {
    if (user == null) return;
    (_statuses[user!.id] ??= <String, String>{})[rowId] = status;
    notifyListeners();
  }

  void setOpenRow(String? rowId) {
    openRowId = rowId;
    notifyListeners();
  }

  /// Go to a generic screen. Tabs switch here; Memory is opened by the app's navigator.
  void go(ScreenKey key) {
    if (key == ScreenKey.chat) closeVoice();
    if (key != ScreenKey.memory) tab = key;
    navigator?.call(key);
    notifyListeners();
  }

  void openVoice() {
    voiceOpen = true;
    notifyListeners();
    unawaited(service.startVoice());
  }

  void closeVoice() {
    if (!voiceOpen) return;
    voiceOpen = false;
    service.endVoice();
    notifyListeners();
  }

  /// The pack's actions, all run by the same rules (logic/actions.dart).
  ActionResult _runAction(PackAction action, Map<String, Object?> args) {
    final outcome = runPackAction(pack, action, args, rows, statuses);
    switch (outcome.effect) {
      case NavigateEffect(:final screen):
        go(screen);
      case OpenItemEffect(:final rowId):
        openRowId = rowId;
        go(ScreenKey.items);
      case SetStatusEffect(:final rowId, :final status):
        setRowStatus(rowId, status);
      case null:
        break;
    }
    final a = outcome.answer;
    return a.status == 'ok' ? ActionResult.ok(a.message, a.data) : ActionResult.refused(a.message);
  }

  /// For tests: run one of the pack's actions as the agent would.
  ActionResult runActionForTest(String name, Map<String, Object?> args) => _runAction(pack.action(name)!, args);

  @override
  void dispose() {
    unawaited(_calls?.cancel());
    _unregister?.call();
    super.dispose();
  }
}

/// The state, for every screen below it.
class DemoScope extends InheritedNotifier<DemoState> {
  const DemoScope({super.key, required DemoState state, required super.child}) : super(notifier: state);

  static DemoState of(BuildContext context) => context.dependOnInheritedWidgetOfExactType<DemoScope>()!.notifier!;
}
