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
 *
 * The admin password is NOT an environment variable. It is set from the browser on first visit,
 * authorised by the admin PIN already in the database, and stored there as a scrypt hash. That
 * keeps the whole setup to two values pasted into the Vercel dashboard, with no command line.
 */

import { timingSafeEqual, randomUUID, scrypt, randomBytes } from "node:crypto";

/**
 * The platform tag each app sends on check-in, mapped to a readable name.
 *
 * One shared `devices` table holds every app's installs, and this column is the only thing that
 * tells them apart. A tag with no entry here is shown as-is rather than hidden: an unknown app
 * appearing in the list is information, and silently dropping it would be the wrong default.
 */
const APPS = {
  "android": "StreamGarden",
  "truevault-android": "TrueVault",
  "copyeye-android": "CopyEye",
};
const appOf = (platform) => APPS[platform] || (platform || "unknown");

/**
 * In-memory, per-instance, and deliberately so.
 *
 * A serverless instance is recycled, so this is not a durable lock — an attacker who spreads guesses
 * across cold starts gets more than ten. It is still worth having: it stops the trivial single-loop
 * attack, which is the one that actually happens. The durable protection is scrypt over a
 * password you chose to be long, not that this map is perfect, and pretending otherwise would be the mistake.
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

/**
 * scrypt, with the salt stored alongside the hash.
 *
 * N=16384 is the Node default and takes tens of milliseconds — deliberate, because this is the one
 * thing standing between a public URL and every install record. A plain SHA of the password would
 * be verified in microseconds, which is exactly what makes offline guessing cheap.
 */
const SCRYPT_N = 16384;
const KEY_LEN = 64;

function derive(password, salt) {
  return new Promise((resolve, reject) => {
    scrypt(password, salt, KEY_LEN, { N: SCRYPT_N }, (err, key) =>
      err ? reject(err) : resolve(key));
  });
}

async function hashPassword(password) {
  const salt = randomBytes(16).toString("hex");
  const key = await derive(password, salt);
  return `scrypt$${SCRYPT_N}$${salt}$${key.toString("hex")}`;
}

/** Constant-time. A malformed or absent stored hash fails closed rather than throwing. */
async function verifyPassword(given, stored) {
  if (typeof given !== "string" || typeof stored !== "string") return false;
  const [scheme, n, salt, hex] = stored.split("$");
  if (scheme !== "scrypt" || !n || !salt || !hex) return false;
  const expected = Buffer.from(hex, "hex");
  const actual = await new Promise((resolve) =>
    scrypt(given, salt, expected.length, { N: Number(n) }, (err, key) => resolve(err ? null : key)));
  if (!actual || actual.length !== expected.length) return false;
  return timingSafeEqual(actual, expected);
}

/** A password worth the scrypt cost. Twelve characters is the floor, not a recommendation. */
function passwordTooWeak(password) {
  return typeof password !== "string" || password.length < 12;
}

/**
 * Unwraps whatever shape PostgREST chose for a single value.
 *
 * A function returning `boolean` or `text` comes back as the bare value — `false`, `null`, a string
 * — not as a row. A function returning a row or a set comes back as an array of objects. Array
 * destructuring handles the second and throws on the first, which is what produced a 502 here:
 * `const [x] = false` is a TypeError, and it happened before any Supabase error could occur, so the
 * upstream status was null and the failure looked like it came from the database when it did not.
 */
function scalar(value) {
  if (Array.isArray(value)) {
    const first = value[0];
    if (first && typeof first === "object") return Object.values(first)[0];
    return first;
  }
  if (value && typeof value === "object") return Object.values(value)[0];
  return value;
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

  const missing = ["SUPABASE_URL", "SUPABASE_SERVICE_ROLE_KEY"]
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
  const correlationId = randomUUID();

  try {
    // Answered before any password check, because its whole job is to tell the page which screen
    // to draw. It returns one boolean and nothing else — knowing that a password exists helps an
    // attacker not at all, and hiding it would just make the first-run page impossible to build.
    if (body.action === "status") {
      const configured = scalar(await rpc("admin_web_configured_srv", {}));
      return res.status(200).json({ configured: Boolean(configured) });
    }

    // First-run and password change. Authorised by the admin PIN already in the database, not by
    // the web password — that is the point, since on first run there is no web password yet.
    if (body.action === "setup") {
      if (passwordTooWeak(body.newPassword)) {
        return res.status(400).json({ error: "Password must be at least 12 characters" });
      }
      const hash = await hashPassword(body.newPassword);
      try {
        await rpc("admin_web_set_srv", { p_pin: body.pin || "", p_hash: hash });
      } catch (e) {
        // Only a genuine `unauthorized` from the function means the PIN was wrong. Treating every
        // failure as a wrong PIN hid a real one: a missing EXECUTE grant reported itself as "that
        // PIN is not right", which sends you looking at the PIN for something that was never about
        // the PIN. An infrastructure failure has to look like an infrastructure failure.
        const wrongPin = typeof e.detail === "string" && e.detail.includes("unauthorized");
        if (!wrongPin) throw e;
        // Counted like any other failed authentication, so setup cannot be used as an unlimited
        // oracle for guessing the PIN.
        noteFailure(ip);
        return res.status(401).json({ error: "Not authorised" });
      }
      return res.status(200).json({ ok: true });
    }

    const stored = scalar(await rpc("admin_web_hash_srv", {}));
    if (!(await verifyPassword(body.password, stored))) {
      noteFailure(ip);
      return res.status(401).json({ error: "Not authorised" });
    }

    switch (body.action) {
      case "list": {
        const rows = await rpc("admin_devices_srv", {});
        // Every app in one list, tagged. The platform column is the only thing separating them,
        // so an app that has never checked in simply has no rows and no group — which is the
        // honest representation of "not wired up yet" rather than an empty section implying it is.
        return res.status(200).json({ installs: rows.map((r) => ({ ...r, app: appOf(r.platform) })) });
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
    // The upstream status code is returned; the upstream body still is not. A status is not
    // sensitive — it does not echo the request the way a PostgREST error message does — and
    // without it a 404 "no such function" and a 403 "permission denied" are indistinguishable
    // from the outside, which is exactly the wall this hit.
    return res.status(502).json({
      error: "The request could not be completed",
      correlationId,
      upstreamStatus: error.status || null,
    });
  }
}
