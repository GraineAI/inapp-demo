/**
 * Booked calls as the app shows them — presentation only: times, status words and colours, who booked,
 * both agents' calls in one list, the call log. Booking itself is the SDK's booking sheet.
 */
import { callEventLine, mergeAgentCalls, sourceLabel, statusChip } from "../logic/scheduling";
import { formatDay, formatTime, formatWhen } from "../logic/time";
import { PACKS } from "../usecases";

// Wednesday 14 October 2026, 18:10:30 local time.
const NOW = new Date(2026, 9, 14, 18, 10, 30);

describe("times as the app shows them", () => {
  it("formats times and days in plain words", () => {
    expect(formatTime(new Date(2026, 9, 14, 0, 5))).toBe("12:05 am");
    expect(formatTime(new Date(2026, 9, 14, 12, 0))).toBe("12:00 pm");
    expect(formatTime(new Date(2026, 9, 14, 18, 30))).toBe("6:30 pm");
    expect(formatDay(new Date(2026, 9, 14, 9), NOW)).toBe("Today");
    expect(formatDay(new Date(2026, 9, 15, 9), NOW)).toBe("Tomorrow");
    expect(formatDay(new Date(2026, 9, 13, 9), NOW)).toBe("Yesterday");
    expect(formatDay(new Date(2026, 9, 17, 9), NOW)).toBe("Sat 17 Oct");
    expect(formatWhen(new Date(2026, 9, 15, 9, 0), NOW)).toBe("Tomorrow, 9:00 am");
    expect(formatWhen(null, NOW)).toBe("Time not set");
  });
});

describe("each pack's booking button", () => {
  it("names the button and the reason the SDK's booking sheet starts with", () => {
    for (const pack of PACKS) {
      expect(pack.call.bookLabel.length).toBeGreaterThan(0);
      expect(pack.call.reasons.length).toBeGreaterThanOrEqual(2);
      expect(pack.agents.help.bookLabel.length).toBeGreaterThan(0);
      expect(pack.agents.help.reasons.length).toBeGreaterThanOrEqual(2);
    }
  });
});

describe("status chips", () => {
  it("names every status, whichever spelling the SDK uses", () => {
    const statuses = ["scheduled", "ringing", "answered", "completed", "declined", "missed", "expired", "failed", "cancelled"];
    for (const s of statuses) expect(statusChip(s).label.toLowerCase()).toBe(s);
    expect(statusChip("noDevice")).toEqual({ label: "No device", tone: "danger" });
    expect(statusChip("no_device")).toEqual({ label: "No device", tone: "danger" });
    expect(statusChip("answered").tone).toBe("success");
    expect(statusChip("missed").tone).toBe("danger");
  });

  it("shows a status it does not know as it came", () => {
    expect(statusChip("rescheduled_later")).toEqual({ label: "Rescheduled later", tone: "neutral" });
    expect(statusChip("onHold")).toEqual({ label: "On hold", tone: "neutral" });
    expect(statusChip(null).label).toBe("Unknown");
  });

  it("says who booked a call only when it was not the person", () => {
    expect(sourceLabel("agent")).toBe("Booked in a conversation");
    expect(sourceLabel("dashboard")).toBe("Booked by the team");
    expect(sourceLabel("api")).toBe("Booked by the team");
    expect(sourceLabel("app")).toBeNull();
    expect(sourceLabel(null)).toBeNull();
  });
});

describe("both agents' calls in one list", () => {
  it("puts upcoming calls first, soonest first, then the rest newest first, each with its agent", () => {
    const at = (h: number) => new Date(2026, 9, 15, h);
    const main = [
      { id: "m1", at: at(18), isUpcoming: true },
      { id: "m2", at: at(9), isUpcoming: false },
    ];
    const help = [
      { id: "h1", at: at(10), isUpcoming: true },
      { id: "h2", at: at(11), isUpcoming: false },
    ];
    expect(mergeAgentCalls(main, help).map((x) => `${x.agent}:${x.call.id}`)).toEqual(["help:h1", "main:m1", "help:h2", "main:m2"]);
    expect(mergeAgentCalls(main, [])).toHaveLength(2);
  });
});

describe("call activity on this phone", () => {
  it("names the agent each call was with, when the call engine says", () => {
    const names = { main: "Asha", help: "Billing & appointments help" };
    const at = new Date(2026, 9, 14, 18, 5);
    expect(callEventLine({ callId: "call_1f2e3d4c-aaaa", kind: "ended", detail: "remote", agent: "help", at }, names))
      .toBe("6:05 pm · Billing & appointments help · ended (remote) · call_1f2e3d4");
    expect(callEventLine({ callId: "c2", kind: "incoming", detail: "Asha · Graine Care", agent: "main", at }, names))
      .toBe("6:05 pm · Asha · incoming (Asha · Graine Care) · c2");
    expect(callEventLine({ callId: "c3", kind: "missed", detail: null, agent: null, at }, names)).toBe("6:05 pm · missed · c3");
  });
});
