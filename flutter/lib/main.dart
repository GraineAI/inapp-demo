import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'app.dart';
import 'graine_service.dart';
import 'state.dart';
import 'usecases.dart';

/// Publishable keys and the base URL, from --dart-define (or --dart-define-from-file=dart_defines.json; copy
/// dart_defines.example.json). Each use-case pack is its own agent and may have its own key; a pack without
/// one uses GRAINE_PUBLISHABLE_KEY.
const String _defaultKey = String.fromEnvironment('GRAINE_PUBLISHABLE_KEY');
const Map<String, String> _packKeys = <String, String>{
  'health-coach': String.fromEnvironment('GRAINE_KEY_HEALTH_COACH'),
  'banking-support': String.fromEnvironment('GRAINE_KEY_BANKING_SUPPORT'),
  'retail-shopping': String.fromEnvironment('GRAINE_KEY_RETAIL_SHOPPING'),
};
/// The help agent of each pack (the Help screen) — optional; a pack without its own uses GRAINE_HELP_KEY.
const String _defaultHelpKey = String.fromEnvironment('GRAINE_HELP_KEY');
const Map<String, String> _helpKeys = <String, String>{
  'health-coach': String.fromEnvironment('GRAINE_HELP_KEY_HEALTH_COACH'),
  'banking-support': String.fromEnvironment('GRAINE_HELP_KEY_BANKING_SUPPORT'),
  'retail-shopping': String.fromEnvironment('GRAINE_HELP_KEY_RETAIL_SHOPPING'),
};
const String _baseUrl = String.fromEnvironment('GRAINE_BASE_URL', defaultValue: 'https://www.graine.ai');

String keyFor(String packId) {
  final own = (_packKeys[packId] ?? '').trim();
  return own.startsWith('pk_') ? own : _defaultKey.trim();
}

/// The help agent's key for a pack, or null when the app has none.
String? helpKeyFor(String packId) {
  final own = (_helpKeys[packId] ?? '').trim();
  if (own.startsWith('pk_')) return own;
  return _defaultHelpKey.trim().startsWith('pk_') ? _defaultHelpKey.trim() : null;
}

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final prefs = await SharedPreferences.getInstance();
  final packs = await loadPacks(rootBundle);
  // The chosen pack's two agents run for this launch; choosing a pack with another key applies at the next launch.
  final packId = prefs.getString('graine-demo.pack') ?? defaultPackId;
  final chosen = packs.any((p) => p.id == packId) ? packId : defaultPackId;
  final key = keyFor(chosen);
  if (!key.startsWith('pk_')) {
    runApp(const _NotConfigured());
    return;
  }
  final service = SdkGraineService.start(baseUrl: _baseUrl, publishableKey: key, helpKey: helpKeyFor(chosen));
  final state = DemoState(packs: packs, service: service, keyFor: keyFor, prefs: prefs, packId: chosen);
  await state.restore();
  runApp(GraineDemoApp(state: state));
}

class _NotConfigured extends StatelessWidget {
  const _NotConfigured();

  @override
  Widget build(BuildContext context) => const MaterialApp(
        title: 'Graine Demo',
        home: Scaffold(
          body: SafeArea(
            child: Padding(
              padding: EdgeInsets.all(24),
              child: Text(
                'No publishable key.\n\nCopy dart_defines.example.json to dart_defines.json, add your key from the dashboard '
                '(your agent → Embed & Widgets), and run:\n\nflutter run --dart-define-from-file=dart_defines.json',
              ),
            ),
          ),
        ),
      );
}
