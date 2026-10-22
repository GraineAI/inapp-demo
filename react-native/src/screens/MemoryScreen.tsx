/**
 * Memory: what the agent remembers about the signed-in person — newest first, pull to refresh — the
 * consent switch, and erasing it all. Sign in as someone else and the list is theirs: memories never
 * cross people.
 */
import { useState } from "react";
import { RefreshControl, ScrollView, Text, View } from "react-native";
import { useDemo } from "../state/DemoState";
import { describeError, eraseMemories, useMemories, useScreenContext } from "../graine";
import { memoryChip, memoryEmptyText } from "../logic/memory";
import { formatWhen } from "../logic/time";
import { confirmErase } from "../ui/confirm";
import { Body, Button, Card, Chip, ErrorText, Notice, SectionLabel, styles, Title, ToggleRow, useColors } from "../ui/components";

export function MemoryScreen() {
  const { pack, user, consent, setConsent, settings } = useDemo();
  const c = useColors();
  const { memories, loading, error, refresh } = useMemories();
  const [erasing, setErasing] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const chip = memoryChip({ agentMemory: settings.memoryEnabled, consent });

  useScreenContext({
    screen: "memory",
    title: "Memory",
    fields: [
      { name: "memory", label: "Memory", value: chip.label === "Memory on" ? "on" : "off", status: "filled" },
      { name: "remembered", label: "Things remembered", value: memories.length, status: "filled" },
    ],
  });

  const erase = async () => {
    setMessage(null);
    setActionError(null);
    if (!(await confirmErase(pack.agentName))) return;
    setErasing(true);
    try {
      const deleted = await eraseMemories();
      const what = deleted === null ? "Everything" : `${deleted} ${deleted === 1 ? "memory" : "memories"}`;
      setMessage(`${what} erased. ${pack.agentName} starts fresh with ${user?.name.split(" ")[0] ?? "you"}.`);
      await refresh();
    } catch (err) {
      setActionError(describeError(err, { agentName: pack.agentName }));
    } finally {
      setErasing(false);
    }
  };

  const toggle = async (on: boolean) => {
    setActionError(null);
    try {
      await setConsent(on);
    } catch (err) {
      setActionError(describeError(err, { agentName: pack.agentName }));
    }
  };

  const now = new Date();
  return (
    <ScrollView
      style={{ backgroundColor: c.background }}
      contentContainerStyle={styles.screen}
      refreshControl={<RefreshControl refreshing={loading} onRefresh={() => void refresh()} />}
    >
      <Title>{`What ${pack.agentName} remembers about you`}</Title>
      <View style={[styles.row, { marginBottom: 12 }]}>
        <Chip label={chip.label} tone={chip.tone} />
        <Body muted style={{ flex: 1 }}>{user ? `${user.name} · ${pack.identifierLabel} ${user.id}` : ""}</Body>
      </View>

      {settings.memoryEnabled === false ? (
        <Notice>{`This agent does not remember conversations. In the dashboard: Users & memory → Remembers users across chats and calls.`}</Notice>
      ) : null}

      {error ? <ErrorText>{describeError(error, { agentName: pack.agentName })}</ErrorText> : null}
      {!error && !loading && memories.length === 0 ? <Card><Body muted>{memoryEmptyText(pack)}</Body></Card> : null}
      {memories.map((m) => (
        <Card key={m.id} accessibilityLabel={m.text}>
          <Text style={{ color: c.text, fontSize: 15, lineHeight: 21 }}>{m.text}</Text>
          <Body muted style={{ marginTop: 4, fontSize: 12 }}>{`Remembered ${formatWhen(m.updatedAt ?? m.createdAt, now)}`}</Body>
        </Card>
      ))}

      <SectionLabel>Your choice</SectionLabel>
      <Card>
        <ToggleRow
          label={`Let ${pack.agentName} remember our conversations`}
          detail={chip.detail}
          value={consent}
          onChange={(v) => void toggle(v)}
        />
      </Card>
      <Button label={`Erase everything ${pack.agentName} remembers`} kind="danger" onPress={erase} busy={erasing} disabled={memories.length === 0 && !error} />
      {message ? <Body style={{ marginTop: 8 }}>{message}</Body> : null}
      {actionError ? <ErrorText>{actionError}</ErrorText> : null}
    </ScrollView>
  );
}
