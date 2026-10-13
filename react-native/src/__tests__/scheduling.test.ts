/**
 * The booking sheet's own logic — presentation only: status words and colours, who booked, the pack's
 * quick times as moments, the range the pickers offer inside the SDK's bounds on a 5-minute grid, and
 * the time zone line. The booking rules are the SDK's.
 */
import { inRange, mergeAgentCalls, pickerRange, quickTimeAt, roundUpToStep, snapToRange, sourceLabel, statusChip, zoneLabel } from "../logic/scheduling";
import { formatDay, formatTime, formatWhen } from "../logic/time";
import { PACKS } from "../usecases";

// Wednesday 14 October 2026, 18:10:30 local time; the SDK's bounds for 5 minutes' notice and 14 days.
const NOW = new Date(2026, 9, 14, 18, 10, 30);
const EARLIEST = new Date(NOW.getTime() + 5 * 60_000);
const LATEST = new Date(NOW.getTime() + 14 * 86_400_000);

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

  it("says which time zone a booking is in", () => {
    expect(zoneLabel(new Date(2026, 9, 15, 18, 0), "Asia/Kolkata", NOW)).toBe("Asia/Kolkata · Tomorrow, 6:00 pm");
  });
});

describe("the pickers, inside the SDK's earliest and latest", () => {
  it("rounds up to the 5-minute grid", () => {
    expect(formatTime(roundUpToStep(new Date(2026, 9, 14, 18, 15, 30), 5))).toBe("6:20 pm");
    expect(formatTime(roundUpToStep(new Date(2026, 9, 14, 18, 15, 0), 5))).toBe("6:15 pm");
  });

  it("offers the earliest on the grid through the latest, and nothing when that is empty", () => {
    const range = pickerRange(EARLIEST, LATEST)!;
    expect(formatTime(range.min)).toBe("6:20 pm");
    expect(range.max).toEqual(LATEST);
    expect(pickerRange(LATEST, EARLIEST)).toBeNull();
  });

  it("keeps a picked time on the grid and inside the range", () => {
    const range = pickerRange(EARLIEST, LATEST)!;
    expect(snapToRange(new Date(2026, 9, 14, 9, 0), range)).toEqual(range.min);
    expect(formatTime(snapToRange(new Date(2026, 9, 16, 9, 2), range))).toBe("9:05 am");
    expect(snapToRange(new Date(2026, 10, 30, 9, 0), range).getTime()).toBeLessThanOrEqual(LATEST.getTime());
    expect(snapToRange(new Date(2026, 10, 30, 9, 0), range).getMinutes() % 5).toBe(0);
  });

  it("turns each pack's quick times into moments, and marks those outside the range", () => {
    const range = pickerRange(EARLIEST, LATEST);
    const at = (label: string, q: { inMinutes?: number; day?: "today" | "tomorrow"; time?: string }) => quickTimeAt({ label, ...q }, NOW);
    expect(formatWhen(at("In 15 minutes", { inMinutes: 15 }), NOW)).toBe("Today, 6:26 pm");
    expect(formatWhen(at("Tomorrow 10:00", { day: "tomorrow", time: "10:00" }), NOW)).toBe("Tomorrow, 10:00 am");
    const earlyToday = at("Today 09:00", { day: "today", time: "09:00" });
    expect(inRange(earlyToday, range)).toBe(false);
    expect(inRange(at("Today 19:00", { day: "today", time: "19:00" }), range)).toBe(true);
    for (const pack of PACKS) {
      for (const q of pack.call.quickTimes) expect(quickTimeAt(q, NOW).getTime()).not.toBeNaN();
      expect(pack.call.bookLabel.length).toBeGreaterThan(0);
      expect(pack.call.reasons.length).toBeGreaterThanOrEqual(2);
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
