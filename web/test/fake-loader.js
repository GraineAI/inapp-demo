/**
 * A stand-in for the Graine website loader WITH identity, memory, scheduling and several agents —
 * for the headless check (test/headless-check.mjs) and for clicking through the demo with no agent:
 *
 *   // web/config.js
 *   export default { agentId: "offline", publishableKey: "pk_test_offline", loaderUrl: "test/fake-loader.js",
 *                    help: { agentId: "offline-help", publishableKey: "pk_test_offline_help" } };
 *
 * Everything lives in this page's memory; nothing is sent anywhere. It answers in the SDK types
 * (WIRE.md → "SDK types"): booked calls with isUpcoming / canCancel; `bookingAvailable` (the agent
 * lets visitors book and someone is identified) and `openBooking({ reason })`, which draws a small
 * booking sheet in its panel — a time 30 minutes out, "What's it about?" and Book call — where the
 * real widget draws its own. Calling load() again with another agent switches the page's agent
 * (MULTI-AGENT.md → Website): the widget is replaced, who is signed in stays. Each agent keeps its
 * own calls and memories; the public calls routes (calls/list, calls/cancel) are answered here too,
 * for the agent that is not on the page.
 *
 * Its panel shows what the page tells the assistant, runs the page's actions and "remembers" what
 * you type. `window.__graineFake` is for the headless check; no real loader has it.
 */
(function () {
  "use strict";
  if (window.GraineAgent) return;

  var actions = {};
  var log = [];
  var loads = [];
  var screens = [];
  var traits = null;
  var identity = null;
  var memoryOn = true;
  var memories = {};   // "key|identity" -> [{ id, text, createdAt, updatedAt }], newest first
  var calls = {};      // "key|identity" -> [{ id, at, status, source, reason }] (wire names)
  var seq = 0;
  var opened = 0;
  var INSTALL_ID = "6f1d2c3b-4a59-4e7f-9a1b-0c2d3e4f5a6b";
  var current = null;  // { agentId, publishableKey } of the agent on the page
  var firstKey = null; // the first agent loaded: the main one
  var widget = null;   // { panel, launcher } while mounted
  var started = false;
  var RULES = { usersCanBook: true, maxPendingPerUser: 3 };
  var bookingAsk = null; // { resolve } while the sheet openBooking() opened is up

  function fail(code, message, status) {
    var e = new Error(message);
    e.code = code;
    e.status = status || 0;
    return Promise.reject(e);
  }

  /** The identifier: the trait that ends in _id (the demo's packs), else user_id. */
  function identityOf(t) {
    if (!t) return null;
    for (var k in t) {
      if (Object.prototype.hasOwnProperty.call(t, k) && /_id$/.test(k) && t[k]) return String(t[k]);
    }
    return null;
  }

  function bucket(key, who) { return (key || "none") + "|" + (who || "install"); }
  function here() { return bucket(current && current.publishableKey, identity); }

  /** A stored call (wire names) as the SDK types return it. */
  function sdkCall(c) {
    return {
      id: c.id,
      at: c.at ? new Date(c.at) : null,
      status: c.status === "no_device" ? "noDevice" : c.status,
      source: c.source === "sdk" ? "app" : c.source,
      reason: c.reason,
      isUpcoming: c.status === "scheduled" || c.status === "ringing",
      canCancel: c.status === "scheduled",
    };
  }

  function runAction(name, args) {
    var handler = actions[name];
    if (typeof handler !== "function") {
      return Promise.resolve({ status: "refused", data: {}, message: 'This page does not implement "' + name + '".' });
    }
    try {
      return Promise.resolve(handler(args || {})).then(function (r) {
        return { status: (r && r.status) || "ok", data: (r && r.data) || {}, message: (r && r.message) || "" };
      }, function (e) {
        return { status: "error", data: {}, message: (e && e.message) || "failed" };
      });
    } catch (e) {
      return Promise.resolve({ status: "error", data: {}, message: (e && e.message) || "failed" });
    }
  }

  function remember(key, who, text) {
    var now = new Date().toISOString(), b = bucket(key, who);
    (memories[b] = memories[b] || []).unshift({ id: "mem_" + (++seq), text: text, createdAt: now, updatedAt: null });
  }

  // ── The panel (what the real loader's frame would be) ─────────────────────

  function el(tag, attrs, text) {
    var n = document.createElement(tag);
    for (var k in attrs || {}) n.setAttribute(k, attrs[k]);
    if (text != null) n.textContent = text;
    return n;
  }

  function drawPanel() {
    if (!widget) return;
    var panel = widget.panel;
    panel.querySelector("[data-fake=agent]").textContent = "Agent: " + (current ? current.agentId : "none");
    panel.querySelector("[data-fake=context]").textContent = JSON.stringify(screens[screens.length - 1] || null, null, 2);
    var select = panel.querySelector("[data-fake=action]");
    var names = Object.keys(actions);
    if (select.options.length !== names.length) {
      select.replaceChildren();
      names.forEach(function (n) { select.append(el("option", { value: n }, n)); });
    }
    panel.querySelector("[data-fake=who]").textContent = identity
      ? "Signed in: " + identity + " · memory " + (memoryOn ? "on" : "off")
      : "Nobody signed in (anonymous)";
  }

  function mount() {
    var launcher = el("button", { type: "button", "aria-label": "Open the assistant (offline preview)", "data-fake": "launcher" });
    launcher.textContent = "◎";
    launcher.style.cssText = "position:fixed;right:24px;bottom:24px;width:56px;height:56px;border-radius:50%;border:0;" +
      "background:#334155;color:#fff;font-size:22px;cursor:pointer;z-index:2147483001";
    var panel = el("section", { "aria-label": "Assistant (offline preview)", "data-fake": "panel", "data-agent": current.agentId });
    panel.style.cssText = "position:fixed;right:24px;bottom:88px;width:360px;max-width:calc(100vw - 32px);max-height:calc(100vh - 112px);" +
      "overflow:auto;display:none;z-index:2147483000;background:#fff;color:#111;border-radius:14px;padding:16px;" +
      "box-shadow:0 12px 48px rgba(0,0,0,.25);font:13px/1.45 system-ui,sans-serif";
    panel.append(
      el("p", { style: "margin:0 0 4px;font-weight:600" }, "Offline preview — no assistant connected"),
      el("p", { "data-fake": "agent", style: "margin:0;color:#555" }, ""),
      el("p", { "data-fake": "who", style: "margin:0 0 10px;color:#555" }, ""),
      el("p", { style: "margin:8px 0 4px;font-weight:600" }, "What the page tells the assistant"),
      el("pre", { "data-fake": "context", style: "margin:0;max-height:160px;overflow:auto;background:#f3f4f6;padding:8px;border-radius:8px;font-size:11.5px;white-space:pre-wrap" }, ""),
      el("p", { style: "margin:10px 0 4px;font-weight:600" }, "Run one of the page's actions"));
    var row = el("div", { style: "display:flex;gap:6px;flex-wrap:wrap" });
    var select = el("select", { "data-fake": "action", "aria-label": "Action" });
    var arg = el("input", { "data-fake": "arg", "aria-label": "Screen or item", placeholder: "screen or item" });
    arg.style.cssText = "flex:1;min-width:120px";
    var run = el("button", { type: "button" }, "Run");
    var out = el("p", { "data-fake": "result", style: "margin:6px 0 0;color:#333" }, "");
    run.addEventListener("click", function () {
      var name = select.value, args = {};
      args[name === "open_screen" ? "screen" : "item"] = arg.value;
      runAction(name, args).then(function (r) { out.textContent = r.status + ": " + r.message; });
    });
    row.append(select, arg, run);
    panel.append(row, out, el("p", { style: "margin:10px 0 4px;font-weight:600" }, "Tell it something to remember"));
    var tellRow = el("div", { style: "display:flex;gap:6px" });
    var tell = el("input", { "data-fake": "tell", "aria-label": "Something to remember", placeholder: "e.g. I prefer calls after 6 pm" });
    tell.style.cssText = "flex:1";
    var save = el("button", { type: "button" }, "Remember");
    save.addEventListener("click", function () {
      if (!identity) { out.textContent = "Sign in first: anonymous conversations are not remembered."; return; }
      if (!memoryOn) { out.textContent = "Memory is off for this person: nothing is kept."; return; }
      if (!tell.value.trim()) return;
      remember(current.publishableKey, identity, tell.value.trim());
      tell.value = "";
      out.textContent = "Remembered. Refresh the Memory screen to see it.";
    });
    tellRow.append(tell, save);
    panel.append(tellRow);
    var close = el("button", { type: "button", style: "margin-top:12px" }, "Close");
    close.addEventListener("click", function () { panel.style.display = "none"; });
    panel.append(close);
    launcher.addEventListener("click", function () { window.GraineAgent.open(); });
    document.body.append(panel, launcher);
    widget = { panel: panel, launcher: launcher };
    started = true;

    // A new widget brings new functions, as the real loader's mount does.
    window.GraineAgent.open = function () { opened += 1; drawPanel(); panel.style.display = "block"; };
    window.GraineAgent.close = function () { panel.style.display = "none"; };
    window.GraineAgent.destroy = unmount;
    drawPanel();
  }

  // ── The booking sheet (what the real widget draws in its panel) ───────────

  /** The sheet openBooking() opened is over: with the call booked there, or null. */
  function settleBooking(call) {
    var ask = bookingAsk;
    bookingAsk = null;
    var sheet = widget && widget.panel.querySelector("[data-fake=booking]");
    if (sheet) sheet.remove();
    if (ask) ask.resolve(call);
  }

  function drawBooking(reason) {
    var panel = widget.panel;
    var at = new Date(Math.ceil((Date.now() + 30 * 60 * 1000) / (5 * 60 * 1000)) * 5 * 60 * 1000);
    var sheet = el("section", { "data-fake": "booking", "aria-label": "Book a call (offline preview)" });
    sheet.style.cssText = "margin:0 0 12px;padding:12px;border:1px solid #d0d5dd;border-radius:10px";
    var note = el("input", { "data-fake": "booking-reason", "aria-label": "What's it about? (optional)", maxlength: "120" });
    note.value = reason || "";
    var line = el("p", { "data-fake": "booking-error", style: "margin:6px 0 0;color:#b42318" }, "");
    var book = el("button", { type: "button", "data-fake": "booking-book" }, "Book call");
    var close = el("button", { type: "button", "data-fake": "booking-close" }, "Close");
    book.addEventListener("click", function () {
      var b = here(), list = (calls[b] = calls[b] || []);
      // The "server's" check, worded in the sheet as the widget words it.
      if (pending(list) >= RULES.maxPendingPerUser) { line.textContent = "You already have 3 calls booked — cancel one first"; return; }
      var call = { id: "call_" + (++seq), at: at.toISOString(), status: "scheduled", source: "sdk", reason: note.value.trim() || null };
      list.push(call);
      log.push(["booked", { at: call.at, reason: call.reason }, current && current.agentId]);
      settleBooking(sdkCall(call));
    });
    close.addEventListener("click", function () { settleBooking(null); });
    sheet.append(
      el("p", { style: "margin:0 0 6px;font-weight:600" }, "Book a call"),
      el("p", { "data-fake": "booking-time", style: "margin:0 0 6px" }, "At " + at.toLocaleString()),
      note, book, close, line);
    panel.prepend(sheet);
    panel.style.display = "block";
  }

  function unmount() {
    settleBooking(null);   // the agent is leaving the page: a sheet still open ends unbooked
    if (widget) { widget.panel.remove(); widget.launcher.remove(); }
    widget = null;
    started = false;
    delete window.GraineAgent.open;
    delete window.GraineAgent.close;
    delete window.GraineAgent.destroy;
  }

  function pending(list) {
    return list.filter(function (c) { return c.status === "scheduled"; }).length;
  }

  // ── The public routes: calls for the agent that is not on the page, and past sessions ──

  /**
   * The signed-in visitor's past sessions with the main agent (the first loaded), newest first, as
   * sessions/list answers them — the help agent has none yet. Three to a page, so "Load more" shows.
   */
  function pastSessionsFor(key, who) {
    if (!who || key !== firstKey) return [];
    var day = 24 * 60 * 60 * 1000, now = Date.now();
    function session(id, ago, channel, direction, status, lines) {
      var start = now - ago;
      var turns = lines.map(function (l, i) { return { role: l[0], text: l[1], at: new Date(start + i * 25000).toISOString() }; });
      var first = turns.filter(function (t) { return t.role === "user"; })[0];
      return {
        id: id, startedAt: new Date(start).toISOString(), endedAt: new Date(start + (turns.length * 25 + 20) * 1000).toISOString(),
        durationSeconds: turns.length ? turns.length * 25 + 20 : null, channel: channel, direction: direction, status: status,
        turns: turns, preview: first ? first.text.slice(0, 120) : "",
      };
    }
    return [
      session("ex-offline-0004", day, "chat", null, "completed", [["assistant", "Hi, how can I help?"], ["user", "Can I move my next call to Friday?"], ["assistant", "Done: Friday at 10:00."]]),
      session("ex-offline-0003", 2 * day, "call", "outbound", "completed", [["assistant", "Hi, is now a good time?"], ["user", "Yes, now is a good time."], ["assistant", "Thank you. Goodbye."]]),
      session("ex-offline-0002", 4 * day, "voice", "inbound", "completed", [["user", "What is due today?"], ["assistant", "Here is what I found."]]),
      session("ex-offline-0001", 6 * day, "call", "outbound", "missed", []),
    ];
  }

  var realFetch = window.fetch ? window.fetch.bind(window) : null;
  window.fetch = function (input, init) {
    var url;
    try { url = new URL(typeof input === "string" ? input : input.url, location.href); } catch (e) { url = null; }
    var path = url && url.origin === location.origin ? url.pathname : "";
    var routes = ["/api/embed/calls/list", "/api/embed/calls/cancel", "/api/embed/sessions/list", "/api/embed/sessions/transcript"];
    if (routes.indexOf(path) === -1) {
      return realFetch ? realFetch(input, init) : Promise.reject(new TypeError("no fetch"));
    }
    var body = {};
    try { body = JSON.parse((init && init.body) || "{}"); } catch (e) { body = {}; }
    log.push(["route", path, { publishableKey: body.publishableKey, installId: body.installId, identity: body.identity, callId: body.callId, cursor: body.cursor, id: body.id }]);
    var answer = function (status, data) {
      return Promise.resolve(new Response(JSON.stringify(data), { status: status, headers: { "Content-Type": "application/json" } }));
    };
    if (!body.publishableKey || body.installId !== INSTALL_ID) return answer(400, { error: "That request was not accepted.", code: "invalid_request" });
    if (path === "/api/embed/sessions/list" || path === "/api/embed/sessions/transcript") {
      var who = body.identity && body.identity.value;
      if (!who) return answer(400, { error: "Sign the user in first.", code: "identity_required" });
      var all = pastSessionsFor(body.publishableKey, who);
      if (path === "/api/embed/sessions/transcript") {
        var found = all.filter(function (x) { return x.id === body.id; })[0];
        if (!found) return answer(404, { error: "Session not found.", code: "not_found" });
        var t = JSON.parse(JSON.stringify(found));
        delete t.preview;
        return answer(200, t);
      }
      var from = Math.max(0, Number(body.cursor) || 0);
      var rows = all.slice(from, from + 3).map(function (x) { var r = JSON.parse(JSON.stringify(x)); r.turns = x.turns.length; return r; });
      return answer(200, { sessions: rows, next: from + 3 < all.length ? String(from + 3) : null });
    }
    var list = calls[bucket(body.publishableKey, body.identity && body.identity.value)] || [];
    if (path === "/api/embed/calls/list") return answer(200, { calls: JSON.parse(JSON.stringify(list)) });
    for (var i = 0; i < list.length; i++) {
      if (list[i].id === body.callId && list[i].status === "scheduled") {
        list[i].status = "cancelled";
        return answer(200, { ok: true, status: "cancelled" });
      }
    }
    return answer(404, { error: "That call does not exist.", code: "not_found" });
  };

  window.GraineAgent = {
    version: "0.0.0-offline",
    /** load() again with another agent switches the page's agent (MULTI-AGENT.md). */
    switchesAgents: true,
    get installId() { return INSTALL_ID; },

    /** Off until the session has answered (the widget mounted); then the agent lets visitors book and someone is identified. */
    get bookingAvailable() { return started && RULES.usersCanBook && identity !== null; },

    load: function (opts) {
      loads.push({ agentId: opts && opts.agentId });
      log.push(["load", { agentId: opts && opts.agentId }]);
      if (widget) unmount();   // a switch: the old widget goes, who is signed in stays
      current = { agentId: opts.agentId, publishableKey: opts.publishableKey };
      if (!firstKey) firstKey = opts.publishableKey;
      setTimeout(mount, 30);   // the real loader mounts after the session answers
    },
    defineAction: function (name, handler) { actions[name] = handler; drawPanel(); },
    removeAction: function (name) { delete actions[name]; },
    setScreen: function (context) { screens.push(JSON.parse(JSON.stringify(context))); drawPanel(); },
    addMaskRule: function () {},
    reportEvent: function () {},

    identify: function (next, options) {
      log.push(["identify", next ? JSON.parse(JSON.stringify(next)) : null, options ? JSON.parse(JSON.stringify(options)) : undefined]);
      traits = next || null;
      identity = identityOf(traits);
      memoryOn = !options || options.memory !== false;
      drawPanel();
    },
    setMemory: function (enabled) {
      log.push(["setMemory", enabled]);
      memoryOn = !!enabled;
      drawPanel();
    },
    forgetMemory: function () {
      log.push(["forgetMemory"]);
      if (!identity) return fail("identity_required", "Nobody is identified. Call identify() with the customer's id first.");
      var b = here(), n = (memories[b] || []).length;
      memories[b] = [];
      return Promise.resolve(n);
    },
    memories: function () {
      log.push(["memories"]);
      if (!identity) return fail("identity_required", "Nobody is identified. Call identify() with the customer's id first.");
      return Promise.resolve((memories[here()] || []).map(function (m) {
        return { id: m.id, text: m.text, createdAt: m.createdAt ? new Date(m.createdAt) : null, updatedAt: m.updatedAt ? new Date(m.updatedAt) : null };
      }));
    },
    /** The booking sheet, in the panel: resolves with the call booked there, or null when it is closed. One at a time. */
    openBooking: function (options) {
      var reason = options && typeof options.reason === "string" ? options.reason.trim().slice(0, 120) : "";
      log.push(["openBooking", { reason: reason || null }, current && current.agentId]);
      settleBooking(null);
      return new Promise(function (resolve) {
        bookingAsk = { resolve: resolve };
        if (widget) drawBooking(reason); else settleBooking(null);
      });
    },
    scheduledCalls: function () {
      log.push(["scheduledCalls", current && current.agentId]);
      var list = (calls[here()] || []).map(sdkCall);
      list.sort(function (a, b) { return (b.isUpcoming - a.isUpcoming) || ((a.at ? a.at.getTime() : 0) - (b.at ? b.at.getTime() : 0)); });
      return Promise.resolve(list);
    },
    cancelScheduledCall: function (id) {
      log.push(["cancelScheduledCall", id, current && current.agentId]);
      var list = calls[here()] || [];
      for (var i = 0; i < list.length; i++) {
        if (list[i].id === id) {
          if (list[i].status !== "scheduled") return fail("not_found", "That call can no longer be cancelled.");
          list[i].status = "cancelled";
          return Promise.resolve("cancelled");
        }
      }
      return fail("not_found", "That call does not exist.");
    },
  };

  window.__graineFake = {
    log: log,
    loads: loads,
    screens: screens,
    actionNames: function () { return Object.keys(actions); },
    runAction: runAction,
    identity: function () { return identity; },
    memoryOn: function () { return memoryOn; },
    opened: function () { return opened; },
    currentAgent: function () { return current && current.agentId; },
    widgets: function () { return document.querySelectorAll("[data-fake=panel]").length; },
    /** Seed what an agent (by key; the first loaded by default) remembers about someone, newest first. */
    seedMemories: function (value, texts, key) {
      texts.slice().reverse().forEach(function (t) { remember(key || firstKey, value, t); });
    },
    /** The agent's "Users, from your app" switch: off, nobody can book from here. */
    setUsersCanBook: function (on) { RULES.usersCanBook = !!on; },
    seedCalls: function (value, list, key) {
      var b = bucket(key || firstKey, value);
      calls[b] = (calls[b] || []).concat(list.map(function (c) {
        return { id: c.id || "call_" + (++seq), at: c.at, status: c.status, source: c.source || null, reason: c.reason || null };
      }));
    },
  };
})();
