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
test('HTML fallback restricts scripts, frames and backend connections without ineffective frame-ancestors', async () => {
  const { contentSecurityPolicy } = await import('../scripts/security-headers.mjs');
  const policy=contentSecurityPolicy('https://example.supabase.co', {meta:true});
  assert.ok(policy.includes("script-src 'self' https://challenges.cloudflare.com;"));
  assert.ok(policy.includes("frame-src https://challenges.cloudflare.com;"));
  assert.ok(policy.includes("connect-src 'self' https://example.supabase.co;"));
  assert.ok(!policy.includes('frame-ancestors'));
  assert.ok(!policy.includes('unsafe-eval'));
});
