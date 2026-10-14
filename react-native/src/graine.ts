/**
 * THE ONE FILE THAT TALKS TO THE GRAINE SDKs.
 *
 * Every screen goes through here, so an SDK change is one file to read. Two packages:
 *   @graineai/inapp-react-native        the agent: identity, memory, screen context, actions, chat, voice
 *   @graineai/inapp-react-native-calls  calls like a phone call: CallKit / VoIP on iOS, a ringing call screen on Android
 */
import { useCallback, useEffect, useRef, useState } from "react";
import { PermissionsAndroid, Platform } from "react-native";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { useFocusEffect } from "@react-navigation/native";
import {
  GraineInAppClient,
  requestMicrophonePermission,
  type AppActionResult,
  type GraineCallStatus,
  type GraineMemory,
  type GraineScheduledCall,
  type GraineSchedulingRules,
  type ScreenContext,
} from "@graineai/inapp-react-native";
import { GraineCalls, type GraineCallOutcome, type GraineIncomingCall } from "@graineai/inapp-react-native-calls";

import { demoConfig, helpKeyFor, isConfigured, publishableKeyFor } from "./config";
import { identifyTraits, type PackAction, type PackUser, type UseCasePack } from "./logic/pack";
import { settingsFromSession, UNKNOWN_SETTINGS, type AgentSettings } from "./logic/session";
import { messageFor, readError, type ErrorContext } from "./logic/errors";

// The SDK's React layer, re-exported so screens import Graine from one place.
export {
  GraineProvider,
  GraineVoiceLauncher,
  GraineVoiceBar,
  GraineWidget,
  useGraineAgent as useAgentConversation,
  useGraineSurface as useAgentSurface,
  useGraineMemories as useMemories,
  useGraineTheme as useAgentDesign,
} from "@graineai/inapp-react-native";
export { useGraineCall as useNativeCall } from "@graineai/inapp-react-native-calls";
export type { GraineMemory as Memory } from "@graineai/inapp-react-native";

// ── The client: one per agent ──────────────────────────────────────────────
//
// Each use-case pack is its own agent with its own publishable key, so the app keeps one client per
// key and works with the current pack's. Made outside React, handed to <GraineProvider client>.
// `storage` keeps this install's id and each person's memory choice between launches — without it
// nobody can be remembered.

const clients = new Map<string, GraineInAppClient>();

function clientForKey(publishableKey: string): GraineInAppClient {
  let c = clients.get(publishableKey);
  if (!c) {
    c = new GraineInAppClient({ baseUrl: demoConfig.baseUrl, publishableKey, storage: AsyncStorage });
    clients.set(publishableKey, c);
  }
  return c;
}

let currentPackId = "";
let current: GraineInAppClient | null = null;
let currentHelp: GraineInAppClient | null = null;

/**
 * Make `pack`'s agents the ones every call below talks to: its main agent (Home, Chat) and, when the app has
 * a key for it, its help agent (the Help screen). Sign the previous person out first.
 */
export function selectPack(pack: UseCasePack): GraineInAppClient {
  if (pack.id !== currentPackId || !current) {
    currentPackId = pack.id;
    current = clientForKey(publishableKeyFor(pack.id));
    const helpKey = helpKeyFor(pack.id);
    // The same key as the main agent would make one agent answer as both: then there is no help agent.
    currentHelp = helpKey && helpKey !== publishableKeyFor(pack.id) ? clientForKey(helpKey) : null;
  }
  return current;
}

/** The help agent's client (for the Help screen's own <GraineProvider client>), or null without a help key. */
export function helpClient(): GraineInAppClient | null {
  return currentHelp;
}

/** Both agents of the current pack — what sign-in, sign-out, the memory choice and actions apply to. */
const everyAgent = (): GraineInAppClient[] => [current, currentHelp].filter((x): x is GraineInAppClient => !!x);

/** The current agent's client (for <GraineProvider client>). */
export function currentClient(): GraineInAppClient {
  if (!current) throw new Error("selectPack(pack) first");
  return current;
}

const c = (): GraineInAppClient => currentClient();

// ── Who is signed in, and memory ───────────────────────────────────────────

/**
 * identify() with the pack's identifier trait (patient_id, customer_id…), the name and the person's
 * traits. The identifier keys memory and booked calls; it is never a prompt variable. `memory` is
 * the consent switch. A new identity applies from the next conversation. Returns at once; the SDK
 * sends what it must (a sign-out of the previous person) by itself.
 *
 * Two agents, one person: the user is identified on BOTH agents' clients (each React Native client is
 * independent), so one sign-in reaches the main agent and the help agent alike.
 */
export function signIn(pack: UseCasePack, user: PackUser, memory: boolean): void {
  selectPack(pack);
  const traits = identifyTraits(pack, user);
  for (const client of everyAgent()) client.identify(traits, { memory });
}

/** identify(null) on both agents: this device stops being the person's (no rings, no memory). */
export function signOut(): void {
  for (const client of everyAgent()) client.identify(null);
}

/** The consent switch, on both agents: false keeps this person's conversations out of memory. */
export function setMemoryConsent(enabled: boolean): void {
  for (const client of everyAgent()) client.setMemory(enabled);
}

/** What the agent remembers about the signed-in person, newest first. */
export function listMemories(): Promise<GraineMemory[]> {
  return c().memories();
}

/** Erase everything the agent remembers about the signed-in person: how many were deleted, when the SDK says. */
export async function eraseMemories(): Promise<number | null> {
  const deleted: unknown = await c().forgetMemory();
  return typeof deleted === "number" ? deleted : null;
}

/** This install's id, for the Profile screen (support and QA). Never shown to the agent. */
export function installId(): Promise<string | null> {
  return c().getInstallId();
}

// ── The agent's settings, read once per agent per launch ───────────────────

const sessions = new WeakMap<GraineInAppClient, Promise<unknown>>();

/** The agent's session, read once per client (again with `refresh`). Rejects like the SDK does. */
function sessionOf(client: GraineInAppClient, refresh = false): Promise<unknown> {
  let p = sessions.get(client);
  if (!p || refresh) {
    const fresh: Promise<unknown> = client.session();
    sessions.set(client, fresh);
    fresh.catch(() => {
      if (sessions.get(client) === fresh) sessions.delete(client); // try again next time
    });
    p = fresh;
  }
  return p;
}

/** Whether the agent remembers people, and the name on its calls — from the session. */
export async function agentSettings(refresh = false): Promise<AgentSettings> {
  if (!current || !isConfigured(currentPackId)) return UNKNOWN_SETTINGS;
  try {
    return settingsFromSession(await sessionOf(current, refresh));
  } catch (err) {
    console.warn("[Graine Demo] The agent's settings could not be read:", readError(err).message);
    return UNKNOWN_SETTINGS;
  }
}

// ── Booked calls: the SDK owns them ────────────────────────────────────────
//
// useScheduling() is the SDK's useGraineScheduling(): `rules` (schedulingRules — usersCanBook,
// earliest(now), latest(now), callName…), `calls` (upcoming first, each with isUpcoming / canCancel and
// a typed status), `schedule(at, reason)` (checks scheduling_off / too_soon / too_far before asking),
// `cancel(id)`, `refresh`, `loading`, `error`. The app only presents them.
export { useGraineScheduling as useScheduling } from "@graineai/inapp-react-native";
export type {
  GraineScheduledCall as ScheduledCall,
  GraineSchedulingRules as SchedulingRules,
  GraineCallStatus as CallStatus,
} from "@graineai/inapp-react-native";

/** A sentence for anything the SDK threw: its code mapped to what to do next. */
export function describeError(err: unknown, ctx: ErrorContext = {}): string {
  const e = readError(err);
  return messageFor(e.code, e.message, { ...ctx, retryAfterSeconds: e.retryAfterSeconds });
}

export function errorCode(err: unknown): string {
  return readError(err).code;
}

// ── Screen context and actions ─────────────────────────────────────────────

/**
 * Tell the agent what this screen shows — when it gains focus, and again whenever it changes while
 * focused. Tab screens stay mounted, so reporting on mount alone would leave the agent on whichever
 * tab was opened last. Withdrawn on unmount (only if it is still the current one).
 */
export function useScreenContext(context: ScreenContext | null): void {
  const serialised = JSON.stringify(context ?? null);
  const latest = useRef(context);
  latest.current = context;
  // The screen is app-wide: both agents are told, so whichever has the conversation knows where the person is.
  useFocusEffect(
    useCallback(() => {
      if (latest.current) for (const client of everyAgent()) client.setScreen(latest.current);
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [serialised]),
  );
  useEffect(() => () => {
    if (latest.current) for (const client of everyAgent()) client.clearScreen(latest.current.screen);
  }, []);
}

export type ActionRunner = (action: PackAction, args: Record<string, unknown>) => AppActionResult | Promise<AppActionResult>;

/**
 * Register every action the pack declares, by name, all handled by `run`. Returns the function that
 * unregisters them. The agent can call only actions that are ALSO declared on it in the dashboard
 * (agent-setup/<pack>/actions.json); the SDK logs any difference once.
 */
export function registerPackActions(pack: UseCasePack, run: ActionRunner): () => void {
  selectPack(pack);
  // The help agent is given the same actions.json, so it can open a screen or an item too.
  const offs = everyAgent().flatMap((client) =>
    pack.actions.map((action) => client.registerAction(action.name, (args) => run(action, (args ?? {}) as Record<string, unknown>))),
  );
  return () => offs.forEach((off) => off());
}

// ── Chat ───────────────────────────────────────────────────────────────────
//
// The app draws its chat — in the Chat tab, and under the Help screen's own buttons — from the SDK's
// conversation state (useAgentConversation: the turns, typing, newConversation) and the SDK's own
// layout rules, so it shows a conversation that continues exactly as the SDK's chat windows do:
//   chatPieces(turns, design.chat)  what to draw — earlier messages (unless the brand hides them in the
//                                   dashboard), the starters only on a new chat, New conversation only
//                                   while earlier messages are on screen and the brand keeps it
//   chatTurnRows(turns)             a day line where the day changes, an earlier call as ONE
//                                   "Voice call · 6:05 PM" row (never its transcript), then the bubbles
// An app that gives the chat a screen of its own can use the SDK's `GraineChatScreen` instead.
export { chatClock, chatPieces, chatTurnRows } from "@graineai/inapp-react-native";

// ── Calls like a phone call ────────────────────────────────────────────────

/** False in Expo Go or a build without the calls package's native code. */
export function callsAvailable(): boolean {
  return GraineCalls.isAvailable;
}

let configured = "";
let voipOn = false;

/** The name the call engine knows the help agent by. The main agent is the engine's default agent. */
const HELP_AGENT = "help";

/**
 * Configure the call engine for `pack`'s agents (kept natively, so a ring that wakes a closed app
 * works before any JavaScript runs) and, on iOS, register for VoIP pushes. One engine for the app:
 * the main agent is its default agent, the help agent is named "help" — so a call can be placed with
 * either, and a ring Graine sends comes in as the agent that booked it. With the workspace's push
 * credentials uploaded (Workspace settings → In-app calling), the SDK registers this device with
 * Graine for every agent itself and reports what became of every ring: no server of ours is
 * involved. Android hands its FCM token and pushes over natively — see plugins/with-graine-fcm.js.
 */
export async function setUpCalls(pack: UseCasePack, callName: string): Promise<boolean> {
  if (!GraineCalls.isAvailable || !isConfigured(pack.id)) return false;
  const publishableKey = publishableKeyFor(pack.id);
  // The same rule as selectPack: the main agent's own key is no help agent.
  const helpKey = helpKeyFor(pack.id) !== publishableKey ? helpKeyFor(pack.id) : "";
  const fingerprint = `${publishableKey}\n${helpKey}\n${callName}`;
  if (configured !== fingerprint) {
    await GraineCalls.configure({
      publishableKey,
      baseUrl: demoConfig.baseUrl,
      displayName: callName,
      appVersion: demoConfig.appVersion,
      callScreen: "sdk",
      debug: typeof __DEV__ !== "undefined" && __DEV__,
      ...(helpKey ? { agents: { [HELP_AGENT]: { publishableKey: helpKey } } } : {}),
    });
    configured = fingerprint;
  }
  if (Platform.OS === "ios" && !voipOn) {
    voipOn = true;
    // Hosted ringing: the SDK hands the token to Graine. Logged as a fact, never the token itself.
    GraineCalls.enableVoIPPushes((token) => console.log(`[Graine Demo] VoIP push token ${token ? "ready" : "invalidated"}`));
  }
  return true;
}

/**
 * Share who is signed in, the screen and the actions with the calls: each agent's client, so a call
 * with the help agent runs the help agent's actions. Returns the function that detaches both.
 */
export function attachCalls(main: GraineInAppClient, help: GraineInAppClient | null = null): () => void {
  if (!GraineCalls.isAvailable) return () => {};
  const offs = [GraineCalls.attach(main), ...(help ? [GraineCalls.attach(help, { agent: HELP_AGENT })] : [])];
  return () => offs.forEach((off) => off());
}

/**
 * The person calls an agent: the main one (the engine's default), or the help agent by its name. The
 * call's id, or null while another call is live.
 */
export function placeCall(reason: string, agent: "main" | "help" = "main"): Promise<string | null> {
  const options: { reason: string; agent?: string } = agent === "help" ? { reason, agent: HELP_AGENT } : { reason };
  return GraineCalls.startCall(options);
}

// ── The help agent's booked calls ──────────────────────────────────────────

export interface HelpCalls {
  calls: GraineScheduledCall[];
  error: unknown;
  refresh: () => Promise<void>;
  schedule: (at: Date, reason?: string) => Promise<GraineScheduledCall>;
  cancel: (id: string) => Promise<GraineCallStatus>;
  rules: GraineSchedulingRules | null;
}

/**
 * The help agent's calls for the Scheduled calls list, read with its own client (its bookings live with that
 * agent). Only loading and passing through: every rule is the SDK's. Empty without a help agent.
 */
export function useHelpCalls(): HelpCalls {
  const client = currentHelp;
  const [calls, setCalls] = useState<GraineScheduledCall[]>([]);
  const [error, setError] = useState<unknown>(null);
  const latest = useRef(0);
  const refresh = useCallback(async () => {
    if (!client) return;
    const mine = ++latest.current;
    try {
      const list = await client.scheduledCalls();
      if (mine === latest.current) { setCalls(list); setError(null); }
    } catch (err) {
      if (mine === latest.current) setError(err);
    }
  }, [client]);
  useEffect(() => {
    setCalls([]);
    if (!client) return;
    void refresh();
    const offs = [client.on("scheduled_calls_changed", () => void refresh()), client.on("identity", () => void refresh())];
    return () => offs.forEach((off) => off());
  }, [client, refresh]);
  const schedule = useCallback((at: Date, reason?: string) => {
    if (!client) return Promise.reject(new Error("No help agent is configured."));
    return client.scheduleCall(at, reason);
  }, [client]);
  const cancel = useCallback((id: string) => {
    if (!client) return Promise.reject(new Error("No help agent is configured."));
    return client.cancelScheduledCall(id);
  }, [client]);
  return { calls, error, refresh, schedule, cancel, rules: client ? client.schedulingRules : null };
}

export interface CallEvent {
  callId: string;
  kind: "incoming" | GraineCallOutcome["outcome"];
  detail: string | null;
  /** Which of the app's agents the call is with, as the call engine says; null when it does not. */
  agent: "main" | "help" | null;
  at: Date;
}

/** The app's agent for the engine's name: "help" is the help agent, the default agent is the main one. */
export function agentOfCall(name: string | null | undefined): "main" | "help" | null {
  if (name === HELP_AGENT) return "help";
  return name ? "main" : null;
}

/** One line for the in-app call log, from what the call engine reports. */
export function callEventFrom(e: { incoming: GraineIncomingCall } | { outcome: GraineCallOutcome }, at = new Date()): CallEvent {
  if ("incoming" in e) return { callId: e.incoming.callId, kind: "incoming", detail: e.incoming.callerName, agent: agentOfCall(e.incoming.agent), at };
  const o = e.outcome as GraineCallOutcome & { reason?: string | null; error?: string | null };
  return { callId: o.callId, kind: o.outcome, detail: o.reason ?? o.error ?? null, agent: agentOfCall(o.agent), at };
}

/** Rings and outcomes, including those from before the app's JavaScript was running. */
export function onCallEvents(fn: (e: CallEvent) => void): () => void {
  if (!GraineCalls.isAvailable) return () => {};
  const offs = [
    GraineCalls.onIncomingCall((incoming) => fn(callEventFrom({ incoming }))),
    GraineCalls.onOutcome((outcome) => fn(callEventFrom({ outcome }))),
  ];
  return () => offs.forEach((off) => off());
}

/**
 * Ask for the microphone, and on Android 13+ for notifications, at sign-in — so a ring is never
 * answered into a permission prompt.
 */
export async function requestPermissions(): Promise<void> {
  try {
    await requestMicrophonePermission();
  } catch {
    /* the bar asks again when it needs it */
  }
  if (Platform.OS === "android" && Number(Platform.Version) >= 33) {
    try {
      await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
    } catch {
      /* rings still arrive; the call notification is quieter */
    }
  }
}
