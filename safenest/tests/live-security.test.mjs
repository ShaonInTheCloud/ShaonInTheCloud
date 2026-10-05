import test from 'node:test';
import assert from 'node:assert/strict';
import { inspectResponse } from '../scripts/check-live-security.mjs';

const secure = "HTTP/2 200\r\nStrict-Transport-Security: max-age=31536000\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; object-src 'none'; frame-ancestors 'none'\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\n\r\n";
test('audit evaluates final origin response instead of a proxy CONNECT header', () => {
  assert.deepEqual(inspectResponse('HTTP/1.1 200 Connection established\r\n\r\n' + secure, { account: true }), []);
  assert.ok(inspectResponse(secure + 'HTTP/2 502\r\n\r\n').includes('Expected HTTP 200, received 502'));
});
test('audit rejects cache revalidation and missing enforced account policies', () => {
  const issues = inspectResponse('HTTP/2 200\r\nCache-Control: public, max-age=0, must-revalidate\r\n\r\n', { account: true });
  assert.ok(issues.includes('Account no-store is missing'));
  assert.ok(issues.includes('Account no-referrer is missing'));
  assert.ok(issues.includes('Enforced CSP frame-ancestors is missing'));
});
test('report-only policies, zero-age HSTS and similarly named directives cannot pass', () => {
  const weak = secure.replace('max-age=31536000', 'max-age=0').replace('Content-Security-Policy:', 'Content-Security-Policy-Report-Only:');
  const issues = inspectResponse(weak, { account: true });
  assert.ok(issues.includes('HSTS is missing'));
  assert.ok(issues.includes('Enforced CSP frame-ancestors is missing'));
  assert.ok(inspectResponse(secure.replace('frame-ancestors', 'x-frame-ancestors')).includes('Enforced CSP frame-ancestors is missing'));
});
test('public asset caching is allowed, but malformed responses fail closed', () => {
  assert.deepEqual(inspectResponse(secure.replace('Cache-Control: no-store', 'Cache-Control: public, max-age=3600')), []);
  assert.deepEqual(inspectResponse('not HTTP'), ['No HTTP response']);
});
