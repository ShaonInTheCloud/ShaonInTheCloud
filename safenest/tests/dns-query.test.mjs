import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {BYPASS_DOMAINS, blockedResponse, handleDoh, matchBlocked, parseDomainList, parseQuery} from '../supabase/functions/_shared/doh.mjs';
import {BUNDLED_COUNT, BUNDLED_DOMAINS} from '../supabase/functions/dns-query/domains.generated.mjs';
import {profileXml, DEFAULT_SERVER} from '../scripts/build-ios-dns-profile.mjs';

function query(name, type = 1, id = 0x1234) {
  const labels = name.split('.').flatMap(l => [l.length, ...Buffer.from(l)]);
  return new Uint8Array([id >> 8, id & 255, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0, ...labels, 0, type >> 8, type & 255, 0, 1]);
}
const b64url = bytes => Buffer.from(bytes).toString('base64url');
const bundled = parseDomainList(BUNDLED_DOMAINS);
const sets = [bundled, new Set(BYPASS_DOMAINS)];
function deps(overrides = {}) {
  const calls = [];
  return {calls, deps: {blocklists: async () => sets, fetch: async (url, init) => {
    calls.push({url, init});
    const q = new Uint8Array(init.body); const r = new Uint8Array(q); r[2] |= 0x80; r[3] |= 0x80;
    return new Response(r, {headers: {'content-type': 'application/dns-message'}});
  }, ...overrides}};
}
const post = bytes => new Request('https://x/dns-query', {method: 'POST', headers: {'content-type': 'application/dns-message'}, body: bytes});

test('bundled list is the full SafeNest data file', () => {
  assert.ok(BUNDLED_COUNT > 30000);
  assert.equal(bundled.size, BUNDLED_COUNT);
  assert.ok(bundled.has('1xbet.com') || [...bundled].some(d => d.includes('1xbet')));
});

test('matching covers exact names and subdomains, not lookalikes', () => {
  const s = [new Set(['bet365.com'])];
  assert.equal(matchBlocked('bet365.com', s), 'bet365.com');
  assert.equal(matchBlocked('www.sports.BET365.com.', s), 'bet365.com');
  assert.equal(matchBlocked('notbet365.com', s), null);
  assert.equal(matchBlocked('google.com', s), null);
  assert.equal(matchBlocked('localhost', s), null);
});

test('parses a wire-format query', () => {
  const q = parseQuery(query('www.Example.com', 28));
  assert.deepEqual([q.id, q.name, q.type, q.qclass], [0x1234, 'www.example.com', 28, 1]);
  assert.throws(() => parseQuery(new Uint8Array(5)));
  const response = query('a.com'); response[2] |= 0x80;
  assert.throws(() => parseQuery(response), /not_a_query/);
});

test('blocked A and AAAA get sinkhole answers; other types get empty NOERROR', () => {
  for (const [type, len] of [[1, 4], [28, 16]]) {
    const bytes = query('casino.bet365.com', type), out = blockedResponse(bytes, parseQuery(bytes));
    const v = new DataView(out.buffer);
    assert.equal(v.getUint16(0), 0x1234);
    assert.equal(v.getUint16(2) & 0x800f, 0x8000, 'response, NOERROR');
    assert.equal(v.getUint16(2) & 0x0100, 0x0100, 'RD echoed');
    assert.equal(v.getUint16(6), 1);
    assert.equal(v.getUint16(out.length - len - 2), len);
    assert.ok(out.subarray(out.length - len).every(b => b === 0));
  }
  const https = query('bet365.com', 65), out = blockedResponse(https, parseQuery(https));
  assert.equal(new DataView(out.buffer).getUint16(6), 0);
});

test('POST: a known gambling domain is answered locally and never forwarded', async () => {
  const name = [...bundled][0];
  const {calls, deps: d} = deps();
  const res = await handleDoh(post(query(`www.${name}`)), d);
  assert.equal(res.status, 200);
  assert.equal(res.headers.get('content-type'), 'application/dns-message');
  const out = new Uint8Array(await res.arrayBuffer());
  assert.deepEqual([...out.subarray(out.length - 4)], [0, 0, 0, 0]);
  assert.equal(calls.length, 0);
});

test('GET (?dns=) for a normal domain is forwarded upstream', async () => {
  const {calls, deps: d} = deps();
  const res = await handleDoh(new Request(`https://x/dns-query?dns=${b64url(query('wikipedia.org'))}`), d);
  assert.equal(res.status, 200);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, 'https://cloudflare-dns.com/dns-query');
});

test('encrypted-DNS bypass endpoints are blocked', async () => {
  const {calls, deps: d} = deps();
  const res = await handleDoh(post(query('dns.google')), d);
  assert.equal(res.status, 200);
  assert.equal(calls.length, 0);
});

test('falls back to the second upstream, then 502', async () => {
  let n = 0;
  const {deps: d} = deps({fetch: async (url, init) => {
    n++; if (url.includes('cloudflare')) throw new Error('down');
    return new Response(new Uint8Array(init.body), {headers: {'content-type': 'application/dns-message'}});
  }});
  assert.equal((await handleDoh(post(query('example.org')), d)).status, 200);
  assert.equal(n, 2);
  const {deps: dead} = deps({fetch: async () => { throw new Error('down'); }});
  assert.equal((await handleDoh(post(query('example.org')), dead)).status, 502);
});

test('rejects malformed and wrong-type requests', async () => {
  const {deps: d} = deps();
  assert.equal((await handleDoh(new Request('https://x/dns-query?dns=***'), d)).status, 400);
  assert.equal((await handleDoh(new Request('https://x/dns-query', {method: 'POST', headers: {'content-type': 'text/plain'}, body: 'x'}), d)).status, 415);
  assert.equal((await handleDoh(new Request('https://x/dns-query', {method: 'PUT'}), d)).status, 405);
  assert.equal((await handleDoh(new Request('https://x/dns-query'), d)).status, 200, 'plain GET explains the endpoint');
});

test('iOS profile points at the DoH endpoint and is reinstall-safe', () => {
  const xml = profileXml();
  assert.match(xml, /<string>com\.apple\.dnsSettings\.managed<\/string>/);
  assert.match(xml, /<key>DNSProtocol<\/key>\s*<string>HTTPS<\/string>/);
  assert.ok(xml.includes(`<string>${DEFAULT_SERVER}</string>`));
  assert.equal(readFileSync(new URL('../website/ios/safenest-dns.mobileconfig', import.meta.url), 'utf8'), xml, 'committed profile is current');
  assert.throws(() => profileXml('http://insecure.example/dns-query'));
});

test('dns-query skips JWT verification (DNS clients send none)', () => {
  const config = readFileSync(new URL('../supabase/config.toml', import.meta.url), 'utf8');
  assert.match(config, /\[functions\.dns-query\]\s*\nverify_jwt\s*=\s*false/);
});
