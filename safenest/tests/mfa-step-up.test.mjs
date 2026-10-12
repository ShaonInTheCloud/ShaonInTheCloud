import test from 'node:test';
import assert from 'node:assert/strict';
import { planStepUp, cleanCode, stepUp } from '../web/mfa-step-up.js';

test('planStepUp: aal2 is done; verified factor is challenged; nothing enrolled means enrol', () => {
  assert.deepEqual(planStepUp({ currentLevel: 'aal2', nextLevel: 'aal2' }, { totp: [] }), { action: 'done' });
  assert.deepEqual(planStepUp({ currentLevel: 'aal1', nextLevel: 'aal2' }, { totp: [{ id: 'f1', status: 'verified' }] }), { action: 'challenge', factorId: 'f1' });
  assert.deepEqual(planStepUp({ currentLevel: 'aal1', nextLevel: 'aal1' }, { totp: [] }), { action: 'enrol', staleFactorIds: [] });
  // Abandoned, unverified factors live only in `all`; they are cleared, not reused.
  assert.deepEqual(planStepUp({ currentLevel: 'aal1', nextLevel: 'aal1' }, { totp: [], all: [{ id: 'stale', factor_type: 'totp', status: 'unverified' }, { id: 'p', factor_type: 'phone', status: 'unverified' }] }), { action: 'enrol', staleFactorIds: ['stale'] });
  assert.deepEqual(planStepUp({ currentLevel: 'aal1', nextLevel: 'aal1' }, { totp: [] }), { action: 'enrol', staleFactorIds: [] });
  assert.equal(planStepUp(null, {}).action, 'error');
});

test('cleanCode accepts only six digits', () => {
  assert.equal(cleanCode(' 123 456 '), '123456');
  assert.equal(cleanCode('12345'), null);
  assert.equal(cleanCode('1234567'), null);
  assert.equal(cleanCode('12a456'), null);
  assert.equal(cleanCode(null), null);
});

function fakeClient({ level = 'aal1', factors = [], enrolFails = false, verifyFails = false } = {}) {
  const calls = [];
  const mfa = {
    getAuthenticatorAssuranceLevel: async () => ({ data: { currentLevel: level, nextLevel: factors.some(f => f.status === 'verified') ? 'aal2' : 'aal1' }, error: null }),
    // Real shape: `totp` holds verified factors only; everything is in `all`.
    listFactors: async () => ({ data: { totp: factors.filter(f => f.status === 'verified'), all: factors.map(f => ({ factor_type: 'totp', ...f })) }, error: null }),
    challengeAndVerify: async ({ factorId, code }) => { calls.push(['verify', factorId, code]); return verifyFails ? { error: { message: 'invalid' } } : { data: {}, error: null }; },
    enroll: async () => { calls.push(['enroll']); return enrolFails ? { error: { message: 'no' } } : { data: { id: 'new', totp: { qr_code: 'data:image/svg+xml;utf8,<svg/>', secret: 'ABC' } }, error: null }; },
    unenroll: async ({ factorId }) => { calls.push(['unenroll', factorId]); return { error: null }; }
  };
  return { client: { auth: { mfa } }, calls };
}
const ui = (answers, log = []) => ({ askCode: async () => answers.code ?? null, showEnrol: async ({ qr, secret }) => { log.push(['enrol-ui', !!qr, secret]); return answers.enrolCode ?? null; }, notify: m => log.push(m) });

test('already aal2: nothing is asked', async () => {
  const { client, calls } = fakeClient({ level: 'aal2', factors: [{ id: 'f1', status: 'verified' }] });
  assert.equal(await stepUp(client, ui({})), true);
  assert.deepEqual(calls, []);
});

test('verified factor: a correct code steps up, a wrong code does not', async () => {
  let { client, calls } = fakeClient({ factors: [{ id: 'f1', status: 'verified' }] });
  const log = [];
  assert.equal(await stepUp(client, ui({ code: '000111' }, log)), true);
  assert.deepEqual(calls, [['verify', 'f1', '000111']]);
  assert.ok(log.includes('mfa_verified'));
  ({ client, calls } = fakeClient({ factors: [{ id: 'f1', status: 'verified' }], verifyFails: true }));
  const log2 = [];
  assert.equal(await stepUp(client, ui({ code: '999999' }, log2)), false);
  assert.ok(log2.includes('mfa_wrong_code'));
  // Nothing gets unenrolled on a wrong code for an existing factor.
  assert.ok(!calls.some(c => c[0] === 'unenroll'));
});

test('no factor: enrol, show QR, verify; cancelling or failing removes the half-made factor', async () => {
  let { client, calls } = fakeClient();
  const log = [];
  assert.equal(await stepUp(client, ui({ enrolCode: '123456' }, log)), true);
  assert.deepEqual(calls, [['enroll'], ['verify', 'new', '123456']]);
  assert.deepEqual(log[0], ['enrol-ui', true, 'ABC']);
  assert.ok(log.includes('mfa_enrolled'));

  ({ client, calls } = fakeClient());
  assert.equal(await stepUp(client, ui({ enrolCode: null })), false);
  assert.deepEqual(calls, [['enroll'], ['unenroll', 'new']]);

  ({ client, calls } = fakeClient({ verifyFails: true }));
  assert.equal(await stepUp(client, ui({ enrolCode: '123456' })), false);
  assert.deepEqual(calls, [['enroll'], ['verify', 'new', '123456'], ['unenroll', 'new']]);

  // A stale unverified factor from an earlier attempt is cleared first.
  ({ client, calls } = fakeClient({ factors: [{ id: 'stale', status: 'unverified' }] }));
  assert.equal(await stepUp(client, ui({ enrolCode: '123456' })), true);
  assert.deepEqual(calls[0], ['unenroll', 'stale']);
});

test('non-numeric or short codes never reach the server', async () => {
  const { client, calls } = fakeClient({ factors: [{ id: 'f1', status: 'verified' }] });
  const log = [];
  assert.equal(await stepUp(client, ui({ code: '12-34' }, log)), false);
  assert.deepEqual(calls, []);
  assert.ok(log.includes('mfa_cancelled'));
});
