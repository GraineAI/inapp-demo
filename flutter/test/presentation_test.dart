import 'package:flutter_test/flutter_test.dart';
import 'package:graine_demo/logic/presentation.dart';

void main() {
  // Wednesday 14 October 2026, 18:10:30 local.
  final now = DateTime(2026, 10, 14, 18, 10, 30);

  test('times in plain words', () {
    expect(formatTime(DateTime(2026, 10, 14, 0, 5)), '12:05 am');
    expect(formatTime(DateTime(2026, 10, 14, 12)), '12:00 pm');
    expect(formatTime(DateTime(2026, 10, 14, 18, 30)), '6:30 pm');
    expect(formatDay(DateTime(2026, 10, 14, 9), now), 'Today');
    expect(formatDay(DateTime(2026, 10, 15, 9), now), 'Tomorrow');
    expect(formatDay(DateTime(2026, 10, 13, 9), now), 'Yesterday');
    expect(formatDay(DateTime(2026, 10, 17, 9), now), 'Sat 17 Oct');
    expect(formatWhen(null, now), 'Time not set');
    expect(formatWhen(DateTime(2026, 10, 15, 18), now), 'Tomorrow, 6:00 pm');
  });

  test('call activity names the agent each call was with, when the call engine says', () {
    final at = DateTime(2026, 10, 14, 18, 5);
    expect(callEventLine(at: at, agentName: 'Billing & appointments help', kind: 'ended', detail: 'remote', callId: 'call_1f2e3d4c-aaaa'),
        '6:05 pm · Billing & appointments help · ended (remote) · call_1f2e3d4');
    expect(callEventLine(at: at, kind: 'missed', callId: 'c3'), '6:05 pm · missed · c3');
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
    expect(messageFor('device_limit', 'x', agentName: 'Asha'), 'This device is not one of yours for Asha. An admin can reset your devices in the dashboard.');
    // A booking's refusals are the SDK's booking sheet's to word; anywhere else, the SDK's sentence.
    expect(messageFor('outside_window', 'Outside calling hours.'), 'Outside calling hours.');
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
