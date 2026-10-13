// The adapter (graine.js) against fake loaders: none configured, blocked, the released 1.1.0
// surface, one that never starts, and one with identity, memory and scheduling.
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  createGraine, GraineError, loaderSwitchesAgents, normaliseCall, normaliseMemory, normaliseRules, normaliseStatus,
  toGraineError, versionAtLeast,
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

/** A loader with the identity, memory and scheduling methods; `answers` decides what each returns. */
function extendedLoader(win, answers = {}) {
  const { api, calls } = releasedLoader(win);
  api.version = "1.2.0";
  api.installId = "1b4e28ba-2fa1-41d2-883f-0016d3cca427";
  for (const name of ["identify", "setMemory", "forgetMemory", "memories", "scheduleCall", "scheduledCalls", "cancelScheduledCall"]) {
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
  await assert.rejects(t.g.scheduleCall({ at: "2026-10-14T18:00:00+05:30" }), { code: "unsupported" });
  await assert.rejects(t.g.forgetMemory(), { code: "unsupported" });
  n = t.notices.filter((x) => x.key === "unsupported").at(-1);
  assert.deepEqual(n.lines.map((l) => l.split(" — ")[0]),
    ["GraineAgent.identify()", "GraineAgent.memories()", "GraineAgent.scheduleCall()", "GraineAgent.forgetMemory()"]);
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

test("scheduling: the booking is sent as given and read back with a Date", async () => {
  const t = setup((win) => extendedLoader(win, {
    scheduleCall: (req) => ({ call: { id: "c1", at: "2026-10-14T12:30:00Z", status: "scheduled", source: "sdk", reason: req.reason } }),
    scheduledCalls: { calls: [
      { id: "c1", at: "2026-10-14T12:30:00Z", status: "scheduled", source: "sdk", reason: "General question" },
      { id: "c2", scheduledAt: "2026-10-13T08:00:00Z", status: "on_hold_new", source: "robot" },
      { status: "missed" },
    ] },
  }));
  await t.g.start(CONFIG);
  const booked = await t.g.scheduleCall({ at: "2026-10-14T18:00:00+05:30", reason: "General question" });
  assert.deepEqual(t.loader.calls.find((c) => c[0] === "scheduleCall"), ["scheduleCall", { at: "2026-10-14T18:00:00+05:30", reason: "General question" }]);
  assert.equal(booked.id, "c1");
  assert.equal(booked.at.toISOString(), "2026-10-14T12:30:00.000Z");
  await t.g.scheduleCall({ at: "2026-10-14T19:00:00+05:30" });
  assert.deepEqual(t.loader.calls.filter((c) => c[0] === "scheduleCall")[1][1], { at: "2026-10-14T19:00:00+05:30" });
  const list = await t.g.scheduledCalls();
  assert.deepEqual(list.map((c) => [c.id, c.status, c.source, c.isUpcoming, c.canCancel]),
    [["c1", "scheduled", "app", true, true], ["c2", "on_hold_new", "robot", false, false]]);
  t.loader.api.cancelScheduledCall = async (id) => { t.loader.calls.push(["cancelScheduledCall", id]); return "cancelled"; };
  assert.equal(await t.g.cancelScheduledCall("c1"), "cancelled");
  assert.deepEqual(t.loader.calls.at(-1), ["cancelScheduledCall", "c1"]);
});

test("the loader's errors leave as GraineError with their code", async () => {
  const t = setup((win) => extendedLoader(win, {
    scheduleCall: () => { throw Object.assign(new Error("Choose a time at least 5 minutes from now."), { code: "too_soon" }); },
    memories: () => { throw Object.assign(new Error("Too many requests."), { code: "rate_limited", retryAfterSeconds: 30 }); },
    scheduledCalls: () => { throw new Error("socket closed"); },
  }));
  await t.g.start(CONFIG);
  await assert.rejects(t.g.scheduleCall({ at: "x" }), (e) => e instanceof GraineError && e.code === "too_soon" && /5 minutes/.test(e.message));
  await assert.rejects(t.g.memories(), (e) => e.code === "rate_limited" && e.retryAfterSeconds === 30);
  await assert.rejects(t.g.scheduledCalls(), (e) => e.code === "unknown" && e.message === "socket closed");
});

test("the SDK's scheduling rules: unknown until the assistant has started, then the loader's own", async () => {
  const earliest = (now) => new Date(now.getTime() + 10 * 60e3);
  const t = setup((win) => {
    const l = extendedLoader(win);
    l.api.schedulingRules = { usersCanBook: true, minLeadMinutes: 10, maxDaysAhead: 7, maxPendingPerUser: 2, callName: "Nova · Sample", earliest };
    return l;
  });
  assert.equal(t.g.schedulingRules(), null);
  await t.g.start(CONFIG);
  assert.equal(t.g.schedulingRules(), null, "still starting: the session is not known yet");
  await waitFor(() => t.g.state === "ready");
  const rules = t.g.schedulingRules();
  assert.equal(rules.usersCanBook, true);
  assert.equal(rules.maxPendingPerUser, 2);
  assert.equal(rules.callName, "Nova · Sample");
  const now = new Date("2026-10-14T12:00:00Z");
  assert.equal(rules.earliest(now).toISOString(), "2026-10-14T12:10:00.000Z", "the loader's earliest()");
  assert.equal(rules.latest(now).toISOString(), "2026-10-21T12:00:00.000Z", "latest() from maxDaysAhead when the loader has none");
  assert.equal(t.notices.filter((n) => n.key === "unsupported").length, 0);
});

test("a loader without schedulingRules: null, and the notice names it as a property", async () => {
  const t = setup((win) => releasedLoader(win));
  await t.g.start(CONFIG);
  await waitFor(() => t.g.state === "ready");
  assert.equal(t.g.schedulingRules(), null);
  const n = t.notices.filter((x) => x.key === "unsupported").at(-1);
  assert.match(n.lines[0], /^GraineAgent\.schedulingRules — /);
});

test("rules read defensively: booking is off unless the loader says on", () => {
  const r = normaliseRules({});
  assert.equal(r.usersCanBook, false);
  assert.deepEqual([r.minLeadMinutes, r.maxDaysAhead, r.maxPendingPerUser, r.ringTimeoutSeconds], [5, 14, 3, 45]);
  const now = new Date("2026-10-14T12:00:00Z");
  assert.equal(r.earliest(now).toISOString(), "2026-10-14T12:05:00.000Z");
  assert.equal(r.latest(now).toISOString(), "2026-10-28T12:00:00.000Z");
  const broken = normaliseRules({ usersCanBook: "yes", earliest: () => { throw new Error("no"); }, latest: () => "nonsense" });
  assert.equal(broken.usersCanBook, false);
  assert.equal(broken.earliest(now).toISOString(), "2026-10-14T12:05:00.000Z");
  assert.equal(broken.latest(now).toISOString(), "2026-10-28T12:00:00.000Z");
  assert.equal(normaliseRules(null), null);
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
