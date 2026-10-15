/// A few plain building blocks, so every screen looks like one app.
library;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import 'graine_service.dart';
import 'logic/presentation.dart';
import 'state.dart';

Color toneColor(BuildContext context, Tone tone) {
  final dark = Theme.of(context).brightness == Brightness.dark;
  return switch (tone) {
    Tone.info => dark ? const Color(0xFF93C5FD) : const Color(0xFF1D4ED8),
    Tone.success => dark ? const Color(0xFF86EFAC) : const Color(0xFF166534),
    Tone.warning => dark ? const Color(0xFFFCD34D) : const Color(0xFF9A5B00),
    Tone.danger => dark ? const Color(0xFFFCA5A5) : const Color(0xFFB42318),
    Tone.muted => dark ? const Color(0xFF8B95A1) : const Color(0xFF667085),
    Tone.neutral => dark ? const Color(0xFFD0D5DD) : const Color(0xFF344054),
  };
}

/// A status chip in the tone's colour.
class ToneChip extends StatelessWidget {
  const ToneChip(this.label, {super.key, this.tone = Tone.neutral});

  final String label;
  final Tone tone;

  @override
  Widget build(BuildContext context) {
    final color = toneColor(context, tone);
    return Semantics(
      label: label,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        decoration: BoxDecoration(color: color.withValues(alpha: 0.12), borderRadius: BorderRadius.circular(999)),
        child: Text(label, style: TextStyle(color: color, fontWeight: FontWeight.w600, fontSize: 13)),
      ),
    );
  }
}

class SectionLabel extends StatelessWidget {
  const SectionLabel(this.text, {super.key});
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(top: 20, bottom: 8),
        child: Text(text.toUpperCase(),
            style: Theme.of(context).textTheme.labelSmall?.copyWith(letterSpacing: 0.6, fontWeight: FontWeight.w700, color: Theme.of(context).hintColor)),
      );
}

class Notice extends StatelessWidget {
  const Notice(this.text, {super.key});
  final String text;

  @override
  Widget build(BuildContext context) => Card(
        elevation: 0,
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        child: Padding(padding: const EdgeInsets.all(12), child: Text(text, style: Theme.of(context).textTheme.bodySmall)),
      );
}

/// Under a booking button while the SDK says its agent cannot be booked.
const String bookingUnavailable = "Booking isn't available for this agent.";

/// A booking button: enabled while the SDK says the agent can be booked ([available]); otherwise it stays on
/// screen, disabled, with one line saying so. [onPressed] opens the SDK's booking sheet.
class BookingButton extends StatelessWidget {
  const BookingButton({super.key, this.buttonKey, required this.label, required this.available, required this.onPressed, this.filled = true});

  final Key? buttonKey;
  final String label;
  final ValueListenable<bool> available;
  final VoidCallback onPressed;

  /// A filled button, else an outlined one.
  final bool filled;

  @override
  Widget build(BuildContext context) => ValueListenableBuilder<bool>(
        valueListenable: available,
        builder: (context, on, _) {
          final press = on ? onPressed : null;
          return Column(crossAxisAlignment: CrossAxisAlignment.stretch, mainAxisSize: MainAxisSize.min, children: [
            filled
                ? FilledButton(key: buttonKey, onPressed: press, child: Text(label))
                : OutlinedButton(key: buttonKey, onPressed: press, child: Text(label)),
            if (!on) Padding(padding: const EdgeInsets.only(top: 6), child: Text(bookingUnavailable, style: Theme.of(context).textTheme.bodySmall)),
          ]);
        },
      );
}

class ErrorText extends StatelessWidget {
  const ErrorText(this.text, {super.key});
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(top: 8),
        child: Semantics(liveRegion: true, child: Text(text, style: TextStyle(color: Theme.of(context).colorScheme.error))),
      );
}

/// Tells the agent what this screen shows — when it becomes the active one, and again whenever what it
/// shows changes while active. Tabs stay mounted, so reporting once on mount would leave the agent on
/// whichever tab was opened last.
class ScreenReport extends StatefulWidget {
  const ScreenReport({super.key, required this.active, required this.screen, required this.child});

  final bool active;
  final GraineScreen screen;
  final Widget child;

  @override
  State<ScreenReport> createState() => _ScreenReportState();
}

class _ScreenReportState extends State<ScreenReport> {
  String? _sent;

  static String _keyOf(GraineScreen s) => [s.screen, s.title, for (final f in s.fields) '${f.name}=${f.value}'].join('|');

  void _report() {
    if (!widget.active) {
      _sent = null;
      return;
    }
    final key = _keyOf(widget.screen);
    if (key == _sent) return;
    _sent = key;
    DemoScope.of(context).service.reportScreen(widget.screen);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _report();
  }

  @override
  void didUpdateWidget(ScreenReport old) {
    super.didUpdateWidget(old);
    _report();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
