/** Whether the voice bar is open. The bar itself lives at the root (App.tsx) so a call outlives every screen. */
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import { useAgentSurface } from "../graine";

interface Voice {
  isOpen: boolean;
  /** Open the bar and start talking (switching the agent from chat to voice). */
  open: () => void;
  close: () => void;
  /** The dashboard offers this agent as a voice conversation. */
  available: boolean;
}

const VoiceCtx = createContext<Voice>({ isOpen: false, open: () => {}, close: () => {}, available: false });

export function VoiceProvider({ children }: { children: ReactNode }) {
  const [isOpen, setOpen] = useState(false);
  const { surfaces, setSurface } = useAgentSurface();
  const available = surfaces.includes("voice");
  const open = useCallback(() => {
    if (!available) return;
    setSurface("voice");
    setOpen(true);
  }, [available, setSurface]);
  const close = useCallback(() => setOpen(false), []);
  const value = useMemo(() => ({ isOpen, open, close, available }), [isOpen, open, close, available]);
  return <VoiceCtx.Provider value={value}>{children}</VoiceCtx.Provider>;
}

export const useVoice = (): Voice => useContext(VoiceCtx);
