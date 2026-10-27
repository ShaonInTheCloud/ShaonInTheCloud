import { spawn } from 'node:child_process';
import assert from 'node:assert/strict';
import { setTimeout as delay } from 'node:timers/promises';
import { inspectResponse } from './check-live-security.mjs';
const origin = 'http://127.0.0.1:8793';
const child = spawn(process.execPath, ['node_modules/wrangler/bin/wrangler.js', 'dev', '--local', '--ip', '127.0.0.1', '--port', '8793', '--config', 'cloudflare/wrangler.toml'], {
  env: { ...process.env, WRANGLER_SEND_METRICS: 'false' }, stdio: ['ignore', 'pipe', 'pipe']
});
let logs = '';
child.stdout.on('data', b => { logs += b; }); child.stderr.on('data', b => { logs += b; });
try {
  let ready = false;
  for (let i = 0; i < 100; i++) {
    if (child.exitCode !== null) throw new Error('Wrangler exited before readiness');
    try { await fetch(origin); ready = true; break; } catch { await delay(200); }
  }
  assert.ok(ready, 'Local Worker ready within 20 seconds');
  for (const path of ['/', '/account', '/account/', '/account.html', '/delete-account', '/delete-account.html']) {
    const r = await fetch(origin + path);
    assert.equal(new URL(r.url).origin, origin);
    const raw = `HTTP/1.1 ${r.status}\r\n` + [...r.headers].map(([k, v]) => `${k}: ${v}`).join('\r\n') + '\r\n\r\n';
    assert.deepEqual(inspectResponse(raw, { account: path !== '/' }), [], path);
    console.log(`${path}: PASS (local response)`);
  }
  for (const path of ['/cloudflare-static-assets-worker.js', '/cloudflare-security-headers-worker.js', '/missing-asset-1234.js']) {
    const r = await fetch(origin + path);
    assert.equal(r.status, 404, path);
    assert.equal(r.headers.get('x-content-type-options'), 'nosniff');
  }
  const redirect = await fetch(origin + '/account.html', { redirect: 'manual' });
  assert.ok([200, 301, 307, 308].includes(redirect.status));
  assert.equal(redirect.headers.get('cache-control'), 'no-store');
  assert.equal(redirect.headers.get('referrer-policy'), 'no-referrer');
  const profile = await fetch(origin + '/ios/safenest-dns.mobileconfig');
  assert.equal(profile.status, 200);
  assert.match(profile.headers.get('content-type'), /^application\/x-apple-aspen-config/);
  assert.equal((await fetch(origin, { method: 'HEAD' })).status, 200);
  console.log('Redirects, HEAD, profile MIME, 404 headers and private Worker source: PASS');
} catch (error) { console.error(logs); throw error; }
finally { child.kill('SIGTERM'); }
