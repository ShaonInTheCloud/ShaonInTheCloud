// Authenticated, read-only paid access endpoint. Never trusts client plan/status/user_metadata.
export async function handleAccess(req: Request, env: Record<string, string>, fetcher = fetch, now = new Date()): Promise<Response> {
  const origin = req.headers.get("Origin");
  const allowedOrigins = new Set([
    "https://mysafenestbd.com",
    "https://safenest-bangladesh.kabirmdhumaun23.chatgpt.site",
  ]);
  const cors: Record<string, string> = origin && allowedOrigins.has(origin) ? {
    "Access-Control-Allow-Origin": origin,
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Vary": "Origin",
  } : {};
  const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
    status, headers: { ...cors, "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
  if (origin && !allowedOrigins.has(origin)) return json({ error: "origin_not_allowed" }, 403);
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const authorization = req.headers.get("Authorization") ?? "";
  if (!/^Bearer [A-Za-z0-9_.-]+$/.test(authorization)) return json({ error: "authentication_required" }, 401);
  const base = env.SUPABASE_URL, key = env.SUPABASE_ANON_KEY;
  if (!base || !key) return json({ error: "not_configured" }, 503);
  const headers = { apikey: key, Authorization: authorization };
  try {
    const auth = await fetcher(`${base}/auth/v1/user`, { headers, signal: AbortSignal.timeout(10_000) });
    if (!auth.ok) return json({ error: "authentication_required" }, 401);
    const user = await auth.json();
    if (!user.id || user.is_anonymous === true) return json({ error: "account_required" }, 403);
    const raw = await req.text();
    if (raw.length > 16_384) return json({ error: "invalid_request" }, 400);
    let body: { entitlement_id?: unknown };
    try { body = raw ? JSON.parse(raw) : {}; } catch { return json({ error: "invalid_request" }, 400); }
    if (!body || typeof body !== "object" || Array.isArray(body)) return json({ error: "invalid_request" }, 400);
    const requested = body.entitlement_id;
    if (requested !== undefined && (typeof requested !== "string" ||
        !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(requested))) {
      return json({ error: "invalid_request" }, 400);
    }
    const query = new URLSearchParams({ select: "id,plan_code,starts_at,ends_at", user_id: `eq.${user.id}`,
      status: "eq.active", starts_at: `lte.${now.toISOString()}`, ends_at: `gt.${now.toISOString()}`,
      order: "ends_at.desc", limit: "1" });
    if (requested) query.set("id", `eq.${requested}`);
    // Pass the user's JWT through to PostgREST; RLS independently enforces ownership.
    const response = await fetcher(`${base}/rest/v1/protection_entitlements?${query}`, { headers, signal: AbortSignal.timeout(10_000) });
    if (!response.ok) return json({ error: "verification_unavailable" }, 503);
    const rows = await response.json();
    if (!Array.isArray(rows)) return json({ error: "verification_unavailable" }, 503);
    const entitlement = rows[0] ?? null;
    return json({ active: !!entitlement, user_id: user.id, server_now: now.toISOString(),
      checked_entitlement_id: requested ?? null, entitlement });
  } catch { return json({ error: "verification_unavailable" }, 503); }
}

if (typeof Deno !== "undefined") Deno.serve((req: Request) => handleAccess(req, {
  SUPABASE_URL: Deno.env.get("SUPABASE_URL") ?? "", SUPABASE_ANON_KEY: Deno.env.get("SUPABASE_ANON_KEY") ?? "",
}));
