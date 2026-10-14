/**
 * What the call engine reported this session — rings and outcomes — shown on the Scheduled calls
 * screen so a tester sees each ring's outcome without a debugger. Newest first, the last 20.
 */
import { useSyncExternalStore } from "react";
import type { CallEvent } from "../graine";

let events: CallEvent[] = [];
const watchers = new Set<() => void>();

export function logCallEvent(e: CallEvent): void {
  events = [e, ...events].slice(0, 20);
  console.log(`[Graine Demo] call ${e.callId}${e.agent ? ` (${e.agent} agent)` : ""}: ${e.kind}${e.detail ? ` (${e.detail})` : ""}`);
  for (const w of [...watchers]) w();
}

export function useCallLog(): CallEvent[] {
  return useSyncExternalStore(
    (fn) => { watchers.add(fn); return () => watchers.delete(fn); },
    () => events,
    () => events,
  );
}
