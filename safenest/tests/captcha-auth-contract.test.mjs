import test from 'node:test';
import assert from 'node:assert/strict';
import { createClient } from '@supabase/supabase-js';

// Explicit provider fixture, not evidence of live Cloudflare or Supabase enforcement.
function fixture() {
  const used = new Set(), calls = [];
  const fetch = async (url, options) => {
    const body = JSON.parse(options.body); calls.push({ url, body });
    const token = body.gotrue_meta_security?.captcha_token;
    if (!token?.startsWith('issued-') || used.has(token))
      return new Response(JSON.stringify({ error_code: 'captcha_failed', msg: 'captcha verification failed' }), { status: 400 });
    used.add(token);
    const user = { id: 'owned-test-user', aud: 'authenticated', email: 'owned@example.test' };
    return new Response(JSON.stringify(url.includes('/token') ?
      { access_token: 'test-jwt', token_type: 'bearer', expires_in: 3600, refresh_token: 'test-refresh', user } : { user }), { status: 200 });
  };
  const client = createClient('https://example.test', 'public-fixture-key', {
    auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false }, global: { fetch }
  });
  return { client, fetch, calls };
}
test('website SDK forwards signup, login, recovery and deletion reauthentication tokens to Auth', async () => {
  const { client, calls } = fixture();
  const credentials = { email: 'owned@example.test', password: 'fixture-only-password' };
  assert.equal((await client.auth.signUp({ ...credentials, options: { captchaToken: 'issued-signup' } })).error, null);
  assert.equal((await client.auth.signInWithPassword({ ...credentials, options: { captchaToken: 'issued-login' } })).error, null);
  assert.equal((await client.auth.resetPasswordForEmail(credentials.email, { captchaToken: 'issued-recovery' })).error, null);
  assert.equal((await client.auth.signInWithPassword({ ...credentials, options: { captchaToken: 'issued-delete' } })).error, null);
  assert.deepEqual(calls.map(call => call.body.gotrue_meta_security.captcha_token),
    ['issued-signup', 'issued-login', 'issued-recovery', 'issued-delete']);
});
test('missing, invalid and replayed website SDK tokens are rejected by the provider fixture', async () => {
  const { client } = fixture();
  const login = captchaToken => client.auth.signInWithPassword({ email: 'owned@example.test', password: 'fixture-password', options: { captchaToken } });
  for (const token of [undefined, '', 'invalid']) assert.equal((await login(token)).error?.code, 'captcha_failed');
  assert.equal((await login('issued-once')).error, null);
  assert.equal((await login('issued-once')).error?.code, 'captcha_failed');
});
test('raw Android password-grant payload shares the website Auth contract and single-use policy', async () => {
  const { fetch } = fixture();
  const send = token => fetch('https://example.test/auth/v1/token?grant_type=password', { body: JSON.stringify({
    email: 'owned@example.test', password: 'fixture-password', gotrue_meta_security: { captcha_token: token }
  }) });
  for (const token of [undefined, '', 'invalid']) assert.equal((await send(token)).status, 400);
  assert.equal((await send('issued-android')).status, 200);
  assert.equal((await send('issued-android')).status, 400);
});
for (const flow of ['signup', 'recovery']) {
  test(`${flow} rejects missing, invalid and replayed tokens in the provider fixture`, async () => {
    const { client } = fixture();
    const send = captchaToken => flow === 'signup'
      ? client.auth.signUp({ email: 'owned@example.test', password: 'fixture-password', options: { captchaToken } })
      : client.auth.resetPasswordForEmail('owned@example.test', { captchaToken });
    for (const token of [undefined, 'invalid']) assert.equal((await send(token)).error?.code, 'captcha_failed');
    assert.equal((await send(`issued-${flow}`)).error, null);
    assert.equal((await send(`issued-${flow}`)).error?.code, 'captcha_failed');
  });
}
