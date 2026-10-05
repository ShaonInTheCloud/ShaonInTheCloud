import test from 'node:test';
import assert from 'node:assert/strict';
import { deletionHandler } from '../supabase/functions/delete-account/handler.mjs';

const request = (body = { password: 'correct', confirm: true }, changes = {}) => new Request(
  'https://backend.example/delete-account', {
    method: 'POST', headers: { origin: 'https://mysafenestbd.com', authorization: 'Bearer valid.jwt', 'content-type': 'application/json' },
    body: JSON.stringify(body), ...changes
  });
function fixture(overrides = {}) {
  const calls = [];
  const user = { id: 'owner-id', email: 'owner@example.com', email_confirmed_at: '2026-10-05' };
  const deps = {
    userAuth: { getUser: async () => ({ data: { user }, error: null }) },
    passwordAuth: { signInWithPassword: async value => { calls.push(['reauth', value.email]); return { data: { user, session: { access_token: 'fresh.jwt' } }, error: null }; } },
    adminAuth: {
      signOut: async (token, scope) => { calls.push(['revoke', token, scope]); return { error: null }; },
      deleteUser: async (id, soft) => { calls.push(['delete', id, soft]); return { error: null }; }
    }, ...overrides
  };
  return { run: deletionHandler(deps), calls };
}
test('confirmed deletion uses verified identity, reauthenticates, revokes sessions then deletes', async () => {
  const { run, calls } = fixture();
  const result = await run(request({ password: 'correct', confirm: true, user_id: 'victim', email: 'victim@example.com' }));
  assert.equal(result.status, 200);
  assert.deepEqual(calls, [['reauth', 'owner@example.com'], ['revoke', 'fresh.jwt', 'global'], ['delete', 'owner-id', false]]);
  assert.equal(result.headers.get('cache-control'), 'no-store');
});
test('untrusted origin and missing auth never reach the admin API', async () => {
  const { run, calls } = fixture();
  assert.equal((await run(request({}, { headers: { origin: 'https://evil.example' } }))).status, 403);
  assert.equal((await run(request({}, { headers: { origin: 'https://mysafenestbd.com' } }))).status, 401);
  assert.deepEqual(calls, []);
});
test('expired/deleted account, absent consent and malformed input cannot delete', async () => {
  const invalid = fixture({ userAuth: { getUser: async () => ({ data: { user: null }, error: new Error('expired') }) } });
  assert.equal((await invalid.run(request())).status, 401);
  assert.deepEqual(invalid.calls, []);
  const { run, calls } = fixture();
  assert.equal((await run(request({ password: 'correct' }))).status, 400);
  assert.equal((await run(request({}, { body: '{' }))).status, 400);
  assert.equal((await run(request({ password: 'x'.repeat(5000), confirm: true }))).status, 413);
  assert.deepEqual(calls, []);
});
test('incorrect password and a different reauthenticated identity cannot delete', async () => {
  for (const data of [null, { user: { id: 'other' }, session: { access_token: 'jwt' } }]) {
    const { run, calls } = fixture({ passwordAuth: { signInWithPassword: async () => ({ data, error: null }) } });
    assert.equal((await run(request())).status, 401);
    assert.deepEqual(calls, []);
  }
});
test('failed session revocation prevents deletion and hides upstream secrets', async () => {
  const { run, calls } = fixture({ adminAuth: {
    signOut: async () => ({ error: new Error('private token') }),
    deleteUser: async () => { calls.push(['delete']); return { error: null }; }
  } });
  const response = await run(request());
  assert.equal(response.status, 503);
  assert.equal((await response.text()).includes('private token'), false);
  assert.equal(calls.some(x => x[0] === 'delete'), false);
});
