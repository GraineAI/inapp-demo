import 'package:flutter/material.dart';

import '../graine_service.dart';
import '../logic/presentation.dart';
import '../state.dart';
import '../ui.dart';

/// Scheduled calls: what is booked with either agent and what became of each ring (status chips, the agent's
/// name, Cancel where the SDK says canCancel), the pack's booking button (the SDK's booking sheet), and the
/// call engine's own log of rings and outcomes. The calls, their rules and every check come from the SDK;
/// this screen only presents them.
class CallsScreen extends StatefulWidget {
  const CallsScreen({super.key, required this.active});

  final bool active;

  @override
  State<CallsScreen> createState() => _CallsScreenState();
}

class _CallsScreenState extends State<CallsScreen> {
  List<AgentCall<GraineScheduledCall>> _calls = const <AgentCall<GraineScheduledCall>>[];
  bool _loading = true;
  String? _loadError;
  String? _message;
  String? _error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  @override
  void didUpdateWidget(CallsScreen old) {
    super.didUpdateWidget(old);
    if (widget.active && !old.active) _load();
  }

  Future<void> _load() async {
    if (!mounted) return;
    final state = DemoScope.of(context);
    setState(() => _loading = true);
    try {
      // Each agent's bookings live with that agent: read from both, listed together.
      final main = await state.service.scheduledCalls();
      final help = await state.service.scheduledCalls(agent: DemoAgent.help);
      final merged = mergeAgentCalls<GraineScheduledCall>(main, help, at: (c) => c.at, upcoming: (c) => c.isUpcoming);
      if (mounted) setState(() { _calls = merged; _loadError = null; });
    } catch (e) {
      if (mounted) setState(() => _loadError = describeError(e, state.pack));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  /// The pack's booking button: the SDK's booking sheet, starting with the pack's reason; then the list again.
  Future<void> _book(DemoState state) async {
    setState(() { _message = null; _error = null; });
    final call = await state.service.openBooking(context, reason: state.pack.call.bookingReason);
    if (call == null || !mounted) return;
    setState(() => _message = 'Call with ${state.pack.agentName} booked for ${formatWhen(call.at, DateTime.now())}.');
    await _load();
  }

  Future<void> _cancel(DemoState state, AgentCall<GraineScheduledCall> x) async {
    setState(() { _message = null; _error = null; });
    final agent = x.isHelp ? DemoAgent.help : DemoAgent.main;
    try {
      final status = await state.service.cancelScheduledCall(x.call.id, agent: agent);
      if (mounted) setState(() => _message = 'Call ${statusChip(status.wire).label.toLowerCase()}.');
      await _load();
    } catch (e) {
      if (mounted) setState(() => _error = describeError(e, state.pack));
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = DemoScope.of(context);
    final pack = state.pack;
    final theme = Theme.of(context);
    final now = DateTime.now();
    final upcoming = _calls.where((x) => x.call.isUpcoming).toList();
    final past = _calls.where((x) => !x.call.isUpcoming).toList();
    String nameOf(AgentCall<GraineScheduledCall> x) => x.isHelp ? pack.helpAgent.name : pack.agentName;

    return ScreenReport(
      active: widget.active,
      screen: GraineScreen('calls', title: pack.call.title, fields: [
        GraineField('upcoming', label: 'Upcoming calls', value: '${upcoming.length}', status: FieldStatus.filled),
      ]),
      child: RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 140),
          children: [
            Text('${pack.agentName} calls this phone at the time you book. It rings like a phone call, even when the app is closed.',
                style: theme.textTheme.bodyMedium),
            const SizedBox(height: 12),
            BookingButton(
              buttonKey: const Key('open-booking'),
              label: pack.call.bookLabel,
              available: state.service.bookingAvailable(DemoAgent.main),
              onPressed: () => _book(state),
            ),
            if (_message != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text(_message!, key: const Key('calls-message'))),
            if (_error != null) ErrorText(_error!),
            const SectionLabel('Upcoming'),
            if (_loadError != null) ErrorText(_loadError!),
            if (!_loading && upcoming.isEmpty && _loadError == null) const Text('Nothing booked.'),
            for (final x in upcoming) _CallCard(call: x.call, agentName: nameOf(x), now: now, onCancel: x.call.canCancel ? () => _cancel(state, x) : null),
            if (past.isNotEmpty) const SectionLabel('Earlier'),
            for (final x in past) _CallCard(call: x.call, agentName: nameOf(x), now: now),
            const SectionLabel('Call activity on this phone'),
            if (state.callLog.isEmpty) Text('Rings and their outcomes appear here.', style: theme.textTheme.bodySmall),
            for (final e in state.callLog)
              Text(
                  callEventLine(
                      at: e.at,
                      agentName: switch (e.agent) { DemoAgent.help => pack.helpAgent.name, DemoAgent.main => pack.agentName, null => null },
                      kind: e.kind,
                      detail: e.detail,
                      callId: e.callId),
                  style: theme.textTheme.bodySmall),
          ],
        ),
      ),
    );
  }
}

class _CallCard extends StatelessWidget {
  const _CallCard({required this.call, required this.agentName, required this.now, this.onCancel});

  final GraineScheduledCall call;
  final String agentName;
  final DateTime now;
  final VoidCallback? onCancel;

  @override
  Widget build(BuildContext context) {
    final look = statusChip(call.statusWire);
    final by = sourceLabel(call.sourceName);
    return Card(
      key: Key('call-${call.id}'),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Expanded(child: Text(formatWhen(call.at, now), style: Theme.of(context).textTheme.titleMedium)),
            ToneChip(look.label, tone: look.tone),
          ]),
          Text(call.reason == null ? agentName : '$agentName · ${call.reason}'),
          if (by != null) Text(by, style: Theme.of(context).textTheme.bodySmall),
          if (onCancel != null)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: OutlinedButton(key: Key('cancel-${call.id}'), onPressed: onCancel, child: const Text('Cancel call')),
            ),
        ]),
      ),
    );
  }
}
