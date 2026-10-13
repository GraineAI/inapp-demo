import 'package:flutter/material.dart';

import '../graine_service.dart';
import '../state.dart';
import '../ui.dart';
import '../usecases.dart';
import 'memory_screen.dart';

/// Profile: who is signed in (as the agent knows them), memory on or off, erase, this device, sign out.
class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key, required this.active});

  final bool active;

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  String? _device;
  String? _message;
  String? _error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      try {
        final id = await DemoScope.of(context).service.installId();
        if (mounted) setState(() => _device = id);
      } catch (_) {
        // No install id yet: shown as such.
      }
    });
  }

  Future<void> _erase(DemoState state) async {
    setState(() { _message = null; _error = null; });
    if (!await confirmErase(context, state.pack.agentName)) return;
    try {
      final deleted = await state.service.forgetMemory();
      if (mounted) setState(() => _message = deleted == 1 ? '1 memory erased.' : '$deleted memories erased.');
    } catch (e) {
      if (mounted) setState(() => _error = describeError(e, state.pack));
    }
  }

  static String _label(String key) {
    final words = key.replaceAll('_', ' ');
    return words.isEmpty ? key : words[0].toUpperCase() + words.substring(1);
  }

  @override
  Widget build(BuildContext context) {
    final state = DemoScope.of(context);
    final pack = state.pack;
    final user = state.user;
    final theme = Theme.of(context);
    if (user == null) return const SizedBox.shrink();

    return ScreenReport(
      active: widget.active,
      screen: const GraineScreen('profile', title: 'Profile'),
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 140),
        children: [
          Text(user.name, style: theme.textTheme.headlineSmall),
          Text('${pack.identifierLabel} ${user.id}', style: theme.textTheme.bodyMedium),
          SectionLabel('What ${pack.agentName} knows'),
          Card(
            child: Column(children: [
              for (final e in user.traits.entries) ListTile(dense: true, title: Text(_label(e.key)), trailing: Text(e.value)),
            ]),
          ),
          const SectionLabel('Memory'),
          SwitchListTile(
            value: state.consent,
            onChanged: state.setConsent,
            title: Text('Let ${pack.agentName} remember our conversations'),
          ),
          OutlinedButton(onPressed: () => state.go(ScreenKey.memory), child: Text('See what ${pack.agentName} remembers')),
          const SizedBox(height: 8),
          OutlinedButton(
            style: OutlinedButton.styleFrom(foregroundColor: theme.colorScheme.error),
            onPressed: () => _erase(state),
            child: Text('Erase what ${pack.agentName} remembers'),
          ),
          if (_message != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text(_message!)),
          if (_error != null) ErrorText(_error!),
          const SectionLabel('This device'),
          Text(_device == null ? 'No install ID yet.' : 'Install ID $_device', style: theme.textTheme.bodySmall),
          const SizedBox(height: 24),
          OutlinedButton(key: const Key('sign-out'), onPressed: state.signOut, child: const Text('Sign out')),
          const SizedBox(height: 8),
          Text('Signing out releases this device: it stops ringing for ${user.firstName} and no longer counts as one of their devices.',
              style: theme.textTheme.bodySmall),
        ],
      ),
    );
  }
}
