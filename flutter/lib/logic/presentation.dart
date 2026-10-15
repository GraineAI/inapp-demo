/// How the app SHOWS things — status words and colours, times, the memory chip, error sentences. No booking
/// or memory rules live here: those are the SDK's (its booking sheet) and the server's.
library;

import '../usecases.dart';

enum Tone { neutral, info, success, warning, danger, muted }

class StatusLook {
  const StatusLook(this.label, this.tone);
  final String label;
  final Tone tone;

  @override
  bool operator ==(Object other) => other is StatusLook && other.label == label && other.tone == tone;

  @override
  int get hashCode => Object.hash(label, tone);

  @override
  String toString() => 'StatusLook($label, $tone)';
}

const Map<String, StatusLook> _chips = <String, StatusLook>{
  'scheduled': StatusLook('Scheduled', Tone.info),
  'ringing': StatusLook('Ringing', Tone.warning),
  'answered': StatusLook('Answered', Tone.success),
  'completed': StatusLook('Completed', Tone.success),
  'declined': StatusLook('Declined', Tone.danger),
  'missed': StatusLook('Missed', Tone.danger),
  'failed': StatusLook('Failed', Tone.danger),
  'cancelled': StatusLook('Cancelled', Tone.muted),
  'expired': StatusLook('Expired', Tone.muted),
  'nodevice': StatusLook('No device', Tone.danger),
};

/// The chip for a status word as the SDK gives it (`no_device` and `noDevice` alike); a word this app does
/// not know is shown as it came.
StatusLook statusChip(String? status) {
  final raw = (status ?? '').trim();
  final known = _chips[raw.replaceAll(RegExp(r'[_\s-]'), '').toLowerCase()];
  if (known != null) return known;
  if (raw.isEmpty) return const StatusLook('Unknown', Tone.neutral);
  final words = raw.replaceAll('_', ' ').replaceAllMapped(RegExp(r'([a-z])([A-Z])'), (m) => '${m[1]} ${m[2]}').toLowerCase();
  return StatusLook(words[0].toUpperCase() + words.substring(1), Tone.neutral);
}

/// Who booked a call, when that is worth saying; null for the person's own booking.
String? sourceLabel(String? source) => switch ((source ?? '').toLowerCase()) {
      'agent' => 'Booked in a conversation',
      'dashboard' || 'api' => 'Booked by the team',
      _ => null,
    };

// ── Times ──────────────────────────────────────────────────────────────────

const List<String> _weekdays = <String>['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const List<String> _months = <String>['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

DateTime _day(DateTime d) => DateTime(d.year, d.month, d.day);
bool _sameDay(DateTime a, DateTime b) => a.year == b.year && a.month == b.month && a.day == b.day;

/// "6:30 pm", on this phone's clock.
String formatTime(DateTime t) {
  final d = t.toLocal();
  final h = d.hour % 12 == 0 ? 12 : d.hour % 12;
  return '$h:${d.minute.toString().padLeft(2, '0')} ${d.hour < 12 ? 'am' : 'pm'}';
}

/// One line of the call activity log: when, which agent the call was with (when the call engine said), what
/// happened and the call's id — "6:05 pm · Asha · ended (remote) · call_1f2e3d4".
String callEventLine({required DateTime at, String? agentName, required String kind, String? detail, required String callId}) =>
    [formatTime(at), if (agentName != null) agentName, detail != null ? '$kind ($detail)' : kind, callId.length > 12 ? callId.substring(0, 12) : callId]
        .join(' · ');

/// "Today", "Tomorrow", "Yesterday", else "Wed 15 Oct".
String formatDay(DateTime t, DateTime now) {
  final d = t.toLocal();
  final today = _day(now.toLocal());
  if (_sameDay(d, today)) return 'Today';
  if (_sameDay(d, today.add(const Duration(days: 1)))) return 'Tomorrow';
  if (_sameDay(d, today.subtract(const Duration(days: 1)))) return 'Yesterday';
  return '${_weekdays[d.weekday - 1]} ${d.day} ${_months[d.month - 1]}';
}

/// "Tomorrow, 9:00 am".
String formatWhen(DateTime? t, DateTime now) => t == null ? 'Time not set' : '${formatDay(t, now)}, ${formatTime(t)}';

// ── Memory ─────────────────────────────────────────────────────────────────

/// The chip on Home and the Memory screen. [agentMemory]: the agent remembers people at all (null until
/// the session is read); [consent]: this person agreed.
({String label, Tone tone, String detail}) memoryChip({required bool? agentMemory, required bool consent}) {
  if (agentMemory == false) return (label: 'Memory off', tone: Tone.muted, detail: 'This agent does not remember conversations.');
  if (!consent) {
    return (label: 'Memory off', tone: Tone.muted, detail: 'You switched memory off. Nothing new is remembered or recalled.');
  }
  return (label: 'Memory on', tone: Tone.success, detail: 'Remembered across chats and calls, for you only.');
}

String memoryEmptyText(UseCasePack pack) {
  final example = pack.memoryExamples.isEmpty ? null : pack.memoryExamples.first.tell;
  return example == null
      ? 'Nothing yet. Tell ${pack.agentName} something in a chat or a call, and it is remembered next time.'
      : 'Nothing yet. Tell ${pack.agentName} something in a chat or a call — for example, “$example” — and it is remembered next time.';
}

// ── Errors ─────────────────────────────────────────────────────────────────

/// The sentence for an error code the SDK reports (WIRE.md codes, as their wire words) when listing or
/// cancelling calls, reading memory or placing a call. A booking's refusals are worded by the SDK's booking
/// sheet; any other code reads as the SDK's own sentence.
String messageFor(String code, String fallback, {String agentName = 'The assistant', int? retryAfterSeconds}) {
  return switch (code) {
    'rate_limited' => retryAfterSeconds != null ? 'Too many requests. Try again in $retryAfterSeconds seconds.' : 'Too many requests. Try again in a minute.',
    'identity_required' => 'Sign in first.',
    'device_limit' => 'This device is not one of yours for $agentName. An admin can reset your devices in the dashboard.',
    'not_found' => 'That no longer exists.',
    'invalid_key' || 'origin_refused' => "The app's publishable key was refused. Check the key, and that the agent's allowed domains include app://.",
    'unavailable' => 'The service is unavailable right now. Try again shortly.',
    'network' => 'Could not connect. Check the connection and try again.',
    'invalid_request' => fallback.isEmpty ? 'That request was not valid.' : fallback,
    _ => fallback.isEmpty ? 'Something went wrong. Try again.' : fallback,
  };
}

// ── Two agents ─────────────────────────────────────────────────────────────

/// A booked call and which agent it is with.
class AgentCall<C> {
  const AgentCall(this.call, this.isHelp);
  final C call;
  final bool isHelp;
}

/// Both agents' calls in one list: upcoming first, soonest first; then the rest, most recent first. Which are
/// upcoming is the SDK's `isUpcoming`; this only orders and labels them.
List<AgentCall<C>> mergeAgentCalls<C>(List<C> main, List<C> help, {required DateTime? Function(C) at, required bool Function(C) upcoming}) {
  final all = <AgentCall<C>>[for (final c in main) AgentCall(c, false), for (final c in help) AgentCall(c, true)];
  int t(AgentCall<C> x) => at(x.call)?.millisecondsSinceEpoch ?? 0;
  final next = all.where((x) => upcoming(x.call)).toList()..sort((a, b) => t(a).compareTo(t(b)));
  final rest = all.where((x) => !upcoming(x.call)).toList()..sort((a, b) => t(b).compareTo(t(a)));
  return [...next, ...rest];
}
