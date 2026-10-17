// SafeNest DNS-over-HTTPS filter (RFC 8484). Portable: runs in Supabase Edge (Deno) and Cloudflare Workers.
// Blocked names get a sinkhole answer (A 0.0.0.0 / AAAA ::, everything else an empty NOERROR);
// every other query is forwarded unchanged to an upstream DoH resolver.
// Privacy: queried names are never logged or stored.

export const DNS_MESSAGE = 'application/dns-message';
export const UPSTREAMS = ['https://cloudflare-dns.com/dns-query', 'https://dns.google/dns-query'];
const MAX_QUERY = 4096;
const BLOCK_TTL = 300;
const corsHeaders = {'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'content-type, accept'};

// Encrypted-DNS endpoints a browser or app could use to step around the filter.
export const BYPASS_DOMAINS = ['dns.google', 'dns64.dns.google', 'cloudflare-dns.com', 'one.one.one.one',
  'security.cloudflare-dns.com', 'family.cloudflare-dns.com', 'dns.quad9.net', 'doh.opendns.com',
  'dns.nextdns.io', 'dns.adguard-dns.com', 'doh.cleanbrowsing.org', 'dns.controld.com', 'freedns.controld.com',
  'doh.mullvad.net', 'dns.mullvad.net', 'doh.dns.sb', 'dns.alidns.com', 'doh.pub'];

export function parseDomainList(text) {
  const set = new Set();
  for (const line of String(text).split(/\r?\n/)) {
    const value = line.trim().toLowerCase().replace(/\.$/, '');
    if (value && !value.startsWith('#') && /^[a-z0-9.-]+$/.test(value) && value.includes('.')) set.add(value);
  }
  return set;
}

/** Returns the listed suffix that matches `name` (exact or parent), or null. */
export function matchBlocked(name, sets) {
  let host = String(name).toLowerCase().replace(/\.$/, '');
  if (!host.includes('.')) return null;
  while (host.includes('.')) {
    for (const set of sets) if (set.has(host)) return host;
    host = host.slice(host.indexOf('.') + 1);
  }
  return null;
}

/** Parses the header and first question of a DNS wire-format query. Throws on malformed input. */
export function parseQuery(bytes) {
  if (!(bytes instanceof Uint8Array) || bytes.length < 17 || bytes.length > MAX_QUERY) throw new Error('bad_length');
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const flags = view.getUint16(2);
  if (flags & 0x8000) throw new Error('not_a_query');
  if (view.getUint16(4) !== 1) throw new Error('one_question_only');
  const labels = [];
  let i = 12;
  for (;;) {
    if (i >= bytes.length) throw new Error('truncated');
    const len = bytes[i];
    if (len === 0) { i += 1; break; }
    if (len & 0xc0) throw new Error('compressed_question');
    if (i + 1 + len > bytes.length) throw new Error('truncated');
    labels.push(String.fromCharCode(...bytes.subarray(i + 1, i + 1 + len)));
    i += 1 + len;
    if (labels.join('.').length > 253) throw new Error('name_too_long');
  }
  if (i + 4 > bytes.length) throw new Error('truncated');
  return {id: view.getUint16(0), flags, name: labels.join('.').toLowerCase(), type: view.getUint16(i), qclass: view.getUint16(i + 2), questionEnd: i + 4};
}

/** Builds the sinkhole reply for a parsed query. */
export function blockedResponse(bytes, q) {
  const question = bytes.subarray(12, q.questionEnd);
  const rdata = q.qclass === 1 && q.type === 1 ? new Uint8Array(4) : q.qclass === 1 && q.type === 28 ? new Uint8Array(16) : null;
  const answerLength = rdata ? 12 + rdata.length : 0;
  const out = new Uint8Array(12 + question.length + answerLength);
  const view = new DataView(out.buffer);
  view.setUint16(0, q.id);
  // QR=1, opcode copied, AA=0, TC=0, RD copied, RA=1, RCODE=0 (NOERROR)
  view.setUint16(2, 0x8000 | (q.flags & 0x7800) | (q.flags & 0x0100) | 0x0080);
  view.setUint16(4, 1);
  view.setUint16(6, rdata ? 1 : 0);
  out.set(question, 12);
  if (rdata) {
    let o = 12 + question.length;
    view.setUint16(o, 0xc00c); view.setUint16(o + 2, q.type); view.setUint16(o + 4, 1);
    view.setUint32(o + 6, BLOCK_TTL); view.setUint16(o + 10, rdata.length);
    out.set(rdata, o + 12);
  }
  return out;
}

function base64UrlDecode(value) {
  if (!/^[A-Za-z0-9_-]+$/.test(value) || value.length > Math.ceil(MAX_QUERY * 4 / 3)) throw new Error('bad_base64');
  const b64 = value.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - value.length % 4) % 4);
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

const reply = (body, status = 200, extra = {}) => new Response(body, {status, headers: {...corsHeaders, 'Cache-Control': 'no-store', ...extra}});
const dnsReply = bytes => reply(bytes, 200, {'Content-Type': DNS_MESSAGE});

/**
 * Handles one DoH request.
 * deps.blocklists(): Promise<Set<string>[]> — sets of blocked registrable names
 * deps.fetch: fetch implementation for the upstream resolvers
 */
export async function handleDoh(req, deps) {
  if (req.method === 'OPTIONS') return reply(null, 204);
  let bytes, q;
  try {
    if (req.method === 'GET') {
      const dns = new URL(req.url).searchParams.get('dns');
      if (!dns) return reply('SafeNest DNS filter. Use DNS-over-HTTPS (RFC 8484).', 200, {'Content-Type': 'text/plain; charset=utf-8'});
      bytes = base64UrlDecode(dns);
    } else if (req.method === 'POST') {
      if (!(req.headers.get('content-type') ?? '').toLowerCase().startsWith(DNS_MESSAGE)) return reply('Unsupported media type', 415);
      const buf = await req.arrayBuffer();
      if (buf.byteLength > MAX_QUERY) return reply('Query too large', 413);
      bytes = new Uint8Array(buf);
    } else return reply('Method not allowed', 405, {Allow: 'GET, POST, OPTIONS'});
    q = parseQuery(bytes);
  } catch { return reply('Malformed DNS query', 400); }

  const sets = await deps.blocklists();
  if (matchBlocked(q.name, sets)) return dnsReply(blockedResponse(bytes, q));

  for (const upstream of deps.upstreams ?? UPSTREAMS) {
    try {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 4000);
      try {
        const res = await deps.fetch(upstream, {method: 'POST', headers: {'Content-Type': DNS_MESSAGE, Accept: DNS_MESSAGE}, body: bytes, signal: controller.signal});
        if (res.ok && (res.headers.get('content-type') ?? '').startsWith(DNS_MESSAGE)) return dnsReply(new Uint8Array(await res.arrayBuffer()));
      } finally { clearTimeout(timer); }
    } catch { /* try the next upstream */ }
  }
  return reply('Upstream resolvers unavailable', 502);
}
