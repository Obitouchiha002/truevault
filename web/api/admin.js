/**
 * Admin API — the only thing that may touch Supabase with privileged credentials.
 *
 * The whole point of moving admin off the phone is this file. The app ships the anon key, which
 * anyone can extract from the APK; as long as `admin_devices` was callable by that key, the admin
 * PIN was one unlimited guessing loop away from falling. Here the credential is the service_role
 * key, it lives in a Vercel environment variable, and it never leaves the server — so once the
 * admin functions are revoked from `anon` (see docs/ADMIN-WEB.sql) there is nothing privileged left
 * for a public key to call at all. The attack surface is not mitigated, it is removed.
 *
 * Required environment variables (set with `vercel env add`, never in the repo):
 *   SUPABASE_URL                 https://<project>.supabase.co
 *   SUPABASE_SERVICE_ROLE_KEY    Project Settings -> API -> service_role. Server-side only.
 *   ADMIN_PASSWORD               What you type on /admin. Long and random.
 */

import { timingSafeEqual, randomUUID } from "node:crypto";

/**
 * In-memory, per-instance, and deliberately so.
 *
 * A serverless instance is recycled, so this is not a durable lock — an attacker who spreads guesses
 * across cold starts gets more than ten. It is still worth having: it stops the trivial single-loop
 * attack, which is the one that actually happens. The durable protection is that ADMIN_PASSWORD is
 * long and random, not that this map is perfect, and pretending otherwise would be the mistake.
 */
const attempts = new Map();
const WINDOW_MS = 15 * 60 * 1000;
const MAX_FAILURES = 10;

function rateLimited(ip) {
  const record = attempts.get(ip);
  if (!record) return false;
  if (Date.now() - record.first > WINDOW_MS) {
    attempts.delete(ip);
    return false;
  }
  return record.count >= MAX_FAILURES;
}

function noteFailure(ip) {
  const record = attempts.get(ip);
  if (!record || Date.now() - record.first > WINDOW_MS) {
    attempts.set(ip, { first: Date.now(), count: 1 });
  } else {
    record.count++;
  }
}

/** Constant-time, and length-safe: comparing different lengths would leak through the exception. */
function passwordMatches(given, expected) {
  if (typeof given !== "string" || typeof expected !== "string") return false;
  const a = Buffer.from(given);
  const b = Buffer.from(expected);
  if (a.length !== b.length) return false;
  return timingSafeEqual(a, b);
}

async function rpc(fn, args) {
  const response = await fetch(`${process.env.SUPABASE_URL}/rest/v1/rpc/${fn}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      apikey: process.env.SUPABASE_SERVICE_ROLE_KEY,
      Authorization: `Bearer ${process.env.SUPABASE_SERVICE_ROLE_KEY}`,
    },
    body: JSON.stringify(args),
  });
  if (!response.ok) {
    // The upstream body is read but never returned to the browser: a PostgREST error echoes the
    // request, which would put install identifiers and names into a client-visible response.
    const detail = await response.text();
    const error = new Error("upstream");
    error.status = response.status;
    error.detail = detail;
    throw error;
  }
  return response.json();
}

export default async function handler(req, res) {
  res.setHeader("Cache-Control", "no-store");
  res.setHeader("X-Content-Type-Options", "nosniff");

  if (req.method !== "POST") {
    return res.status(405).json({ error: "Method not allowed" });
  }

  const missing = ["SUPABASE_URL", "SUPABASE_SERVICE_ROLE_KEY", "ADMIN_PASSWORD"]
    .filter((name) => !process.env[name]);
  if (missing.length) {
    // Names only. Printing which variable is set and which is not is fine; printing a value is not.
    console.error(`admin: missing environment variables: ${missing.join(", ")}`);
    return res.status(500).json({ error: "Server is not configured" });
  }

  const ip = (req.headers["x-forwarded-for"] || "").split(",")[0].trim() || "unknown";
  if (rateLimited(ip)) {
    // Same message as a wrong password, on purpose. "You are rate limited" confirms the previous
    // guesses were wrong and that continuing later is worthwhile.
    return res.status(401).json({ error: "Not authorised" });
  }

  const body = typeof req.body === "string" ? JSON.parse(req.body || "{}") : (req.body || {});

  if (!passwordMatches(body.password, process.env.ADMIN_PASSWORD)) {
    noteFailure(ip);
    return res.status(401).json({ error: "Not authorised" });
  }

  const correlationId = randomUUID();

  try {
    switch (body.action) {
      case "list": {
        const rows = await rpc("admin_devices_srv", {});
        // Only this app's installs. StreamGarden shares the table and its users are different
        // people; filtering here means the browser never receives their rows at all.
        const own = rows.filter((r) => r.platform === "truevault-android");
        return res.status(200).json({ installs: own });
      }

      case "block":
        await rpc("admin_block_srv", {
          p_id: body.id,
          p_blocked: Boolean(body.blocked),
          p_reason: body.blocked ? (body.reason || "Access suspended by the developer.") : null,
          p_minutes: body.minutes ?? null,
          p_code: body.blocked ? (body.code || "403") : null,
        });
        return res.status(200).json({ ok: true });

      case "premium":
        await rpc("admin_premium_srv", { p_id: body.id, p_premium: Boolean(body.premium) });
        return res.status(200).json({ ok: true });

      default:
        return res.status(400).json({ error: "Unknown action" });
    }
  } catch (error) {
    // Detail to the server log, a correlation id to the browser. The client is told enough to
    // report the failure and nothing about the database, the query or the deployment.
    console.error(`admin ${correlationId}: ${error.status || ""} ${error.detail || error.message}`);
    return res.status(502).json({ error: "The request could not be completed", correlationId });
  }
}
