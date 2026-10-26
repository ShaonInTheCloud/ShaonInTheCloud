import test from 'node:test';
import assert from 'node:assert/strict';
import { securityHeaders } from '../scripts/security-headers.mjs';
import { workerSource, parseHeaders } from '../scripts/cloudflare-worker.mjs';

async function load() {
  const source = workerSource(securityHeaders('https://example.supabase.co'));
  return (await import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'))).default;
}
async function run(worker, path) {
  globalThis.fetch = async () => new Response('<html></html>', { status: 200, headers: { 'cache-control': 'public, max-age=0', 'content-type': 'text/html' } });
  return worker.fetch(new Request('https://mysafenestbd.com' + path));
}

test('every page gets HSTS, nosniff, frame protection and an enforced CSP', async () => {
  const worker = await load();
  for (const path of ['/', '/pricing.html', '/account']) {
    const r = await run(worker, path);
    assert.equal(r.status, 200);
    assert.match(r.headers.get('strict-transport-security'), /max-age=31536000/);
    assert.equal(r.headers.get('x-content-type-options'), 'nosniff');
    assert.match(r.headers.get('content-security-policy'), /frame-ancestors 'none'/);
    assert.match(r.headers.get('content-security-policy'), /object-src 'none'/);
    assert.equal(r.headers.get('content-type'), 'text/html');
  }
});

test('account and deletion pages are never cached and send no referrer', async () => {
  const worker = await load();
  for (const path of ['/account', '/account/', '/account.html', '/delete-account', '/delete-account.html']) {
    const r = await run(worker, path);
    assert.equal(r.headers.get('cache-control'), 'no-store', path);
    assert.equal(r.headers.get('referrer-policy'), 'no-referrer', path);
  }
  const home = await run(worker, '/');
  assert.equal(home.headers.get('cache-control'), 'public, max-age=0');
  assert.equal(home.headers.get('referrer-policy'), 'strict-origin-when-cross-origin');
});

test('body and status pass through and malformed policy is rejected', async () => {
  const worker = await load();
  assert.equal(await (await run(worker, '/')).text(), '<html></html>');
  assert.throws(() => parseHeaders('  Orphan: header'));
});

test('static Worker uses ASSETS, preserving redirects and errors with policy headers', async () => {
  const source = workerSource(securityHeaders('https://example.supabase.co'), { assets: true });
  const worker = (await import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'))).default;
  for (const status of [200, 302, 404, 500]) {
    const r = await worker.fetch(new Request('https://example.com/account'), { ASSETS: { fetch: async () => new Response('body', { status, headers: { location: '/account', 'set-cookie': 'example=value', 'referrer-policy': 'wrong, duplicate' } }) } });
    assert.equal(r.status, status);
    assert.equal(r.headers.get('location'), '/account');
    assert.equal(r.headers.get('set-cookie'), 'example=value');
    assert.equal(r.headers.get('referrer-policy'), 'no-referrer');
    assert.equal(r.headers.get('cache-control'), 'no-store');
    assert.equal(await r.text(), 'body');
  }
  const r = await worker.fetch(new Request('https://example.com/'), { ASSETS: { fetch: async () => { throw new Error('private detail'); } } });
  assert.equal(r.status, 503);
  assert.equal(r.headers.get('x-content-type-options'), 'nosniff');
  assert.equal(await r.text(), 'Service unavailable');
});
