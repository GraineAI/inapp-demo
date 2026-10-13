import 'package:flutter_test/flutter_test.dart';
import 'package:graine_demo/logic/presentation.dart';
import 'package:graine_demo/usecases.dart';

void main() {
  // Wednesday 14 October 2026, 18:10:30 local; the SDK's bounds for 5 minutes' notice and 14 days.
  final now = DateTime(2026, 10, 14, 18, 10, 30);
  final earliest = now.add(const Duration(minutes: 5));
  final latest = now.add(const Duration(days: 14));

  test('times in plain words', () {
    expect(formatTime(DateTime(2026, 10, 14, 0, 5)), '12:05 am');
    expect(formatTime(DateTime(2026, 10, 14, 12)), '12:00 pm');
    expect(formatTime(DateTime(2026, 10, 14, 18, 30)), '6:30 pm');
    expect(formatDay(DateTime(2026, 10, 14, 9), now), 'Today');
    expect(formatDay(DateTime(2026, 10, 15, 9), now), 'Tomorrow');
    expect(formatDay(DateTime(2026, 10, 13, 9), now), 'Yesterday');
    expect(formatDay(DateTime(2026, 10, 17, 9), now), 'Sat 17 Oct');
    expect(formatWhen(null, now), 'Time not set');
    expect(zoneLabel(DateTime(2026, 10, 15, 18), 'Asia/Kolkata', now), 'Asia/Kolkata · Tomorrow, 6:00 pm');
  });

  test('the pickers stay on the 5-minute grid, inside the SDK bounds', () {
    expect(formatTime(roundUpToStep(DateTime(2026, 10, 14, 18, 15, 30), 5)), '6:20 pm');
    expect(formatTime(roundUpToStep(DateTime(2026, 10, 14, 18, 15), 5)), '6:15 pm');
    final range = pickerRange(earliest, latest)!;
    expect(formatTime(range.min), '6:20 pm');
    expect(pickerRange(latest, earliest), isNull);
    expect(snapToRange(DateTime(2026, 10, 14, 9), range), range.min);
    expect(formatTime(snapToRange(DateTime(2026, 10, 16, 9, 2), range)), '9:05 am');
    final last = snapToRange(DateTime(2026, 11, 30, 9), range);
    expect(last.isAfter(latest), isFalse);
    expect(last.minute % 5, 0);
  });

  test('quick times become moments; those outside the range are marked', () {
    final range = pickerRange(earliest, latest);
    expect(formatWhen(quickTimeAt(const QuickTime(label: 'In 15 minutes', inMinutes: 15), now), now), 'Today, 6:26 pm');
    expect(formatWhen(quickTimeAt(const QuickTime(label: 'Tomorrow 10:00', day: 'tomorrow', time: '10:00'), now), now), 'Tomorrow, 10:00 am');
    expect(inRange(quickTimeAt(const QuickTime(label: 'Today 09:00', day: 'today', time: '09:00'), now), range), isFalse);
    expect(inRange(quickTimeAt(const QuickTime(label: 'Today 19:00', day: 'today', time: '19:00'), now), range), isTrue);
  });

  test('status chips name every status, whichever spelling', () {
    for (final s in ['scheduled', 'ringing', 'answered', 'completed', 'declined', 'missed', 'expired', 'failed', 'cancelled']) {
      expect(statusChip(s).label.toLowerCase(), s);
    }
    expect(statusChip('no_device'), const StatusLook('No device', Tone.danger));
    expect(statusChip('noDevice'), const StatusLook('No device', Tone.danger));
    expect(statusChip('answered').tone, Tone.success);
    expect(statusChip('rescheduled_later'), const StatusLook('Rescheduled later', Tone.neutral));
    expect(statusChip(null).label, 'Unknown');
    expect(sourceLabel('agent'), 'Booked in a conversation');
    expect(sourceLabel('dashboard'), 'Booked by the team');
    expect(sourceLabel('sdk'), isNull);
  });

  test('memory chip and error sentences', () {
    expect(memoryChip(agentMemory: true, consent: true).label, 'Memory on');
    expect(memoryChip(agentMemory: null, consent: true).label, 'Memory on');
    expect(memoryChip(agentMemory: true, consent: false).label, 'Memory off');
    expect(memoryChip(agentMemory: false, consent: true).detail, 'This agent does not remember conversations.');
    expect(messageFor('too_soon', 'x', minLeadMinutes: 10), 'Pick a time at least 10 minutes from now.');
    expect(messageFor('outside_window', 'x', agentName: 'Asha'), "That time is outside Asha's calling hours. Pick another time.");
    expect(messageFor('rate_limited', 'x', retryAfterSeconds: 30), 'Too many requests. Try again in 30 seconds.');
    expect(messageFor('something_new', 'Server words.'), 'Server words.');
  });

  test("both agents' calls: upcoming first by time, then the rest newest first, each labelled", () {
    DateTime at(int h) => DateTime(2026, 10, 15, h);
    final main = [(id: 'm1', at: at(18), up: true), (id: 'm2', at: at(9), up: false)];
    final help = [(id: 'h1', at: at(10), up: true), (id: 'h2', at: at(11), up: false)];
    final merged = mergeAgentCalls(main, help, at: (c) => c.at, upcoming: (c) => c.up);
    expect(merged.map((x) => '${x.isHelp ? 'help' : 'main'}:${x.call.id}'), ['help:h1', 'main:m1', 'help:h2', 'main:m2']);
  });
}
