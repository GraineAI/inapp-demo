import 'package:flutter/material.dart';

import '../graine_service.dart';
import '../state.dart';
import '../ui.dart';

/// Sign in: choose the use case (remembered on this device) and who you are, and whether you may be remembered.
class SignInScreen extends StatefulWidget {
  const SignInScreen({super.key});

  @override
  State<SignInScreen> createState() => _SignInScreenState();
}

class _SignInScreenState extends State<SignInScreen> {
  String? _userId;
  bool _consent = true;
  bool _busy = false;
  String? _error;

  @override
  Widget build(BuildContext context) {
    final state = DemoScope.of(context);
    final pack = state.pack;
    final userId = pack.users.any((u) => u.id == _userId) ? _userId! : pack.users.first.id;
    final theme = Theme.of(context);
    final restartPack = state.restartFor == null ? null : state.packs.firstWhere((p) => p.id == state.restartFor);

    return Scaffold(
      appBar: AppBar(title: const Text('Graine Demo')),
      body: ScreenReport(
        active: true,
        screen: const GraineScreen('sign_in', title: 'Sign in'),
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
          children: [
            Text('Choose a use case', style: theme.textTheme.headlineSmall),
            const SizedBox(height: 4),
            Text('The same app and the same SDK — the agent, the people and the screens come from the use case.',
                style: theme.textTheme.bodyMedium?.copyWith(color: theme.hintColor)),
            const SectionLabel('Use case'),
            for (final p in state.packs)
              Card(
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                  side: BorderSide(color: p.id == (state.restartFor ?? pack.id) ? theme.colorScheme.primary : theme.dividerColor, width: p.id == (state.restartFor ?? pack.id) ? 2 : 1),
                ),
                child: ListTile(
                  key: Key('pack-${p.id}'),
                  title: Text(p.appTitle, style: const TextStyle(fontWeight: FontWeight.w700)),
                  subtitle: Text(p.id == pack.id ? '${p.agentName}, ${p.agentRole}\n${p.tagline}' : '${p.agentName}, ${p.agentRole}'),
                  isThreeLine: p.id == pack.id,
                  selected: p.id == pack.id,
                  onTap: () async {
                    setState(() => _error = null);
                    await state.choosePack(p.id);
                  },
                ),
              ),
            if (restartPack != null)
              Notice('${restartPack.appTitle} uses another agent. Close and reopen the app to switch to ${restartPack.agentName}.'),
            SectionLabel('Sign in as a ${pack.userNoun}'),
            RadioGroup<String>(
              groupValue: userId,
              onChanged: (v) => setState(() => _userId = v),
              child: Column(
                children: [
                  for (final u in pack.users)
                    RadioListTile<String>(
                      key: Key('user-${u.id}'),
                      value: u.id,
                      title: Text(u.name),
                      subtitle: Text('${pack.identifierLabel} ${u.id} · ${u.traits.values.join(' · ')}'),
                    ),
                ],
              ),
            ),
            const SizedBox(height: 8),
            SwitchListTile(
              key: const Key('consent'),
              value: _consent,
              onChanged: (v) => setState(() => _consent = v),
              title: Text('Let ${pack.agentName} remember our conversations'),
              subtitle: Text('${pack.agentName} recalls what you said in earlier chats and calls — for you only. '
                  'You can see it, switch it off or erase it at any time.'),
            ),
            const SizedBox(height: 16),
            FilledButton(
              key: const Key('sign-in'),
              onPressed: _busy || restartPack != null
                  ? null
                  : () async {
                      setState(() {
                        _busy = true;
                        _error = null;
                      });
                      try {
                        await state.signIn(pack.users.firstWhere((u) => u.id == userId), _consent);
                      } catch (e) {
                        if (mounted) setState(() => _error = describeError(e, pack));
                      } finally {
                        if (mounted) setState(() => _busy = false);
                      }
                    },
              child: const Text('Sign in'),
            ),
            if (_error != null) ErrorText(_error!),
          ],
        ),
      ),
    );
  }
}
