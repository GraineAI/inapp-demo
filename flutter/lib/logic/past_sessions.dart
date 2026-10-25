/// PAST SESSIONS: the signed-in user's earlier sessions with an agent — each chat, voice conversation and call,
/// newest first, like the dashboard's history — and one session's transcript. The SDK keeps them
/// (`chat.pastSessions`, `refreshPastSessions()`, `loadMorePastSessions()`, `sessionTranscript(id)`); these are the
/// app's view of that state and how it reads, so the screen and its tests read the same thing. No SDK types here:
/// graine_service.dart turns the SDK's into these. Contract: sdk/contract/past-sessions.json.
library;

/// One earlier session, as the list shows it.
class PastSession {
  const PastSession({
    required this.id,
    this.startedAt,
    this.endedAt,
    this.durationSeconds,
    required this.channel,
    this.direction,
    this.status,
    this.turns = 0,
    this.preview,
  });

  /// The session's id (the execution id the dashboard shows).
  final String id;
  final DateTime? startedAt;
  final DateTime? endedAt;
  final int? durationSeconds;

  /// `chat`, `voice` or `call`.
  final String channel;

  /// `inbound`, `outbound`, or null when it has none (a chat).
  final String? direction;

  /// How it ended, in the SDK's word (`completed`, …).
  final String? status;

  /// How many messages it holds.
  final int turns;

  /// Its first words, for the row.
  final String? preview;

  @override
  bool operator ==(Object other) =>
      other is PastSession &&
      other.id == id &&
      other.startedAt == startedAt &&
      other.endedAt == endedAt &&
      other.durationSeconds == durationSeconds &&
      other.channel == channel &&
      other.direction == direction &&
      other.status == status &&
      other.turns == turns &&
      other.preview == preview;

  @override
  int get hashCode => Object.hash(id, startedAt, endedAt, durationSeconds, channel, direction, status, turns, preview);

  @override
  String toString() => 'PastSession($id, $channel, $status)';
}

/// The list as the SDK holds it: the sessions loaded so far, newest first, and where loading stands.
class PastSessionsState {
  const PastSessionsState({this.sessions = const <PastSession>[], this.loading = false, this.hasMore = false, this.error});

  final List<PastSession> sessions;
  final bool loading;
  final bool hasMore;

  /// Why the last load failed: `identity_required`, `device_limit`, `rate_limited`, `unavailable`, or another word.
  final String? error;

  @override
  bool operator ==(Object other) =>
      other is PastSessionsState &&
      other.loading == loading &&
      other.hasMore == hasMore &&
      other.error == error &&
      _same(other.sessions, sessions);

  @override
  int get hashCode => Object.hash(Object.hashAll(sessions), loading, hasMore, error);

  @override
  String toString() => 'PastSessionsState(${sessions.length} sessions${loading ? ', loading' : ''}${hasMore ? ', more' : ''}${error == null ? '' : ', $error'})';
}

/// One line of a transcript.
class TranscriptTurn {
  const TranscriptTurn({required this.role, required this.text, this.at});

  /// `customer` or `agent`.
  final String role;
  final String text;
  final DateTime? at;

  @override
  bool operator ==(Object other) => other is TranscriptTurn && other.role == role && other.text == text && other.at == at;

  @override
  int get hashCode => Object.hash(role, text, at);

  @override
  String toString() => 'TranscriptTurn($role, "$text")';
}

/// One session in full, oldest line first: read only.
class SessionTranscript {
  const SessionTranscript({
    required this.id,
    this.startedAt,
    this.endedAt,
    this.durationSeconds,
    required this.channel,
    this.direction,
    this.status,
    this.turns = const <TranscriptTurn>[],
  });

  final String id;
  final DateTime? startedAt;
  final DateTime? endedAt;
  final int? durationSeconds;
  final String channel;
  final String? direction;
  final String? status;
  final List<TranscriptTurn> turns;

  @override
  bool operator ==(Object other) =>
      other is SessionTranscript &&
      other.id == id &&
      other.startedAt == startedAt &&
      other.endedAt == endedAt &&
      other.durationSeconds == durationSeconds &&
      other.channel == channel &&
      other.direction == direction &&
      other.status == status &&
      _same(other.turns, turns);

  @override
  int get hashCode => Object.hash(id, startedAt, endedAt, durationSeconds, channel, direction, status, Object.hashAll(turns));

  @override
  String toString() => 'SessionTranscript($id, ${turns.length} turns)';
}

/// How past sessions read in the app.
abstract final class PastSessionsText {
  /// The list's empty and loading lines.
  static const String empty = 'No past sessions yet. A session appears here once it has closed.';
  static const String loading = 'Loading past sessions…';

  /// Why the list did not load, in plain words: the two the SDK names for this list first, then the rest.
  static String? error(String? code) => switch (code) {
        null || '' => null,
        'identity_required' => 'Sign in to see past sessions.',
        'device_limit' => 'Not available on this device.',
        'rate_limited' => 'Too many requests. Try again in a few minutes.',
        _ => 'Past sessions could not be loaded. Try again.',
      };

  /// A refused list offers Try again, except when signing in or another device is the answer.
  static bool canRetry(String? code) => code != 'identity_required' && code != 'device_limit';

  /// Why a transcript did not open: an unknown session, or another user's, is `not_found`; the rest as for the list.
  static String? transcriptError(String? code) => code == 'not_found' ? 'That session is not available.' : error(code);

  /// "Chat", "Voice", "Call · outbound".
  static String kind(String channel, String? direction) {
    final base = switch (channel.toLowerCase()) {
      'chat' => 'Chat',
      'voice' => 'Voice',
      'call' => 'Call',
      _ => channel.isEmpty ? channel : channel[0].toUpperCase() + channel.substring(1),
    };
    return direction == null || direction.trim().isEmpty || direction == 'none' ? base : '$base · $direction';
  }

  /// How it ended, as a word on a chip: the SDK's word, readable.
  static String status(String? status) {
    if (status == null || status.trim().isEmpty) return 'Ended';
    final words = status.replaceAll('_', ' ');
    return words[0].toUpperCase() + words.substring(1);
  }

  /// "4 messages", "1 message".
  static String messages(int turns) => turns == 1 ? '1 message' : '$turns messages';

  /// A session's length: "4:36", "1:02:03", or null when it is not known.
  static String? length(int? seconds) {
    if (seconds == null || seconds < 0) return null;
    final h = seconds ~/ 3600;
    final m = (seconds % 3600) ~/ 60;
    final s = (seconds % 60).toString().padLeft(2, '0');
    return h > 0 ? '$h:${m.toString().padLeft(2, '0')}:$s' : '$m:$s';
  }

  /// The row's second line: kind · length · messages.
  static String summary(PastSession s) => [kind(s.channel, s.direction), length(s.durationSeconds), messages(s.turns)].whereType<String>().join(' · ');

  /// The transcript's subtitle: kind · length · how it ended.
  static String transcriptSummary(SessionTranscript t) =>
      [kind(t.channel, t.direction), length(t.durationSeconds), status(t.status)].whereType<String>().join(' · ');

  /// Whose line it is, for a transcript bubble.
  static bool isCustomer(TranscriptTurn turn) => const {'customer', 'user'}.contains(turn.role.toLowerCase());

  /// A session id as a row shows it: enough to tell two apart, never the whole of it (Copy takes all of it).
  static String shortId(String? id) {
    if (id == null || id.isEmpty) return '—';
    return id.length <= 8 ? id : '…${id.substring(id.length - 6)}';
  }
}

bool _same<T>(List<T> a, List<T> b) {
  if (identical(a, b)) return true;
  if (a.length != b.length) return false;
  for (var i = 0; i < a.length; i++) {
    if (a[i] != b[i]) return false;
  }
  return true;
}
