#!/usr/bin/env node
/**
 * DEMO TOKEN MINTER — for trying server tokens with the demo apps. Not for production.
 *
 * In a real app, YOUR server mints a token for the user who signed in to YOUR app, with your secret
 * API key, and your app's tokenProvider fetches it from there. The demo apps have no server and must
 * never carry a secret, so this little script stands in for that server on your own computer:
 *
 *   GRAINE_API_KEY=gat_…  GRAINE_AGENT_ID=<your agent's id>  node agent-setup/token-minter.mjs
 *
 * Then in the app: Profile → Server token (demo) → Local minter, and restart the app.
 *   Android emulator  http://10.0.2.2:8787     iOS simulator  http://localhost:8787
 *
 * It answers GET /token?user_id=<id> with {"token", "expires_at"} by calling
 * POST {GRAINE_API_BASE}/v2/client-tokens with your key. It does NOT check who is asking:
 * anyone who can reach this port can get a token for any user of the agent — exactly what your real
 * server must never do (it mints only for the user its own sign-in proved). So it listens on this
 * computer only (127.0.0.1) unless you set HOST, and it never prints your key or a token.
 *
 * Optional: GRAINE_API_BASE (default https://api.graine.ai), PORT (default 8787), HOST (default
 * 127.0.0.1 — the Android emulator still reaches it at 10.0.2.2), SCOPES (default
 * chat,call,memory,booking), TTL_SECONDS (default 900).
 */
import { createServer } from "node:http";

const KEY = (process.env.GRAINE_API_KEY || "").trim();
const AGENT = (process.env.GRAINE_AGENT_ID || "").trim();
const BASE = (process.env.GRAINE_API_BASE || "https://api.graine.ai").replace(/\/+$/, "");
const PORT = Number(process.env.PORT || 8787);
const HOST = process.env.HOST || "127.0.0.1";
const SCOPES = (process.env.SCOPES || "chat,call,memory,booking").split(",").map((s) => s.trim()).filter(Boolean);
const TTL = Number(process.env.TTL_SECONDS || 900);

if (!KEY || !AGENT) {
  console.error("Set GRAINE_API_KEY (a secret API key from Settings → API keys) and GRAINE_AGENT_ID.");
  process.exit(1);
}

const send = (res, status, body) => {
  res.writeHead(status, { "Content-Type": "application/json", "Cache-Control": "no-store" });
  res.end(JSON.stringify(body));
};

createServer(async (req, res) => {
  const url = new URL(req.url || "/", "http://minter.local");
  if (req.method !== "GET" || url.pathname !== "/token") return send(res, 404, { error: "GET /token?user_id=<id>" });
  const userId = (url.searchParams.get("user_id") || "").trim();
  if (!userId) return send(res, 400, { error: "user_id is required" });
  try {
    const answer = await fetch(`${BASE}/v2/client-tokens`, {
      method: "POST",
      headers: { Authorization: `Bearer ${KEY}`, "Content-Type": "application/json" },
      body: JSON.stringify({ agent_id: AGENT, user_id: userId, scopes: SCOPES, ttl_seconds: TTL }),
      signal: AbortSignal.timeout(10_000),
    });
    const body = await answer.json().catch(() => ({}));
    if (!answer.ok) {
      console.error(`[minter] Graine answered ${answer.status} for one user`);
      return send(res, 502, { error: body?.error?.message || `Graine answered ${answer.status}` });
    }
    console.log(`[minter] minted a token (${SCOPES.join(", ")}, ${body.expires_in}s)`);
    return send(res, 200, { token: body.token, expires_at: body.expires_at });
  } catch (e) {
    console.error(`[minter] Graine could not be reached: ${e?.name || "error"}`);
    return send(res, 502, { error: "Graine could not be reached" });
  }
}).listen(PORT, HOST, () => {
  console.log(`Demo token minter on http://${HOST}:${PORT} — for agent ${AGENT}, scopes ${SCOPES.join(", ")}.`);
  console.log("Demo only: it mints for any user id it is asked for. Your production server must check who is signed in first.");
});
