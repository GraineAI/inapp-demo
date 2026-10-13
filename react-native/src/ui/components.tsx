/** A few plain building blocks, so every screen looks like one app. */
import type { ReactNode } from "react";
import { ActivityIndicator, Pressable, StyleSheet, Switch, Text, View, type StyleProp, type ViewStyle } from "react-native";
import { useDemo } from "../state/DemoState";
import type { ChipTone } from "../logic/scheduling";
import { usePalette } from "./theme";

export function useColors() {
  const { pack } = useDemo();
  return usePalette(pack.accent);
}

export function Card({ children, style, onPress, accessibilityLabel }: { children: ReactNode; style?: StyleProp<ViewStyle>; onPress?: () => void; accessibilityLabel?: string }) {
  const c = useColors();
  const box = [styles.card, { backgroundColor: c.surface, borderColor: c.border }, style];
  if (!onPress) return <View style={box}>{children}</View>;
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={accessibilityLabel} onPress={onPress} style={({ pressed }) => [box, pressed && { opacity: 0.85 }]}>
      {children}
    </Pressable>
  );
}

export function Title({ children }: { children: ReactNode }) {
  const c = useColors();
  return <Text style={[styles.title, { color: c.text }]}>{children}</Text>;
}

export function Body({ children, muted, style }: { children: ReactNode; muted?: boolean; style?: StyleProp<any> }) {
  const c = useColors();
  return <Text style={[styles.body, { color: muted ? c.textMuted : c.text }, style]}>{children}</Text>;
}

export function SectionLabel({ children }: { children: ReactNode }) {
  const c = useColors();
  return <Text style={[styles.section, { color: c.textMuted }]}>{children}</Text>;
}

export function Chip({ label, tone = "neutral", onPress, selected, disabled, accessibilityLabel }: { label: string; tone?: ChipTone; onPress?: () => void; selected?: boolean; disabled?: boolean; accessibilityLabel?: string }) {
  const c = useColors();
  const look = selected ? { bg: c.accent, fg: c.onAccent } : c.chip[tone];
  const content = <Text style={[styles.chipText, { color: look.fg }]}>{label}</Text>;
  if (!onPress) return <View style={[styles.chip, { backgroundColor: look.bg }]}>{content}</View>;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? label}
      accessibilityState={{ selected: !!selected, disabled: !!disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [styles.chip, { backgroundColor: look.bg, opacity: disabled ? 0.4 : pressed ? 0.8 : 1 }]}
    >
      {content}
    </Pressable>
  );
}

export function Button({
  label, onPress, kind = "primary", disabled, busy, accessibilityLabel,
}: { label: string; onPress: () => void; kind?: "primary" | "secondary" | "danger"; disabled?: boolean; busy?: boolean; accessibilityLabel?: string }) {
  const c = useColors();
  const bg = kind === "primary" ? c.accent : kind === "danger" ? c.surface : c.surfaceMuted;
  const fg = kind === "primary" ? c.onAccent : kind === "danger" ? c.danger : c.text;
  const off = disabled || busy;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? label}
      accessibilityState={{ disabled: !!off, busy: !!busy }}
      disabled={off}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        { backgroundColor: bg, borderColor: kind === "danger" ? c.danger : bg, opacity: off ? 0.5 : pressed ? 0.85 : 1 },
      ]}
    >
      {busy ? <ActivityIndicator color={fg} /> : <Text style={[styles.buttonText, { color: fg }]}>{label}</Text>}
    </Pressable>
  );
}

export function ToggleRow({ label, detail, value, onChange, disabled }: { label: string; detail?: string; value: boolean; onChange: (v: boolean) => void; disabled?: boolean }) {
  const c = useColors();
  return (
    <View style={styles.toggle}>
      <View style={{ flex: 1, paddingRight: 12 }}>
        <Text style={[styles.body, { color: c.text, fontWeight: "600" }]}>{label}</Text>
        {detail ? <Text style={[styles.small, { color: c.textMuted }]}>{detail}</Text> : null}
      </View>
      <Switch accessibilityLabel={label} value={value} onValueChange={onChange} disabled={disabled} trackColor={{ true: c.accent }} />
    </View>
  );
}

export function Notice({ children }: { children: ReactNode }) {
  const c = useColors();
  return (
    <View style={[styles.notice, { backgroundColor: c.surfaceMuted, borderColor: c.border }]}>
      <Text style={[styles.small, { color: c.textMuted }]}>{children}</Text>
    </View>
  );
}

export function ErrorText({ children }: { children: ReactNode }) {
  const c = useColors();
  return <Text accessibilityRole="alert" style={[styles.small, { color: c.danger, marginTop: 8 }]}>{children}</Text>;
}

export const styles = StyleSheet.create({
  card: { borderWidth: StyleSheet.hairlineWidth, borderRadius: 14, padding: 16, marginBottom: 12 },
  title: { fontSize: 22, fontWeight: "700", marginBottom: 4 },
  body: { fontSize: 15, lineHeight: 21 },
  small: { fontSize: 13, lineHeight: 18 },
  section: { fontSize: 12, fontWeight: "700", letterSpacing: 0.6, textTransform: "uppercase", marginTop: 20, marginBottom: 8 },
  chip: { borderRadius: 999, paddingHorizontal: 10, paddingVertical: 5, alignSelf: "flex-start" },
  chipText: { fontSize: 13, fontWeight: "600" },
  button: { borderRadius: 12, paddingVertical: 13, paddingHorizontal: 16, alignItems: "center", justifyContent: "center", borderWidth: 1, minHeight: 48 },
  buttonText: { fontSize: 15, fontWeight: "600" },
  toggle: { flexDirection: "row", alignItems: "center", paddingVertical: 8 },
  notice: { borderWidth: StyleSheet.hairlineWidth, borderRadius: 10, padding: 12, marginBottom: 12 },
  row: { flexDirection: "row", alignItems: "center", gap: 8 },
  wrap: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  screen: { padding: 16, paddingBottom: 140 },
});
