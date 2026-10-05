import { spawnSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';

// Read actual GET response headers; do not equate generated _headers with host enforcement.
export function inspectResponse(raw, { account = false } = {}) {
  const blocks = raw.split(/\r?\n\r?\n/).filter(block => /^HTTP\/\S+\s+\d{3}/.test(block));
  if (!blocks.length) return ['No HTTP response'];
  const lines = blocks.at(-1).split(/\r?\n/);
  const status = Number(lines.shift().match(/^HTTP\/\S+\s+(\d{3})/)[1]);
  const headers = new Map();
  for (const line of lines) {
    const at = line.indexOf(':');
    if (at < 1) continue;
    const name = line.slice(0, at).toLowerCase();
    const value = line.slice(at + 1).trim();
    headers.set(name, headers.has(name) ? `${headers.get(name)}, ${value}` : value);
  }
  const failures = [];
  if (status !== 200) failures.push(`Expected HTTP 200, received ${status}`);
  const hsts = headers.get('strict-transport-security') ?? '';
  if (!/(?:^|;)\s*max-age=([1-9][0-9]*)\b/i.test(hsts)) failures.push('HSTS is missing');
  if (headers.get('x-content-type-options')?.toLowerCase() !== 'nosniff') failures.push('nosniff is missing');
  const csp = headers.get('content-security-policy') ?? '';
  if (!/(?:^|;)\s*frame-ancestors\s+'none'\s*(?:;|$)/i.test(csp)) failures.push('Enforced CSP frame-ancestors is missing');
  if (!/(?:^|;)\s*object-src\s+'none'\s*(?:;|$)/i.test(csp)) failures.push('Enforced CSP object-src is missing');
  if (account) {
    if (!headers.get('cache-control')?.split(',').some(x => x.trim().toLowerCase() === 'no-store')) failures.push('Account no-store is missing');
    if (headers.get('referrer-policy')?.toLowerCase() !== 'no-referrer') failures.push('Account no-referrer is missing');
  }
  return failures;
}

export function checkLive(origin = 'https://mysafenestbd.com') {
  const base = new URL(origin);
  if (base.protocol !== 'https:' || base.username || base.password || base.pathname !== '/' || base.search || base.hash) {
    throw new Error('Supply an HTTPS origin without credentials, path or query');
  }
  let failed = false;
  for (const path of ['/', '/account', '/account.html', '/delete-account.html']) {
    const result = spawnSync('curl', ['--silent', '--show-error', '--location', '--max-redirs', '3', '--proto-redir', '=https', '--max-time', '15', '--dump-header', '-', '--output', '/dev/null', '--write-out', '\nCHECK_URL:%{url_effective}\n', new URL(path, base).href], { encoding: 'utf8', timeout: 17000 });
    const effective = result.stdout?.match(/CHECK_URL:(\S+)/)?.[1];
    const sameOrigin = effective && new URL(effective).origin === base.origin;
    const problems = result.status === 0 && sameOrigin ? inspectResponse(result.stdout, { account: path !== '/' }) : ['Request failed or redirected outside the expected origin'];
    failed ||= problems.length > 0;
    // Never print cookies, tokens or the raw response.
    console.log(`${path}: ${problems.length ? problems.join('; ') : 'PASS'}`);
  }
  return failed ? 1 : 0;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try { process.exitCode = checkLive(process.argv[2]); }
  catch { console.error('Invalid origin or live check failed'); process.exitCode = 1; }
}
