import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:graine_demo/usecases.dart';

/// The bundled packs are byte-for-byte the shared ones in ../usecases (scripts/sync-flutter-usecases.sh).
void main() {
  final shared = Directory('../usecases');
  final bundled = Directory('assets/usecases');

  test('every shared pack is bundled, unchanged, and nothing else is', () {
    final names = shared.listSync().whereType<File>().map((f) => f.uri.pathSegments.last).where((n) => n.endsWith('.json') && n != 'usecase.schema.json').toSet();
    final ours = bundled.listSync().whereType<File>().map((f) => f.uri.pathSegments.last).toSet();
    expect(ours, names);
    for (final n in names) {
      expect(File('assets/usecases/$n').readAsStringSync(), File('../usecases/$n').readAsStringSync(), reason: '$n drifted: run scripts/sync-flutter-usecases.sh');
    }
    expect(packIds.toSet(), names.map((n) => n.replaceAll('.json', '')).toSet());
    expect(packIds.first, defaultPackId);
  });

  test('every pack parses, with its booking buttons and reasons', () {
    for (final id in packIds) {
      final pack = UseCasePack.fromJson(jsonDecode(File('assets/usecases/$id.json').readAsStringSync()) as Map<String, dynamic>);
      expect(pack.id, id);
      expect(pack.users, hasLength(3));
      expect(pack.call.bookLabel, isNotEmpty);
      expect(pack.call.reasons.length, inInclusiveRange(2, 4));
      expect(pack.call.bookingReason, pack.call.reasons.first);
      expect(pack.mainAgent.name, pack.agentName);
      expect(pack.helpAgent.name, isNot(pack.agentName));
      expect(pack.helpAgent.reasons.length, inInclusiveRange(2, 4));
      expect(pack.helpAgent.bookingReason, pack.helpAgent.reasons.first);
      expect(pack.helpAgent.bookLabel, isNotNull);
      for (final u in pack.users) {
        expect(pack.rowsFor(u.id), isNotEmpty, reason: '${u.id} has rows');
        expect(pack.identifyTraits(u)[pack.identifierField], u.id);
      }
    }
  });

  test("identify sends the id under the pack's field and under user_id", () {
    final health = UseCasePack.fromJson(jsonDecode(File('assets/usecases/health-coach.json').readAsStringSync()) as Map<String, dynamic>);
    final user = health.users[0];
    final traits = health.identifyTraits(user);
    expect(traits[health.identifierField], user.id, reason: "the pack's own field");
    expect(traits['user_id'], user.id, reason: 'and User ID, so an agent set to it still knows the user');
    final explicit = PackUser(id: user.id, name: user.name, traits: {...user.traits, 'user_id': 'kept-as-sent'});
    expect(health.identifyTraits(explicit)['user_id'], 'kept-as-sent', reason: 'a user_id the person already has is kept');
  });

  test("each pack maps its people to the agent's variables, as an API call would", () {
    final json = jsonDecode(File('assets/usecases/health-coach.json').readAsStringSync()) as Map<String, dynamic>;
    final health = UseCasePack.fromJson(json);
    expect(health.variables, {'callee_name': 'name', 'age': 'age'});
    expect(health.variablesFor(health.users[0]), {'callee_name': 'Riya Sharma', 'age': '34'});
    // Any name the agent's author chose, from the name or any trait; a field the person lacks is left out.
    final custom = UseCasePack.fromJson({...json, 'variables': {'name_user': 'name', 'plan_name': 'plan', 'dob': 'date_of_birth'}});
    expect(custom.variablesFor(custom.users[0]), {'name_user': 'Riya Sharma', 'plan_name': 'Diabetes care'});
    // A pack that names none: the name as callee_name — what greetings and Call History read.
    final none = UseCasePack.fromJson({...json}..remove('variables'));
    expect(none.variablesFor(none.users[0]), {'callee_name': 'Riya Sharma'});
  });
}
