import 'package:flutter/material.dart';

import '../graine_service.dart';
import '../logic/presentation.dart';
import '../state.dart';
import '../ui.dart';

/// Open the booking sheet for [agent]. Completes with the booked call, or null when the person closed it.
Future<GraineScheduledCall?> showBookingSheet(BuildContext context, {DemoAgent agent = DemoAgent.main}) =>
    showModalBottomSheet<GraineScheduledCall>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (_) => BookingSheet(agent: agent),
    );

/// The booking sheet: a reason, a one-tap time or the platform's date and time pickers (5-minute steps,
/// bounded by the SDK's earliest and latest), the time zone it is booked in, then scheduleCall(at, reason).
/// The SDK checks the booking; this sheet shows its answer in plain words.
class BookingSheet extends StatefulWidget {
  const BookingSheet({super.key, this.agent = DemoAgent.main, this.clock});

  /// Who books it: the main agent (its `call` button and reasons) or the help agent (`agents.help`).
  final DemoAgent agent;

  /// For tests: what "now" is.
  final DateTime Function()? clock;

  @override
  State<BookingSheet> createState() => _BookingSheetState();
}

class _BookingSheetState extends State<BookingSheet> {
  late final DateTime _now = (widget.clock ?? DateTime.now)();
  String? _reason;
  DateTime? _at;
  bool _busy = false;
  String? _error;

  Future<void> _pickDateAndTime(PickerRange range) async {
    final first = range.min;
    final start = _at ?? first;
    final day = await showDatePicker(
      context: context,
      initialDate: start,
      firstDate: DateTime(first.year, first.month, first.day),
      lastDate: range.max,
      helpText: 'Day of the call',
    );
    if (day == null || !mounted) return;
    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(start),
      helpText: 'Time of the call (5-minute steps)',
    );
    if (time == null || !mounted) return;
    setState(() => _at = snapToRange(DateTime(day.year, day.month, day.day, time.hour, time.minute), range));
  }

  Future<void> _book(DemoState state, String reason) async {
    final at = _at;
    if (at == null) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final call = await state.service.scheduleCall(at, reason, agent: widget.agent);
      if (mounted) Navigator.of(context).pop(call);
    } catch (e) {
      if (mounted) setState(() => _error = describeError(e, state.pack, state.service.rulesOf(widget.agent), _agentName(state)));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  bool get _help => widget.agent == DemoAgent.help;
  String _agentName(DemoState state) => _help ? state.pack.helpAgent.name : state.pack.agentName;

  @override
  Widget build(BuildContext context) {
    final state = DemoScope.of(context);
    final pack = state.pack;
    final rules = state.service.rulesOf(widget.agent);
    final range = pickerRange(rules.earliest(_now), rules.latest(_now));
    final reasons = _help ? pack.helpAgent.reasons : pack.call.reasons;
    final bookLabel = _help ? (pack.helpAgent.bookLabel ?? pack.call.bookLabel) : pack.call.bookLabel;
    final agentName = _agentName(state);
    final reason = _reason ?? reasons.first;
    final zone = state.service.timeZoneLabel;
    final theme = Theme.of(context);

    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: ListView(
        shrinkWrap: true,
        padding: const EdgeInsets.fromLTRB(16, 20, 16, 32),
        children: [
          Text(bookLabel, style: theme.textTheme.headlineSmall),
          const SizedBox(height: 4),
          Text('$agentName calls this phone at the time you pick. It rings like a phone call, even when the app is closed.',
              style: theme.textTheme.bodyMedium),
          if (!rules.usersCanBook)
            Notice('$agentName does not take bookings from the app. In the dashboard: Calls & scheduling → Who may book a call → Users, from your app.'),
          const SectionLabel('Reason'),
          Wrap(spacing: 8, runSpacing: 8, children: [
            for (final r in reasons)
              ChoiceChip(key: Key('reason-$r'), label: Text(r), selected: r == reason, onSelected: (_) => setState(() => _reason = r)),
          ]),
          const SectionLabel('When'),
          Wrap(spacing: 8, runSpacing: 8, children: [
            for (final q in pack.call.quickTimes)
              Builder(builder: (context) {
                final at = quickTimeAt(q, _now);
                final ok = inRange(at, range);
                return ChoiceChip(
                  key: Key('quick-${q.label}'),
                  label: Text(q.label),
                  selected: _at == at,
                  onSelected: ok ? (_) => setState(() => _at = at) : null,
                );
              }),
          ]),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            key: const Key('pick-date-time'),
            onPressed: range == null ? null : () => _pickDateAndTime(range),
            icon: const Icon(Icons.event),
            label: const Text('Pick a date and time'),
          ),
          const SizedBox(height: 16),
          Semantics(
            liveRegion: true,
            child: Text(_at == null ? 'Times are in $zone.' : zoneLabel(_at!, zone, _now),
                key: const Key('booking-summary'), style: theme.textTheme.titleMedium),
          ),
          const SizedBox(height: 16),
          FilledButton(
            key: const Key('book'),
            onPressed: _at == null || _busy || !rules.usersCanBook ? null : () => _book(state, reason),
            child: Text(_at == null ? 'Pick a time' : 'Book for ${formatTime(_at!)}'),
          ),
          TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Not now')),
          if (_error != null) ErrorText(_error!),
        ],
      ),
    );
  }
}
