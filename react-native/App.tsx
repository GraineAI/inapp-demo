/**
 * Graine Demo — one generic app; the use-case pack decides the agent, the people and the words.
 *
 * The SDK pieces, from the outside in: GraineProvider (the agent: identity, screen context, actions,
 * chat) with the current pack's client; the screens; the voice bar at the root so a call outlives
 * every screen; the calls package configured and attached; the pack's actions registered app-wide.
 */
import { useEffect, useRef } from "react";
import { ActivityIndicator, View, useColorScheme } from "react-native";
import { DarkTheme, DefaultTheme, NavigationContainer, useNavigationContainerRef } from "@react-navigation/native";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { StatusBar } from "expo-status-bar";
import * as Font from "expo-font";
import Svg, { Path } from "react-native-svg";
import { WebView } from "react-native-webview";

import * as graine from "./src/graine";
import { GraineProvider, GraineVoiceBar, GraineVoiceLauncher } from "./src/graine";
import { DemoStateProvider, useDemo } from "./src/state/DemoState";
import { VoiceProvider, useVoice } from "./src/state/voice";
import { logCallEvent } from "./src/state/callLog";
import { runPackAction } from "./src/logic/actions";
import { go, RootNavigator, type RootStackParamList, type RootNavigation } from "./src/navigation";
import { usePalette } from "./src/ui/theme";

export default function App() {
  return (
    <SafeAreaProvider>
      <DemoStateProvider>
        <AppRoot />
      </DemoStateProvider>
    </SafeAreaProvider>
  );
}

function AppRoot() {
  const demo = useDemo();
  const scheme = useColorScheme();
  const colors = usePalette(demo.pack.accent);
  const navigationRef = useNavigationContainerRef<RootStackParamList>();

  if (!demo.ready) {
    return (
      <View style={{ flex: 1, alignItems: "center", justifyContent: "center", backgroundColor: colors.background }}>
        <ActivityIndicator color={colors.accent} />
      </View>
    );
  }

  const base = scheme === "dark" ? DarkTheme : DefaultTheme;
  return (
    // Keyed by pack: another pack is another agent, so the provider starts over with its client.
    <GraineProvider
      key={demo.pack.id}
      client={demo.client}
      navigationRef={navigationRef}
      autoConnect={false}
      loadFont={Font.loadAsync}
      svg={{ Svg, Path }}
    >
      <VoiceProvider>
        <NavigationContainer
          ref={navigationRef}
          theme={{ ...base, colors: { ...base.colors, primary: colors.accent, background: colors.background, card: colors.surface, text: colors.text, border: colors.border } }}
        >
          <RootNavigator />
        </NavigationContainer>
        <PackActions navigation={navigationRef} />
        <VoiceBar />
        <CallsBridge />
        <StatusBar style="auto" />
      </VoiceProvider>
    </GraineProvider>
  );
}

/** The voice bar, mounted early and idle so a tap on "Talk" connects at once. */
function VoiceBar() {
  const { user } = useDemo();
  const voice = useVoice();
  if (!user) return null;
  return (
    <GraineVoiceLauncher
      webView={WebView}
      active={voice.isOpen}
      autoStart
      onEnded={voice.close}
      onError={(m) => console.warn("[Graine Demo]", m)}
    >
      {(api) => (voice.isOpen ? <GraineVoiceBar api={api} onClose={voice.close} /> : null)}
    </GraineVoiceLauncher>
  );
}

/**
 * The pack's actions, registered while someone is signed in, all run by the same rules
 * (logic/actions.ts): open a screen, open an item, set an item's status.
 */
function PackActions({ navigation }: { navigation: RootNavigation }) {
  const { pack, user, rows, statuses, setRowStatus, setOpenRow } = useDemo();
  const latest = useRef({ rows, statuses });
  latest.current = { rows, statuses };

  useEffect(() => {
    if (!user) return;
    return graine.registerPackActions(pack, (action, args) => {
      const { answer, effect } = runPackAction(pack, action, args, latest.current.rows, latest.current.statuses);
      if (effect?.type === "navigate") go(navigation, effect.screen);
      if (effect?.type === "open_item") {
        setOpenRow(effect.rowId);
        go(navigation, "items");
      }
      if (effect?.type === "set_status") setRowStatus(effect.rowId, effect.status);
      return answer;
    });
  }, [pack, user, navigation, setRowStatus, setOpenRow]);
  return null;
}

/**
 * Calls: configured for the current pack's agents (the main one and the help one) at launch and on every
 * pack change, both agents attached, outcomes logged with the agent each call was with.
 */
function CallsBridge() {
  const { pack, client, helpClient, settings } = useDemo();
  useEffect(() => graine.onCallEvents(logCallEvent), []);
  useEffect(() => {
    let detach: () => void = () => {};
    let cancelled = false;
    graine
      .setUpCalls(pack, settings.callName ?? pack.call.callName)
      .then((ok) => {
        if (ok && !cancelled) detach = graine.attachCalls(client, helpClient);
      })
      .catch((err) => console.warn("[Graine Demo] Calls could not be set up:", graine.describeError(err)));
    return () => {
      cancelled = true;
      detach();
    };
  }, [pack, client, helpClient, settings.callName]);
  return null;
}
