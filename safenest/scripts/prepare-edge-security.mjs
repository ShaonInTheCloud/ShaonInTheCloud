import { pathToFileURL } from 'node:url';
import { contentSecurityPolicy, sensitiveRoutes } from './security-headers.mjs';

// Draft only: no API, credentials, DNS changes, deployment or paid resources.
// Ordered fragments must be appended to, not replace, existing entrypoints.
export function prepareEdgeSecurity(host = 'mysafenestbd.com', backend = 'https://kflenmeizngmafwnwhgv.supabase.co') {
  if (!/^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$/.test(host))
    throw new Error('Supply a plain lowercase hostname');
  const scoped = `http.host eq ${JSON.stringify(host)}`;
  const privatePaths = `${scoped} and http.request.uri.path in {${sensitiveRoutes.map(x => JSON.stringify(x)).join(' ')}}`;
  const set = value => ({ operation: 'set', value });
  return {
    status: 'DRAFT_NOT_APPLIED', host,
    prerequisites: ['Owner-approved hosting or proxied-zone setup; no DNS migration is authorized by this draft',
      'Export existing rules and retain their order; confirm origin/TLS/CAA/mail records',
      'Enable and verify cache bypass separately from browser Cache-Control',
      'Review full CSP compatibility, then verify actual public GET responses'],
    phases: {
      http_request_cache_settings: [{
        ref: 'safenest_sensitive_cache_bypass', description: 'SafeNest sensitive routes: bypass edge cache',
        enabled: false, expression: privatePaths, action: 'set_cache_settings', action_parameters: { cache: false }
      }],
      http_response_headers_transform: [{
        ref: 'safenest_security_baseline', description: 'SafeNest HTTPS response security baseline',
        enabled: false, expression: `${scoped} and ssl`, action: 'rewrite', action_parameters: { headers: {
          'strict-transport-security': set('max-age=31536000'),
          'x-content-type-options': set('nosniff'), 'x-frame-options': set('DENY'),
          'referrer-policy': set('strict-origin-when-cross-origin'),
          'permissions-policy': set('camera=(), microphone=(), geolocation=()'),
          'content-security-policy': set(contentSecurityPolicy(backend))
        } }
      }, {
        ref: 'safenest_sensitive_browser_policy', description: 'SafeNest sensitive routes: browser no-store and no-referrer',
        enabled: false, expression: privatePaths, action: 'rewrite', action_parameters: { headers: {
          'cache-control': set('no-store'), 'referrer-policy': set('no-referrer')
        } }
      }, {
        ref: 'safenest_account_bundle_revalidate', description: 'SafeNest account bundle: revalidate cached clients',
        enabled: false, expression: `${scoped} and http.request.uri.path eq "/account.js"`, action: 'rewrite',
        action_parameters: { headers: { 'cache-control': set('no-cache') } }
      }]
    }
  };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try { console.log(JSON.stringify(prepareEdgeSecurity(process.argv[2], process.argv[3]), null, 2)); }
  catch { console.error('Invalid edge security configuration'); process.exitCode = 1; }
}
