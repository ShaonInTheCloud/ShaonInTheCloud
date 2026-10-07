import test from 'node:test';
import assert from 'node:assert/strict';
import { createCaptchaGate } from '../web/auth-captcha.js';
function setup() {
  const tokens = new Map(); let renders = 0; const expired = new Set();
  const api = { getResponse: id => tokens.get(id), isExpired: id => expired.has(id), reset: id => tokens.delete(id) };
  const gate = createCaptchaGate({ enabled: true, adapter: async () => api, render: (_, form) => { renders++; return form; } });
  return { gate, tokens, expired, count: () => renders };
}
test('disabled rollout leaves existing authentication untouched and does not load third-party code', async () => {
  const gate = createCaptchaGate({enabled: false, adapter: () => { throw new Error('must not load'); }});
  assert.equal(await gate.take('login'), undefined);
});
test('missing or expired challenges block the authentication action', async () => {
  const { gate, tokens, expired } = setup();
  await assert.rejects(gate.take('login'), { code: 'captcha_required' });
  tokens.set('login', 'old-token'); expired.add('login');
  await assert.rejects(gate.take('login'), { code: 'captcha_required' });
  assert.equal(tokens.has('login'), false);
});
test('reset consumes a token locally and cannot reuse it for a retry', async () => {
  const { gate, tokens } = setup(); await gate.activate('login'); tokens.set('login', 'fresh-token');
  assert.equal(await gate.take('login'), 'fresh-token'); gate.reset('login');
  await assert.rejects(gate.take('login'), { code: 'captcha_required' });
});
test('signup, login, recovery and deletion never borrow another form’s token', async () => {
  const { gate, tokens } = setup(); await gate.activate('login'); tokens.set('login', 'login-token');
  for (const form of ['signup','reset','delete']) await assert.rejects(gate.take(form), { code: 'captcha_required' });
});
test('simultaneous readiness checks render one widget per form', async () => {
  const { gate, count } = setup(); await Promise.all([gate.activate('login'),gate.activate('login')]);
  assert.equal(count(),1);
});
test('provider loading failure blocks enabled auth rather than bypassing CAPTCHA', async () => {
  const gate=createCaptchaGate({enabled:true,adapter:async()=>{throw new Error('unavailable');}});
  await assert.rejects(gate.take('login'),/unavailable/);
});
