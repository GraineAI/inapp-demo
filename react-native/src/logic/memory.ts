/** How the app talks about memory: the chip on Home and the Memory screen's empty state. Pure. */
import type { ChipTone } from "./scheduling";
import type { UseCasePack } from "./pack";

export interface MemoryState {
  /** The agent remembers people at all (Memory & identity). null until the session is read. */
  agentMemory: boolean | null;
  /** This person agreed to be remembered (the consent switch). */
  consent: boolean;
}

export function memoryChip(state: MemoryState): { label: string; tone: ChipTone; detail: string } {
  if (state.agentMemory === false) {
    return { label: "Memory off", tone: "muted", detail: "This agent does not remember conversations." };
  }
  if (!state.consent) {
    return { label: "Memory off", tone: "muted", detail: "You switched memory off. Nothing new is remembered or recalled." };
  }
  return { label: "Memory on", tone: "success", detail: "Remembered across chats and calls, for you only." };
}

export function memoryEmptyText(pack: UseCasePack): string {
  const example = pack.memoryExamples[0]?.tell;
  return example
    ? `Nothing yet. Tell ${pack.agentName} something in a chat or a call — for example, “${example}” — and it is remembered next time.`
    : `Nothing yet. Tell ${pack.agentName} something in a chat or a call, and it is remembered next time.`;
}
