import test from 'node:test';
import assert from 'node:assert/strict';
import { auditSurfaces, publicGet, validateOrigin } from '../scripts/check-launch-surfaces.mjs';

const origin = 'https://fixture.invalid';
const headers = { 'Strict-Transport-Security': 'max-age=31536000', 'X-Content-Type-Options': 'nosniff',
  'Content-Security-Policy': "object-src 'none'; frame-ancestors 'none'", 'Cache-Control': 'no-store', 'Referrer-Policy': 'no-referrer' };
function fixture({ stale = false, open = false, insecure = false } = {}) {
  return async value => {
    const path = new URL(value).pathname;
    const body = path === '/functions/v1/payment-catalogue' ? JSON.stringify({ available: open, products: open ? [{}] : [] })
      : path === '/sitemap.xml' ? `<urlset>${['/', '/pricing', '/campaign', '/support', '/contact'].map(x => `<url><loc>${origin}${x}</loc></url>`).join('')}</urlset>`
      : path === '/account' ? '<form id="login-form"></form><form id="signup-form"></form>Version 0.4.10 development APK'
      : path === '/android-captcha' ? '<meta name="robots" content="noindex"><script src="android-captcha.js"></script>'
      : path === '/support' && stale ? 'The older 0.4.0 download' : '<html>Development support</html>';
    return new Response(body, { headers: insecure ? {} : headers });
  };
}
test('public audit verifies coherent safe pages and disabled catalogue', async () => {
  const report = await auditSurfaces({ origin, backend: origin, fetcher: fixture() });
  assert.equal(report.passed, true);
  assert.match(report.scope, /not live Auth/);
});
test('stale download copy and accidentally opened checkout fail readiness', async () => {
  const report = await auditSurfaces({ origin, backend: origin, fetcher: fixture({ stale: true, open: true }) });
  assert.equal(report.passed, false);
  assert.ok(report.checks.some(x => x.surface === '/support' && !x.passed));
  assert.ok(report.checks.some(x => x.surface === 'payment-catalogue' && !x.passed));
});
test('meta policy or HTTP availability cannot substitute for enforced headers', async () => {
  const report = await auditSurfaces({ origin, backend: origin, fetcher: fixture({ insecure: true }) });
  assert.equal(report.passed, false);
  assert.ok(report.checks.some(x => x.check === 'Account no-store is missing'));
});
test('external, credential and query redirects are rejected before following', async () => {
  for (const target of ['http://fixture.invalid/', 'https://external.invalid/', 'https://user:pass@fixture.invalid/', '/?token=secret']) {
    let calls = 0;
    await assert.rejects(publicGet(origin, async () => { calls++; return new Response(null, { status: 302, headers: { location: target } }); }));
    assert.equal(calls, 1);
  }
});
test('same-origin redirects are bounded and response bodies cannot leak into reports', async () => {
  let calls = 0;
  await assert.rejects(publicGet(origin, async () => { calls++; return new Response(null, { status: 302, headers: { location: '/' } }); }), /Too many/);
  assert.equal(calls, 4);
  const report = await auditSurfaces({ origin, backend: origin, fetcher: async () => new Response('private-session-marker') });
  assert.ok(!JSON.stringify(report).includes('private-session-marker'));
});
test('oversized responses and credential-bearing origins fail closed', async () => {
  await assert.rejects(publicGet(origin, async () => new Response('x'.repeat(1024 * 1024 + 1))), /too large/);
  for (const value of ['http://fixture.invalid', origin + '/', origin + '?token=secret', 'https://user:pass@fixture.invalid']) assert.throws(() => validateOrigin(value));
});
