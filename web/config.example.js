/**
 * Copy this file to config.js (git-ignored) and fill it in:
 *
 *   cp web/config.example.js web/config.js
 *
 * The publishable key is public by design — it ships in the page source — and works only from
 * the domains on the agent's Allowed domains list (Agent → Embed & Widgets → Install).
 */
export default {
  // Where the Graine website loader is served from: the host in the dashboard's snippet.
  baseUrl: "https://www.graine.ai",

  // The main agent every use case uses, unless it has its own entry under `agents` below.
  // Dashboard: Agent → Embed & Widgets → Install.
  agentId: "",
  publishableKey: "",

  // Optional: the help agent the Help section talks to (agent-setup/<pack>/ has its prompt and
  // settings). Without one, the Help section says how to add it.
  help: { agentId: "", publishableKey: "" },

  // One entry per use case, when each has its own agents. An agent needs both values.
  agents: {
    // "health-coach": { agentId: "", publishableKey: "", help: { agentId: "", publishableKey: "" } },
    // "banking-support": { agentId: "", publishableKey: "", help: { agentId: "", publishableKey: "" } },
    // "retail-shopping": { agentId: "", publishableKey: "", help: { agentId: "", publishableKey: "" } },
  },

  // Optional. Which use cases the sign-in screen offers, and which opens first.
  // packs: ["health-coach", "banking-support", "retail-shopping"],
  // defaultPack: "health-coach",

  // Optional. The loader's full URL, instead of baseUrl + "/embed/v1.js".
  // loaderUrl: "",
};
