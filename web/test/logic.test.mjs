// The demo's rules (js/logic.js), against a test pack and the real packs in usecases/.
// Run: node --test web/test/
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import * as L from "../js/logic.js";

const here = dirname(fileURLToPath(import.meta.url));
const sample = JSON.parse(readFileSync(join(here, "fixtures", "sample-pack.json"), "utf8"));
const usecases = join(here, "..", "..", "usecases");
const realPacks = readdirSync(usecases)
  .filter((f) => f.endsWith(".json") && f !== "usecase.schema.json")
  .map((f) => JSON.parse(readFileSync(join(usecases, f), "utf8")));

const action = (pack, name) => pack.actions.find((a) => a.name === name);
const decide = (name, args, userId = "M-001", statuses = {}) =>
  L.decideAction(sample, action(sample, name), args, { userId, statuses });

/** Run `fn` with the process in time zone `tz`. */
function inZone(tz, fn) {
  const before = process.env.TZ;
  process.env.TZ = tz;
  try { return fn(); } finally {
    if (before === undefined) delete process.env.TZ; else process.env.TZ = before;
  }
}

test("screens: generic keys, the pack's list name, a component's path; never sign-in", () => {
  assert.equal(L.parseScreen("home", sample), "home");
  assert.equal(L.parseScreen("  Calls ", sample), "calls");
  assert.equal(L.parseScreen("tickets", sample), "items");
  assert.equal(L.parseScreen("TICKETS", sample), "items");
  assert.equal(L.parseScreen("/tickets", sample), "items");
  assert.equal(L.parseScreen("items", sample), "items");
  assert.equal(L.parseScreen("sign_in", sample), null);
  assert.equal(L.parseScreen("settings", sample), null);
  assert.equal(L.parseScreen(42, sample), null);
  assert.deepEqual(L.screenNames(sample), ["home", "chat", "tickets", "calls", "memory", "profile"]);
  assert.equal(L.screenTitle("home", sample), "Overview");
  assert.equal(L.screenTitle("chat", sample), "Nova");
  assert.equal(L.screenTitle("items", sample), "Tickets");
  assert.equal(L.screenTitle("calls", sample), "Callbacks");
  assert.equal(L.contextName("items", sample), "tickets");
  assert.equal(L.contextName("calls", sample), "calls");
});

test("rows: each person sees their own rows and the shared ones", () => {
  assert.deepEqual(L.rowsFor(sample, "M-001").map((r) => r.id), ["t-100", "t-101", "t-300"]);
  assert.deepEqual(L.rowsFor(sample, "M-002").map((r) => r.id), ["t-200", "t-300"]);
});

test("finding a row: id, then title, then the one row containing it", () => {
  const rows = L.rowsFor(sample, "M-001");
  assert.equal(L.findRow(rows, "T-100").row.id, "t-100");
  assert.equal(L.findRow(rows, " router replacement ").row.id, "t-100");
  assert.equal(L.findRow(rows, "password").row.id, "t-101");
  assert.equal(L.findRow(rows, "55120").row.id, "t-100", "a field value counts");
  assert.equal(L.findRow(rows, "router").kind, "ambiguous");
  assert.deepEqual(L.findRow(rows, "5512").rows.map((r) => r.id), ["t-100", "t-101"]);
  assert.equal(L.findRow(rows, "printer").kind, "none");
  assert.equal(L.findRow(rows, "  ").kind, "none");
  assert.equal(L.findRow(rows, null).kind, "none");
});

test("the README's examples find their rows in the real packs", () => {
  const banking = realPacks.find((p) => p.id === "banking-support");
  const retail = realPacks.find((p) => p.id === "retail-shopping");
  if (banking) assert.equal(L.findRow(L.rowsFor(banking, "CUST-10041"), "7730").row.id, "credit-7730");
  if (retail) assert.equal(L.findRow(L.rowsFor(retail, "SHP-20031"), "GD-55120").row.id, "gd-55120");
});

test("open_screen answers in the pack's words", () => {
  assert.deepEqual(decide("open_screen", { screen: "tickets" }), {
    result: { status: "ok", message: "Opened Tickets." }, effects: { screen: "items" },
  });
  assert.deepEqual(decide("open_screen", { path: "/memory" }).effects, { screen: "memory" });
  assert.deepEqual(decide("open_screen", { screen: "billing" }).result, {
    status: "refused",
    message: 'There is no screen called "billing". Screens: home, chat, tickets, calls, memory, profile.',
  });
  assert.equal(decide("open_screen", {}).result.status, "refused");
});

test("open_item opens the list with the row expanded", () => {
  assert.deepEqual(decide("open_item", { item: "REF-55121" }), {
    result: { status: "ok", message: "Showing Router password reset." },
    effects: { screen: "items", openRow: "t-101" },
  });
  assert.deepEqual(decide("open_item", { item: "router" }).result, {
    status: "refused", message: "Which one? Router replacement, Router password reset.",
  });
  assert.deepEqual(decide("open_item", { item: "printer" }).result, {
    status: "refused",
    message: 'No ticket called "printer". Tickets: Router replacement, Router password reset, Shared notice.',
  });
});

test("set_status sets, repeats as already, refuses where the row does not offer it", () => {
  assert.deepEqual(decide("close_ticket", { item: "t-100" }), {
    result: { status: "ok", message: "Router replacement: Closed." },
    effects: { setStatus: { rowId: "t-100", status: "Closed" } },
  });
  assert.deepEqual(decide("close_ticket", { item: "t-100" }, "M-001", { "t-100": "Closed" }), {
    result: { status: "ok", message: "Router replacement is already Closed." }, effects: {},
  });
  // The pack's starting status counts as the current one.
  assert.deepEqual(decide("reopen_ticket", { item: "t-101" }).result, { status: "ok", message: "Router password reset: Open." });
  assert.deepEqual(decide("reopen_ticket", { item: "t-100" }).result, { status: "refused", message: "Reopen is not available for Router replacement." });
  assert.deepEqual(decide("close_ticket", { item: "Shared notice" }).result, { status: "refused", message: "Close ticket is not available for Shared notice." });
});

test("another person's rows are not there; nobody signed in is refused", () => {
  assert.deepEqual(decide("close_ticket", { item: "t-100" }, "M-002").result, {
    status: "refused", message: 'No ticket called "t-100". Tickets: Billing question, Shared notice.',
  });
  assert.deepEqual(decide("open_screen", { screen: "home" }, null).result, {
    status: "refused", message: "Nobody is signed in yet. Ask the user to choose who they are on the sign-in screen.",
  });
  assert.equal(L.decideAction(sample, { name: "x", effect: "teleport", label: "Teleport" }, {}, { userId: "M-001" }).result.status, "refused");
});

test("status buttons hide the status the row already has", () => {
  const row = sample.items.rows.find((r) => r.id === "t-101");   // no starting status: both offered
  assert.deepEqual(L.statusButtons(sample, row, {}).map((a) => a.name), ["close_ticket", "reopen_ticket"]);
  assert.deepEqual(L.statusButtons(sample, row, { "t-101": "Closed" }).map((a) => a.name), ["reopen_ticket"]);
  assert.deepEqual(L.statusButtons(sample, row, { "t-101": "Open" }).map((a) => a.name), ["close_ticket"]);
  const open = sample.items.rows.find((r) => r.id === "t-100");   // starts "Open"; offers close only
  assert.deepEqual(L.statusButtons(sample, open, {}).map((a) => a.name), ["close_ticket"]);
  assert.deepEqual(L.statusButtons(sample, open, { "t-100": "Closed" }).map((a) => a.name), []);
  const plain = sample.items.rows.find((r) => r.id === "t-200");
  assert.equal(L.rowStatus(plain, {}), "Waiting");
  assert.equal(L.rowValue(sample.items.rows.find((r) => r.id === "t-300"), {}), "For everyone");
  assert.equal(L.statusToneForRow(row, { "t-101": "Closed" }), "info");
  assert.equal(L.statusToneForRow(row, {}), "neutral");
});

test("every real pack: each action decides for each person without throwing", () => {
  assert.ok(realPacks.length >= 3, "the three packs are in usecases/");
  for (const pack of realPacks) {
    for (const user of pack.users) {
      for (const a of pack.actions) {
        for (const args of [{}, { screen: "home" }, { item: L.rowsFor(pack, user.id)[0].id }, { item: "nothing like this" }]) {
          const { result } = L.decideAction(pack, a, args, { userId: user.id, statuses: {} });
          assert.ok(["ok", "refused"].includes(result.status), `${pack.id}/${a.name}`);
          assert.ok(result.message.length > 0);
        }
      }
    }
    for (const s of L.SCREENS) assert.ok(L.screenTitle(s, pack), `${pack.id} has a title for ${s}`);
  }
});

test("call statuses: labels, tones, unknown ones pass through", () => {
  assert.equal(L.statusLabel("no_device"), "No device");
  assert.equal(L.statusLabel("ringing"), "Ringing");
  assert.equal(L.statusLabel("on_hold_now"), "On hold now");
  assert.equal(L.statusLabel(""), "Unknown");
  assert.equal(L.statusTone("completed"), "positive");
  assert.equal(L.statusTone("missed"), "warning");
  assert.equal(L.statusTone("failed"), "negative");
  assert.equal(L.statusTone("cancelled"), "neutral");
  for (const s of L.CALL_STATUSES) assert.ok(L.STATUS_HELP[s], `help for ${s}`);
  assert.equal(L.bookedBy("agent", "Nova"), "Booked by Nova");
  assert.equal(L.bookedBy("app", "Nova"), "Booked by you");
  assert.equal(L.bookedBy("api", "Nova"), "Booked by the team");
  assert.equal(L.bookedBy(null, "Nova"), null);
  assert.equal(L.bookedBy("robot", "Nova"), null);
  assert.equal(L.statusLabel("noDevice"), "No device");
  assert.equal(L.statusLabel("onHoldNow"), "On hold now");
  assert.equal(L.statusTone("noDevice"), "warning");
});

test("calls are grouped by the SDK's isUpcoming: soonest first, the rest most recent first", () => {
  const at = (iso) => new Date(iso);
  const calls = [
    { id: "a", status: "completed", isUpcoming: false, at: at("2026-10-10T10:00:00Z") },
    { id: "b", status: "scheduled", isUpcoming: true, at: at("2026-10-15T10:00:00Z") },
    { id: "c", status: "ringing", isUpcoming: true, at: at("2026-10-14T10:00:00Z") },
    { id: "d", status: "scheduled", isUpcoming: true, at: null },
    { id: "e", status: "missed", isUpcoming: false, at: at("2026-10-12T10:00:00Z") },
    { id: "f", status: "cancelled", isUpcoming: false, at: null },
    // The page follows the SDK, whatever the status says.
    { id: "g", status: "somethingNew", isUpcoming: true, at: at("2026-10-16T10:00:00Z") },
  ];
  const { upcoming, past } = L.orderCalls(calls);
  assert.deepEqual(upcoming.map((c) => c.id), ["c", "b", "g", "d"]);
  assert.deepEqual(past.map((c) => c.id), ["e", "a", "f"]);
  assert.equal(L.nextCall(calls).id, "c");
  assert.equal(L.nextCall([{ id: "x", status: "scheduled", isUpcoming: true, at: null }]), null);
});

test("a booking carries the visitor's wall clock and offset", () => {
  inZone("Asia/Kolkata", () => {
    const d = L.fromLocalInputValue("2026-10-14T18:00");
    assert.equal(L.toOffsetIso(d), "2026-10-14T18:00:00+05:30");
    assert.equal(d.toISOString(), "2026-10-14T12:30:00.000Z");
    assert.equal(L.toLocalInputValue(d), "2026-10-14T18:00");
    assert.equal(L.toUtcIso(d), "2026-10-14T12:30:00Z");
    assert.match(L.timeZoneLabel(d), /^Asia\/(Kolkata|Calcutta) \(GMT\+5:30\)$/);
  });
  inZone("America/New_York", () => {
    assert.equal(L.toOffsetIso(L.fromLocalInputValue("2026-07-01T09:15")), "2026-07-01T09:15:00-04:00");
    assert.equal(L.toOffsetIso(L.fromLocalInputValue("2026-12-01T09:15")), "2026-12-01T09:15:00-05:00");
  });
  assert.equal(L.fromLocalInputValue("2026-02-30T10:00"), null);
  assert.equal(L.fromLocalInputValue("tomorrow"), null);
  assert.equal(L.fromLocalInputValue(""), null);
});

test("the booking form opens 30 minutes out, on a quarter hour, inside the SDK's window", () => {
  const now = new Date("2026-10-14T12:07:31Z");
  assert.equal(L.suggestedCallTime(now).toISOString(), "2026-10-14T12:45:00.000Z");
  assert.equal(L.suggestedCallTime(new Date("2026-10-14T12:00:00Z")).toISOString(), "2026-10-14T12:30:00.000Z");
  const rules = (lead, days) => ({
    minLeadMinutes: lead, maxDaysAhead: days, maxPendingPerUser: 3,
    earliest: (n) => new Date(n.getTime() + lead * 60e3), latest: (n) => new Date(n.getTime() + days * 864e5),
  });
  assert.equal(L.suggestedCallTime(now, rules(120, 14)).toISOString(), "2026-10-14T14:15:00.000Z");
  assert.equal(L.suggestedCallTime(now, rules(5, 14)).toISOString(), "2026-10-14T12:45:00.000Z");
  const tight = { ...rules(5, 1), latest: (n) => new Date(n.getTime() + 20 * 60e3) };
  assert.equal(L.suggestedCallTime(now, tight).toISOString(), "2026-10-14T12:27:31.000Z");
  assert.equal(L.bookingWindowWords(rules(5, 14)), "Between 5 minutes and 14 days from now");
  assert.equal(L.bookingWindowWords(rules(1, 1)), "Between 1 minute and 1 day from now");
  assert.equal(L.bookingWindowWords(null), "");
});

test("quick times: minutes from now, or today / tomorrow at a local time", () => {
  inZone("Asia/Kolkata", () => {
    const now = new Date("2026-10-14T12:07:31Z");   // 17:37:31 in Kolkata
    assert.equal(L.quickTimeAt({ label: "In 20 minutes", inMinutes: 20 }, now).toISOString(), "2026-10-14T12:28:00.000Z", "rounded up to the minute");
    assert.equal(L.toOffsetIso(L.quickTimeAt({ label: "x", day: "today", time: "19:00" }, now)), "2026-10-14T19:00:00+05:30");
    assert.equal(L.toOffsetIso(L.quickTimeAt({ label: "x", day: "tomorrow", time: "09:30" }, now)), "2026-10-15T09:30:00+05:30");
    assert.equal(L.quickTimeAt({ label: "x", day: "someday", time: "09:30" }, now), null);
    assert.equal(L.quickTimeAt({ label: "x", day: "today", time: "25:00" }, now), null);
    assert.equal(L.quickTimeAt({ label: "x", inMinutes: 0 }, now), null);
    assert.equal(L.quickTimeAt(null, now), null);
  });
});

test("quick-time chips are disabled when past or outside the SDK's window", () => {
  inZone("Asia/Kolkata", () => {
    const rules = (lead, days) => ({
      minLeadMinutes: lead, maxDaysAhead: days,
      earliest: (n) => new Date(n.getTime() + lead * 60e3), latest: (n) => new Date(n.getTime() + days * 864e5),
    });
    const quick = sample.call.quickTimes;
    const at = (iso) => new Date(iso);
    const view = (now, r) => L.quickTimeOptions(quick, now, r).map((q) => [q.label, q.disabled]);
    // 17:37 in Kolkata: all three are ahead and inside a 5-minute / 14-day window; the broken one is shown disabled.
    assert.deepEqual(view(at("2026-10-14T12:07:31Z"), rules(5, 14)),
      [["In 20 minutes", false], ["Today 19:00", false], ["Tomorrow 09:30", false], ["Broken", true]]);
    // 18:58: today's 19:00 is inside the 5-minute notice — disabled.
    assert.deepEqual(view(at("2026-10-14T13:28:00Z"), rules(5, 14)).slice(0, 3),
      [["In 20 minutes", false], ["Today 19:00", true], ["Tomorrow 09:30", false]]);
    // A 30-minute notice rules out "In 20 minutes"; a window shorter than tomorrow rules that out.
    assert.deepEqual(view(at("2026-10-14T12:07:31Z"), { ...rules(30, 14), latest: (n) => new Date(n.getTime() + 6 * 3600e3) }).slice(0, 3),
      [["In 20 minutes", true], ["Today 19:00", false], ["Tomorrow 09:30", true]]);
    // Without the rules only the past is known to be out: 20:00 has passed today's 19:00.
    assert.deepEqual(view(at("2026-10-14T14:30:00Z"), null).slice(0, 3),
      [["In 20 minutes", false], ["Today 19:00", true], ["Tomorrow 09:30", false]]);
    const opts = L.quickTimeOptions(quick, at("2026-10-14T12:07:31Z"), null);
    assert.deepEqual(opts.map((q) => [q.index, q.relative]), [[0, true], [1, false], [2, false], [3, false]]);
    assert.deepEqual(L.quickTimeOptions(undefined, new Date(), null), []);
    assert.deepEqual(L.quickTimeOptions([{ inMinutes: 5 }], new Date(), null), [], "no label, no chip");
  });
});

test("the picker's line: time zone, the chosen time, the SDK's window; 5-minute steps", () => {
  inZone("Asia/Kolkata", () => {
    const now = new Date("2026-10-14T06:00:00Z");
    const rules = { minLeadMinutes: 5, maxDaysAhead: 14 };
    // The chosen time is written in the visitor's own locale, as everywhere on the page.
    const when = new Date("2026-10-14T12:30:00Z");
    assert.equal(L.zoneLine(now, when, rules),
      `Your time zone: ${L.timeZoneLabel(now)} · ${L.formatWhen(when, now)}. Between 5 minutes and 14 days from now.`);
    assert.match(L.formatWhen(when, now), /^Today, (18:00|0?6:00\s?PM)$/i);
    assert.match(L.zoneLine(now, null, null), /^Your time zone: Asia\/(Kolkata|Calcutta) \(GMT\+5:30\)\.$/);
    assert.equal(L.ceilToStep(new Date("2026-10-14T12:07:31Z"), 5).toISOString(), "2026-10-14T12:10:00.000Z");
    assert.equal(L.ceilToStep(new Date("2026-10-14T12:10:00Z"), 5).toISOString(), "2026-10-14T12:10:00.000Z");
  });
});

test("every real pack names its booking button, 2–4 reasons and quick times that resolve", () => {
  const now = new Date();
  for (const pack of realPacks) {
    assert.ok(typeof pack.call.bookLabel === "string" && pack.call.bookLabel, `${pack.id} bookLabel`);
    assert.ok(pack.call.reasons.length >= 2 && pack.call.reasons.length <= 4, `${pack.id} reasons`);
    const quick = L.quickTimeOptions(pack.call.quickTimes, now, null);
    assert.equal(quick.length, pack.call.quickTimes.length, `${pack.id}: every quick time is a chip`);
    for (const q of quick) assert.ok(q.at instanceof Date, `${pack.id}: "${q.label}" resolves to a time`);
  }
  const health = realPacks.find((p) => p.id === "health-coach");
  if (health) assert.equal(health.call.bookLabel, "Schedule a consultation call");
});

test("times read as Today / Tomorrow / Yesterday, else the day", () => {
  inZone("Asia/Kolkata", () => {
    const now = new Date("2026-10-14T06:00:00Z");   // 11:30 in Kolkata
    assert.equal(L.formatWhen(new Date("2026-10-14T12:30:00Z"), now, "en-GB"), "Today, 18:00");
    assert.equal(L.formatWhen(new Date("2026-10-15T03:30:00Z"), now, "en-GB"), "Tomorrow, 09:00");
    assert.equal(L.formatWhen(new Date("2026-10-13T12:30:00Z"), now, "en-GB"), "Yesterday, 18:00");
    assert.equal(L.formatWhen(new Date("2026-10-16T12:30:00Z"), now, "en-GB"), "Fri 16 Oct, 18:00");
    // Another year shows the year (the punctuation is the locale's own).
    assert.match(L.formatWhen(new Date("2027-01-05T12:30:00Z"), now, "en-GB"), /^Tue,? 5 Jan 2027, 18:00$/);
    assert.equal(L.formatWhen(null, now), "Time not available");
  });
});

test("errors in words, with the SDK's limits when the page has them", () => {
  const rules = { minLeadMinutes: 10, maxDaysAhead: 7, maxPendingPerUser: 2 };
  assert.equal(L.messageFor("too_soon", "sdk sentence", rules), "Choose a time at least 10 minutes from now.");
  assert.equal(L.messageFor("too_far", "sdk sentence", rules), "Choose a time within the next 7 days.");
  assert.equal(L.messageFor("too_many_pending", "", rules), "You already have 2 upcoming calls. Cancel one to book another.");
  assert.equal(L.messageFor("too_soon", "Choose a time at least 5 minutes from now."), "Choose a time at least 5 minutes from now.");
  assert.equal(L.messageFor("too_soon", ""), "That time is too soon. Choose a later time.");
  assert.match(L.messageFor("scheduling_off"), /turned off/);
  assert.equal(L.messageFor("identity_required", "anything"), "Sign in to use this.");
  assert.match(L.messageFor("origin_refused"), /Allowed domains/);
  assert.equal(L.messageFor("mystery", "Server said this."), "Server said this.");
  assert.equal(L.messageFor("mystery"), "Something went wrong. Try again.");
});

test("screen context: the same fields as every platform (usecases/README.md)", () => {
  const now = Date.now();
  const calls = [
    { id: "a", status: "scheduled", isUpcoming: true, canCancel: true, at: new Date(now + 3600e3) },
    { id: "b", status: "completed", isUpcoming: false, canCancel: false, at: new Date(now - 3600e3) },
  ];
  const base = { pack: sample, userId: "M-001", memoryOn: true, statuses: { "t-100": "Closed" }, calls, memoriesCount: 4 };

  const home = L.screenContext({ ...base, screen: "home" });
  assert.equal(home.screen, "home");
  assert.equal(home.title, "Overview");
  assert.deepEqual(home.fields.map((f) => [f.name, f.status]), [["memory", "filled"], ["next_call", "filled"]]);
  assert.equal(home.fields[0].value, "on");
  assert.match(home.fields[1].value, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/);
  assert.equal(L.screenContext({ ...base, screen: "home", calls: [], memoryOn: false }).fields[1].value, "none");
  assert.equal(L.screenContext({ ...base, screen: "home", memoryOn: false }).fields[0].value, "off");

  const items = L.screenContext({ ...base, screen: "items" });
  assert.equal(items.screen, "tickets");
  assert.equal(items.title, "Tickets");
  assert.deepEqual(items.fields, [
    { name: "t-100", label: "Router replacement", value: "Closed", status: "filled" },
    { name: "t-101", label: "Router password reset", value: "Opened 5 October", status: "filled" },
    { name: "t-300", label: "Shared notice", value: "For everyone", status: "filled" },
  ]);

  assert.deepEqual(L.screenContext({ ...base, screen: "calls" }).fields, [
    { name: "upcoming", label: "Upcoming calls", value: "1", status: "filled" },
  ]);
  const memory = L.screenContext({ ...base, screen: "memory" });
  assert.deepEqual(memory.fields.map((f) => [f.name, f.value]), [["memory", "on"], ["remembered", "4"]]);
  assert.deepEqual(L.screenContext({ ...base, screen: "chat" }), { screen: "chat", title: "Nova", fields: [] });
  assert.deepEqual(L.screenContext({ ...base, screen: "profile" }).fields, []);
  assert.deepEqual(L.screenContext({ ...base, screen: "sign_in" }), { screen: "sign_in", title: "Sign in", fields: [] });
  assert.deepEqual(L.screenContext({ pack: null }), { screen: "sign_in", title: "Sign in", fields: [] });
});

test("identify gets the identifier under the pack's field, the name and the traits", () => {
  assert.deepEqual(L.traitsFor(sample, sample.users[0]), { member_id: "M-001", name: "Alex Rivera", tier: "Gold", city: "Pune" });
  for (const pack of realPacks) {
    const traits = L.traitsFor(pack, pack.users[0]);
    assert.equal(traits[pack.identifierField], pack.users[0].id);
  }
  assert.equal(L.humanize("home_branch"), "Home branch");
  assert.equal(L.humanize("patient_id"), "Patient ID");
  assert.equal(L.humanize("careTeam"), "Care team");
  assert.equal(L.firstName("Riya Sharma"), "Riya");
});

test("the pack's two agents: main repeats the top level, help comes from agents.help", () => {
  for (const pack of realPacks) {
    const main = L.agentInfo(pack, "main");
    const help = L.agentInfo(pack, "help");
    assert.deepEqual([main.name, main.role], [pack.agentName, pack.agentRole], `${pack.id}: main`);
    assert.ok(help && help.name && help.callName && help.bookLabel && help.reasons.length >= 2 && help.starters.length, `${pack.id}: help`);
    assert.equal(L.screenTitle("help", pack), help.name);
    assert.deepEqual(L.screenContext({ pack, screen: "help", userId: pack.users[0].id, memoryOn: true, statuses: {}, calls: [], memoriesCount: 0 }),
      { screen: "help", title: help.name, fields: [] });
    // Help is not in the pack's open_screen list, so the agent cannot open it.
    assert.equal(L.parseScreen("help", pack), null);
  }
  // A pack from before two agents: main from the top-level fields, no help.
  assert.deepEqual(L.agentInfo(sample, "main"), { name: "Nova", role: "service assistant", starters: sample.starters });
  assert.equal(L.agentInfo(sample, "help"), null);
  assert.equal(L.screenTitle("help", sample), "Help");
});
