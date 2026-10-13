/** Sign in: choose the use case (remembered on this device) and who you are, and whether you may be remembered. */
import { useEffect, useState } from "react";
import { ScrollView, Text, View } from "react-native";
import { useDemo } from "../state/DemoState";
import { PACKS } from "../usecases";
import { isConfigured } from "../config";
import { describeError, useScreenContext } from "../graine";
import { Body, Button, Card, ErrorText, Notice, SectionLabel, styles, Title, ToggleRow, useColors } from "../ui/components";

export function SignInScreen() {
  const { pack, choosePack, signIn } = useDemo();
  const c = useColors();
  const [userId, setUserId] = useState(pack.users[0]?.id ?? "");
  const [consent, setConsent] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useScreenContext({ screen: "sign_in", title: "Sign in" });
  useEffect(() => setUserId(pack.users[0]?.id ?? ""), [pack.id, pack.users]);

  const person = pack.users.find((u) => u.id === userId) ?? null;

  const submit = async () => {
    if (!person) return;
    setBusy(true);
    setError(null);
    try {
      await signIn(person, consent);
    } catch (err) {
      setError(describeError(err, { agentName: pack.agentName }));
      setBusy(false);
    }
  };

  return (
    <ScrollView style={{ backgroundColor: c.background }} contentContainerStyle={styles.screen}>
      <Title>Choose a use case</Title>
      <Body muted>The same app and the same SDK — the agent, the people and the screens come from the use case.</Body>

      <SectionLabel>Use case</SectionLabel>
      {PACKS.map((p) => {
        const selected = p.id === pack.id;
        return (
          <Card
            key={p.id}
            onPress={() => void choosePack(p.id)}
            accessibilityLabel={`${p.appTitle}, ${p.agentName}, ${p.agentRole}${selected ? ", selected" : ""}`}
            style={selected ? { borderColor: c.accent, borderWidth: 2 } : undefined}
          >
            <Text style={{ color: c.text, fontSize: 16, fontWeight: "700" }}>{p.appTitle}</Text>
            <Body muted>{`${p.agentName}, ${p.agentRole}`}</Body>
            {selected ? <Body style={{ marginTop: 6 }}>{p.tagline}</Body> : null}
          </Card>
        );
      })}

      <SectionLabel>{`Sign in as a ${pack.userNoun}`}</SectionLabel>
      {pack.users.map((u) => {
        const selected = u.id === userId;
        return (
          <Card
            key={u.id}
            onPress={() => setUserId(u.id)}
            accessibilityLabel={`${u.name}, ${pack.identifierLabel} ${u.id}${selected ? ", selected" : ""}`}
            style={selected ? { borderColor: c.accent, borderWidth: 2 } : undefined}
          >
            <View style={styles.row}>
              <Text style={{ color: c.text, fontSize: 16, fontWeight: "600", flex: 1 }}>{u.name}</Text>
              <Text style={{ color: c.textMuted, fontSize: 13 }}>{u.id}</Text>
            </View>
            <Body muted>{Object.values(u.traits).join(" · ")}</Body>
          </Card>
        );
      })}

      <Card>
        <ToggleRow
          label={`Let ${pack.agentName} remember our conversations`}
          detail={`${pack.agentName} recalls what you said in earlier chats and calls — for you only. You can see it, switch it off or erase it at any time.`}
          value={consent}
          onChange={setConsent}
        />
      </Card>

      {!isConfigured(pack.id) ? (
        <Notice>{`No publishable key for this use case. Add one to react-native/.env (see .env.example) and restart the bundler.`}</Notice>
      ) : null}
      <Button label="Sign in" onPress={submit} busy={busy} disabled={!person} />
      {error ? <ErrorText>{error}</ErrorText> : null}
    </ScrollView>
  );
}
