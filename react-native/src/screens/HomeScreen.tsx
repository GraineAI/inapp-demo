/** Home: who is signed in, whether they are remembered, the next booked call, and the three ways to reach the agent. */
import { useCallback, useState } from "react";
import { Pressable, RefreshControl, ScrollView, Text, View } from "react-native";
import { useFocusEffect, useNavigation } from "@react-navigation/native";
import type { BottomTabNavigationProp } from "@react-navigation/bottom-tabs";
import { useDemo } from "../state/DemoState";
import { useVoice } from "../state/voice";
import { callsAvailable, describeError, placeCall, useAgentSurface, useHelpCalls, useScheduling, useScreenContext } from "../graine";
import { mergeAgentCalls } from "../logic/scheduling";
import { memoryChip } from "../logic/memory";
import { formatWhen } from "../logic/time";
import { Body, BookingButton, Button, Card, Chip, ErrorText, Notice, SectionLabel, styles, Title, useColors } from "../ui/components";
import type { TabParamList } from "../navigation";

export function HomeScreen() {
  const { pack, user, consent, settings } = useDemo();
  const c = useColors();
  const voice = useVoice();
  const { surfaces } = useAgentSurface();
  const navigation = useNavigation<BottomTabNavigationProp<TabParamList>>();
  const { calls, loading, refresh, bookingAvailable, openBooking } = useScheduling();
  const helpCalls = useHelpCalls();
  const [error, setError] = useState<string | null>(null);

  const chip = memoryChip({ agentMemory: settings.memoryEnabled, consent });
  // The next call from either agent.
  const nextOne = mergeAgentCalls(calls, helpCalls.calls).find((x) => x.call.isUpcoming) ?? null;
  const next = nextOne?.call ?? null;
  const nextAgent = nextOne ? (nextOne.agent === "main" ? pack.agents.main.name : pack.agents.help.name) : null;

  useFocusEffect(useCallback(() => { void refresh(); void helpCalls.refresh(); }, [refresh, helpCalls.refresh]));

  useScreenContext({
    screen: "home",
    title: pack.home.title,
    fields: [
      { name: "memory", label: "Memory", value: chip.label === "Memory on" ? "on" : "off", status: "filled" },
      { name: "next_call", label: "Next call", value: next?.at ? next.at.toISOString() : "none", status: "filled" },
    ],
  });

  const call = async () => {
    setError(null);
    try {
      const id = await placeCall(pack.call.placeReason);
      if (!id) setError("A call is already in progress.");
    } catch (err) {
      setError(describeError(err, { agentName: pack.agentName }));
    }
  };

  /** The pack's booking button: the SDK's booking sheet, starting with the pack's reason; then the next call again. */
  const book = async () => {
    setError(null);
    const booked = await openBooking({ reason: pack.call.reasons[0] });
    if (booked) void refresh();
  };

  const firstName = user?.name.split(" ")[0] ?? "";
  const canCall = callsAvailable();

  return (
    <ScrollView
      style={{ backgroundColor: c.background }}
      contentContainerStyle={styles.screen}
      refreshControl={<RefreshControl refreshing={loading} onRefresh={() => void refresh()} />}
    >
      <Title>{`Hello, ${firstName}`}</Title>
      <View style={[styles.row, { marginBottom: 12 }]}>
        <Pressable accessibilityRole="button" accessibilityLabel={`${chip.label}. ${chip.detail}`} onPress={() => navigation.getParent()?.navigate("memory")}>
          <Chip label={chip.label} tone={chip.tone} />
        </Pressable>
        <Body muted style={{ flex: 1 }}>{chip.detail}</Body>
      </View>

      {pack.notice ? <Notice>{pack.notice}</Notice> : null}

      <Card onPress={() => navigation.navigate("calls")} accessibilityLabel="Next call">
        <SectionLabel>Next call</SectionLabel>
        {next ? (
          <>
            <Text style={{ color: c.text, fontSize: 17, fontWeight: "600" }}>{formatWhen(next.at, new Date())}</Text>
            <Body muted>{[nextAgent, next.reason].filter(Boolean).join(" · ")}</Body>
          </>
        ) : (
          <Body muted>{`No calls booked. ${pack.agentName} can call you at a time you choose.`}</Body>
        )}
      </Card>

      <View style={{ gap: 10, marginVertical: 8 }}>
        <Button label={`Talk to ${pack.agentName}`} onPress={voice.open} disabled={!voice.available} />
        <Button label="Chat" kind="secondary" onPress={() => navigation.navigate("chat")} disabled={!surfaces.includes("chat")} />
        <Button label={`Call ${pack.agentName}`} kind="secondary" onPress={call} disabled={!canCall} />
        <BookingButton label={pack.call.bookLabel} kind="secondary" available={bookingAvailable} onPress={() => void book()} />
        <Button label={`Help · ${pack.agents.help.name}`} kind="secondary" onPress={() => navigation.getParent()?.navigate("help")} />
      </View>
      {!canCall ? <Body muted>Calls need a development build with the calls package (not Expo Go).</Body> : null}
      {error ? <ErrorText>{error}</ErrorText> : null}

      <SectionLabel>{pack.home.title}</SectionLabel>
      {pack.home.cards.map((card) => (
        <Card
          key={card.id}
          accessibilityLabel={card.title}
          onPress={() => (card.target === "memory" ? navigation.getParent()?.navigate("memory") : navigation.navigate(card.target))}
        >
          <Text style={{ color: c.text, fontSize: 16, fontWeight: "600" }}>{card.title}</Text>
          <Body muted>{card.body}</Body>
        </Card>
      ))}
    </ScrollView>
  );
}
