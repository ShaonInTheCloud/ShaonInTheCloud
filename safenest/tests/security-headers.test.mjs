import test from 'node:test';
import assert from 'node:assert/strict';
import { securityHeaders } from '../scripts/security-headers.mjs';
test('canonical account URLs and deletion pages disable caching and referrer disclosure', () => {
  const headers = securityHeaders('https://example.supabase.co');
  for (const route of ['/account', '/account/', '/account.html', '/delete-account', '/delete-account.html']) {
    assert.ok(headers.includes(`${route}\n  Cache-Control: no-store\n  Referrer-Policy: no-referrer\n`));
  }
  assert.ok(headers.includes("connect-src 'self' https://example.supabase.co;"));
  assert.ok(headers.includes("frame-ancestors 'none'"));
  assert.ok(headers.includes("object-src 'none'"));
});
test('backend configuration cannot inject arbitrary headers or downgrade to HTTP', () => {
  for (const value of ['http://example.com', 'https://example.com/\nX-Test: yes', 'https://user:pass@example.com']) {
    assert.throws(() => securityHeaders(value));
  }
});
