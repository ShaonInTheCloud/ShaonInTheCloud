// SafeNest DNS filter for iPhone/iPad/Mac (and any DoH client).
// URL: https://<project>.supabase.co/functions/v1/dns-query — installed through the SafeNest DNS profile.
// Blocks the bundled SafeNest gambling list, the latest published (signed) blocklist when there is one,
// and common encrypted-DNS bypass endpoints. verify_jwt = false (config.toml): DNS clients send no JWT.
import {createClient} from 'npm:@supabase/supabase-js@2.117.1';
import {BYPASS_DOMAINS, handleDoh, parseDomainList} from '../_shared/doh.mjs';
import {BUNDLED_DOMAINS} from './domains.generated.mjs';

const bundled = parseDomainList(BUNDLED_DOMAINS);
const bypass = new Set(BYPASS_DOMAINS);
let published = new Set<string>();
let publishedAt = 0;

const url = Deno.env.get('SUPABASE_URL');
const key = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
const db = url && key ? createClient(url, key, {auth: {persistSession: false, autoRefreshToken: false}}) : null;

async function refreshPublished() {
  if (!db || Date.now() - publishedAt < 600_000) return;
  publishedAt = Date.now();
  try {
    const {data} = await db.from('blocklist_versions').select('domains').order('version', {ascending: false}).limit(1).maybeSingle();
    if (data?.domains) published = new Set(data.domains.map((r: {domain: string}) => String(r.domain).toLowerCase()));
  } catch { /* keep the previous list; the bundled list always applies */ }
}

Deno.serve(req => handleDoh(req, {
  fetch,
  blocklists: async () => { refreshPublished(); return [bundled, published, bypass]; }, // refresh runs in the background
}));
