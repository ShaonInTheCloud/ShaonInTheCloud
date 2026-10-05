import test from 'node:test';
import assert from 'node:assert/strict';
import { entitlementState } from '../web/entitlement-state.js';
const active = () => ({ user_id: 'owner', active: true, server_now: '2026-10-06T00:00:00Z',
  entitlement: { id: 'receipt', plan_code: 'monthly', starts_at: '2026-10-01T00:00:00Z', ends_at: '2026-11-01T00:00:00Z' } });
test('verified access shows the finite subscription without asserting device protection', () => {
  assert.deepEqual(entitlementState(active(), 'owner'), { kind: 'active', plan: 'monthly', endsAt: '2026-11-01T00:00:00.000Z' });
});
test('another account, invalid clock, future or expired period cannot show paid access', () => {
  for (const change of [ { user_id: 'other' }, { server_now: 'invalid' }, { active: 'true' },
      { server_now: '2026-09-30T00:00:00Z' }, { server_now: '2026-11-01T00:00:00Z' } ]) {
    assert.equal(entitlementState({ ...active(), ...change }, 'owner').kind, 'unavailable');
  }
});
test('unavailable status is distinct from a verified absence of a subscription', () => {
  assert.deepEqual(entitlementState({ ...active(), active: false, entitlement: null }, 'owner'), { kind: 'inactive' });
  for (const data of [null, {}, { ...active(), active: false }, { ...active(), entitlement: {} }]) {
    assert.equal(entitlementState(data, 'owner').kind, 'unavailable');
  }
});
