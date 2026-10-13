import 'package:flutter/material.dart';

import '../graine_service.dart';
import '../logic/presentation.dart';
import '../state.dart';
import '../ui.dart';

/// Ask before erasing: it cannot be undone. True on Erase.
Future<bool> confirmErase(BuildContext context, String agentName) async =>
    await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Erase everything $agentName remembers?'),
        content: Text('$agentName will no longer recall anything from your earlier chats and calls. This cannot be undone.'),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Cancel')),
          TextButton(key: const Key('confirm-erase'), onPressed: () => Navigator.of(context).pop(true), child: const Text('Erase')),
        ],
      ),
    ) ??
    false;

/// Memory: what the agent remembers about the signed-in person — newest first, pull to refresh — the consent
/// switch, and erasing it all. Sign in as someone else and the list is theirs: memories never cross people.
class MemoryScreen extends StatefulWidget {
  const MemoryScreen({super.key});

  @override
  State<MemoryScreen> createState() => _MemoryScreenState();
}

class _MemoryScreenState extends State<MemoryScreen> {
  List<GraineMemory> _memories = const <GraineMemory>[];
  bool _loading = true;
  String? _loadError;
  String? _message;
  String? _error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  Future<void> _load() async {
    if (!mounted) return;
    final state = DemoScope.of(context);
    setState(() => _loading = true);
    try {
      final list = await state.service.memories();
      if (mounted) setState(() { _memories = list; _loadError = null; });
    } catch (e) {
      if (mounted) setState(() { _memories = const <GraineMemory>[]; _loadError = describeError(e, state.pack); });
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _erase(DemoState state) async {
    setState(() { _message = null; _error = null; });
    if (!await confirmErase(context, state.pack.agentName)) return;
    try {
      final deleted = await state.service.forgetMemory();
      if (mounted) {
        setState(() => _message = '${deleted == 1 ? '1 memory' : '$deleted memories'} erased. ${state.pack.agentName} starts fresh with ${state.user?.firstName ?? 'you'}.');
      }
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
    final chip = memoryChip(agentMemory: state.service.agentRemembers, consent: state.consent);
    final now = DateTime.now();

    return Scaffold(
      appBar: AppBar(title: const Text('Memory')),
      body: ScreenReport(
        active: true,
        screen: GraineScreen('memory', title: 'Memory', fields: [
          GraineField('memory', label: 'Memory', value: chip.label == 'Memory on' ? 'on' : 'off', status: FieldStatus.filled),
          GraineField('remembered', label: 'Things remembered', value: '${_memories.length}', status: FieldStatus.filled),
        ]),
        child: RefreshIndicator(
          onRefresh: _load,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 140),
            children: [
              Text('What ${pack.agentName} remembers about you', style: theme.textTheme.headlineSmall),
              const SizedBox(height: 8),
              Row(children: [
                ToneChip(chip.label, tone: chip.tone),
                const SizedBox(width: 8),
                Expanded(child: Text(state.user == null ? '' : '${state.user!.name} · ${pack.identifierLabel} ${state.user!.id}', style: theme.textTheme.bodySmall)),
              ]),
              const SizedBox(height: 12),
              if (state.service.agentRemembers == false)
                const Notice('This agent does not remember conversations. In the dashboard: Memory → Settings → Remembers users across chats and calls.'),
              if (_loadError != null) ErrorText(_loadError!),
              if (!_loading && _loadError == null && _memories.isEmpty) Card(child: Padding(padding: const EdgeInsets.all(16), child: Text(memoryEmptyText(pack)))),
              for (final m in _memories)
                Card(
                  child: ListTile(
                    title: Text(m.text),
                    subtitle: Text('Remembered ${formatWhen(m.updatedAt ?? m.createdAt, now)}'),
                  ),
                ),
              const SectionLabel('Your choice'),
              SwitchListTile(
                key: const Key('memory-consent'),
                value: state.consent,
                onChanged: state.setConsent,
                title: Text('Let ${pack.agentName} remember our conversations'),
                subtitle: Text(chip.detail),
              ),
              const SizedBox(height: 8),
              OutlinedButton(
                key: const Key('erase'),
                style: OutlinedButton.styleFrom(foregroundColor: theme.colorScheme.error),
                onPressed: _memories.isEmpty && _loadError == null ? null : () => _erase(state),
                child: Text('Erase everything ${pack.agentName} remembers'),
              ),
              if (_message != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text(_message!)),
              if (_error != null) ErrorText(_error!),
            ],
          ),
        ),
      ),
    );
  }
}
