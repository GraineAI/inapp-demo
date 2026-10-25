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

test("the screen context carries a time as ISO-8601 in UTC", () => {
  assert.equal(L.toUtcIso(new Date("2026-10-14T12:30:00.123Z")), "2026-10-14T12:30:00Z");
});

test("every real pack names its booking button and the reasons the booking sheet starts with", () => {
  for (const pack of realPacks) {
    assert.ok(typeof pack.call.bookLabel === "string" && pack.call.bookLabel, `${pack.id} bookLabel`);
    assert.ok(pack.call.reasons.length >= 2 && pack.call.reasons.length <= 4, `${pack.id} reasons`);
    assert.ok(pack.call.reasons.every((r) => r.length <= 120), `${pack.id}: each reason fits the sheet's 120 characters`);
  }
  const health = realPacks.find((p) => p.id === "health-coach");
  if (health) assert.equal(health.call.bookLabel, "Schedule a consultation call");
  assert.equal(L.BOOKING_UNAVAILABLE, "Booking needs, in Graine UI: users may book (Calls & scheduling) and Who is the user? set to this pack's ID.");
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

test("errors in words; any other code reads as the SDK's own sentence", () => {
  // A booking's refusals are the widget's booking sheet's to word; anywhere else, the SDK's sentence.
  assert.equal(L.messageFor("outside_window", "Outside calling hours."), "Outside calling hours.");
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
  // The id rides under the pack's field and under user_id, so either "Who is the user?" choice recognises the user.
  assert.deepEqual(L.traitsFor(sample, sample.users[0]), { member_id: "M-001", name: "Alex Rivera", tier: "Gold", city: "Pune", user_id: "M-001" });
  for (const pack of realPacks) {
    const traits = L.traitsFor(pack, pack.users[0]);
    assert.equal(traits[pack.identifierField], pack.users[0].id);
  }
  assert.equal(L.humanize("home_branch"), "Home branch");
  assert.equal(L.humanize("patient_id"), "Patient ID");
  assert.equal(L.humanize("careTeam"), "Care team");
  assert.equal(L.firstName("Riya Sharma"), "Riya");
});

test("identify sends the id under the pack's field and under user_id", () => {
  const health = realPacks.find((p) => p.id === "health-coach");
  const user = health.users[0];
  const traits = L.traitsFor(health, user);
  assert.equal(traits[health.identifierField], user.id, "the pack's own field");
  assert.equal(traits.user_id, user.id, "and User ID, so an agent set to it still knows the user");
  const explicit = { ...user, traits: { ...user.traits, user_id: "kept-as-sent" } };
  assert.equal(L.traitsFor(health, explicit).user_id, "kept-as-sent", "a user_id the person already has is kept");
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
