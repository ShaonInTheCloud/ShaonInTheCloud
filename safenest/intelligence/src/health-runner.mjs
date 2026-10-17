// Smart blocklist checker — runs on a small always-on server (Oracle Cloud Always Free), talks to Supabase.
//
//   node src/health-runner.mjs seed              import the bundled 541k list (new names only; never resets states)
//   node src/health-runner.mjs run               check every domain that is due, then exit (run daily by systemd)
//   node src/health-runner.mjs summary           count domains per state
//   node src/health-runner.mjs export [dir]      write blocked.txt (for the apps / iPhone filter) and review.csv (for the team)
//
// Environment: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY (secret — server only),
//   HEALTH_CONCURRENCY (default 48), HEALTH_MAX_HOURS (default 20), HEALTH_BATCH (default 500).
import {createClient} from '@supabase/supabase-js';
import {mkdir, writeFile} from 'node:fs/promises';
import {decide} from './health.mjs';
import {safeGet} from './probe.mjs';
import {normalize} from './core.mjs';

const env = process.env;
const concurrency = Math.max(1, Math.min(256, Number(env.HEALTH_CONCURRENCY) || 48));
const maxMs = (Number(env.HEALTH_MAX_HOURS) || 20) * 3600000;
const batchSize = Math.max(50, Math.min(2000, Number(env.HEALTH_BATCH) || 500));

function client() {
  if (!env.SUPABASE_URL || !env.SUPABASE_SERVICE_ROLE_KEY) throw Error('Set SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY');
  return createClient(env.SUPABASE_URL, env.SUPABASE_SERVICE_ROLE_KEY, {auth: {persistSession: false, autoRefreshToken: false}});
}
const checked = async q => { const {data, error} = await q; if (error) throw Error(error.message); return data; };

const DEAD = /ENOTFOUND|ENODATA|EAI_AGAIN|ECONNREFUSED|ECONNRESET|EHOSTUNREACH|ENETUNREACH|ETIMEDOUT|timeout|Nonpublic destination|socket hang up|certificate|SSL|TLS|EPROTO/i;
const ALIVE_NO_TEXT = /Nontext response|Compressed response|Response too large/i;

/** Visits a domain over HTTPS (then HTTP), following up to 5 redirects. Never throws. */
export async function checkDomain(domain, get = safeGet) {
  const visit = async start => {
    let url = start, status = null;
    for (let step = 0; step < 5; step++) {
      const res = await get(url, {maxBytes: 400000, timeout: 9000});
      status = res.status;
      if (res.status >= 300 && res.status < 400 && res.location) {
        const next = new URL(res.location, url);
        normalize(next.href); // rejects IPs, ports, credentials and private names
        url = next.href; continue;
      }
      const title = (res.text.match(/<title[^>]*>([\s\S]*?)<\/title>/i) || [])[1] || '';
      const text = res.text.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, ' ').replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi, ' ').replace(/<[^>]*>/g, ' ');
      return {alive: true, status, finalHost: new URL(url).hostname, title, text};
    }
    return {alive: true, status, finalHost: new URL(url).hostname, title: '', text: '', error: 'too many redirects'};
  };
  for (const scheme of ['https://', 'http://']) {
    try { return await visit(scheme + domain); }
    catch (error) {
      const message = String(error?.code || '') + ' ' + String(error?.message || error);
      if (ALIVE_NO_TEXT.test(message)) return {alive: true, status: null, title: '', text: '', error: message.trim().slice(0, 200)};
      if (!DEAD.test(message)) return {alive: true, status: null, title: '', text: '', error: message.trim().slice(0, 200)}; // answered, but oddly
      if (/ENOTFOUND|ENODATA|Nonpublic destination/i.test(message) || scheme === 'http://') return {alive: false, error: message.trim().slice(0, 200)};
      // HTTPS failed in a way HTTP might not (bad certificate, port 443 closed): try plain HTTP once.
    }
  }
  return {alive: false, error: 'unreachable'};
}

/** Runs fn over items with at most `limit` in flight. */
async function pool(items, limit, fn) {
  let next = 0;
  await Promise.all(Array.from({length: Math.min(limit, items.length)}, async () => {
    while (next < items.length) { const item = items[next++]; await fn(item); }
  }));
}

async function seed(db) {
  const {compile} = await import('../../scripts/build-dns-domains.mjs');
  const {list} = compile();
  let sent = 0;
  for (let i = 0; i < list.length; i += 1000) {
    const rows = list.slice(i, i + 1000).map(domain => ({domain, source: 'bundled'}));
    await checked(db.from('domain_health').upsert(rows, {onConflict: 'domain', ignoreDuplicates: true}));
    sent += rows.length;
    if (sent % 50000 === 0) console.log(`seed: ${sent}/${list.length}`);
  }
  console.log(`seed: ${list.length} names offered; existing rows and their states were left unchanged.`);
}

async function run(db) {
  const started = Date.now();
  const totals = {checked: 0, active: 0, dormant: 0, archived: 0, parked: 0, review: 0, discovered: 0};
  while (Date.now() - started < maxMs) {
    const due = await checked(db.from('domain_health')
      .select('domain,state,dead_since,fail_streak,low_streak,score,signals,reviewed_by_team')
      .lte('next_check', new Date().toISOString()).order('next_check').limit(batchSize));
    if (!due.length) break;
    const updates = [], discovered = [];
    await pool(due, concurrency, async row => {
      const {update, discovered: found} = decide(row, await checkDomain(row.domain));
      updates.push(update); totals.checked++; totals[update.state] = (totals[update.state] || 0) + 1;
      if (found) { try { discovered.push({domain: normalize(found.domain).domain, source: found.source}); } catch {} }
    });
    await checked(db.from('domain_health').upsert(updates, {onConflict: 'domain'}));
    if (discovered.length) {
      await checked(db.from('domain_health').upsert(discovered, {onConflict: 'domain', ignoreDuplicates: true}));
      totals.discovered += discovered.length;
    }
    console.log(`run: ${totals.checked} checked · ${JSON.stringify(totals)}`);
  }
  console.log(`run finished in ${Math.round((Date.now() - started) / 60000)} min: ${JSON.stringify(totals)}`);
}

async function pageAll(db, build) {
  const out = [];
  for (let from = 0; ; from += 1000) {
    const page = await checked(build().range(from, from + 999));
    out.push(...page);
    if (page.length < 1000) return out;
  }
}

async function exportLists(db, dir = 'health-export') {
  await mkdir(dir, {recursive: true});
  const blocked = await pageAll(db, () => db.from('domain_health_blocked').select('domain').order('domain'));
  await writeFile(`${dir}/blocked.txt`, `# SafeNest live blocklist, ${new Date().toISOString()} — ${blocked.length} names\n` + blocked.map(r => r.domain).join('\n') + '\n');
  const review = await pageAll(db, () => db.from('domain_health').select('domain,source,score,signals,http_status,redirect_to,last_checked').eq('state', 'review').order('domain'));
  const csv = ['domain,source,score,signals,http_status,redirect_to,last_checked',
    ...review.map(r => [r.domain, r.source, r.score ?? '', (r.signals || []).join(' '), r.http_status ?? '', r.redirect_to ?? '', r.last_checked ?? ''].map(v => `"${String(v).replace(/"/g, '""')}"`).join(','))];
  await writeFile(`${dir}/review.csv`, csv.join('\n') + '\n');
  console.log(`export: ${blocked.length} blocked → ${dir}/blocked.txt; ${review.length} for review → ${dir}/review.csv`);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const command = process.argv[2];
  const db = client();
  if (command === 'seed') await seed(db);
  else if (command === 'run') await run(db);
  else if (command === 'summary') console.table(await checked(db.rpc('domain_health_summary')));
  else if (command === 'export') await exportLists(db, process.argv[3]);
  else { console.error('Usage: node src/health-runner.mjs seed | run | summary | export [dir]'); process.exit(2); }
}
