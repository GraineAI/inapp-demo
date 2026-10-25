// The adapter (graine.js) against fake loaders: none configured, blocked, the released 1.1.0
// surface, one that never starts, and one with identity, memory, scheduling and the booking sheet.
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  createGraine, GraineError, loaderSwitchesAgents, normaliseCall, normaliseMemory, normalisePastSession,
  normalisePastSessionsPage, normaliseSessionTranscript, normaliseStatus, toGraineError, versionAtLeast,
} from "../graine.js";

const FAST = { scriptWaitMs: 30, startWaitMs: 40, pollMs: 2 };
const CONFIG = { agentId: "agent_test", publishableKey: "pk_test_adapter", baseUrl: "https://www.graine.ai" };

function fakeWindow() {
  const warnings = [];
  return {
    GraineAgent: undefined,
    console: { warn: (...a) => warnings.push(a.join(" ")), error: () => {} },
    location: { host: "localhost:3000" },
    warnings,
  };
}

/** The released loader's public surface (1.1.0): open() appears once the assistant has started. */
function releasedLoader(win, { starts = true, says = "" } = {}) {
  const calls = [];
  const api = {
    version: "1.1.0",
    actions: {},
    load(opts) {
      calls.push(["load", opts]);
      if (says) win.console.error(`[Graine] ${says}`);
      if (starts) setTimeout(() => { api.open = () => calls.push(["open"]); api.close = () => {}; }, 5);
    },
    defineAction(name, fn) { calls.push(["defineAction", name]); api.actions[name] = fn; },
    removeAction() {},
    setScreen(ctx) { calls.push(["setScreen", ctx]); },
    addMaskRule() {},
    reportEvent() {},
  };
  return { api, calls };
}

/** A loader with the identity, memory, scheduling and booking-sheet methods; `answers` decides what each returns. */
function extendedLoader(win, answers = {}) {
  const { api, calls } = releasedLoader(win);
  api.version = "1.2.0";
  api.installId = "1b4e28ba-2fa1-41d2-883f-0016d3cca427";
  for (const name of ["identify", "setMemory", "forgetMemory", "memories", "openBooking", "scheduledCalls", "cancelScheduledCall"]) {
    api[name] = async (...args) => {
      calls.push([name, ...args]);
      const a = answers[name];
      return typeof a === "function" ? a(...args) : a;
    };
  }
  return { api, calls };
}

function setup(loaderFor, extra = {}) {
  const win = fakeWindow();
  const notices = [];
  const scripts = [];
  let loader = null;
  const g = createGraine({
    window: win,
    ...FAST,
    onNotice: (n) => notices.push(n),
    insertScript: (doc, src) => {
      scripts.push(src);
      if (!loaderFor) return Promise.reject(new Error("blocked"));
      loader = loaderFor(win);
      win.GraineAgent = loader.api;
      return Promise.resolve();
    },
    ...extra,
  });
  return { g, win, notices, scripts, get loader() { return loader; } };
}

// Generous: the machine running the suite may be busy; a pass is usually a few milliseconds.
async function waitFor(check, ms = 5000) {
  const end = Date.now() + ms;
  while (!check()) {
    if (Date.now() > end) throw new Error("timed out");
    await new Promise((r) => setTimeout(r, 2));
  }
}

test("no agent configured: nothing is loaded, a notice says so, calls fail with not_configured", async () => {
  for (const cfg of [{}, { agentId: "your-agent-id", publishableKey: "pk_live_…" }, { agentId: "a", publishableKey: " " }]) {
    const t = setup(() => { throw new Error("must not load"); });
    await t.g.start(cfg);
    assert.equal(t.g.state, "not_configured");
    assert.equal(t.scripts.length, 0);
    assert.equal(t.notices[0].key, "not_configured");
    await assert.rejects(t.g.identify({ member_id: "M-1" }), (e) => e instanceof GraineError && e.code === "not_configured");
    await assert.rejects(t.g.open(), { code: "not_configured" });
    t.g.setScreen({ screen: "home" });          // never throws
    t.g.defineAction("open_screen", () => ({}));
  }
});

test("the loader is blocked: the page says it did not load, and every call fails with not_loaded", async () => {
  const t = setup(null);
  await t.g.start(CONFIG);
  assert.deepEqual(t.scripts, ["https://www.graine.ai/embed/v1.js"]);
  assert.equal(t.g.state, "unavailable");
  const n = t.notices.find((x) => x.key === "not_loaded");
  assert.ok(n, "a not_loaded notice");
  assert.match(n.text, /https:\/\/www\.graine\.ai\/embed\/v1\.js/);
  for (const call of [() => t.g.identify({}), () => t.g.memories(), () => t.g.scheduledCalls(), () => t.g.open(), () => t.g.signOut()]) {
    await assert.rejects(call(), { code: "not_loaded" });
  }
  t.g.setScreen({ screen: "home" });
  t.g.defineAction("x", () => ({}));
  assert.equal(t.g.supports("identify"), false);
  assert.equal(t.g.installId(), null);
});

test("where the loader comes from: baseUrl, a trailing slash, or loaderUrl", async () => {
  const a = setup(null);
  await a.g.start({ ...CONFIG, baseUrl: "http://localhost:3001/" });
  assert.deepEqual(a.scripts, ["http://localhost:3001/embed/v1.js"]);
  const b = setup(null);
  await b.g.start({ ...CONFIG, loaderUrl: "test/fake-loader.js" });
  assert.deepEqual(b.scripts, ["test/fake-loader.js"]);
  const c = setup(null);
  await c.g.start({ agentId: "a", publishableKey: "pk_test_x" });
  assert.deepEqual(c.scripts, ["https://www.graine.ai/embed/v1.js"]);
});

test("the released loader: what the page set up early is handed over before load(), then ready", async () => {
  const t = setup((win) => releasedLoader(win));
  const handler = () => ({ status: "ok", message: "Opened Home." });
  t.g.defineAction("open_screen", handler);
  t.g.setScreen({ screen: "sign_in", title: "Sign in", fields: [] });
  await t.g.start(CONFIG);
  assert.deepEqual(t.loader.calls.map((c) => c[0]), ["defineAction", "setScreen", "load"]);
  assert.deepEqual(t.loader.calls[2][1], { agentId: "agent_test", publishableKey: "pk_test_adapter" });
  assert.equal(t.g.state, "starting");
  await waitFor(() => t.g.state === "ready");
  await t.g.open();
  assert.deepEqual(t.loader.calls.at(-1), ["open"]);
  // The handler the loader holds answers, and a throwing one answers "error" instead of hanging.
  assert.deepEqual(await t.loader.api.actions.open_screen({ screen: "home" }), { status: "ok", message: "Opened Home." });
  t.g.defineAction("boom", () => { throw new Error("broke"); });
  assert.deepEqual(await t.loader.api.actions.boom({}), { status: "error", message: "broke" });
  t.g.setScreen({ screen: "home" });
  assert.deepEqual(t.loader.calls.at(-1), ["setScreen", { screen: "home" }]);
});

test("the released loader lacks identity, memory and scheduling: one notice names each, with the version", async () => {
  const t = setup((win) => releasedLoader(win));
  await t.g.start(CONFIG);
  await assert.rejects(t.g.identify({ member_id: "M-001", name: "Alex" }), { code: "unsupported" });
  let n = t.notices.filter((x) => x.key === "unsupported").at(-1);
  assert.match(n.text, /version 1\.1\.0/);
  assert.deepEqual(n.lines.map((l) => l.split(" — ")[0]), ["GraineAgent.identify()"]);
  await assert.rejects(t.g.memories(), { code: "unsupported" });
  await assert.rejects(t.g.openBooking({ reason: "General question" }), { code: "unsupported" });
  await assert.rejects(t.g.forgetMemory(), { code: "unsupported" });
  n = t.notices.filter((x) => x.key === "unsupported").at(-1);
  assert.deepEqual(n.lines.map((l) => l.split(" — ")[0]),
    ["GraineAgent.identify()", "GraineAgent.memories()", "GraineAgent.openBooking()", "GraineAgent.forgetMemory()"]);
  assert.equal(t.g.supports("memories"), false);
  assert.equal(t.g.supports("setScreen"), true);
  assert.equal(t.g.version(), "1.1.0");
});

test("a loader that never starts the assistant: open() says why, with what the loader printed", async () => {
  const t = setup((win) => releasedLoader(win, { starts: false, says: "Publishable key was rejected." }));
  await t.g.start(CONFIG);
  await assert.rejects(t.g.open(), { code: "starting" });
  await waitFor(() => t.g.state === "not_started");
  const n = t.notices.find((x) => x.key === "not_started");
  assert.match(n.text, /localhost:3000/);
  assert.match(n.text, /Publishable key was rejected\./);
  assert.equal(t.g.loaderError, "Publishable key was rejected.");
  await assert.rejects(t.g.open(), { code: "not_started" });
});

test("a loader that arrives after the wait still starts", async () => {
  const win = fakeWindow();
  const states = [];
  const g = createGraine({
    window: win, ...FAST,
    insertScript: () => new Promise((resolve) => setTimeout(() => {
      win.GraineAgent = releasedLoader(win).api;
      resolve();
    }, 60)),
  });
  g.onState((s) => states.push(s));
  await g.start(CONFIG);
  assert.equal(g.state, "unavailable");
  await waitFor(() => g.state === "ready");
  assert.deepEqual(states, ["loading", "unavailable", "starting", "ready"]);
});

test("identity and memory go through as given", async () => {
  const t = setup((win) => extendedLoader(win, {
    forgetMemory: { deleted: 2 },
    memories: [
      { id: "m1", text: "Prefers calls after 6 pm.", createdAt: "2026-10-13T10:00:00Z", updatedAt: null },
      { id: "", text: "no id: dropped" },
      { id: "m2", memory: "Older shape.", created_at: 1760000000000 },
      null,
    ],
  }));
  await t.g.start(CONFIG);
  await t.g.identify({ member_id: "M-001", name: "Alex" });
  await t.g.identify({ member_id: "M-001", name: "Alex" }, { memory: false });
  await t.g.signOut();
  await t.g.setMemory(1);
  const calls = t.loader.calls.filter((c) => ["identify", "setMemory"].includes(c[0]));
  assert.deepEqual(calls, [
    ["identify", { member_id: "M-001", name: "Alex" }, { memory: true }],
    ["identify", { member_id: "M-001", name: "Alex" }, { memory: false }],
    ["identify", null],
    ["setMemory", true],
  ]);
  const list = await t.g.memories();
  assert.deepEqual(list.map((m) => m.id), ["m1", "m2"]);
  assert.ok(list[0].createdAt instanceof Date && list[0].updatedAt === null);
  assert.equal(list[1].text, "Older shape.");
  assert.equal(await t.g.forgetMemory(), 2);
  assert.equal(t.g.installId(), "1b4e28ba-2fa1-41d2-883f-0016d3cca427");
  assert.equal(t.g.supports("memories"), true);
});

test("memories and forget accept the other answer shapes", async () => {
  const t = setup((win) => extendedLoader(win, { memories: { memories: [{ id: "a", text: "x" }] }, forgetMemory: 5 }));
  await t.g.start(CONFIG);
  assert.equal((await t.g.memories()).length, 1);
  assert.equal(await t.g.forgetMemory(), 5);
  t.loader.api.forgetMemory = async () => undefined;
  assert.equal(await t.g.forgetMemory(), null);
  // The committed loader shows the install id through getInstallId().
  delete t.loader.api.installId;
  t.loader.api.getInstallId = function () { return "0f8fad5b-d9cb-469f-a165-70867728950e"; };
  assert.equal(t.g.installId(), "0f8fad5b-d9cb-469f-a165-70867728950e");
});

test("booking: the widget's sheet opens with the reason, and the call booked there comes back with a Date", async () => {
  const t = setup((win) => extendedLoader(win, {
    openBooking: (o) => ({ id: "c1", at: "2026-10-14T12:30:00Z", status: "scheduled", source: "sdk", reason: o.reason }),
    scheduledCalls: { calls: [
      { id: "c1", at: "2026-10-14T12:30:00Z", status: "scheduled", source: "sdk", reason: "General question" },
      { id: "c2", scheduledAt: "2026-10-13T08:00:00Z", status: "on_hold_new", source: "robot" },
      { status: "missed" },
    ] },
  }));
  await t.g.start(CONFIG);
  const booked = await t.g.openBooking({ reason: "General question" });
  assert.deepEqual(t.loader.calls.find((c) => c[0] === "openBooking"), ["openBooking", { reason: "General question" }]);
  assert.equal(booked.id, "c1");
  assert.equal(booked.at.toISOString(), "2026-10-14T12:30:00.000Z");
  assert.equal(booked.source, "app");
  await t.g.openBooking();
  assert.deepEqual(t.loader.calls.filter((c) => c[0] === "openBooking")[1], ["openBooking", {}]);
  // Closed without booking: null.
  t.loader.api.openBooking = async () => null;
  assert.equal(await t.g.openBooking({ reason: "x" }), null);
  const list = await t.g.scheduledCalls();
  assert.deepEqual(list.map((c) => [c.id, c.status, c.source, c.isUpcoming, c.canCancel]),
    [["c1", "scheduled", "app", true, true], ["c2", "on_hold_new", "robot", false, false]]);
  t.loader.api.cancelScheduledCall = async (id) => { t.loader.calls.push(["cancelScheduledCall", id]); return "cancelled"; };
  assert.equal(await t.g.cancelScheduledCall("c1"), "cancelled");
  assert.deepEqual(t.loader.calls.at(-1), ["cancelScheduledCall", "c1"]);
});

test("the loader's errors leave as GraineError with their code", async () => {
  const t = setup((win) => extendedLoader(win, {
    memories: () => { throw Object.assign(new Error("Too many requests."), { code: "rate_limited", retryAfterSeconds: 30 }); },
    scheduledCalls: () => { throw new Error("socket closed"); },
  }));
  await t.g.start(CONFIG);
  await assert.rejects(t.g.memories(), (e) => e instanceof GraineError && e.code === "rate_limited" && e.retryAfterSeconds === 30);
  await assert.rejects(t.g.scheduledCalls(), (e) => e.code === "unknown" && e.message === "socket closed");
});

test("bookingAvailable: false until the assistant has started, then the loader's own", async () => {
  let available = true;
  const t = setup((win) => {
    const l = extendedLoader(win);
    Object.defineProperty(l.api, "bookingAvailable", { get: () => available, enumerable: true });
    return l;
  });
  assert.equal(t.g.bookingAvailable(), false);
  await t.g.start(CONFIG);
  assert.equal(t.g.bookingAvailable(), false, "still starting: the session is not known yet");
  await waitFor(() => t.g.state === "ready");
  assert.equal(t.g.bookingAvailable(), true);
  available = false;
  assert.equal(t.g.bookingAvailable(), false, "read again every time: it changes with the session and the person");
  assert.equal(t.notices.filter((n) => n.key === "unsupported").length, 0);
});

test("a loader without the booking sheet: booking is off, and the notice names openBooking() and bookingAvailable", async () => {
  const t = setup((win) => releasedLoader(win));
  await t.g.start(CONFIG);
  await waitFor(() => t.g.state === "ready");
  assert.equal(t.g.bookingAvailable(), false);
  const n = t.notices.filter((x) => x.key === "unsupported").at(-1);
  assert.deepEqual(n.lines.map((l) => l.split(" — ")[0]), ["GraineAgent.openBooking()", "GraineAgent.bookingAvailable"]);
});

test("booked calls come out in the SDK types' shape, whichever loader answered", () => {
  // A loader with the SDK types: its isUpcoming / canCancel are kept as given.
  assert.deepEqual(normaliseCall({ id: "a", at: new Date("2026-10-14T12:30:00Z"), status: "noDevice", source: "app", reason: null, isUpcoming: false, canCancel: false }),
    { id: "a", at: new Date("2026-10-14T12:30:00Z"), status: "noDevice", source: "app", reason: null, isUpcoming: false, canCancel: false });
  // One from before them: the wire's names, and the two flags read off the status.
  const wire = normaliseCall({ id: "b", at: "2026-10-14T12:30:00Z", status: "no_device", source: "sdk" });
  assert.deepEqual([wire.status, wire.source, wire.isUpcoming, wire.canCancel], ["noDevice", "app", false, false]);
  const ringing = normaliseCall({ id: "c", status: "ringing" });
  assert.deepEqual([ringing.isUpcoming, ringing.canCancel], [true, false]);
  const fresh = normaliseCall({ id: "d", status: "scheduled" });
  assert.deepEqual([fresh.isUpcoming, fresh.canCancel], [true, true]);
  assert.equal(normaliseCall({ id: "e", status: { odd: true } }).status, "unknown");
  assert.equal(normaliseStatus("cancelled"), "cancelled");
  assert.equal(normaliseStatus({ ok: true, status: "no_device" }), "noDevice");
  assert.equal(normaliseStatus(undefined), null);
});

test("shapes are read defensively", () => {
  assert.equal(normaliseCall(null), null);
  assert.equal(normaliseCall({ status: "scheduled" }), null);
  assert.deepEqual(normaliseCall({ call_id: "x", scheduled_at: "nonsense" }),
    { id: "x", at: null, status: "scheduled", source: null, reason: null, isUpcoming: true, canCancel: true });
  assert.equal(normaliseMemory({ id: "m", text: "" }), null);
  assert.equal(normaliseMemory({ id: "m", text: "t", updatedAt: "2026-10-01T00:00:00Z" }).updatedAt.toISOString(), "2026-10-01T00:00:00.000Z");
  assert.equal(toGraineError("plain").code, "unknown");
  assert.equal(toGraineError({ code: "device_limit", message: "m" }).code, "device_limit");
});

// ── Two agents (MULTI-AGENT.md → Website) ───────────────────────────────────

const HELP = { agentId: "agent_help", publishableKey: "pk_test_help" };
const TWO = { agents: { main: { agentId: "agent_test", publishableKey: "pk_test_adapter" }, help: HELP }, baseUrl: "http://127.0.0.1:9" };

/** A loader that switches: load() resolves true once the agent is on the page, and replaces the widget. */
function switchingLoader(win, answers = {}) {
  const l = extendedLoader(win, answers);
  l.api.version = "1.2.0";
  l.api.load = (opts) => {
    l.calls.push(["load", opts]);
    delete l.api.open;
    return new Promise((resolve) => setTimeout(() => {
      l.api.open = () => l.calls.push(["open", opts.agentId]);
      resolve(true);
    }, 5));
  };
  return l;
}

test("two agents: the main one starts; useAgent switches with load() again and back", async () => {
  const t = setup((win) => switchingLoader(win));
  await t.g.start(TWO);
  assert.equal(t.g.agent, "main");
  assert.ok(t.g.hasAgent("help"));
  await waitFor(() => t.g.state === "ready");
  assert.equal(t.g.canSwitch(), true, "a load() that returns a promise switches");
  await t.g.useAgent("help");
  assert.equal(t.g.agent, "help");
  assert.equal(t.g.state, "starting", "the help agent's widget is not up yet");
  await waitFor(() => t.g.state === "ready");
  await t.g.open();
  assert.deepEqual(t.loader.calls.filter((c) => c[0] === "load" || c[0] === "open").map((c) => c[0] === "load" ? c[1].agentId : `open ${c[1]}`),
    ["agent_test", "agent_help", "open agent_help"]);
  await t.g.useAgent("help");   // already there: nothing happens
  assert.equal(t.loader.calls.filter((c) => c[0] === "load").length, 2);
  await t.g.useAgent("main");
  await waitFor(() => t.g.state === "ready");
  assert.equal(t.g.agent, "main");
});

test("a loader that cannot switch is never asked to: the notice names it, the page stays on main", async () => {
  const t = setup((win) => releasedLoader(win));
  await t.g.start({ ...TWO, baseUrl: CONFIG.baseUrl });
  await waitFor(() => t.g.state === "ready");
  assert.equal(t.g.canSwitch(), false);
  await assert.rejects(t.g.useAgent("help"), { code: "unsupported" });
  assert.equal(t.g.agent, "main");
  assert.equal(t.loader.calls.filter((c) => c[0] === "load").length, 1, "no second assistant mounted");
  const n = t.notices.filter((x) => x.key === "unsupported").at(-1);
  assert.match(n.lines.join(" "), /GraineAgent\.load\(\) with another agent/);
});

test("no help agent configured: useAgent refuses, nothing loads", async () => {
  const t = setup((win) => switchingLoader(win));
  await t.g.start({ agents: { main: TWO.agents.main, help: { agentId: "", publishableKey: "" } } });
  assert.equal(t.g.hasAgent("help"), false);
  await assert.rejects(t.g.useAgent("help"), { code: "not_configured" });
  assert.deepEqual(await t.g.scheduledCallsOf("help", "M-001"), []);
});

test("the other agent's calls come from the public route with its key, the install id and the identifier", async () => {
  const sent = [];
  const t = setup((win) => switchingLoader(win, { scheduledCalls: [{ id: "m1", at: "2026-10-14T12:30:00Z", status: "scheduled", source: "app", isUpcoming: true, canCancel: true }] }), {
    fetch: async (url, init) => {
      sent.push({ url, body: JSON.parse(init.body) });
      if (url.endsWith("/calls/list")) {
        return { ok: true, status: 200, json: async () => ({ calls: [{ id: "h1", at: "2026-10-15T04:30:00Z", status: "no_device", source: "agent" }] }) };
      }
      return { ok: false, status: 404, headers: { get: () => null }, json: async () => ({ error: "That call does not exist.", code: "not_found" }) };
    },
  });
  await t.g.start(TWO);
  const mainCalls = await t.g.scheduledCallsOf("main", "M-001");
  assert.deepEqual(mainCalls.map((c) => c.id), ["m1"], "the agent on the page answers through the loader");
  const helpCalls = await t.g.scheduledCallsOf("help", "M-001");
  assert.deepEqual(helpCalls.map((c) => [c.id, c.status, c.source, c.isUpcoming]), [["h1", "noDevice", "agent", false]]);
  assert.deepEqual(sent[0], {
    url: "http://127.0.0.1:9/api/embed/calls/list",
    body: { publishableKey: "pk_test_help", installId: "1b4e28ba-2fa1-41d2-883f-0016d3cca427", identity: { value: "M-001" } },
  });
  await assert.rejects(t.g.cancelScheduledCallOf("help", "nope", "M-001"), (e) => e.code === "not_found" && e.message === "That call does not exist.");
  assert.deepEqual(sent[1].body, { publishableKey: "pk_test_help", installId: "1b4e28ba-2fa1-41d2-883f-0016d3cca427", callId: "nope", identity: { value: "M-001" } });
});

test("versions and switch detection", () => {
  assert.ok(versionAtLeast("1.3.0", "1.3.0"));
  assert.ok(versionAtLeast("1.10.0", "1.3.0"));
  assert.ok(!versionAtLeast("1.2.9", "1.3.0"));
  assert.ok(!versionAtLeast(undefined, "1.3.0"));
  assert.equal(loaderSwitchesAgents({ version: "1.2.0" }), false);
  assert.equal(loaderSwitchesAgents({ version: "1.2.0" }, true), true, "load() returned a promise");
  assert.equal(loaderSwitchesAgents({ version: "1.3.0" }), true);
  assert.equal(loaderSwitchesAgents({ version: "9.0.0", switchesAgents: false }), false, "the loader's own word wins");
  assert.equal(loaderSwitchesAgents(null), false);
});

// ── Past sessions (sdk/contract/past-sessions.json): the public routes, for either agent ──────────

const INSTALL = "1b4e28ba-2fa1-41d2-883f-0016d3cca427";
const answer = (status, data) => ({ ok: status >= 200 && status < 300, status, headers: { get: () => null }, json: async () => data });
const plain = (s) => ({ ...s, startedAt: s.startedAt && s.startedAt.toISOString(), endedAt: s.endedAt && s.endedAt.toISOString() });

test("past sessions: pages read as the contract's vectors say", () => {
  const first = normalisePastSessionsPage({
    sessions: [
      { id: "ex-7a1c0001", startedAt: "2026-10-21T12:00:05Z", endedAt: "2026-10-21T12:04:41Z", durationSeconds: 276, channel: "chat", direction: null, status: "completed", turns: 9, preview: "Can I move my check-in call to Friday?" },
      { id: "ex-31f00002", startedAt: "2026-10-20T08:00:00Z", endedAt: "2026-10-20T08:03:12Z", durationSeconds: 192, channel: "call", direction: "outbound", status: "completed", turns: 14, preview: "Hi, yes I can talk now." },
    ],
    next: "c2",
  });
  assert.equal(first.next, "c2");
  assert.deepEqual(first.sessions.map(plain), [
    { id: "ex-7a1c0001", startedAt: "2026-10-21T12:00:05.000Z", endedAt: "2026-10-21T12:04:41.000Z", durationSeconds: 276, channel: "chat", direction: null, status: "completed", turns: 9, preview: "Can I move my check-in call to Friday?" },
    { id: "ex-31f00002", startedAt: "2026-10-20T08:00:00.000Z", endedAt: "2026-10-20T08:03:12.000Z", durationSeconds: 192, channel: "call", direction: "outbound", status: "completed", turns: 14, preview: "Hi, yes I can talk now." },
  ]);
  // Unreadable items are skipped and loose fields fall back.
  const loose = normalisePastSessionsPage({
    sessions: [
      { startedAt: "2026-10-21T12:00:05Z", channel: "chat", turns: 3, preview: "no id" },
      { id: "ex-bad-time", startedAt: "yesterday", channel: "chat", turns: 3, preview: "bad time" },
      { id: "ex-loose0003", startedAt: "2026-10-19T18:30:00Z", endedAt: "soon", durationSeconds: "long", channel: "sms", direction: "sideways", status: "missed", turns: -2, preview: 42 },
    ],
    next: null,
  });
  assert.equal(loose.next, null);
  assert.deepEqual(loose.sessions.map(plain), [
    { id: "ex-loose0003", startedAt: "2026-10-19T18:30:00.000Z", endedAt: null, durationSeconds: null, channel: "chat", direction: null, status: "missed", turns: 0, preview: "" },
  ]);
  // A voice session and an unknown status word pass through; an id repeated on a page is kept once.
  const voice = { id: "ex-voice0004", startedAt: "2026-10-18T09:00:00Z", endedAt: "2026-10-18T09:01:00Z", durationSeconds: 60, channel: "voice", direction: "inbound", status: "ended_by_agent", turns: 4, preview: "What time is my appointment?" };
  const page = normalisePastSessionsPage({ sessions: [voice, voice] });
  assert.deepEqual(page.sessions.map((s) => [s.id, s.channel, s.direction, s.status]), [["ex-voice0004", "voice", "inbound", "ended_by_agent"]]);
  assert.equal(normalisePastSession(null), null);
  assert.deepEqual(normalisePastSessionsPage(null), { sessions: [], next: null });
});

test("past sessions: a transcript's lines oldest first, the wire's user and assistant as customer and agent", () => {
  const t = normaliseSessionTranscript({
    id: "ex-7a1c0001", startedAt: "2026-10-21T12:00:05Z", endedAt: "2026-10-21T12:04:41Z", durationSeconds: 276, channel: "chat", direction: null, status: "completed",
    turns: [
      { role: "assistant", text: "Hi Meera, how can I help?", at: "2026-10-21T12:00:05Z" },
      { role: "user", text: "Can I move my check-in call to Friday?", at: "2026-10-21T12:00:20Z" },
      { role: "system", text: "dropped", at: "2026-10-21T12:00:21Z" },
      { role: "assistant", text: 7, at: "2026-10-21T12:00:22Z" },
      { role: "assistant", text: "Done: Friday at 10:00.", at: "not a time" },
      { role: "assistant", text: "Done: Friday at 10:00.", at: "2026-10-21T12:00:25Z" },
    ],
  }, "asked");
  assert.deepEqual(t.turns.map((l) => [l.role, l.text, l.at.toISOString()]), [
    ["agent", "Hi Meera, how can I help?", "2026-10-21T12:00:05.000Z"],
    ["customer", "Can I move my check-in call to Friday?", "2026-10-21T12:00:20.000Z"],
    ["agent", "Done: Friday at 10:00.", "2026-10-21T12:00:25.000Z"],
  ]);
  assert.deepEqual([t.id, t.channel, t.direction, t.status, t.durationSeconds], ["ex-7a1c0001", "chat", null, "completed", 276]);
  assert.equal(normaliseSessionTranscript({}, "ex-asked").id, "ex-asked", "the id asked for when the answer does not say");
});

test("past sessions: either agent through the public routes — the key, the install id, the identifier, the cursor and the id", async () => {
  const sent = [];
  const replies = [
    answer(200, { sessions: [{ id: "a1", startedAt: "2026-10-21T10:00:00Z", channel: "chat", status: "completed", turns: 2, preview: "One" }], next: "c2" }),
    answer(200, { sessions: [{ id: "a2", startedAt: "2026-10-20T10:00:00Z", channel: "call", direction: "outbound", status: "completed", turns: 6, preview: "Two" }], next: null }),
    answer(200, { sessions: [], next: null }),
    answer(200, { id: "a1", startedAt: "2026-10-21T10:00:00Z", channel: "chat", status: "completed", turns: [{ role: "user", text: "One", at: "2026-10-21T10:00:00Z" }] }),
  ];
  const t = setup((win) => switchingLoader(win), {
    fetch: async (url, init) => { sent.push({ url, body: JSON.parse(init.body) }); return replies.shift(); },
  });
  await t.g.start(TWO);
  const one = await t.g.pastSessionsOf("main", "M-001");
  assert.deepEqual([one.sessions.map((s) => s.id), one.next], [["a1"], "c2"]);
  const two = await t.g.pastSessionsOf("main", "M-001", "c2");
  assert.deepEqual([two.sessions.map((s) => [s.id, s.channel, s.direction]), two.next], [[["a2", "call", "outbound"]], null]);
  const help = await t.g.pastSessionsOf("help", "M-001");
  assert.deepEqual(help, { sessions: [], next: null });
  const tr = await t.g.sessionTranscriptOf("main", "M-001", " a1 ");
  assert.deepEqual(tr.turns.map((l) => [l.role, l.text]), [["customer", "One"]]);
  assert.deepEqual(sent, [
    { url: "http://127.0.0.1:9/api/embed/sessions/list", body: { publishableKey: "pk_test_adapter", installId: INSTALL, identity: { value: "M-001" } } },
    { url: "http://127.0.0.1:9/api/embed/sessions/list", body: { publishableKey: "pk_test_adapter", installId: INSTALL, cursor: "c2", identity: { value: "M-001" } } },
    { url: "http://127.0.0.1:9/api/embed/sessions/list", body: { publishableKey: "pk_test_help", installId: INSTALL, identity: { value: "M-001" } } },
    { url: "http://127.0.0.1:9/api/embed/sessions/transcript", body: { publishableKey: "pk_test_adapter", installId: INSTALL, id: "a1", identity: { value: "M-001" } } },
  ]);
  assert.equal(t.loader.calls.filter((c) => c[0] === "load").length, 1, "reading them never moves the page to another agent");
  await assert.rejects(t.g.sessionTranscriptOf("main", "M-001", "  "), { code: "invalid_request" });
  assert.equal(sent.length, 4, "a blank id asks nothing");
});

test("past sessions: refusals keep the route's code — identity_required, device_limit, not_found, rate_limited, unavailable, network", async () => {
  const refusals = [
    [400, { error: "Sign the user in first.", code: "identity_required" }, "identity_required"],
    [403, { error: "This device is not one of the user's.", code: "device_limit" }, "device_limit"],
    [404, { error: "Session not found.", code: "not_found" }, "not_found"],
    [429, { error: "Slow down.", code: "rate_limited" }, "rate_limited"],
    [503, {}, "unavailable"],
  ];
  let next = null;
  const t = setup((win) => switchingLoader(win), {
    fetch: async () => { if (next === "offline") throw new TypeError("Failed to fetch"); return answer(next[0], next[1]); },
  });
  await t.g.start(TWO);
  for (const r of refusals) {
    next = r;
    await assert.rejects(t.g.pastSessionsOf("main", "M-001"), (e) => e instanceof GraineError && e.code === r[2], `list ${r[0]}`);
    await assert.rejects(t.g.sessionTranscriptOf("help", "M-001", "ex-1"), (e) => e instanceof GraineError && e.code === r[2], `transcript ${r[0]}`);
  }
  next = "offline";
  await assert.rejects(t.g.pastSessionsOf("main", "M-001"), { code: "network" });
});

test("past sessions: not on a page without the agent, nor with a loader that does not show its install id", async () => {
  const one = setup((win) => switchingLoader(win), { fetch: async () => { throw new Error("must not be asked"); } });
  await one.g.start(CONFIG);
  await assert.rejects(one.g.pastSessionsOf("help", "M-001"), { code: "not_configured" });
  await assert.rejects(one.g.sessionTranscriptOf("help", "M-001", "ex-1"), { code: "not_configured" });
  const old = setup((win) => releasedLoader(win), { fetch: async () => { throw new Error("must not be asked"); } });
  await old.g.start(CONFIG);
  await assert.rejects(old.g.pastSessionsOf("main", "M-001"), (e) => e.code === "unsupported" && /past sessions cannot be read/.test(e.message));
});
