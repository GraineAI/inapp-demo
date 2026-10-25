import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../graine_service.dart';
import '../logic/presentation.dart';
import '../state.dart';
import '../ui.dart';

/// Open Past sessions for [agent] over the screen it was asked from (the Chat tab, or Help).
Future<void> openPastSessions(BuildContext context, DemoAgent agent) => Navigator.of(context).push(
      MaterialPageRoute<void>(builder: (_) => PastSessionsScreen(agent: agent), settings: const RouteSettings(name: 'past-sessions')),
    );

/// The agent's name as the screens show it.
String _nameOf(DemoState state, DemoAgent agent) => agent == DemoAgent.help ? state.pack.helpAgent.name : state.pack.agentName;

/// The wire's word for why the SDK refused, when it said one.
String? _codeOf(Object e) => e is GraineException ? (e.code == GraineErrorCode.unknown ? e.codeName : e.code.wire) : null;

void _copy(BuildContext context, String id) {
  Clipboard.setData(ClipboardData(text: id));
  ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Session id copied.')));
}

/// PAST SESSIONS, DRAWN BY THE APP — the signed-in user's closed sessions with the agent, newest first, from the
/// SDK's `chat.pastSessions`: when, chat or call, how long, how it ended, how many messages, the first words, and
/// the session id shortened with Copy. The next page loads at the end of the list (`loadMorePastSessions()`);
/// empty, loading and refused lists say so in plain words. A tap opens the session's transcript
/// (`sessionTranscript(id)`), read only.
class PastSessionsScreen extends StatefulWidget {
  const PastSessionsScreen({super.key, required this.agent});

  final DemoAgent agent;

  @override
  State<PastSessionsScreen> createState() => _PastSessionsScreenState();
}

class _PastSessionsScreenState extends State<PastSessionsScreen> {
  /// The session whose transcript is on its way.
  String? _opening;

  /// Why the last transcript could not be opened, in plain words.
  String? _transcriptError;

  /// A next page asked for at the end of the list, not yet sent.
  bool _endQueued = false;

  @override
  void initState() {
    super.initState();
    // The first page again, newest first.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) DemoScope.of(context).service.refreshPastSessions(agent: widget.agent);
    });
  }

  /// The next page, from Load more or Try again: only while there is more and nothing is on its way.
  void _loadMore() {
    final service = DemoScope.of(context).service;
    final s = service.pastSessions(widget.agent).value;
    if (s.loading || !s.hasMore) return;
    service.loadMorePastSessions(agent: widget.agent);
  }

  /// The end of the list is in view: the next page, while there is one, nothing is on its way and nothing failed.
  void _atEnd() {
    if (_endQueued) return;
    _endQueued = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _endQueued = false;
      if (!mounted) return;
      final s = DemoScope.of(context).service.pastSessions(widget.agent).value;
      if (s.hasMore && !s.loading && s.error == null && s.sessions.isNotEmpty) _loadMore();
    });
  }

  Future<void> _open(PastSession session) async {
    final state = DemoScope.of(context);
    setState(() {
      _opening = session.id;
      _transcriptError = null;
    });
    try {
      final transcript = await state.service.sessionTranscript(session.id, agent: widget.agent);
      if (!mounted || _opening != session.id) return;
      setState(() => _opening = null);
      await Navigator.of(context).push(MaterialPageRoute<void>(
        builder: (_) => TranscriptScreen(agent: widget.agent, transcript: transcript),
        settings: const RouteSettings(name: 'past-session'),
      ));
    } catch (e) {
      if (!mounted || _opening != session.id) return;
      setState(() {
        _opening = null;
        _transcriptError = PastSessionsText.transcriptError(_codeOf(e)) ?? describeError(e, state.pack);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = DemoScope.of(context);
    final service = state.service;
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Past sessions'),
        actions: [
          IconButton(
            key: const Key('past-sessions-refresh'),
            tooltip: 'Refresh',
            icon: const Icon(Icons.refresh),
            onPressed: () => service.refreshPastSessions(agent: widget.agent),
          ),
        ],
      ),
      body: Stack(children: [
        ValueListenableBuilder<PastSessionsState>(
          valueListenable: service.pastSessions(widget.agent),
          builder: (context, s, _) => RefreshIndicator(
            onRefresh: () => service.refreshPastSessions(agent: widget.agent),
            child: ListView.builder(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
              itemCount: 2 + (s.sessions.isEmpty ? 0 : s.sessions.length),
              itemBuilder: (context, i) {
                if (i == 0) {
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: Text('With ${_nameOf(state, widget.agent)} · newest first · drawn by this app', style: theme.textTheme.bodySmall),
                  );
                }
                if (s.sessions.isEmpty) return _emptyState(context, s);
                final j = i - 1;
                if (j < s.sessions.length) {
                  final session = s.sessions[j];
                  return _SessionRow(
                    session: session,
                    opening: _opening == session.id,
                    onOpen: () => _open(session),
                    onCopy: () => _copy(context, session.id),
                  );
                }
                return _footer(context, s);
              },
            ),
          ),
        ),
        if (_transcriptError != null)
          Positioned(
            left: 16,
            right: 16,
            bottom: 16,
            child: Material(
              key: const Key('transcript-error'),
              color: theme.colorScheme.errorContainer,
              borderRadius: BorderRadius.circular(10),
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Semantics(liveRegion: true, child: Text(_transcriptError!, style: TextStyle(color: theme.colorScheme.onErrorContainer))),
              ),
            ),
          ),
      ]),
    );
  }

  /// Nothing listed: why not, still loading, or none yet.
  Widget _emptyState(BuildContext context, PastSessionsState s) {
    final theme = Theme.of(context);
    final words = PastSessionsText.error(s.error);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 24),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        if (words != null) ...[
          Text(words, key: const Key('past-sessions-error'), style: theme.textTheme.bodyLarge),
          if (PastSessionsText.canRetry(s.error))
            Padding(
              padding: const EdgeInsets.only(top: 12),
              child: OutlinedButton(onPressed: () => DemoScope.of(context).service.refreshPastSessions(agent: widget.agent), child: const Text('Try again')),
            ),
        ] else if (s.loading)
          Row(children: [
            const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)),
            const SizedBox(width: 10),
            Text(PastSessionsText.loading, style: theme.textTheme.bodyMedium),
          ])
        else
          Text(PastSessionsText.empty, key: const Key('past-sessions-empty'), style: theme.textTheme.bodyMedium?.copyWith(color: theme.hintColor)),
      ]),
    );
  }

  /// Under the list: the next page on its way, why it did not come, more to load, or the end.
  Widget _footer(BuildContext context, PastSessionsState s) {
    final theme = Theme.of(context);
    final Widget child;
    if (s.loading) {
      child = const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2));
    } else if (s.error != null) {
      child = Column(children: [
        Text(PastSessionsText.error(s.error) ?? '', style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.error)),
        TextButton(onPressed: _loadMore, child: const Text('Try again')),
      ]);
    } else if (s.hasMore) {
      _atEnd();
      child = TextButton(key: const Key('past-sessions-more'), onPressed: _loadMore, child: const Text('Load more'));
    } else {
      child = Text('No earlier sessions.', style: theme.textTheme.labelSmall?.copyWith(color: theme.hintColor));
    }
    return Padding(padding: const EdgeInsets.symmetric(vertical: 12), child: Center(child: child));
  }
}

/// One session: when, what kind and how long, its first words, how it ended, and its id with Copy.
class _SessionRow extends StatelessWidget {
  const _SessionRow({required this.session, required this.opening, required this.onOpen, required this.onCopy});

  final PastSession session;
  final bool opening;
  final VoidCallback onOpen;
  final VoidCallback onCopy;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(children: [
      InkWell(
        key: Key('past-session-${session.id}'),
        onTap: onOpen,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 10),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(formatWhen(session.startedAt, DateTime.now()), style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600)),
                const SizedBox(height: 3),
                Text(PastSessionsText.summary(session), style: theme.textTheme.bodySmall?.copyWith(color: theme.hintColor)),
                if (session.preview != null) ...[
                  const SizedBox(height: 3),
                  Text('“${session.preview}”', style: theme.textTheme.bodySmall, maxLines: 2, overflow: TextOverflow.ellipsis),
                ],
                TextButton(
                  onPressed: onCopy,
                  style: TextButton.styleFrom(padding: EdgeInsets.zero, minimumSize: const Size(0, 32), tapTargetSize: MaterialTapTargetSize.shrinkWrap),
                  child: Text('${PastSessionsText.shortId(session.id)}  Copy', style: theme.textTheme.labelSmall?.copyWith(fontFamily: 'monospace')),
                ),
              ]),
            ),
            const SizedBox(width: 8),
            Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
              ToneChip(PastSessionsText.status(session.status)),
              if (opening) const Padding(padding: EdgeInsets.only(top: 6), child: SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))),
            ]),
          ]),
        ),
      ),
      const Divider(height: 1),
    ]);
  }
}

/// One past session, read only: its lines oldest first, each with its time; who said it by the side it sits on.
class TranscriptScreen extends StatelessWidget {
  const TranscriptScreen({super.key, required this.agent, required this.transcript});

  final DemoAgent agent;
  final SessionTranscript transcript;

  @override
  Widget build(BuildContext context) {
    final state = DemoScope.of(context);
    final theme = Theme.of(context);
    final name = _nameOf(state, agent);
    return Scaffold(
      appBar: AppBar(
        title: Text(formatWhen(transcript.startedAt, DateTime.now())),
        actions: [
          TextButton(
            key: const Key('transcript-copy'),
            onPressed: () => _copy(context, transcript.id),
            child: Text(PastSessionsText.shortId(transcript.id), style: const TextStyle(fontFamily: 'monospace')),
          ),
        ],
      ),
      body: ListView(
        key: const Key('transcript'),
        padding: const EdgeInsets.fromLTRB(14, 10, 14, 32),
        children: [
          Center(child: Text(PastSessionsText.transcriptSummary(transcript), style: theme.textTheme.bodySmall?.copyWith(color: theme.hintColor))),
          const SizedBox(height: 10),
          if (transcript.turns.isEmpty) Text('This session has no messages.', style: TextStyle(color: theme.hintColor)),
          for (final line in transcript.turns) _bubble(context, line, name),
        ],
      ),
    );
  }

  Widget _bubble(BuildContext context, TranscriptTurn line, String name) {
    final theme = Theme.of(context);
    final mine = PastSessionsText.isCustomer(line);
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Align(
        alignment: mine ? Alignment.centerRight : Alignment.centerLeft,
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 300),
          child: Column(crossAxisAlignment: mine ? CrossAxisAlignment.end : CrossAxisAlignment.start, children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: mine ? theme.colorScheme.primary : theme.colorScheme.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(14),
              ),
              child: Text(line.text, style: TextStyle(color: mine ? theme.colorScheme.onPrimary : theme.colorScheme.onSurfaceVariant)),
            ),
            Padding(
              padding: const EdgeInsets.only(top: 2, left: 4, right: 4),
              child: Text('${mine ? 'You' : name}${line.at != null ? ' · ${formatTime(line.at!)}' : ''}', style: theme.textTheme.labelSmall?.copyWith(color: theme.hintColor)),
            ),
          ]),
        ),
      ),
    );
  }
}
