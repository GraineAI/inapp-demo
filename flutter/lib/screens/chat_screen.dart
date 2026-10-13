import 'package:flutter/material.dart';

import '../graine_service.dart';
import '../state.dart';
import '../ui.dart';
import '../usecases.dart';

/// Chat: the SDK's chat window, full screen, with the components the agent shows. It is mounted the first
/// time the person opens it and then stays, so the chat carries on while they move between screens — the
/// agent keeps seeing where they are and can act there.
class ChatScreen extends StatelessWidget {
  const ChatScreen({super.key, required this.active});

  final bool active;

  @override
  Widget build(BuildContext context) {
    final state = DemoScope.of(context);
    final pack = state.pack;
    return ScreenReport(
      active: active,
      screen: GraineScreen('chat', title: pack.agentName),
      child: Column(children: [
        if (pack.notice != null) Padding(padding: const EdgeInsets.fromLTRB(12, 8, 12, 0), child: Notice(pack.notice!)),
        Expanded(child: GraineChat(name: pack.agentName, onClose: () => state.go(ScreenKey.home))),
      ]),
    );
  }
}
