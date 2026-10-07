import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { inspectResponse } from './check-live-security.mjs';

const run = promisify(execFile);
const limit = 1024 * 1024;
const redirects = new Set([301, 302, 303, 307, 308]);
export function validateOrigin(value) {
  const url = new URL(value);
  if (url.protocol !== 'https:' || url.origin !== value || url.username || url.password)
    throw new Error('Expected a plain HTTPS origin');
  return url;
}

// GET only. No credentials, cookies, tokens, customer IDs or response bodies
// enter the report. Reject external redirects before requesting their target.
export async function publicGet(value, fetcher = curlGet) {
  const original = new URL(value);
  if (original.protocol !== 'https:' || original.username || original.password || original.search || original.hash)
    throw new Error('Invalid public URL');
  let url = original;
  for (let hop = 0; hop < 4; hop++) {
    const response = await fetcher(url.href);
    if (redirects.has(response.status)) {
      const next = new URL(response.headers.get('location'), url);
      if (!response.headers.has('location') || next.origin !== original.origin || next.username || next.password || next.search || next.hash)
        throw new Error('Unsafe redirect');
      url = next;
      continue;
    }
    const bytes = new Uint8Array(await response.arrayBuffer());
    if (bytes.length > limit) throw new Error('Response too large');
    return { status: response.status, headers: response.headers, body: new TextDecoder().decode(bytes), redirected: hop > 0 };
  }
  throw new Error('Too many redirects');
}

async function curlGet(url) {
  const dir = await mkdtemp(join(tmpdir(), 'safenest-public-audit-'));
  try {
    await run('curl', ['--silent', '--show-error', '--proto', '=https', '--max-time', '15',
      '--max-filesize', String(limit), '--dump-header', join(dir, 'headers'), '--output', join(dir, 'body'), url],
    { timeout: 17000, maxBuffer: 4096 });
    const raw = await readFile(join(dir, 'headers'), 'utf8');
    const block = raw.split(/\r?\n\r?\n/).filter(x => /^HTTP\/\S+\s+\d{3}/.test(x)).at(-1);
    if (!block) throw new Error('No response');
    const lines = block.split(/\r?\n/);
    const status = Number(lines.shift().match(/^HTTP\/\S+\s+(\d{3})/)[1]);
    const headers = new Headers();
    for (const line of lines) {
      const at = line.indexOf(':');
      if (at > 0) headers.append(line.slice(0, at), line.slice(at + 1).trim());
    }
    const body = await readFile(join(dir, 'body'));
    if (body.length > limit) throw new Error('Response too large');
    return new Response(body, { status, headers });
  } finally { await rm(dir, { recursive: true, force: true }); }
}

export async function auditSurfaces({ origin = 'https://mysafenestbd.com',
  backend = 'https://kflenmeizngmafwnwhgv.supabase.co', fetcher = curlGet } = {}) {
  validateOrigin(origin); validateOrigin(backend);
  const checks = [];
  const check = (surface, name, passed) => checks.push({ surface, check: name, passed: Boolean(passed) });
  for (const path of ['/', '/account', '/delete-account', '/android-captcha', '/support', '/sitemap.xml']) {
    try {
      const result = await publicGet(origin + path, fetcher);
      check(path, 'HTTP 200', result.status === 200);
      const sensitive = ['/account', '/delete-account', '/android-captcha'].includes(path);
      if (path !== '/sitemap.xml') {
        const raw = `HTTP/2 ${result.status}\r\n` + [...result.headers].map(([k,v]) => `${k}: ${v}`).join('\r\n') + '\r\n\r\n';
        const issues = inspectResponse(raw, { account: sensitive });
        check(path, 'Enforced response security headers', issues.length === 0);
        // Record fixed diagnostic labels only, never server-controlled strings.
        for (const issue of issues) if (!issue.startsWith('Expected HTTP')) check(path, issue, false);
      }
      if (path === '/account') {
        check(path, 'Login and registration forms', /id="login-form"/.test(result.body) && /id="signup-form"/.test(result.body));
        check(path, 'Development APK disclosure', /0\.4\.10/.test(result.body) && /development|পরীক্ষামূলক/.test(result.body));
      }
      if (path === '/android-captcha') {
        check(path, 'No redirect for Android challenge', !result.redirected);
        check(path, 'Challenge excluded from indexing', /name=["']robots["'][^>]*content=["'][^"']*noindex/i.test(result.body));
        check(path, 'Native challenge entry script', /src=["']android-captcha\.js["']/.test(result.body));
        check(path, 'Quiet challenge layout', !/liquid-pink\.js/.test(result.body));
      }
      if (path === '/support') check(path, 'No stale 0.4.0 download instructions', !/0\.4\.0|০\.৪\.০/.test(result.body));
      if (path === '/sitemap.xml') {
        const links = [...result.body.matchAll(/<loc>([^<]+)<\/loc>/g)].map(x => x[1]);
        check(path, 'Marketing URLs are present', links.length >= 5);
        check(path, 'Same-origin marketing URLs only', links.every(link => {
          try { const url = new URL(link); return url.origin === origin && !url.search && !url.hash && !/account|checkout|captcha/.test(url.pathname); }
          catch { return false; }
        }));
      }
    } catch { check(path, 'Public request completed safely', false); }
  }
  try {
    const result = await publicGet(backend + '/functions/v1/payment-catalogue', fetcher);
    const data = JSON.parse(result.body);
    check('payment-catalogue', 'Closed checkout returns no products', result.status === 200 && data.available === false && Array.isArray(data.products) && data.products.length === 0);
    check('payment-catalogue', 'Payment response is not cached', result.headers.get('cache-control')?.split(',').some(x => x.trim().toLowerCase() === 'no-store'));
  } catch { check('payment-catalogue', 'Public request completed safely', false); }
  return { checked_at: new Date().toISOString(), scope: 'Public GET checks only; not live Auth, trial, merchant, signing or phone acceptance',
    passed: checks.every(x => x.passed), checks };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try { const report = await auditSurfaces(); console.log(JSON.stringify(report, null, 2)); process.exitCode = report.passed ? 0 : 1; }
  catch { console.error('Public launch audit could not run'); process.exitCode = 1; }
}
