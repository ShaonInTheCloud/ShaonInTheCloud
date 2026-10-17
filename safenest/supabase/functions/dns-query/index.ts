// SafeNest DNS filter for iPhone/iPad/Mac (and any DoH client).
// URL: https://<project>.supabase.co/functions/v1/dns-query — installed through the SafeNest DNS profile.
// Blocks the bundled SafeNest gambling list (541k names, same sources as the Android app), the latest published (signed) blocklist when there is one,
// and common encrypted-DNS bypass endpoints. verify_jwt = false (config.toml): DNS clients send no JWT.
import {createClient} from 'npm:@supabase/supabase-js@2.117.1';
import {BYPASS_DOMAINS, handleDoh, loadCompressedList} from '../_shared/doh.mjs';
import {BUNDLED_GZIP_BASE64} from './domains.generated.mjs';

// Decoded once per instance (~0.2 s, ~25 MB); every request awaits the same promise.
const bundled = loadCompressedList(BUNDLED_GZIP_BASE64);
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
  blocklists: async () => { refreshPublished(); return [await bundled, published, bypass]; }, // refresh runs in the background
}));
