import 'package:flutter/material.dart';

import '../graine_service.dart';
import '../state.dart';
import '../ui.dart';
import 'booking_sheet.dart';

/// Help: the pack's SECOND agent (for example "Billing & appointments help" beside Asha). Its chat, voice and
/// calls use the help agent (configured as `GraineInApp.init(config, name: 'help')`), while Home and Chat keep
/// the main one. The person signed in once: both agents know who they are. One conversation at a time: the
/// SDK ends the main agent's live chat or call when this one starts.
class HelpScreen extends StatefulWidget {
  const HelpScreen({super.key});

  @override
  State<HelpScreen> createState() => _HelpScreenState();
}

class _HelpScreenState extends State<HelpScreen> {
  bool _talking = false;
  String? _message;
  String? _error;

  void _talk(DemoState state) {
    state.closeVoice(); // the main agent's bar
    setState(() => _talking = true);
    state.service.startVoice(agent: DemoAgent.help);
  }

  void _stopTalking(DemoState state) {
    state.service.endVoice(agent: DemoAgent.help);
    setState(() => _talking = false);
  }

  Future<void> _call(DemoState state) async {
    setState(() { _message = null; _error = null; });
    try {
      final id = await state.service.placeCall(state.pack.helpAgent.placeReason ?? 'help', agent: DemoAgent.help);
      if (id == null && mounted) setState(() => _error = 'A call is already in progress.');
    } catch (e) {
      if (mounted) setState(() => _error = describeError(e, state.pack, null, state.pack.helpAgent.name));
    }
  }

  Future<void> _book(DemoState state) async {
    setState(() { _message = null; _error = null; });
    final call = await showBookingSheet(context, agent: DemoAgent.help);
    if (call == null || !mounted) return;
    setState(() => _message = 'Booked with ${state.pack.helpAgent.name}. It is listed under ${state.pack.call.title}.');
  }

  @override
  Widget build(BuildContext context) {
    final state = DemoScope.of(context);
    final pack = state.pack;
    final help = pack.helpAgent;
    final theme = Theme.of(context);
    final screen = GraineScreen('help', title: help.name);

    if (!state.service.hasHelpAgent) {
      return Scaffold(
        appBar: AppBar(title: Text(help.name)),
        body: ScreenReport(
          active: true,
          screen: screen,
          child: ListView(padding: const EdgeInsets.all(16), children: [
            Notice('This app has no key for ${help.name} yet. Create the help agent from agent-setup/${pack.id}/help-settings.md, '
                'then add its publishable key to dart_defines.json as GRAINE_HELP_KEY (or one key per use case — see '
                'dart_defines.example.json) and run the app again. Everything else works without it.'),
          ]),
        ),
      );
    }

    final body = Column(children: [
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Text('${help.name}, ${help.role}. You are signed in here too — no need to say who you are.', style: theme.textTheme.bodyMedium),
          const SizedBox(height: 8),
          Row(children: [
            Expanded(child: FilledButton(key: const Key('help-talk'), onPressed: () => _talk(state), child: const Text('Talk'))),
            const SizedBox(width: 8),
            Expanded(
              child: OutlinedButton(key: const Key('help-call'), onPressed: state.service.callsSupported ? () => _call(state) : null, child: const Text('Call')),
            ),
          ]),
          const SizedBox(height: 8),
          OutlinedButton(key: const Key('help-book'), onPressed: () => _book(state), child: Text(help.bookLabel ?? pack.call.bookLabel)),
          if (_message != null) Padding(padding: const EdgeInsets.only(top: 6), child: Text(_message!, key: const Key('help-message'))),
          if (_error != null) ErrorText(_error!),
        ]),
      ),
      const Divider(height: 1),
      Expanded(child: state.service.helpChat(help.name)),
    ]);

    return Scaffold(
      appBar: AppBar(title: Text(help.name)),
      body: ScreenReport(
        active: true,
        screen: screen,
        child: state.service.helpVoice(child: body, talking: _talking, agentName: help.name, onClose: () => _stopTalking(state)),
      ),
    );
  }
}
