// Same SafeNest DNS filter as supabase/functions/dns-query, packaged as a Cloudflare Worker
// (100k requests/day on the free plan — better suited than Edge Functions once many phones use it).
// Deploy: npx wrangler deploy -c cloudflare/dns/wrangler.toml   → https://dns.mysafenestbd.com/dns-query
import {BYPASS_DOMAINS, handleDoh, loadCompressedList} from '../../supabase/functions/_shared/doh.mjs';
import {BUNDLED_GZIP_BASE64} from '../../supabase/functions/dns-query/domains.generated.mjs';

let bundled;
const bypass = new Set(BYPASS_DOMAINS);
export default {fetch: req => handleDoh(req, {fetch: (u, i) => fetch(u, i), blocklists: async () => [await (bundled ??= loadCompressedList(BUNDLED_GZIP_BASE64)), bypass]})};
