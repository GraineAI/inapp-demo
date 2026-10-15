/**
 * Help: the pack's SECOND agent (agents.help — for example "Billing & appointments help" beside Asha). Its
 * chat, voice and calls use its own client under its own <GraineProvider>, while Home and Chat keep the
 * main agent. The person signed in once: both agents know who they are. One conversation at a time:
 * opening Help ends the main agent's voice and chat.
 */
import { useCallback, useState } from "react";
import { View } from "react-native";
import { useFocusEffect } from "@react-navigation/native";
import * as Font from "expo-font";
import Svg, { Path } from "react-native-svg";
import { WebView } from "react-native-webview";
import { useDemo } from "../state/DemoState";
import { useVoice } from "../state/voice";
import {
  callsAvailable,
  describeError,
  GraineProvider,
  GraineVoiceBar,
  GraineVoiceLauncher,
  placeCall,
  useAgentConversation,
  useAgentSurface,
  useScreenContext,
} from "../graine";
import { formatWhen } from "../logic/time";
import { ChatWindow } from "./ChatScreen";
import { Body, BookingButton, Button, ErrorText, Notice, styles, useColors } from "../ui/components";

export function HelpScreen() {
  const { pack, helpClient } = useDemo();
  const voice = useVoice();
  const { setSurface } = useAgentSurface(); // the MAIN agent's
  const c = useColors();
  const help = pack.agents.help;

  useScreenContext({ screen: "help", title: help.name });
  useFocusEffect(
    useCallback(() => {
      // One conversation at a time across both agents.
      voice.close();
      setSurface("voice");
    }, [voice.close, setSurface]),
  );

  if (!helpClient) {
    return (
      <View style={[styles.screen, { flex: 1, backgroundColor: c.background }]}>
        <Notice>
          {`This app has no key for ${help.name} yet. Create the help agent from agent-setup/${pack.id}/help-settings.md, ` +
            `then add its publishable key to react-native/.env as EXPO_PUBLIC_GRAINE_HELP_KEY (or one key per use case — ` +
            `see .env.example) and restart the bundler. Everything else in the app works without it.`}
        </Notice>
      </View>
    );
  }

  return (
    // The help agent's own provider: its session, chat, voice and actions — keyed by its client.
    <GraineProvider key={pack.id} client={helpClient} autoConnect={false} loadFont={Font.loadAsync} svg={{ Svg, Path }}>
      <HelpBody />
    </GraineProvider>
  );
}

function HelpBody() {
  const { pack } = useDemo();
  const c = useColors();
  const help = pack.agents.help;
  const { surfaces, setSurface } = useAgentSurface(); // the HELP agent's, from the provider above
  // Booking with the help agent: its own sheet, drawn by the provider above.
  const { bookingAvailable, openBooking } = useAgentConversation();
  const [talking, setTalking] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // The chat with the help agent is open while Help is on screen.
  useFocusEffect(
    useCallback(() => {
      setSurface("chat");
    }, [setSurface]),
  );

  const talk = () => {
    setSurface("voice");
    setTalking(true);
  };
  const stopTalking = () => {
    setTalking(false);
    setSurface("chat");
  };

  const call = async () => {
    setError(null);
    try {
      const id = await placeCall(help.placeReason, "help");
      if (!id) setError("A call is already in progress.");
    } catch (err) {
      setError(describeError(err, { agentName: help.name }));
    }
  };

  const book = async () => {
    setMessage(null);
    setError(null);
    const booked = await openBooking({ reason: help.reasons[0] });
    // The Calls screen hears the booking from the help agent's client and reads its list again.
    if (booked) setMessage(`Booked with ${help.name} for ${formatWhen(booked.at, new Date())}. It is listed under ${pack.call.title}.`);
  };

  return (
    <View style={{ flex: 1, backgroundColor: c.background }}>
      <View style={{ padding: 16, gap: 8, borderBottomWidth: 1, borderBottomColor: c.border, backgroundColor: c.surface }}>
        <Body muted>{`${help.name}, ${help.role}. You are signed in here too — no need to say who you are.`}</Body>
        <View style={styles.row}>
          <View style={{ flex: 1 }}><Button label="Talk" onPress={talk} disabled={!surfaces.includes("voice")} /></View>
          <View style={{ flex: 1 }}><Button label="Call" kind="secondary" onPress={call} disabled={!callsAvailable()} /></View>
        </View>
        <BookingButton label={help.bookLabel} kind="secondary" available={bookingAvailable} onPress={() => void book()} />
        {message ? <Body>{message}</Body> : null}
        {error ? <ErrorText>{error}</ErrorText> : null}
      </View>
      <View style={{ flex: 1 }}>
        <ChatWindow agentName={help.name} starters={help.starters} />
      </View>
      <GraineVoiceLauncher webView={WebView} active={talking} autoStart onEnded={stopTalking} onError={(m) => console.warn("[Graine Demo] help:", m)}>
        {(api) => (talking ? <GraineVoiceBar api={api} onClose={stopTalking} /> : null)}
      </GraineVoiceLauncher>
    </View>
  );
}
