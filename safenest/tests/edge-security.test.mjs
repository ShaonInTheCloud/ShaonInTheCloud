import test from 'node:test';
import assert from 'node:assert/strict';
import { prepareEdgeSecurity } from '../scripts/prepare-edge-security.mjs';
import { contentSecurityPolicy, sensitiveRoutes, securityHeaders } from '../scripts/security-headers.mjs';

test('draft is disabled, host-scoped, ordered, and does not pretend headers bypass the edge cache', () => {
  const plan = prepareEdgeSecurity();
  assert.equal(plan.status, 'DRAFT_NOT_APPLIED');
  const cache = plan.phases.http_request_cache_settings[0];
  assert.equal(cache.action, 'set_cache_settings');
  assert.deepEqual(cache.action_parameters, { cache: false });
  const [baseline, sensitive, bundle] = plan.phases.http_response_headers_transform;
  assert.match(baseline.expression, /http.host eq "mysafenestbd.com" and ssl/);
  assert.equal(baseline.action_parameters.headers['content-security-policy'].value, contentSecurityPolicy('https://kflenmeizngmafwnwhgv.supabase.co'));
  assert.equal(sensitive.expression, cache.expression);
  assert.equal(sensitive.action_parameters.headers['cache-control'].value, 'no-store');
  assert.equal(sensitive.action_parameters.headers['referrer-policy'].value, 'no-referrer');
  assert.match(bundle.expression, /"\/account.js"$/);
  for (const rule of Object.values(plan.phases).flat()) {
    assert.equal(rule.enabled, false);
    assert.match(rule.expression, /^http.host eq "mysafenestbd.com"/);
    for (const header of Object.values(rule.action_parameters.headers ?? {})) assert.equal(header.operation, 'set');
  }
  assert.ok(!JSON.stringify(plan).includes('includeSubDomains'));
  assert.ok(!JSON.stringify(plan).includes('preload'));
});
test('all sensitive aliases share static, browser and edge-cache protection; public downloads are not disabled', () => {
  const plan = prepareEdgeSecurity();
  const headers = securityHeaders('https://example.supabase.co');
  const expression = plan.phases.http_request_cache_settings[0].expression;
  assert.equal(sensitiveRoutes.length, 18);
  assert.equal(new Set(sensitiveRoutes).size, sensitiveRoutes.length);
  for (const route of sensitiveRoutes) {
    assert.ok(expression.includes(JSON.stringify(route)));
    assert.ok(headers.includes(`${route}\n  Cache-Control: no-store\n  Referrer-Policy: no-referrer\n`));
  }
  assert.ok(!expression.includes('/downloads'));
  assert.ok(!expression.includes('/pricing'));
});
test('host and backend reject header/rule injection, wildcards, credentials and protocol downgrade', () => {
  for (const host of ['*', '*.mysafenestbd.com', 'mysafenestbd.com" or true', 'https://mysafenestbd.com', 'MySafeNestBD.com', 'mysafenestbd.com/path', '-bad.com'])
    assert.throws(() => prepareEdgeSecurity(host));
  for (const backend of ['http://example.com', 'https://user:pass@example.com', 'https://example.com/\nX-Test: yes'])
    assert.throws(() => prepareEdgeSecurity('mysafenestbd.com', backend));
});
