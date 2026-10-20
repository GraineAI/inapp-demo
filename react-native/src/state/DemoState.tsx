/**
 * The demo's own state: which pack, who is signed in, their memory consent, the statuses set on
 * their rows this session, and the agent's settings. The chosen pack and the signed-in person are
 * remembered on the device and signed in again at launch (identity is the app's to restore).
 */
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import AsyncStorage from "@react-native-async-storage/async-storage";
import * as graine from "../graine";
import { findPack, rowsFor, type PackRow, type PackUser, type UseCasePack } from "../logic/pack";
import { UNKNOWN_SETTINGS, type AgentSettings } from "../logic/session";
import type { GraineInAppClient } from "@graineai/inapp-react-native";
import { DEFAULT_PACK_ID, PACKS } from "../usecases";

const PACK_KEY = "graine-demo.pack";
const SIGNED_IN_KEY = "graine-demo.signed-in";

interface SignedIn { packId: string; userId: string; consent: boolean }

export interface DemoState {
  ready: boolean;
  pack: UseCasePack;
  /** The current pack's agent. Changes with the pack. */
  client: GraineInAppClient;
  /** The current pack's help agent (the Help screen), or null when the app has no key for it. */
  helpClient: GraineInAppClient | null;
  user: PackUser | null;
  consent: boolean;
  settings: AgentSettings;
  rows: PackRow[];
  /** Row id → status set this session, for the signed-in person. */
  statuses: Record<string, string>;
  openRowId: string | null;
  choosePack: (id: string) => Promise<void>;
  signIn: (user: PackUser, consent: boolean) => Promise<void>;
  signOut: () => Promise<void>;
  setConsent: (on: boolean) => Promise<void>;
  setRowStatus: (rowId: string, status: string) => void;
  setOpenRow: (rowId: string | null) => void;
  refreshSettings: () => Promise<void>;
}

const Ctx = createContext<DemoState | null>(null);

export function useDemo(): DemoState {
  const v = useContext(Ctx);
  if (!v) throw new Error("useDemo must be used inside <DemoStateProvider>");
  return v;
}

const defaultPack = (): UseCasePack => findPack(PACKS, DEFAULT_PACK_ID) ?? PACKS[0];

async function readJson<T>(key: string): Promise<T | null> {
  try {
    const raw = await AsyncStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}

export function DemoStateProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [pack, setPack] = useState<UseCasePack>(defaultPack);
  const [client, setClient] = useState<GraineInAppClient>(() => graine.selectPack(defaultPack()));
  const [user, setUser] = useState<PackUser | null>(null);
  const [consent, setConsentState] = useState(true);
  const [settings, setSettings] = useState<AgentSettings>(UNKNOWN_SETTINGS);
  const [statusesByUser, setStatusesByUser] = useState<Record<string, Record<string, string>>>({});
  const [openRowId, setOpenRow] = useState<string | null>(null);
  const live = useRef(true);
  useEffect(() => () => { live.current = false; }, []);

  const refreshSettings = useCallback(async () => {
    const s = await graine.agentSettings(true);
    if (live.current) setSettings(s);
  }, []);

  // Launch: the remembered pack, and the remembered person signed in again.
  useEffect(() => {
    (async () => {
      const packId = await AsyncStorage.getItem(PACK_KEY).catch(() => null);
      const chosen = findPack(PACKS, packId) ?? defaultPack();
      const saved = await readJson<SignedIn>(SIGNED_IN_KEY);
      const person = saved && saved.packId === chosen.id ? chosen.users.find((u) => u.id === saved.userId) ?? null : null;
      const chosenClient = graine.selectPack(chosen);
      if (person) {
        try {
          graine.signIn(chosen, person, saved!.consent);
        } catch (err) {
          console.warn("[Graine Demo] Could not sign in again:", graine.describeError(err));
        }
      }
      if (!live.current) return;
      setPack(chosen);
      setClient(chosenClient);
      setUser(person);
      setConsentState(person ? saved!.consent : true);
      setReady(true);
      const s = await graine.agentSettings();
      if (live.current) setSettings(s);
    })();
  }, []);

  const choosePack = useCallback(async (id: string) => {
    const next = findPack(PACKS, id);
    if (!next) return;
    if (user) {
      graine.signOut(pack);
      setUser(null);
      await AsyncStorage.removeItem(SIGNED_IN_KEY).catch(() => undefined);
    }
    setPack(next);
    setClient(graine.selectPack(next));
    setOpenRow(null);
    setSettings(UNKNOWN_SETTINGS);
    await AsyncStorage.setItem(PACK_KEY, next.id).catch(() => undefined);
    const s = await graine.agentSettings();
    if (live.current) setSettings(s);
  }, [user, pack]);

  const signIn = useCallback(async (person: PackUser, agreed: boolean) => {
    graine.signIn(pack, person, agreed);
    setUser(person);
    setConsentState(agreed);
    setOpenRow(null);
    await AsyncStorage.setItem(SIGNED_IN_KEY, JSON.stringify({ packId: pack.id, userId: person.id, consent: agreed } satisfies SignedIn)).catch(() => undefined);
    void graine.requestPermissions();
  }, [pack]);

  const signOut = useCallback(async () => {
    graine.signOut(pack);
    setUser(null);
    setOpenRow(null);
    await AsyncStorage.removeItem(SIGNED_IN_KEY).catch(() => undefined);
  }, [pack]);

  const setConsent = useCallback(async (on: boolean) => {
    graine.setMemoryConsent(on);
    setConsentState(on);
    if (user) {
      await AsyncStorage.setItem(SIGNED_IN_KEY, JSON.stringify({ packId: pack.id, userId: user.id, consent: on } satisfies SignedIn)).catch(() => undefined);
    }
  }, [pack.id, user]);

  const setRowStatus = useCallback((rowId: string, status: string) => {
    if (!user) return;
    setStatusesByUser((all) => ({ ...all, [user.id]: { ...(all[user.id] ?? {}), [rowId]: status } }));
  }, [user]);

  const value = useMemo<DemoState>(() => ({
    ready,
    pack,
    client,
    helpClient: client === graine.currentClient() ? graine.helpClient() : null,
    user,
    consent,
    settings,
    rows: rowsFor(pack, user?.id ?? null),
    statuses: (user && statusesByUser[user.id]) || {},
    openRowId,
    choosePack,
    signIn,
    signOut,
    setConsent,
    setRowStatus,
    setOpenRow,
    refreshSettings,
  }), [ready, pack, client, user, consent, settings, statusesByUser, openRowId, choosePack, signIn, signOut, setConsent, setRowStatus, refreshSettings]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}
