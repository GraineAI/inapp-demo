/// Graine Demo — one generic app; the use-case pack decides the agent, the people and the words.
///
/// The SDK pieces, from the outside in: GraineEngine once around the navigator (it owns the voice call, so a
/// call outlives every screen), the voice bar above every screen while it is open, then the screens.
library;

import 'package:flutter/material.dart';

import 'graine_service.dart';
import 'screens/calls_screen.dart';
import 'screens/chat_screen.dart';
import 'screens/home_screen.dart';
import 'screens/items_screen.dart';
import 'screens/memory_screen.dart';
import 'screens/past_sessions_screen.dart';
import 'screens/profile_screen.dart';
import 'screens/sign_in_screen.dart';
import 'state.dart';
import 'usecases.dart';

class GraineDemoApp extends StatefulWidget {
  const GraineDemoApp({super.key, required this.state, this.withEngine = true});

  final DemoState state;

  /// False in widget tests, which have no web view to host the engine.
  final bool withEngine;

  @override
  State<GraineDemoApp> createState() => _GraineDemoAppState();
}

class _GraineDemoAppState extends State<GraineDemoApp> {
  final GlobalKey<NavigatorState> _navigator = GlobalKey<NavigatorState>();

  @override
  void initState() {
    super.initState();
    widget.state.navigator = (key) {
      final nav = _navigator.currentState;
      if (nav == null) return;
      if (key == ScreenKey.memory) {
        nav.push(MaterialPageRoute<void>(builder: (_) => const MemoryScreen(), settings: const RouteSettings(name: 'memory')));
      } else {
        nav.popUntil((route) => route.isFirst);
      }
    };
  }

  static Color _accent(String? hex) {
    final h = (hex ?? '#4F46E5').replaceFirst('#', '');
    return Color(int.parse('FF$h', radix: 16));
  }

  @override
  Widget build(BuildContext context) {
    final state = widget.state;
    return DemoScope(
      state: state,
      child: ListenableBuilder(
        listenable: state,
        builder: (context, _) {
          final seed = _accent(state.pack.accent);
          return MaterialApp(
            title: 'Graine Demo',
            navigatorKey: _navigator,
            theme: ThemeData(colorSchemeSeed: seed, useMaterial3: true),
            darkTheme: ThemeData(colorSchemeSeed: seed, brightness: Brightness.dark, useMaterial3: true),
            builder: (context, child) {
              final page = Stack(children: [
                child!,
                if (state.user != null && state.voiceOpen) GraineAgentBar(name: state.pack.agentName, onClose: state.closeVoice),
              ]);
              return widget.withEngine ? GraineEngine(child: page) : page;
            },
            home: const _Root(),
          );
        },
      ),
    );
  }
}

class _Root extends StatelessWidget {
  const _Root();

  @override
  Widget build(BuildContext context) {
    final state = DemoScope.of(context);
    if (!state.ready) return const Scaffold(body: Center(child: CircularProgressIndicator()));
    return state.user == null ? const SignInScreen() : const _Shell();
  }
}

/// The tabs. Each is mounted the first time it is opened and then kept, so a chat carries on while the
/// person moves between screens.
class _Shell extends StatefulWidget {
  const _Shell();

  @override
  State<_Shell> createState() => _ShellState();
}

class _ShellState extends State<_Shell> {
  static const List<ScreenKey> _tabs = <ScreenKey>[ScreenKey.home, ScreenKey.chat, ScreenKey.items, ScreenKey.calls, ScreenKey.profile];
  final Set<ScreenKey> _opened = <ScreenKey>{ScreenKey.home};

  Widget _screen(ScreenKey key, bool active) => switch (key) {
        ScreenKey.home => HomeScreen(active: active),
        ScreenKey.chat => ChatScreen(active: active),
        ScreenKey.items => ItemsScreen(active: active),
        ScreenKey.calls => CallsScreen(active: active),
        _ => ProfileScreen(active: active),
      };

  @override
  Widget build(BuildContext context) {
    final state = DemoScope.of(context);
    final pack = state.pack;
    final current = _tabs.contains(state.tab) ? state.tab : ScreenKey.home;
    _opened.add(current);
    final onTop = ModalRoute.of(context)?.isCurrent ?? true;

    return Scaffold(
      appBar: AppBar(
        title: Text(current == ScreenKey.home ? pack.appTitle : pack.screenTitle(current)),
        actions: [
          // The user's past sessions with the agent, beside its chat.
          if (current == ScreenKey.chat)
            TextButton(key: const Key('past-sessions-open'), onPressed: () => openPastSessions(context, DemoAgent.main), child: const Text('Past sessions')),
        ],
      ),
      body: IndexedStack(
        index: _tabs.indexOf(current),
        children: [for (final k in _tabs) _opened.contains(k) ? _screen(k, onTop && k == current) : const SizedBox.shrink()],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tabs.indexOf(current),
        onDestinationSelected: (i) => state.go(_tabs[i]),
        destinations: [
          const NavigationDestination(icon: Icon(Icons.home_outlined), label: 'Home'),
          const NavigationDestination(icon: Icon(Icons.chat_bubble_outline), label: 'Chat'),
          NavigationDestination(icon: const Icon(Icons.list_alt), label: pack.itemsTitle),
          const NavigationDestination(icon: Icon(Icons.call_outlined), label: 'Calls'),
          const NavigationDestination(icon: Icon(Icons.person_outline), label: 'Profile'),
        ],
      ),
    );
  }
}
