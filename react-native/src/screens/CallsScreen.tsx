/**
 * Scheduled calls: what is booked and what became of each ring (status chips, Cancel where the SDK
 * says canCancel), the booking sheet, and the call engine's own log of rings and outcomes. The calls,
 * their rules and every check come from the SDK (useScheduling); this screen only presents them.
 */
import { useCallback, useState } from "react";
import { RefreshControl, ScrollView, Text, View } from "react-native";
import { useFocusEffect, useNavigation, useRoute, type RouteProp } from "@react-navigation/native";
import type { BottomTabNavigationProp } from "@react-navigation/bottom-tabs";
import { useDemo } from "../state/DemoState";
import { useCallLog } from "../state/callLog";
import { describeError, useHelpCalls, useScheduling, useScreenContext, type ScheduledCall } from "../graine";
import { callEventLine, deviceTimeZone, mergeAgentCalls, sourceLabel, statusChip, zoneLabel } from "../logic/scheduling";
import { formatWhen } from "../logic/time";
import type { TabParamList } from "../navigation";
import { BookingSheet } from "./BookingSheet";
import { Body, Button, Card, Chip, ErrorText, SectionLabel, styles, useColors } from "../ui/components";

export function CallsScreen() {
  const { pack } = useDemo();
  const c = useColors();
  const log = useCallLog();
  const route = useRoute<RouteProp<TabParamList, "calls">>();
  const navigation = useNavigation<BottomTabNavigationProp<TabParamList, "calls">>();
  const { rules, calls, schedule, cancel, refresh, loading, error: loadError } = useScheduling();
  // The help agent's bookings live with that agent: read with its client and listed beside the main agent's.
  const helpCalls = useHelpCalls();
  const [sheet, setSheet] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const now = new Date();

  useFocusEffect(
    useCallback(() => {
      void refresh();
      void helpCalls.refresh();
      // Home's booking button lands here with the sheet open.
      if (route.params?.book) {
        setSheet(true);
        navigation.setParams({ book: undefined });
      }
    }, [refresh, helpCalls.refresh, route.params?.book, navigation]),
  );

  const merged = mergeAgentCalls(calls, helpCalls.calls);
  const upcoming = merged.filter((x) => x.call.isUpcoming);
  const past = merged.filter((x) => !x.call.isUpcoming);
  const agentName = (agent: "main" | "help") => (agent === "main" ? pack.agents.main.name : pack.agents.help.name);
  const ctx = { agentName: pack.agentName, minLeadMinutes: rules.minLeadMinutes, maxDaysAhead: rules.maxDaysAhead, maxPendingPerUser: rules.maxPendingPerUser };

  useScreenContext({
    screen: "calls",
    title: pack.call.title,
    fields: [{ name: "upcoming", label: "Upcoming calls", value: upcoming.length, status: "filled" }],
  });

  const cancelCall = async (id: string, agent: "main" | "help") => {
    setError(null);
    setMessage(null);
    try {
      const status = agent === "main" ? await cancel(id) : await helpCalls.cancel(id);
      if (agent === "help") void helpCalls.refresh();
      setMessage(`Call ${statusChip(status).label.toLowerCase()}.`);
    } catch (err) {
      setError(describeError(err, ctx));
    }
  };

  const booked = (call: ScheduledCall) => {
    setError(null);
    setMessage(call.at ? `Booked: ${zoneLabel(call.at, deviceTimeZone(), new Date())}.` : "Booked.");
  };

  return (
    <ScrollView
      style={{ backgroundColor: c.background }}
      contentContainerStyle={styles.screen}
      refreshControl={<RefreshControl refreshing={loading} onRefresh={() => void refresh()} />}
    >
      <Body muted>{`${pack.agentName} calls this phone at the time you book. It rings like a phone call, even when the app is closed.`}</Body>
      <View style={{ marginTop: 12 }}>
        <Button label={pack.call.bookLabel} onPress={() => { setMessage(null); setSheet(true); }} />
      </View>
      {message ? <Body style={{ marginTop: 8 }}>{message}</Body> : null}
      {error ? <ErrorText>{error}</ErrorText> : null}

      <SectionLabel>Upcoming</SectionLabel>
      {loadError ? <ErrorText>{describeError(loadError, ctx)}</ErrorText> : null}
      {helpCalls.error ? <ErrorText>{describeError(helpCalls.error, { agentName: pack.agents.help.name })}</ErrorText> : null}
      {upcoming.length === 0 && !loadError ? <Body muted>Nothing booked.</Body> : null}
      {upcoming.map((x) => (
        <CallRow key={`${x.agent}-${x.call.id}`} call={x.call} agentName={agentName(x.agent)} now={now} onCancel={x.call.canCancel ? () => void cancelCall(x.call.id, x.agent) : undefined} />
      ))}

      {past.length ? <SectionLabel>Earlier</SectionLabel> : null}
      {past.map((x) => <CallRow key={`${x.agent}-${x.call.id}`} call={x.call} agentName={agentName(x.agent)} now={now} />)}

      <SectionLabel>Call activity on this phone</SectionLabel>
      {log.length === 0 ? <Body muted>Rings and their outcomes appear here.</Body> : null}
      {log.map((e, i) => (
        <Body key={`${e.callId}-${i}`} muted>{callEventLine(e, { main: pack.agents.main.name, help: pack.agents.help.name })}</Body>
      ))}

      <BookingSheet
        visible={sheet}
        agent={{ name: pack.agents.main.name, bookLabel: pack.call.bookLabel, reasons: pack.call.reasons }}
        rules={rules}
        schedule={schedule}
        onClose={() => setSheet(false)}
        onBooked={booked}
      />
    </ScrollView>
  );
}

function CallRow({ call, agentName, now, onCancel }: { call: ScheduledCall; agentName: string; now: Date; onCancel?: () => void }) {
  const c = useColors();
  const chip = statusChip(call.status);
  const by = sourceLabel(call.source);
  return (
    <Card accessibilityLabel={`${agentName}, ${formatWhen(call.at, now)}, ${chip.label}`}>
      <View style={styles.row}>
        <Text style={{ color: c.text, fontSize: 16, fontWeight: "600", flex: 1 }}>{formatWhen(call.at, now)}</Text>
        <Chip label={chip.label} tone={chip.tone} />
      </View>
      <Body muted>{call.reason ? `${agentName} · ${call.reason}` : agentName}</Body>
      {by ? <Body muted>{by}</Body> : null}
      {onCancel ? <View style={{ marginTop: 10 }}><Button label="Cancel call" kind="danger" onPress={onCancel} /></View> : null}
    </Card>
  );
}
