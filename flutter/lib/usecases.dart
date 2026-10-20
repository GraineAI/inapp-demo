/// The use-case packs (../usecases/*.json, bundled from assets/usecases/) and the few questions the
/// app asks of them. Nothing in the app is specific to a domain: every word comes from a pack.
library;

import 'dart:convert';

import 'package:flutter/services.dart';

/// The packs this build bundles, in the order the sign-in screen offers them. The first is the default.
const List<String> packIds = <String>['health-coach', 'banking-support', 'retail-shopping'];
const String defaultPackId = 'health-coach';

/// The app's generic screens. The list screen is reported under the pack's own name.
enum ScreenKey { home, chat, items, calls, memory, profile }

class PackUser {
  const PackUser({required this.id, required this.name, required this.traits});

  factory PackUser.fromJson(Map<String, dynamic> j) => PackUser(
        id: j['id'] as String,
        name: j['name'] as String,
        traits: (j['traits'] as Map<String, dynamic>? ?? const <String, dynamic>{}).map((k, v) => MapEntry(k, '$v')),
      );

  /// The identifier's value, sent as traits[identifierField].
  final String id;
  final String name;
  final Map<String, String> traits;

  String get firstName => name.split(' ').first;
}

class PackField {
  const PackField(this.name, this.label, this.value);

  factory PackField.fromJson(Map<String, dynamic> j) => PackField(j['name'] as String, j['label'] as String, j['value'] as String);

  final String name;
  final String label;
  final String value;
}

class PackRow {
  const PackRow({
    required this.id,
    required this.title,
    required this.subtitle,
    this.status,
    this.fields = const <PackField>[],
    this.actions = const <String>[],
    this.userIds,
  });

  factory PackRow.fromJson(Map<String, dynamic> j) => PackRow(
        id: j['id'] as String,
        title: j['title'] as String,
        subtitle: j['subtitle'] as String? ?? '',
        status: j['status'] as String?,
        fields: [for (final f in j['fields'] as List<dynamic>? ?? const <dynamic>[]) PackField.fromJson(f as Map<String, dynamic>)],
        actions: [for (final a in j['actions'] as List<dynamic>? ?? const <dynamic>[]) a as String],
        userIds: (j['userIds'] as List<dynamic>?)?.map((e) => e as String).toList(),
      );

  final String id;
  final String title;
  final String subtitle;
  final String? status;
  final List<PackField> fields;
  final List<String> actions;
  final List<String>? userIds;
}

class PackActionArg {
  const PackActionArg({required this.name, required this.type, required this.required, required this.description, this.enumValues});

  factory PackActionArg.fromJson(Map<String, dynamic> j) => PackActionArg(
        name: j['name'] as String,
        type: j['type'] as String,
        required: j['required'] as bool? ?? false,
        description: j['description'] as String? ?? '',
        enumValues: (j['enum'] as List<dynamic>?)?.map((e) => e as String).toList(),
      );

  final String name;
  final String type;
  final bool required;
  final String description;
  final List<String>? enumValues;
}

/// One of the pack's actions. [effect] is `open_screen`, `open_item` or `set_status`.
class PackAction {
  const PackAction({required this.name, required this.effect, required this.label, required this.description, this.status, this.args = const <PackActionArg>[]});

  factory PackAction.fromJson(Map<String, dynamic> j) => PackAction(
        name: j['name'] as String,
        effect: j['effect'] as String,
        label: j['label'] as String,
        description: j['description'] as String? ?? '',
        status: j['status'] as String?,
        args: [for (final a in j['args'] as List<dynamic>? ?? const <dynamic>[]) PackActionArg.fromJson(a as Map<String, dynamic>)],
      );

  final String name;
  final String effect;
  final String label;
  final String description;
  final String? status;
  final List<PackActionArg> args;
}

class PackCall {
  const PackCall({
    required this.callName,
    required this.title,
    required this.bookLabel,
    required this.reasons,
    required this.placeReason,
  });

  factory PackCall.fromJson(Map<String, dynamic> j) => PackCall(
        callName: j['callName'] as String,
        title: j['title'] as String,
        bookLabel: j['bookLabel'] as String,
        reasons: [for (final r in j['reasons'] as List<dynamic>) r as String],
        placeReason: j['placeReason'] as String,
      );

  final String callName;
  final String title;

  /// The button that opens the SDK's booking sheet: "Schedule a consultation call".
  final String bookLabel;

  /// What a booking is about: the first starts the sheet's "What's it about?" line, which the person may change.
  final List<String> reasons;
  final String placeReason;

  /// What the booking sheet's "What's it about?" starts with.
  String? get bookingReason => reasons.firstOrNull;
}

class HomeCard {
  const HomeCard({required this.id, required this.title, required this.body, required this.target});

  factory HomeCard.fromJson(Map<String, dynamic> j) => HomeCard(
        id: j['id'] as String,
        title: j['title'] as String,
        body: j['body'] as String,
        target: ScreenKey.values.byName(j['target'] as String),
      );

  final String id;
  final String title;
  final String body;
  final ScreenKey target;
}

class MemoryExample {
  const MemoryExample(this.tell, this.ask, this.expect);

  factory MemoryExample.fromJson(Map<String, dynamic> j) => MemoryExample(j['tell'] as String, j['ask'] as String, j['expect'] as String);

  final String tell;
  final String ask;
  final String expect;
}

/// One of a pack's two agents. The help agent also has its call name, booking button, reasons and placed-call reason.
class PackAgent {
  const PackAgent({required this.name, required this.role, required this.starters, this.callName, this.placeReason, this.bookLabel, this.reasons = const <String>[]});

  factory PackAgent.fromJson(Map<String, dynamic> j) => PackAgent(
        name: j['name'] as String,
        role: j['role'] as String,
        starters: [for (final s in j['starters'] as List<dynamic>) s as String],
        callName: j['callName'] as String?,
        placeReason: j['placeReason'] as String?,
        bookLabel: j['bookLabel'] as String?,
        reasons: [for (final r in j['reasons'] as List<dynamic>? ?? const <dynamic>[]) r as String],
      );

  final String name;
  final String role;
  final List<String> starters;
  final String? callName;
  final String? placeReason;
  final String? bookLabel;
  final List<String> reasons;

  /// What the booking sheet's "What's it about?" starts with, when this agent has reasons of its own.
  String? get bookingReason => reasons.firstOrNull;
}

class UseCasePack {
  const UseCasePack({
    required this.id,
    required this.appTitle,
    required this.agentName,
    required this.agentRole,
    required this.mainAgent,
    required this.helpAgent,
    required this.tagline,
    this.notice,
    this.accent,
    required this.identifierField,
    required this.identifierLabel,
    required this.userNoun,
    required this.users,
    required this.homeTitle,
    required this.homeCards,
    required this.itemsTitle,
    required this.itemsScreenName,
    required this.itemNoun,
    required this.emptyText,
    required this.rows,
    required this.actions,
    required this.call,
    required this.memoryExamples,
    required this.starters,
    this.variables = defaultVariables,
  });

  /// A pack that names no variables: the person's name as `callee_name`, what greetings and Call History read.
  static const Map<String, String> defaultVariables = <String, String>{'callee_name': 'name'};

  factory UseCasePack.fromJson(Map<String, dynamic> j) {
    final home = j['home'] as Map<String, dynamic>;
    final items = j['items'] as Map<String, dynamic>;
    return UseCasePack(
      id: j['id'] as String,
      appTitle: j['appTitle'] as String,
      agentName: j['agentName'] as String,
      agentRole: j['agentRole'] as String,
      mainAgent: PackAgent.fromJson((j['agents'] as Map<String, dynamic>)['main'] as Map<String, dynamic>),
      helpAgent: PackAgent.fromJson((j['agents'] as Map<String, dynamic>)['help'] as Map<String, dynamic>),
      tagline: j['tagline'] as String,
      notice: j['notice'] as String?,
      accent: j['accent'] as String?,
      identifierField: j['identifierField'] as String,
      identifierLabel: j['identifierLabel'] as String,
      userNoun: j['userNoun'] as String,
      users: [for (final u in j['users'] as List<dynamic>) PackUser.fromJson(u as Map<String, dynamic>)],
      homeTitle: home['title'] as String,
      homeCards: [for (final c in home['cards'] as List<dynamic>) HomeCard.fromJson(c as Map<String, dynamic>)],
      itemsTitle: items['title'] as String,
      itemsScreenName: items['screenName'] as String,
      itemNoun: items['itemNoun'] as String,
      emptyText: items['emptyText'] as String,
      rows: [for (final r in items['rows'] as List<dynamic>) PackRow.fromJson(r as Map<String, dynamic>)],
      actions: [for (final a in j['actions'] as List<dynamic>) PackAction.fromJson(a as Map<String, dynamic>)],
      call: PackCall.fromJson(j['call'] as Map<String, dynamic>),
      memoryExamples: [for (final m in j['memoryExamples'] as List<dynamic>) MemoryExample.fromJson(m as Map<String, dynamic>)],
      starters: [for (final s in j['starters'] as List<dynamic>) s as String],
      variables: (j['variables'] as Map<String, dynamic>?)?.map((k, v) => MapEntry(k, v as String)) ?? defaultVariables,
    );
  }

  final String id;
  final String appTitle;
  final String agentName;
  final String agentRole;

  /// Home and Chat talk to [mainAgent]; the Help screen to [helpAgent] — each its own agent and key.
  final PackAgent mainAgent;
  final PackAgent helpAgent;
  final String tagline;
  final String? notice;
  final String? accent;
  final String identifierField;
  final String identifierLabel;
  final String userNoun;
  final List<PackUser> users;
  final String homeTitle;
  final List<HomeCard> homeCards;
  final String itemsTitle;
  final String itemsScreenName;
  final String itemNoun;
  final String emptyText;
  final List<PackRow> rows;
  final List<PackAction> actions;
  final PackCall call;
  final List<MemoryExample> memoryExamples;
  final List<String> starters;

  /// The agent's prompt variables and the user field each takes — `{"callee_name": "name", "age": "age"}`: a key
  /// is a variable the agent's prompt or welcome uses, a value is "name" or one of the person's traits (never the
  /// identifier). Set through the SDK at sign-in, as an API call sends `variables`.
  final Map<String, String> variables;

  /// What identify() is given: the identifier under the pack's field, the name, and the person's traits.
  Map<String, Object?> identifyTraits(PackUser user) => <String, Object?>{...user.traits, 'name': user.name, identifierField: user.id};

  /// The agent's prompt variables for [user], from [variables] — set through the SDK exactly as an API call
  /// sends them. A field the person does not have is left out.
  Map<String, String> variablesFor(PackUser user) {
    final out = <String, String>{};
    variables.forEach((variable, field) {
      final value = field == 'name' ? user.name : user.traits[field];
      if (value != null && value.trim().isNotEmpty) out[variable] = value;
    });
    return out;
  }

  /// The rows this person sees.
  List<PackRow> rowsFor(String? userId) =>
      userId == null ? const <PackRow>[] : rows.where((r) => r.userIds == null || r.userIds!.contains(userId)).toList();

  /// The name a screen is reported under: the generic key, except the list screen, which the pack names.
  String screenName(ScreenKey key) => key == ScreenKey.items ? itemsScreenName : key.name;

  String screenTitle(ScreenKey key) => switch (key) {
        ScreenKey.home => homeTitle,
        ScreenKey.chat => agentName,
        ScreenKey.items => itemsTitle,
        ScreenKey.calls => call.title,
        ScreenKey.memory => 'Memory',
        ScreenKey.profile => 'Profile',
      };

  /// The generic screen for a name the agent (or a component) used, or null. Case-insensitive.
  ScreenKey? screenKeyFor(Object? name) {
    if (name is! String) return null;
    final n = name.trim().replaceFirst(RegExp(r'^/+'), '').toLowerCase();
    if (n.isEmpty) return null;
    if (n == itemsScreenName.toLowerCase()) return ScreenKey.items;
    for (final k in ScreenKey.values) {
      if (k.name == n) return k;
    }
    return null;
  }

  PackAction? action(String name) {
    for (final a in actions) {
      if (a.name == name) return a;
    }
    return null;
  }
}

/// Every bundled pack, read from assets/usecases/.
Future<List<UseCasePack>> loadPacks(AssetBundle bundle) async => [
      for (final id in packIds)
        UseCasePack.fromJson(jsonDecode(await bundle.loadString('assets/usecases/$id.json')) as Map<String, dynamic>),
    ];
