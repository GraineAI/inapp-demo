/**
 * A stand-in for the RELEASED Graine website loader (version 1.1.0): the same public surface —
 * load, defineAction, removeAction, setScreen, addMaskRule, reportEvent, version, and open /
 * close / destroy once the assistant has started — and nothing newer. No network: it "starts"
 * right away. The headless check uses it to prove the page tells a visitor which methods the
 * loaded loader lacks instead of breaking. `window.__graineStub` is for the check only.
 */
(function () {
  "use strict";
  if (window.GraineAgent) return;

  var actions = {};
  var screens = [];
  var loads = [];
  var opened = 0;

  window.GraineAgent = {
    load: function (options) {
      loads.push({ agentId: options && options.agentId });
      setTimeout(function () {
        var frame = document.createElement("div");
        frame.setAttribute("data-stub", "panel");
        frame.style.display = "none";
        document.body.appendChild(frame);
        window.GraineAgent.open = function () { opened += 1; frame.style.display = "block"; };
        window.GraineAgent.close = function () { frame.style.display = "none"; };
        window.GraineAgent.destroy = function () { frame.remove(); };
      }, 20);
    },
    version: "1.1.0",
    defineAction: function (name, handler) { actions[name] = handler; },
    removeAction: function (name) { delete actions[name]; },
    setScreen: function (context) { screens.push(JSON.parse(JSON.stringify(context))); },
    addMaskRule: function () {},
    reportEvent: function () {},
  };

  window.__graineStub = {
    loads: loads,
    screens: screens,
    opened: function () { return opened; },
    actionNames: function () { return Object.keys(actions); },
    runAction: function (name, args) {
      var handler = actions[name];
      if (typeof handler !== "function") return Promise.resolve({ status: "refused", message: "not implemented" });
      return Promise.resolve(handler(args || {}));
    },
  };
})();
