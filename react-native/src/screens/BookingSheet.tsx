/**
 * The booking sheet: a reason, a one-tap time or the platform's own date and time pickers (5-minute
 * steps, bounded by the SDK's earliest and latest), the time zone it is booked in, then
 * schedule(at, reason). The SDK checks the booking; this sheet shows its answer in plain words.
 */
import { useEffect, useMemo, useState } from "react";
import { Modal, Platform, ScrollView, Text, View } from "react-native";
import DateTimePicker, { DateTimePickerAndroid, type DateTimePickerEvent } from "@react-native-community/datetimepicker";
import { useDemo } from "../state/DemoState";
import { describeError, type ScheduledCall, type SchedulingRules } from "../graine";
import { deviceTimeZone, inRange, pickerRange, quickTimeAt, snapToRange, zoneLabel } from "../logic/scheduling";
import { formatTime } from "../logic/time";
import { Body, Button, Chip, ErrorText, Notice, SectionLabel, styles, Title, useColors } from "../ui/components";

interface Props {
  visible: boolean;
  /** Which agent books it — its name, button label and reasons (the main agent's from `call`, the help agent's from `agents.help`). */
  agent: { name: string; bookLabel: string; reasons: string[] };
  rules: SchedulingRules;
  schedule: (at: Date, reason?: string) => Promise<ScheduledCall>;
  onClose: () => void;
  onBooked: (call: ScheduledCall) => void;
}

export function BookingSheet({ visible, agent, rules, schedule, onClose, onBooked }: Props) {
  const { pack } = useDemo();
  const c = useColors();
  const zone = deviceTimeZone();
  const [now, setNow] = useState(() => new Date());
  const [reason, setReason] = useState(agent.reasons[0] ?? "");
  const [at, setAt] = useState<Date | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // A fresh "now" each time the sheet opens, so the bounds are today's.
  useEffect(() => {
    if (!visible) return;
    setNow(new Date());
    setAt(null);
    setError(null);
    setReason(agent.reasons[0] ?? "");
  }, [visible, agent.reasons]);

  const range = useMemo(() => pickerRange(rules.earliest(now), rules.latest(now)), [rules, now]);
  const quick = useMemo(() => pack.call.quickTimes.map((q) => ({ label: q.label, at: quickTimeAt(q, now) })), [pack.call.quickTimes, now]);
  const ctx = { agentName: agent.name, minLeadMinutes: rules.minLeadMinutes, maxDaysAhead: rules.maxDaysAhead, maxPendingPerUser: rules.maxPendingPerUser };

  const picked = (_e: DateTimePickerEvent, d?: Date) => {
    if (d && range) setAt(snapToRange(d, range));
  };

  const openAndroid = (mode: "date" | "time") => {
    if (!range) return;
    DateTimePickerAndroid.open({
      value: at ?? range.min,
      mode,
      minimumDate: range.min,
      maximumDate: range.max,
      minuteInterval: 5,
      onChange: (e, d) => {
        if (e.type === "set" && d) picked(e, d);
      },
    });
  };

  const book = async () => {
    if (!at) return;
    setBusy(true);
    setError(null);
    try {
      const call = await schedule(at, reason || undefined);
      onBooked(call);
      onClose();
    } catch (err) {
      setError(describeError(err, ctx));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal visible={visible} animationType="slide" presentationStyle="pageSheet" onRequestClose={onClose}>
      <ScrollView style={{ backgroundColor: c.background }} contentContainerStyle={[styles.screen, { paddingBottom: 48 }]}>
        <Title>{agent.bookLabel}</Title>
        <Body muted>{`${agent.name} calls this phone at the time you pick. It rings like a phone call, even when the app is closed.`}</Body>
        {!rules.usersCanBook ? (
          <Notice>{`${agent.name} does not take bookings from the app. In the dashboard: Calls & scheduling → Who may book a call → Users, from your app.`}</Notice>
        ) : null}

        <SectionLabel>Reason</SectionLabel>
        <View style={styles.wrap}>
          {agent.reasons.map((r) => <Chip key={r} label={r} selected={r === reason} onPress={() => setReason(r)} />)}
        </View>

        <SectionLabel>When</SectionLabel>
        <View style={styles.wrap}>
          {quick.map((q) => (
            <Chip
              key={q.label}
              label={q.label}
              selected={at?.getTime() === q.at.getTime()}
              disabled={!inRange(q.at, range)}
              onPress={() => setAt(q.at)}
              accessibilityLabel={`${q.label}, ${formatTime(q.at)}`}
            />
          ))}
        </View>

        {range ? (
          Platform.OS === "ios" ? (
            <DateTimePicker
              value={at ?? range.min}
              mode="datetime"
              display="inline"
              minuteInterval={5}
              minimumDate={range.min}
              maximumDate={range.max}
              accentColor={c.accent}
              onChange={picked}
            />
          ) : (
            <View style={[styles.row, { marginTop: 12 }]}>
              <View style={{ flex: 1 }}><Button label="Pick a date" kind="secondary" onPress={() => openAndroid("date")} /></View>
              <View style={{ flex: 1 }}><Button label="Pick a time" kind="secondary" onPress={() => openAndroid("time")} /></View>
            </View>
          )
        ) : (
          <Body muted>No time can be booked right now.</Body>
        )}

        <Text accessibilityLiveRegion="polite" style={{ color: c.text, fontSize: 16, fontWeight: "600", marginTop: 16 }}>
          {at ? zoneLabel(at, zone, now) : `Times are in ${zone}.`}
        </Text>

        <View style={{ gap: 10, marginTop: 16 }}>
          <Button label={at ? `Book for ${formatTime(at)}` : "Pick a time"} onPress={book} busy={busy} disabled={!at || !rules.usersCanBook} />
          <Button label="Not now" kind="secondary" onPress={onClose} />
        </View>
        {error ? <ErrorText>{error}</ErrorText> : null}
      </ScrollView>
    </Modal>
  );
}
