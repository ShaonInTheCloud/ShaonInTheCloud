import test from 'node:test';
import assert from 'node:assert/strict';
import { createNativeChallenge, mountNativeChallenge } from '../web/android-captcha.js';

const nonce = '1234567890abcdef1234567890abcdef';
function setup() {
  const messages = [], states = [];
  const challenge = createNativeChallenge({ nonce, send: value => messages.push(JSON.parse(value)), status: state => states.push(state) });
  return { challenge, messages, states };
}
test('native page delivers only the fresh token and request nonce, once', () => {
  const { challenge, messages, states } = setup();
  assert.equal(challenge.success('fresh'), true);
  assert.equal(challenge.success('fresh'), false);
  assert.equal(challenge.success('another'), false);
  assert.deepEqual(messages, [{ type: 'token', nonce, token: 'fresh' }]);
  assert.deepEqual(states, ['complete']);
});
test('missing or malformed native bridge context cannot start the challenge', () => {
  for (const value of [undefined, '', 'bad', 'a'.repeat(33)])
    assert.throws(() => createNativeChallenge({ nonce: value, send() {}, status() {} }), /Open this check/);
  assert.throws(() => createNativeChallenge({ nonce, status() {} }), /Open this check/);
});
test('missing, blank and oversized tokens do not reach Android', () => {
  const { challenge, messages, states } = setup();
  for (const value of [undefined, '', ' ', 'x'.repeat(2049)]) assert.equal(challenge.success(value), false);
  assert.equal(messages.length, 0);
  assert.deepEqual(states, ['retry', 'retry', 'retry', 'retry']);
});
test('provider error or expiry requires another challenge without sending an auth request', () => {
  const { challenge, messages, states } = setup();
  challenge.failure(); challenge.expired();
  assert.deepEqual(states, ['retry', 'retry']); assert.equal(messages.length, 0);
  assert.equal(challenge.success('new-token'), true);
  challenge.expired(); assert.deepEqual(states, ['retry', 'retry', 'complete']);
});
test('ordinary browser visit without Android bridge does not load Cloudflare', () => {
  const status = {}, retry = {};
  mountNativeChallenge({ window: { location: { hash: `#nonce=${nonce}` } }, sitekey: 'public-sitekey',
    document: { querySelector: selector => selector === '#status' ? status : retry,
      createElement() { assert.fail('No third-party script should load without bridge'); } } });
  assert.match(status.textContent, /Open this check from/);
});
