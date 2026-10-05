import test from 'node:test';
import assert from 'node:assert/strict';
import { handleAccess } from '../index.ts';
const env = { SUPABASE_URL: 'https://test.supabase.co', SUPABASE_ANON_KEY: 'public-test-key' };
const now = new Date('2026-10-04T03:00:00Z');
const req = (token = 'user.jwt.value', body = {}) => new Request('https://test/access', {
  method: 'POST', headers: token ? { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } : {}, body: JSON.stringify(body),
});
const json = x => new Response(JSON.stringify(x), { headers: { 'Content-Type': 'application/json' } });
test('browser preflight allows the public portal and rejects untrusted origins before querying', async () => {
  const fetcher = async () => { throw new Error('Must not reach the backend'); };
  const preflight = new Request('https://test/access', { method: 'OPTIONS', headers: { Origin: 'https://mysafenestbd.com' } });
  const allowed = await handleAccess(preflight, env, fetcher, now);
  assert.equal(allowed.status, 204);
  assert.equal(allowed.headers.get('Access-Control-Allow-Origin'), 'https://mysafenestbd.com');
  const denied = await handleAccess(new Request('https://test/access', { method: 'OPTIONS', headers: { Origin: 'https://evil.example' } }), env, fetcher, now);
  assert.equal(denied.status, 403);
  assert.equal(denied.headers.get('Access-Control-Allow-Origin'), null);
});
test('missing or invalid auth never queries paid rows', async () => {
  let calls = 0;
  const fetcher = async () => { calls++; return new Response('', { status: 401 }); };
  assert.equal((await handleAccess(req(null), env, fetcher, now)).status, 401);
  assert.equal(calls, 0);
  assert.equal((await handleAccess(req(), env, fetcher, now)).status, 401);
  assert.equal(calls, 1);
});
test('client paid flags and other user IDs cannot create access', async () => {
  const calls = [];
  const fetcher = async (url, options) => {
    calls.push({ url, options });
    return url.includes('/auth/') ? json({ id: 'own-user' }) : json([]);
  };
  const response = await handleAccess(req(undefined, { paid: true, user_id: 'victim', plan: 'annual' }), env, fetcher, now);
  assert.equal((await response.json()).active, false);
  const url = new URL(calls[1].url);
  assert.equal(url.searchParams.get('user_id'), 'eq.own-user');
  assert.equal(url.searchParams.get('status'), 'eq.active');
  assert.equal(url.searchParams.get('starts_at'), `lte.${now.toISOString()}`);
  assert.equal(url.searchParams.get('ends_at'), `gt.${now.toISOString()}`);
  assert.equal(calls[1].options.headers.Authorization, 'Bearer user.jwt.value');
});
test('anonymous accounts cannot activate paid mode', async () => {
  const response = await handleAccess(req(), env, async () => json({ id: 'anon', is_anonymous: true }), now);
  assert.equal(response.status, 403);
});
test('trusted active window returns server clock and finite period', async () => {
  const entitlement = { id: 'receipt', plan_code: 'monthly', starts_at: '2026-10-01T00:00:00Z', ends_at: '2026-11-01T00:00:00Z' };
  const response = await handleAccess(req(), env, async url => url.includes('/auth/') ? json({ id: 'own-user' }) : json([entitlement]), now);
  const data = await response.json();
  assert.equal(data.active, true); assert.deepEqual(data.entitlement, entitlement);
  assert.equal(data.server_now, now.toISOString());
  assert.equal(response.headers.get('Cache-Control'), 'no-store');
});
test('backend errors cannot grant protection', async () => {
  const response = await handleAccess(req(), env, async () => { throw new Error('offline'); }, now);
  assert.equal(response.status, 503);
});
test('support rechecks are bound to the same authenticated account and exact period', async () => {
  const id = '11111111-2222-3333-4444-555555555555';
  const calls = [];
  const response = await handleAccess(req(undefined, { entitlement_id: id, user_id: 'victim' }), env,
    async url => { calls.push(url); return url.includes('/auth/') ? json({ id: 'own-user' }) : json([]); }, now);
  const data = await response.json();
  assert.equal(data.active, false); assert.equal(data.user_id, 'own-user');
  assert.equal(data.checked_entitlement_id, id);
  const query = new URL(calls[1]).searchParams;
  assert.equal(query.get('id'), `eq.${id}`); assert.equal(query.get('user_id'), 'eq.own-user');
});
test('invalid entitlement IDs cannot inject filters', async () => {
  let queried = false;
  const response = await handleAccess(req(undefined, { entitlement_id: 'x&status=eq.active' }), env,
    async url => { if (!url.includes('/auth/')) queried = true; return json({ id: 'own-user' }); }, now);
  assert.equal(response.status, 400); assert.equal(queried, false);
});
test('a malformed backend reply never authorizes a release', async () => {
  const response = await handleAccess(req(), env, async url => url.includes('/auth/') ? json({ id: 'own-user' }) : json({ error: 'bad rows' }), now);
  assert.equal(response.status, 503);
});
