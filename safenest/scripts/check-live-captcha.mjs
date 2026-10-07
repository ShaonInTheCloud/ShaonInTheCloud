// Run only after activation using a confirmed disposable OWNED account and two
// freshly completed challenges. Public key only; never a Turnstile/service secret.
import assert from 'node:assert/strict';
import { createClient } from '@supabase/supabase-js';

if (process.env.SAFENEST_LIVE_CAPTCHA_ACCEPTANCE !== 'true')
  throw new Error('Live CAPTCHA acceptance is opt-in. Follow docs/android-auth-captcha-2026-10-07.md.');
const required = name => { const value = process.env[name]; if (!value) throw new Error(`${name} is required`); return value; };
const base = required('SUPABASE_URL');
const key = required('SUPABASE_PUBLISHABLE_KEY');
const email = required('SAFENEST_TEST_EMAIL');
const password = required('SAFENEST_TEST_PASSWORD');
const webToken = required('SAFENEST_TEST_WEB_CAPTCHA_TOKEN');
const androidToken = required('SAFENEST_TEST_ANDROID_CAPTCHA_TOKEN');
if (!/^https:\/\/[a-z0-9]+\.supabase\.co$/.test(base)) throw new Error('Use an HTTPS Supabase project URL.');
if (!/^sb_publishable_/.test(key)) throw new Error('Use only a public publishable key.');
assert.ok(webToken !== androidToken, 'Each flow needs a distinct token.');

async function rawLogin(captchaToken) {
  const body = { email, password };
  if (captchaToken !== undefined) body.gotrue_meta_security = { captcha_token: captchaToken };
  const response = await fetch(`${base}/auth/v1/token?grant_type=password`, {
    method: 'POST', redirect: 'error', headers: { apikey: key, 'Content-Type': 'application/json' },
    body: JSON.stringify(body), signal: AbortSignal.timeout(15000)
  });
  return { response, body: await response.json() };
}
for (const [label, token] of [['missing', undefined], ['invalid', 'invalid-safenest-acceptance-token']]) {
  const result = await rawLogin(token);
  assert.equal(result.response.status, 400, `${label} token did not fail closed`);
  assert.equal(result.body.error_code, 'captcha_failed', `${label} token was not rejected by CAPTCHA`);
  console.log(`${label} token: Auth captcha_failed verified`);
}
const client = createClient(base, key, { auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false } });
const web = await client.auth.signInWithPassword({ email, password, options: { captchaToken: webToken } });
assert.ok(!web.error, 'Website password login failed');
assert.ok(web.data.user?.email_confirmed_at, 'Account must be confirmed');
console.log('website SDK login: confirmed account verified');
const replay = await rawLogin(webToken);
assert.equal(replay.response.status, 400);
assert.equal(replay.body.error_code, 'captcha_failed');
console.log('replayed token: direct Auth rejection verified');
const android = await rawLogin(androidToken);
assert.equal(android.response.status, 200, 'Android password-grant contract failed');
assert.equal(android.body.user?.id, web.data.user.id);
assert.ok(android.body.access_token);
const access = await fetch(`${base}/functions/v1/protection-access`, {
  method: 'POST', redirect: 'error', headers: { apikey: key, 'Content-Type': 'application/json', Authorization: `Bearer ${android.body.access_token}` },
  body: '{}', signal: AbortSignal.timeout(15000)
});
assert.equal(access.status, 200);
assert.equal((await access.json()).user_id, web.data.user.id);
console.log('Android Auth + read-only subscription verification: identity verified');
await fetch(`${base}/auth/v1/logout?scope=global`, {
  method: 'POST', redirect: 'error', headers: { apikey: key, Authorization: `Bearer ${android.body.access_token}` },
  signal: AbortSignal.timeout(15000)
}).then(response => assert.ok(response.ok, 'Signout failed'));
console.log('global signout verified. Full signup, recovery, deletion and device flows still require the acceptance matrix.');
