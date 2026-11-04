# Moving the SafeNest DNS filter to Cloudflare (security audit H-1)

The DNS filter for iPhone/iPad/Mac currently runs as the Supabase Edge Function `dns-query`. It has no
sign-in (DNS clients cannot send one), so anyone can send it lookups, and every lookup is an Edge Function
invocation from the same Free-plan quota that payments and verification use.

## What this branch changes

- `handleDoh` takes an `allow(req)` limiter. Over the limit it answers **429** with `Retry-After: 60` and
  forwards nothing. If the limiter itself fails, the query goes through: a broken limiter must not take
  everyone's DNS down.
- **Edge Function:** a per-instance limiter, 1,200 lookups per minute per client address
  (`SAFENEST_DNS_LIMIT_PER_MIN` overrides it). Instances do not share counts, so this only blunts a flood.
- **Cloudflare Worker** (`cloudflare/dns/`): uses Cloudflare's rate-limiting binding `DNS_LIMITER`
  (1,200 per 60 s per address; counted per Cloudflare location and approximately). It now also applies the
  brand-mirror rule and serves `?profile=ios`, the same as the Edge Function.
- The limit is generous on purpose: carrier NAT puts many phones behind one address. A single phone
  browsing normally sends well under 100 lookups a minute.
- Bundle size: about 3.0 MB raw, **2.28 MB gzipped**, under the 3 MB Workers Free limit.

## Known gap

The Worker serves the **bundled** list (541k names) plus brand mirrors and bypass endpoints. It does not
read the latest published list from Supabase `blocklist_versions` the way the Edge Function does. The bundled
list is refreshed at each build, so this matters only between builds.

## Owner steps (in order)

1. Deploy once by hand: `npx wrangler deploy -c cloudflare/dns/wrangler.toml` (Cloudflare account with the
   `mysafenestbd.com` zone). If `namespace_id = "1001"` is already used in the account, pick another number.
2. Check it, from a computer:
   - `curl -s 'https://dns.mysafenestbd.com/dns-query'` returns the plain-text banner.
   - A lookup for a normal site resolves; a gambling site returns `0.0.0.0`
     (e.g. `dog @https://dns.mysafenestbd.com/dns-query example.com`, or any DoH client).
3. Reissue the iOS profile at the Worker:
   `node scripts/build-ios-dns-profile.mjs https://dns.mysafenestbd.com/dns-query`, change `DEFAULT_SERVER`
   in that script to match, run `npm test`, commit, deploy the website. The profile keeps its fixed UUIDs,
   so installing the new one replaces the old one.
4. Leave the Edge Function running for a few weeks so existing iPhones keep working, then retire it.

Do step 3 only after step 2 passes; a profile pointing at an undeployed Worker breaks DNS on every iPhone
that installs it.
