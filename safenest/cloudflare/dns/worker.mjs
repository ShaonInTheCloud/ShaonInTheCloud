// Same SafeNest DNS filter as supabase/functions/dns-query, packaged as a Cloudflare Worker.
// This is the intended production home (security audit H-1): Worker requests do not draw on the Supabase
// Edge Function quota that payments and verification share, and Cloudflare's rate-limiting binding caps each
// client address (counted per Cloudflare location).
// Deploy: npx wrangler deploy -c cloudflare/dns/wrangler.toml   → https://dns.mysafenestbd.com/dns-query
import {BYPASS_DOMAINS, clientKey, handleDoh, loadCompressedList} from '../../supabase/functions/_shared/doh.mjs';
import {BUNDLED_BRANDS, BUNDLED_GZIP_BASE64} from '../../supabase/functions/dns-query/domains.generated.mjs';
import {IOS_PROFILE} from '../../supabase/functions/dns-query/profile.generated.mjs';

let bundled;
const bypass = new Set(BYPASS_DOMAINS);

/** Builds the Worker; `fetcher` is injectable for tests. */
export function makeWorker(fetcher = (u, i) => fetch(u, i)) {
  return {
    async fetch(req, env = {}) {
      if (req.method === 'GET' && new URL(req.url).searchParams.get('profile') === 'ios')
        return new Response(IOS_PROFILE, {headers: {'Content-Type': 'application/x-apple-aspen-config',
          'Content-Disposition': 'attachment; filename="safenest-dns.mobileconfig"', 'Cache-Control': 'no-cache'}});
      return handleDoh(req, {
        fetch: fetcher,
        brands: BUNDLED_BRANDS, // same brand-mirror rule as the Edge Function and the Android app
        // DNS_LIMITER is the [[ratelimits]] binding in wrangler.toml. Without it (local dev) nothing is limited.
        allow: env.DNS_LIMITER ? async r => (await env.DNS_LIMITER.limit({key: clientKey(r)})).success : undefined,
        blocklists: async () => [await (bundled ??= loadCompressedList(BUNDLED_GZIP_BASE64)), bypass],
      });
    },
  };
}

export default makeWorker();
