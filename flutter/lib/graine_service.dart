/// THE ONE FILE THAT TALKS TO THE GRAINE SDK (`graine_inapp`).
///
/// Every screen goes through [GraineService], so an SDK change is one file to read, and the tests swap in a
/// fake. [SdkGraineService] is the real one: two agents (the pack's main agent on Home and Chat, its help agent
/// on the Help screen — each with its own publishable key), and calls like a phone call (CallKit / VoIP on
/// iOS, a ringing call screen on Android).
library;

import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:graine_inapp/graine_inapp.dart';
import 'package:permission_handler/permission_handler.dart';

import 'logic/presentation.dart';
import 'usecases.dart';

export 'package:graine_inapp/graine_inapp.dart'
    show
        ActionResult,
        FieldStatus,
        GraineAgentBar,
        GraineCallSource,
        GraineCallStatus,
        GraineChat,
        GraineEngine,
        GraineErrorCode,
        GraineException,
        GraineField,
        GraineMemory,
        GraineScheduledCall,
        GraineSchedulingRules,
        GraineScreen;

/// The app's two agents: the pack's main agent (Home, Chat) and its help agent (the Help screen).
enum DemoAgent { main, help }

/// One line of the in-app call log: a ring, or what became of a call.
class CallEvent {
  const CallEvent(this.callId, this.kind, this.detail, this.at, {this.agent});

  final String callId;

  /// `incoming`, `ringing`, `answered`, `declined`, `missed`, `ended` or `failed`.
  final String kind;
  final String? detail;
  final DateTime at;

  /// Which of the app's agents the call is with, as the call engine says; null when it does not.
  final DemoAgent? agent;
}

typedef ActionRunner = FutureOr<ActionResult> Function(PackAction action, Map<String, Object?> args);

abstract class GraineService {
  /// The main agent's publishable key this run started with (main.dart picks the pack's agents at launch).
  String get publishableKey;

  /// The app has a help agent (a help key was given).
  bool get hasHelpAgent;

  /// identify() with the pack's identifier trait (patient_id, customer_id…) and the same id under user_id, the name
  /// and the person's traits (`UseCasePack.identifyTraits`). Once: the SDK shares who is signed in with every agent of the app. The identifier keys memory and booked
  /// calls; it is never a prompt variable. [memory] is the consent switch.
  void signIn(UseCasePack pack, PackUser user, {required bool memory});

  /// identify(null): signed out of every agent; this device stops being the person's.
  void signOut();

  /// The agents' prompt {variables} — what an API call sends as `variables` — set through the SDK, for every
  /// agent: merged into each chat, voice session and call from now on; null under a key removes it.
  void setVariables(Map<String, String?> values);

  /// The consent switch (shared by every agent): false keeps this person's conversations out of memory.
  void setMemoryConsent(bool enabled);

  /// What the main agent remembers about the signed-in person, newest first.
  Future<List<GraineMemory>> memories();

  /// Erase everything the main agent remembers about the signed-in person: how many were deleted.
  Future<int> forgetMemory();

  Future<String> installId();

  /// Whether the main agent remembers people at all; null until its session is read.
  bool? get agentRemembers;

  Future<void> loadSession();

  /// [agent]'s scheduling settings from its session: the name its calls ring with (callName)…
  GraineSchedulingRules rulesOf(DemoAgent agent);

  /// Whether [agent] can be booked from the app now (the SDK's `bookingAvailable`), for the booking buttons to
  /// redraw on: it changes when the agent's session arrives and when someone signs in or out.
  ValueListenable<bool> bookingAvailable(DemoAgent agent);

  /// The SDK's booking sheet for [agent] — the day, the time, the time zone and an optional note, in the
  /// agent's design — its "What's it about?" starting with [reason]. The call booked, or null when the person
  /// closed it without booking; a refusal stays in the sheet.
  Future<GraineScheduledCall?> openBooking(BuildContext context, {String? reason, DemoAgent agent = DemoAgent.main});

  /// [agent]'s calls for the signed-in person, upcoming first. Empty for a help agent the app does not have.
  Future<List<GraineScheduledCall>> scheduledCalls({DemoAgent agent = DemoAgent.main});

  Future<GraineCallStatus> cancelScheduledCall(String id, {DemoAgent agent = DemoAgent.main});

  /// What the screen in front of the person shows (the screen is the app's: every agent hears it).
  void reportScreen(GraineScreen screen);

  /// Register the pack's actions on every agent, all run by [run]. Returns the function that unregisters them.
  void Function() registerActions(UseCasePack pack, ActionRunner run);

  /// False on a platform or build without the native call engine.
  bool get callsSupported;

  /// Configure the call engine (kept natively, so a ring that wakes a closed app works), register for VoIP
  /// pushes on iOS, and attach the agents' context to calls. With the workspace's push credentials uploaded,
  /// the SDK registers this device with Graine and reports what became of every ring itself.
  Future<void> setUpCalls(String callName);

  /// The person calls [agent]: the call's id, or null while another call is live.
  Future<String?> placeCall(String reason, {DemoAgent agent = DemoAgent.main});

  Stream<CallEvent> get callEvents;

  Future<void> startVoice({DemoAgent agent = DemoAgent.main});
  void endVoice({DemoAgent agent = DemoAgent.main});

  /// The help agent's chat window, full size.
  Widget helpChat(String agentName);

  /// The help agent's call engine around [child], with its voice bar above it while [talking].
  Widget helpVoice({required Widget child, required bool talking, required String agentName, required VoidCallback onClose});

  /// The microphone, and on Android 13+ notifications, asked for at sign-in.
  Future<void> requestPermissions();
}

/// A sentence for anything the SDK threw: its typed code mapped to what to do next.
String describeError(Object err, UseCasePack pack, [String? agentName]) {
  final name = agentName ?? pack.agentName;
  if (err is GraineException) {
    final code = err.code == GraineErrorCode.unknown ? (err.codeName ?? 'unknown') : err.code.wire;
    return messageFor(code, err.message, agentName: name, retryAfterSeconds: err.retryAfterSeconds);
  }
  return messageFor('unknown', '$err', agentName: name);
}

/// The name the help agent is configured under.
const String helpAgentName = 'help';

/// The app's agent for the call engine's name: [helpAgentName] is the help agent, the default agent the main one.
DemoAgent? agentOfCall(String? name) => name == null ? null : (name == helpAgentName ? DemoAgent.help : DemoAgent.main);

class SdkGraineService implements GraineService {
  SdkGraineService._(this._main, this._help, this.publishableKey, this._baseUrl);

  /// Start the SDK for the pack's agents: the main one (the default agent) and, with [helpKey], the help one,
  /// named [helpAgentName] (`GraineInApp.init(config, name: 'help')`). Once per run.
  factory SdkGraineService.start({required String baseUrl, required String publishableKey, String? helpKey}) {
    GraineConfig config(String key) => GraineConfig(
          baseUrl: baseUrl,
          publishableKey: key,
          appVersion: '1.0.0',
          debug: kDebugMode,
          // The app asks for the microphone; the SDK grants it to the agent.
          requestMicrophone: () async => (await Permission.microphone.request()).isGranted,
        );
    final main = GraineInApp.init(config(publishableKey));
    final help = helpKey == null || helpKey == publishableKey ? null : GraineInApp.init(config(helpKey), name: helpAgentName);
    return SdkGraineService._(main, help, publishableKey, baseUrl);
  }

  final GraineInApp _main;
  final GraineInApp? _help;
  final String _baseUrl;

  @override
  final String publishableKey;

  @override
  bool get hasHelpAgent => _help != null;

  GraineInApp _of(DemoAgent agent) => agent == DemoAgent.help ? (_help ?? _main) : _main;

  @override
  void signIn(UseCasePack pack, PackUser user, {required bool memory}) => _main.identify(pack.identifyTraits(user), memory: memory);

  @override
  void signOut() => _main.identify(null);

  // Each agent holds its own variables: every one is given them.
  @override
  void setVariables(Map<String, String?> values) {
    _main.setVariables(values);
    _help?.setVariables(values);
  }

  @override
  void setMemoryConsent(bool enabled) => _main.setMemory(enabled);

  @override
  Future<List<GraineMemory>> memories() => _main.memories();

  @override
  Future<int> forgetMemory() => _main.forgetMemory();

  @override
  Future<String> installId() => _main.installId;

  @override
  bool? get agentRemembers => _main.session?.memory?.enabled;

  @override
  Future<void> loadSession() async {
    await _main.loadSession();
    if (_help != null) await _help.loadSession();
  }

  @override
  GraineSchedulingRules rulesOf(DemoAgent agent) => _of(agent).schedulingRules;

  /// For a help agent the app has no key for: never bookable.
  static final ValueListenable<bool> _never = ValueNotifier<bool>(false);

  @override
  ValueListenable<bool> bookingAvailable(DemoAgent agent) =>
      agent == DemoAgent.help && _help == null ? _never : _of(agent).bookingListenable;

  @override
  Future<GraineScheduledCall?> openBooking(BuildContext context, {String? reason, DemoAgent agent = DemoAgent.main}) =>
      GraineBooking.show(context, app: _of(agent), reason: reason);

  @override
  Future<List<GraineScheduledCall>> scheduledCalls({DemoAgent agent = DemoAgent.main}) async {
    if (agent == DemoAgent.help && _help == null) return const <GraineScheduledCall>[];
    return _of(agent).scheduledCalls();
  }

  @override
  Future<GraineCallStatus> cancelScheduledCall(String id, {DemoAgent agent = DemoAgent.main}) => _of(agent).cancelScheduledCall(id);

  @override
  void reportScreen(GraineScreen screen) => _main.setScreen(screen);

  @override
  void Function() registerActions(UseCasePack pack, ActionRunner run) {
    // The help agent is given the same actions.json, so it can open a screen or an item too.
    final agents = <GraineInApp>[_main, if (_help case final h?) h];
    final offs = [
      for (final g in agents)
        for (final a in pack.actions) g.registerAction(a.name, (args) => run(a, args)),
    ];
    return () {
      for (final off in offs) {
        off();
      }
    };
  }

  @override
  bool get callsSupported => GraineCalls.instance.isSupported;

  String? _configuredName;
  void Function()? _voip;
  void Function()? _detach;

  @override
  Future<void> setUpCalls(String callName) async {
    if (!callsSupported) return;
    if (_configuredName != callName) {
      await GraineCalls.instance.configure(GraineCallsOptions(
        publishableKey: publishableKey,
        displayName: callName,
        baseUrl: _baseUrl,
        appVersion: '1.0.0',
        debug: kDebugMode,
      ));
      _configuredName = callName;
    }
    // Hosted ringing: the SDK hands the token to Graine. Logged as a fact, never the token itself.
    _voip ??= GraineCalls.instance.enableVoIPPushes((token) => debugPrint('[Graine Demo] VoIP push token ${token == null ? 'invalidated' : 'ready'}'));
    _detach ??= GraineCalls.instance.attach(_main);
  }

  @override
  Future<String?> placeCall(String reason, {DemoAgent agent = DemoAgent.main}) =>
      GraineCalls.instance.startCall(reason: reason, agent: agent == DemoAgent.help && _help != null ? helpAgentName : null);

  @override
  Stream<CallEvent> get callEvents {
    late final StreamController<CallEvent> out;
    final subs = <StreamSubscription<Object?>>[];
    out = StreamController<CallEvent>.broadcast(
      onListen: () {
        // Each event says which agent the call is with (`agent`): a ring Graine sends comes in as the agent that booked it.
        subs.add(GraineCalls.instance.incomingCalls
            .listen((c) => out.add(CallEvent(c.callId, 'incoming', c.callerName, DateTime.now(), agent: agentOfCall(c.agent)))));
        subs.add(GraineCalls.instance.outcomes
            .listen((o) => out.add(CallEvent(o.callId, o.wire, o.reason, DateTime.now(), agent: agentOfCall(o.agent)))));
      },
      onCancel: () {
        for (final s in subs) {
          unawaited(s.cancel());
        }
        subs.clear();
      },
    );
    return out.stream;
  }

  @override
  Future<void> startVoice({DemoAgent agent = DemoAgent.main}) => _of(agent).voice.start();

  @override
  void endVoice({DemoAgent agent = DemoAgent.main}) => _of(agent).voice.end();

  @override
  Widget helpChat(String agentName) => GraineChat(app: _help, name: agentName);

  @override
  Widget helpVoice({required Widget child, required bool talking, required String agentName, required VoidCallback onClose}) => GraineEngine(
        app: _help,
        child: Stack(children: [
          child,
          if (talking) GraineAgentBar(app: _help, name: agentName, onClose: onClose),
        ]),
      );

  @override
  Future<void> requestPermissions() async {
    try {
      await Permission.microphone.request();
      if (defaultTargetPlatform == TargetPlatform.android) await Permission.notification.request();
    } catch (e) {
      debugPrint('[Graine Demo] Permissions could not be asked: $e');
    }
  }
}
