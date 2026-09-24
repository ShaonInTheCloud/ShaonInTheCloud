import test from 'node:test';
import assert from 'node:assert/strict';
import { nextAccountState, mayShowProfile } from '../web/account-state.js';

const a = { user: { id: 'user-a' } };
const b = { user: { id: 'user-b' } };
test('signup without a confirmed session leaves account UI locked', () => {
  assert.equal(nextAccountState({}, 'INITIAL_SESSION', null).view, 'login');
});
test('password recovery remains visible across token refresh and user update', () => {
  let state = nextAccountState({}, 'PASSWORD_RECOVERY', a);
  for (const event of ['TOKEN_REFRESHED', 'USER_UPDATED', 'SIGNED_IN']) {
    state = nextAccountState(state, event, a);
    assert.equal(state.view, 'recovery');
  }
});
test('reload of a recovery link with a session opens the new password form', () => {
  assert.equal(nextAccountState({ recovering: true }, 'INITIAL_SESSION', a).view, 'recovery');
});
test('switching accounts does not inherit another user recovery state', () => {
  const state = nextAccountState({}, 'PASSWORD_RECOVERY', a);
  assert.equal(nextAccountState(state, 'SIGNED_IN', b).view, 'account');
});
test('signout clears account and recovery state', () => {
  assert.deepEqual(nextAccountState({ recovering: true }, 'SIGNED_OUT', null), {
    user: null, recovering: false, view: 'login'
  });
});
test('late profile responses cannot expose a previous account', () => {
  const state = nextAccountState({}, 'SIGNED_IN', b);
  assert.equal(mayShowProfile(state, 'user-a', 1, 2), false);
  assert.equal(mayShowProfile(state, 'user-b', 1, 2), false);
  assert.equal(mayShowProfile(state, 'user-b', 2, 2), true);
  assert.equal(mayShowProfile({ user: null }, 'user-a', 1, 1), false);
});
