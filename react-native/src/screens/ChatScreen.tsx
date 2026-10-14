/**
 * Chat: the conversation with the agent, full screen, with the components the agent shows. Opening it
 * moves the agent to its chat (one conversation at a time, so a voice call ends); the chat stays open
 * while the person moves between screens, so the agent keeps seeing where they are and can act there.
 */
import { useCallback, useEffect, useRef, useState } from "react";
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, Text, TextInput, View } from "react-native";
import { useFocusEffect } from "@react-navigation/native";
import { useDemo } from "../state/DemoState";
import { useVoice } from "../state/voice";
import {
  chatClock,
  chatPieces,
  chatTurnRows,
  describeError,
  GraineWidget,
  useAgentConversation,
  useAgentDesign,
  useAgentSurface,
  useScreenContext,
} from "../graine";
import { Chip, ErrorText, Notice, styles, useColors } from "../ui/components";

export function ChatScreen() {
  const { pack } = useDemo();
  const voice = useVoice();
  const { setSurface } = useAgentSurface();

  useScreenContext({ screen: "chat", title: pack.agentName });
  useFocusEffect(
    useCallback(() => {
      voice.close();
      setSurface("chat");
    }, [voice.close, setSurface]),
  );

  return <ChatWindow agentName={pack.agents.main.name} starters={pack.agents.main.starters} notice={pack.notice} />;
}

/**
 * The chat drawn from the SDK's own conversation state (see graine.ts → Chat), laid out by the SDK's own
 * rules, so a conversation that continues looks as it does in the SDK's chat windows: the earlier messages
 * under a day line where the day changes, an earlier call as one "Voice call" row, no starters, and
 * "New conversation" in the header. It talks to whichever agent's <GraineProvider> is above it: the main
 * agent here, the help agent on Help.
 */
export function ChatWindow({ agentName, starters, notice }: { agentName: string; starters: string[]; notice?: string }) {
  const c = useColors();
  const { messages, send, connected, connecting, typing, reconnecting, error, newConversation } = useAgentConversation();
  // What the brand chose in the dashboard (Chat window: Earlier messages, New conversation), as the SDK reads it.
  const pieces = chatPieces(messages, useAgentDesign().chat);
  const [text, setText] = useState("");
  const [startingOver, setStartingOver] = useState(false);
  const [startOverError, setStartOverError] = useState<string | null>(null);
  const scroll = useRef<ScrollView>(null);

  useEffect(() => {
    const t = setTimeout(() => scroll.current?.scrollToEnd({ animated: true }), 50);
    return () => clearTimeout(t);
  }, [messages.length, typing]);

  const submit = (value: string) => {
    const v = value.trim();
    if (!v) return;
    send(v);
    setText("");
  };

  // Graine lets go of the conversation it kept for this person and agent; the chat clears and opens fresh,
  // with the welcome and the starters. What the agent remembers about them is untouched.
  const startOver = async () => {
    setStartOverError(null);
    setStartingOver(true);
    try {
      await newConversation();
    } catch (err) {
      setStartOverError(describeError(err, { agentName }));
    } finally {
      setStartingOver(false);
    }
  };

  const status = error ? error : connected ? "Online" : reconnecting ? "Reconnecting…" : connecting ? "Connecting…" : "Offline";

  return (
    <KeyboardAvoidingView style={{ flex: 1, backgroundColor: c.background }} behavior={Platform.OS === "ios" ? "padding" : undefined} keyboardVerticalOffset={90}>
      <View style={{ paddingHorizontal: 16, paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: c.border, backgroundColor: c.surface }}>
        <View style={styles.row}>
          <Text accessibilityLiveRegion="polite" style={{ color: c.textMuted, fontSize: 13, flex: 1 }}>{status}</Text>
          {/* Offered while an earlier conversation is on screen, and where the brand keeps it. */}
          {pieces.newConversation ? (
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="New conversation"
              accessibilityState={{ busy: startingOver }}
              disabled={startingOver}
              hitSlop={10}
              onPress={() => void startOver()}
            >
              <Text style={{ color: c.accent, fontSize: 13, fontWeight: "600", opacity: startingOver ? 0.5 : 1 }}>New conversation</Text>
            </Pressable>
          ) : null}
        </View>
        {startOverError ? <ErrorText>{startOverError}</ErrorText> : null}
      </View>
      <ScrollView ref={scroll} contentContainerStyle={{ padding: 16, gap: 8 }}>
        {notice ? <Notice>{notice}</Notice> : null}
        {chatTurnRows(pieces.turns).map((row) => {
          if (row.kind === "day") {
            return (
              <Text key={row.key} accessibilityRole="header" style={{ alignSelf: "center", color: c.textMuted, fontSize: 12, fontWeight: "600", marginVertical: 4 }}>
                {row.label}
              </Text>
            );
          }
          if (row.kind === "voice") {
            // An earlier call: that it happened, never its transcript.
            return (
              <Text key={row.key} accessibilityLabel={`Voice call at ${chatClock(row.at)}`} style={{ alignSelf: "center", color: c.textMuted, fontSize: 12 }}>
                {`☏ ${row.label}`}
              </Text>
            );
          }
          const m = row.item;
          const mine = m.role === "customer";
          return (
            <View
              key={row.key}
              accessible
              accessibilityLabel={`${mine ? "You" : agentName}: ${m.text}`}
              style={{
                alignSelf: mine ? "flex-end" : "flex-start",
                maxWidth: "85%",
                backgroundColor: mine ? c.accent : c.surface,
                borderColor: c.border,
                borderWidth: mine ? 0 : 1,
                borderRadius: 16,
                paddingHorizontal: 12,
                paddingVertical: 8,
                opacity: m.live ? 0.85 : 1,
              }}
            >
              <Text style={{ color: mine ? c.onAccent : c.text, fontSize: 15, lineHeight: 21 }}>{m.text}</Text>
              {m.pending ? <Text style={{ color: mine ? c.onAccent : c.textMuted, fontSize: 11, marginTop: 2 }}>Waiting for connection…</Text> : null}
            </View>
          );
        })}
        {/* The starters belong to a new chat: not once the person has said something, not on a conversation that continues. */}
        {pieces.starters ? (
          <View style={{ gap: 8 }}>
            <Text style={{ color: c.textMuted, fontSize: 15 }}>{`Ask ${agentName} anything, or start with one of these:`}</Text>
            <View style={styles.wrap}>
              {starters.map((s) => <Chip key={s} label={s} onPress={() => submit(s)} />)}
            </View>
          </View>
        ) : null}
        {typing ? <Text style={{ color: c.textMuted, fontSize: 13 }}>{`${agentName} is typing…`}</Text> : null}
        {/* The agent's components: check-in cards, lists, time slots — drawn by the SDK. */}
        <GraineWidget />
      </ScrollView>
      <View style={{ flexDirection: "row", gap: 8, padding: 12, paddingBottom: 20, borderTopWidth: 1, borderTopColor: c.border, backgroundColor: c.surface }}>
        <TextInput
          accessibilityLabel="Message"
          value={text}
          onChangeText={setText}
          onSubmitEditing={() => submit(text)}
          placeholder={`Message ${agentName}`}
          placeholderTextColor={c.textMuted}
          returnKeyType="send"
          style={{ flex: 1, color: c.text, backgroundColor: c.surfaceMuted, borderRadius: 20, paddingHorizontal: 14, paddingVertical: 10, fontSize: 15 }}
        />
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Send"
          onPress={() => submit(text)}
          disabled={!text.trim()}
          style={{ backgroundColor: c.accent, borderRadius: 20, paddingHorizontal: 16, justifyContent: "center", opacity: text.trim() ? 1 : 0.5 }}
        >
          <Text style={{ color: c.onAccent, fontWeight: "600" }}>Send</Text>
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}
