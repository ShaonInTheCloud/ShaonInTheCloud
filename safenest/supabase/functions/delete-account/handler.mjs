const ORIGINS = new Set([
  'https://mysafenestbd.com',
  'https://www.mysafenestbd.com',
  'https://safenest-bangladesh.kabirmdhumaun23.chatgpt.site'
]);

// Dependencies are injected so security decisions can be tested without deleting users.
export function deletionHandler({ userAuth, passwordAuth, adminAuth }) {
  return async request => {
    const origin = request.headers.get('origin');
    const headers = {
      'Content-Type': 'application/json', 'Cache-Control': 'no-store',
      'Vary': 'Origin', 'X-Content-Type-Options': 'nosniff'
    };
    const reply = (status, code) => new Response(JSON.stringify({ code }), { status, headers });
    if (!ORIGINS.has(origin)) return reply(403, 'origin_not_allowed');
    headers['Access-Control-Allow-Origin'] = origin;
    headers['Access-Control-Allow-Headers'] = 'authorization, apikey, content-type, x-client-info';
    headers['Access-Control-Allow-Methods'] = 'POST, OPTIONS';
    if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers });
    if (request.method !== 'POST') return reply(405, 'method_not_allowed');
    const authorization = request.headers.get('authorization') || '';
    if (!/^Bearer [A-Za-z0-9._-]+$/.test(authorization)) return reply(401, 'authentication_required');
    try {
      // getUser verifies the account still exists; JWT decoding alone is insufficient.
      const token = authorization.slice(7);
      const { data: identity, error: identityError } = await userAuth.getUser(token);
      if (identityError || !identity?.user?.id || !identity.user.email_confirmed_at) {
        return reply(401, 'authentication_required');
      }
      const text = await request.text();
      if (text.length > 4096) return reply(413, 'request_too_large');
      let body;
      try { body = JSON.parse(text); } catch { return reply(400, 'invalid_request'); }
      if (body?.confirm !== true || typeof body.password !== 'string' ||
          !body.password.length || body.password.length > 1024) return reply(400, 'confirmation_required');
      // Never accept an email or user ID from the caller as the deletion target.
      const { data: fresh, error: passwordError } = await passwordAuth.signInWithPassword({
        email: identity.user.email, password: body.password,
        options: { captchaToken: typeof body.captchaToken === 'string' ? body.captchaToken : undefined }
      });
      if (passwordError || fresh?.user?.id !== identity.user.id || !fresh?.session?.access_token) {
        return reply(401, 'reauthentication_failed');
      }
      const { error: revokeError } = await adminAuth.signOut(fresh.session.access_token, 'global');
      if (revokeError) return reply(503, 'deletion_unavailable');
      // Profiles and protection entitlements use ON DELETE CASCADE in the live schema.
      const { error: deleteError } = await adminAuth.deleteUser(identity.user.id, false);
      if (deleteError) return reply(503, 'deletion_unavailable');
      return reply(200, 'account_deleted');
    } catch {
      // No passwords, JWTs, email addresses or raw upstream errors are logged or returned.
      return reply(503, 'deletion_unavailable');
    }
  };
}
