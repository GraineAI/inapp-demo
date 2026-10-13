/** The demo app's own chrome: neutral greys, the pack's accent, light and dark. The agent's look comes from the dashboard. */
import { useColorScheme } from "react-native";
import type { ChipTone } from "../logic/scheduling";

export interface Palette {
  background: string;
  surface: string;
  surfaceMuted: string;
  text: string;
  textMuted: string;
  border: string;
  accent: string;
  onAccent: string;
  danger: string;
  chip: Record<ChipTone, { bg: string; fg: string }>;
}

const DEFAULT_ACCENT = "#4F46E5";

export function palette(scheme: "light" | "dark", accent?: string): Palette {
  const a = accent ?? DEFAULT_ACCENT;
  if (scheme === "dark") {
    return {
      background: "#0B0D10",
      surface: "#15181D",
      surfaceMuted: "#1D2127",
      text: "#F2F4F7",
      textMuted: "#9AA3AF",
      border: "#2A2F37",
      accent: a,
      onAccent: "#FFFFFF",
      danger: "#F87171",
      chip: {
        neutral: { bg: "#262B33", fg: "#D0D5DD" },
        info: { bg: "#172554", fg: "#BFDBFE" },
        success: { bg: "#052E1C", fg: "#86EFAC" },
        warning: { bg: "#3B2405", fg: "#FCD34D" },
        danger: { bg: "#3F0F12", fg: "#FCA5A5" },
        muted: { bg: "#1F232A", fg: "#8B95A1" },
      },
    };
  }
  return {
    background: "#F6F7F9",
    surface: "#FFFFFF",
    surfaceMuted: "#F0F2F5",
    text: "#101828",
    textMuted: "#5D6B7E",
    border: "#E4E7EC",
    accent: a,
    onAccent: "#FFFFFF",
    danger: "#C0262D",
    chip: {
      neutral: { bg: "#EEF0F3", fg: "#344054" },
      info: { bg: "#E8F0FE", fg: "#1D4ED8" },
      success: { bg: "#E7F6EC", fg: "#166534" },
      warning: { bg: "#FEF3E2", fg: "#9A5B00" },
      danger: { bg: "#FDECEC", fg: "#B42318" },
      muted: { bg: "#F2F4F7", fg: "#667085" },
    },
  };
}

export function usePalette(accent?: string): Palette {
  return palette(useColorScheme() === "dark" ? "dark" : "light", accent);
}
