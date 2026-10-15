import 'package:flutter/material.dart';

import '../graine_service.dart';
import '../logic/presentation.dart';
import '../state.dart';
import '../ui.dart';
import '../usecases.dart';
import 'help_screen.dart';

/// Home: who is signed in, whether they are remembered, the next booked call, and the ways to reach the agent.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.active});

  final bool active;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  GraineScheduledCall? _next;
  bool _nextIsHelp = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  @override
  void didUpdateWidget(HomeScreen old) {
    super.didUpdateWidget(old);
    if (widget.active && !old.active) _load();
  }

  Future<void> _load() async {
    if (!mounted) return;
    final state = DemoScope.of(context);
    try {
      // The next call with either agent.
      final main = await state.service.scheduledCalls();
      final help = await state.service.scheduledCalls(agent: DemoAgent.help);
      final next = mergeAgentCalls<GraineScheduledCall>(main, help, at: (c) => c.at, upcoming: (c) => c.isUpcoming).where((x) => x.call.isUpcoming).firstOrNull;
      if (mounted) setState(() { _next = next?.call; _nextIsHelp = next?.isHelp ?? false; });
    } catch (_) {
      if (mounted) setState(() => _next = null); // the Calls screen says why
    }
  }

  Future<void> _call(DemoState state) async {
    setState(() => _error = null);
    try {
      final id = await state.service.placeCall(state.pack.call.placeReason);
      if (id == null && mounted) setState(() => _error = 'A call is already in progress.');
    } catch (e) {
      if (mounted) setState(() => _error = describeError(e, state.pack));
    }
  }

  /// The pack's booking button: the SDK's booking sheet, starting with the pack's reason; then the next call again.
  Future<void> _book(DemoState state) async {
    setState(() => _error = null);
    final call = await state.service.openBooking(context, reason: state.pack.call.bookingReason);
    if (call == null || !mounted) return;
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    final state = DemoScope.of(context);
    final pack = state.pack;
    final theme = Theme.of(context);
    final chip = memoryChip(agentMemory: state.service.agentRemembers, consent: state.consent);
    final now = DateTime.now();

    return ScreenReport(
      active: widget.active,
      screen: GraineScreen('home', title: pack.homeTitle, fields: [
        GraineField('memory', label: 'Memory', value: chip.label == 'Memory on' ? 'on' : 'off', status: FieldStatus.filled),
        GraineField('next_call', label: 'Next call', value: _next?.at?.toUtc().toIso8601String() ?? 'none', status: FieldStatus.filled),
      ]),
      child: RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 140),
          children: [
            Text('Hello, ${state.user?.firstName ?? ''}', style: theme.textTheme.headlineSmall),
            const SizedBox(height: 8),
            Row(children: [
              InkWell(
                key: const Key('memory-chip'),
                onTap: () => state.go(ScreenKey.memory),
                child: ToneChip(chip.label, tone: chip.tone),
              ),
              const SizedBox(width: 8),
              Expanded(child: Text(chip.detail, style: theme.textTheme.bodySmall)),
            ]),
            const SizedBox(height: 12),
            if (pack.notice != null) Notice(pack.notice!),
            Card(
              child: ListTile(
                title: const Text('Next call'),
                subtitle: Text(_next == null
                    ? 'No calls booked. ${pack.agentName} can call you at a time you choose.'
                    : '${formatWhen(_next!.at, now)}\n${_nextIsHelp ? pack.helpAgent.name : pack.agentName}${_next!.reason != null ? ' · ${_next!.reason}' : ''}'),
                onTap: () => state.go(ScreenKey.calls),
              ),
            ),
            const SizedBox(height: 8),
            FilledButton(key: const Key('talk'), onPressed: state.openVoice, child: Text('Talk to ${pack.agentName}')),
            const SizedBox(height: 8),
            OutlinedButton(onPressed: () => state.go(ScreenKey.chat), child: const Text('Chat')),
            const SizedBox(height: 8),
            OutlinedButton(
              key: const Key('call'),
              onPressed: state.service.callsSupported ? () => _call(state) : null,
              child: Text('Call ${pack.agentName}'),
            ),
            const SizedBox(height: 8),
            BookingButton(
              buttonKey: const Key('book-from-home'),
              label: pack.call.bookLabel,
              available: state.service.bookingAvailable(DemoAgent.main),
              onPressed: () => _book(state),
              filled: false,
            ),
            const SizedBox(height: 8),
            OutlinedButton(
              key: const Key('open-help'),
              onPressed: () {
                state.closeVoice();
                Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => const HelpScreen(), settings: const RouteSettings(name: 'help')));
              },
              child: Text('Help · ${pack.helpAgent.name}'),
            ),
            if (!state.service.callsSupported)
              Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Text('Calls need the iOS or Android app.', style: theme.textTheme.bodySmall),
              ),
            if (_error != null) ErrorText(_error!),
            SectionLabel(pack.homeTitle),
            for (final card in pack.homeCards)
              Card(
                child: ListTile(
                  title: Text(card.title, style: const TextStyle(fontWeight: FontWeight.w600)),
                  subtitle: Text(card.body),
                  onTap: () => state.go(card.target),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
