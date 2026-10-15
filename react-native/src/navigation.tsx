/**
 * Sign in, then tabs (Home · Chat · Items · Scheduled calls · Profile) and Memory above them. Route
 * names are the generic screen keys, which is also what the SDK reports while a screen has not
 * described itself in more detail.
 */
import type { NavigationContainerRefWithCurrent, NavigatorScreenParams } from "@react-navigation/native";
import { createBottomTabNavigator } from "@react-navigation/bottom-tabs";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { Text } from "react-native";
import { useDemo } from "./state/DemoState";
import { useColors } from "./ui/components";
import { screenTitle, type ScreenKey } from "./logic/pack";
import { SignInScreen } from "./screens/SignInScreen";
import { HomeScreen } from "./screens/HomeScreen";
import { ChatScreen } from "./screens/ChatScreen";
import { ItemsScreen } from "./screens/ItemsScreen";
import { CallsScreen } from "./screens/CallsScreen";
import { MemoryScreen } from "./screens/MemoryScreen";
import { ProfileScreen } from "./screens/ProfileScreen";
import { HelpScreen } from "./screens/HelpScreen";

export type TabParamList = { home: undefined; chat: undefined; items: undefined; calls: undefined; profile: undefined };
export type RootStackParamList = { sign_in: undefined; main: NavigatorScreenParams<TabParamList>; memory: undefined; help: undefined };
export type RootNavigation = NavigationContainerRefWithCurrent<RootStackParamList>;

const Stack = createNativeStackNavigator<RootStackParamList>();
const Tabs = createBottomTabNavigator<TabParamList>();

/** Go to a generic screen from anywhere — an action the agent ran, a card, a chip. */
export function go(nav: RootNavigation, key: ScreenKey): void {
  if (!nav.isReady()) return;
  if (key === "memory") nav.navigate("memory");
  else nav.navigate("main", { screen: key });
}

const GLYPH: Record<keyof TabParamList, string> = { home: "⌂", chat: "✉", items: "☰", calls: "☏", profile: "◉" };

function MainTabs() {
  const { pack } = useDemo();
  const c = useColors();
  return (
    <Tabs.Navigator
      screenOptions={({ route }) => ({
        headerShown: true,
        headerStyle: { backgroundColor: c.surface },
        headerTitleStyle: { color: c.text },
        tabBarActiveTintColor: c.accent,
        tabBarInactiveTintColor: c.textMuted,
        tabBarStyle: { backgroundColor: c.surface, borderTopColor: c.border },
        tabBarIcon: ({ color }) => <Text style={{ color, fontSize: 18 }}>{GLYPH[route.name]}</Text>,
      })}
    >
      <Tabs.Screen name="home" component={HomeScreen} options={{ title: pack.appTitle, tabBarLabel: "Home" }} />
      <Tabs.Screen name="chat" component={ChatScreen} options={{ title: screenTitle(pack, "chat"), tabBarLabel: "Chat" }} />
      <Tabs.Screen name="items" component={ItemsScreen} options={{ title: screenTitle(pack, "items") }} />
      <Tabs.Screen name="calls" component={CallsScreen} options={{ title: screenTitle(pack, "calls"), tabBarLabel: "Calls" }} />
      <Tabs.Screen name="profile" component={ProfileScreen} options={{ title: "Profile" }} />
    </Tabs.Navigator>
  );
}

export function RootNavigator() {
  const { user, pack } = useDemo();
  const c = useColors();
  return (
    <Stack.Navigator screenOptions={{ headerStyle: { backgroundColor: c.surface }, headerTitleStyle: { color: c.text }, headerTintColor: c.accent }}>
      {user ? (
        <>
          <Stack.Screen name="main" component={MainTabs} options={{ headerShown: false }} />
          <Stack.Screen name="memory" component={MemoryScreen} options={{ title: "Memory" }} />
          <Stack.Screen name="help" component={HelpScreen} options={{ title: pack.agents.help.name }} />
        </>
      ) : (
        <Stack.Screen name="sign_in" component={SignInScreen} options={{ title: "Graine Demo" }} />
      )}
    </Stack.Navigator>
  );
}
