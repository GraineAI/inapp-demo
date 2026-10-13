/** Profile: who is signed in (as the agent knows them), memory on or off, erase, this device, sign out. */
import { useEffect, useState } from "react";
import { ScrollView, Text, View } from "react-native";
import { useNavigation } from "@react-navigation/native";
import { useDemo } from "../state/DemoState";
import { describeError, eraseMemories, installId, useScreenContext } from "../graine";
import { confirmErase } from "../ui/confirm";
import { Body, Button, Card, ErrorText, SectionLabel, styles, Title, ToggleRow, useColors } from "../ui/components";

const label = (key: string) => key.replace(/_/g, " ").replace(/^./, (ch) => ch.toUpperCase());

export function ProfileScreen() {
  const { pack, user, consent, setConsent, signOut } = useDemo();
  const c = useColors();
  const navigation = useNavigation();
  const [device, setDevice] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useScreenContext({ screen: "profile", title: "Profile" });
  useEffect(() => { installId().then(setDevice, () => setDevice(null)); }, [pack.id]);

  if (!user) return null;

  const erase = async () => {
    setMessage(null);
    setError(null);
    if (!(await confirmErase(pack.agentName))) return;
    try {
      const deleted = await eraseMemories();
      setMessage(deleted === null ? "Erased." : `${deleted} ${deleted === 1 ? "memory" : "memories"} erased.`);
    } catch (err) {
      setError(describeError(err, { agentName: pack.agentName }));
    }
  };

  return (
    <ScrollView style={{ backgroundColor: c.background }} contentContainerStyle={styles.screen}>
      <Title>{user.name}</Title>
      <Body muted>{`${pack.identifierLabel} ${user.id}`}</Body>

      <SectionLabel>{`What ${pack.agentName} knows`}</SectionLabel>
      <Card>
        {Object.entries(user.traits).map(([k, v]) => (
          <View key={k} style={[styles.row, { paddingVertical: 4 }]}>
            <Text style={{ color: c.textMuted, width: 120, fontSize: 14 }}>{label(k)}</Text>
            <Text style={{ color: c.text, flex: 1, fontSize: 14 }}>{v}</Text>
          </View>
        ))}
      </Card>

      <SectionLabel>Memory</SectionLabel>
      <Card>
        <ToggleRow
          label={`Let ${pack.agentName} remember our conversations`}
          value={consent}
          onChange={(v) => void setConsent(v).catch((err) => setError(describeError(err, { agentName: pack.agentName })))}
        />
      </Card>
      <View style={{ gap: 10 }}>
        <Button label={`See what ${pack.agentName} remembers`} kind="secondary" onPress={() => navigation.getParent()?.navigate("memory")} />
        <Button label={`Erase what ${pack.agentName} remembers`} kind="danger" onPress={erase} />
      </View>
      {message ? <Body style={{ marginTop: 8 }}>{message}</Body> : null}
      {error ? <ErrorText>{error}</ErrorText> : null}

      <SectionLabel>This device</SectionLabel>
      <Body muted>{device ? `Install ID ${device}` : "No install ID yet."}</Body>

      <View style={{ marginTop: 24 }}>
        <Button label="Sign out" kind="secondary" onPress={() => void signOut()} />
      </View>
      <Body muted style={{ marginTop: 8 }}>{`Signing out releases this device: it stops ringing for ${user.name.split(" ")[0]} and no longer counts as one of their devices.`}</Body>
    </ScrollView>
  );
}
