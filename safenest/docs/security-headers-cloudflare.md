# Workers Static Assets migration — prepared, not deployed

The October 25 handoff supersedes the old origin-proxy instructions. Cloudflare edge
addresses do not prove zone ownership: the existing `zone_name` route cannot attach
while mysafenestbd.com is outside the owner's account. Static Assets supplies the
origin, but **production still requires an active zone in that same account**.

## Source preservation is a deployment prerequisite

Live Sites v45 is commit `4081fe8ac308609a7cf8b4aa5ac79f2080f3847c`, deployment
`appgdep_6add6ae0c6608191b4e030cf8a4cca62`. It includes newer auth, CAPTCHA and download
functionality absent from GitHub. Do not deploy the current GitHub website over it.
`cloudflare/source-reconciliation.json` deliberately remains `blocked`.

Before any preview or production upload:
1. Export and retain the exact Sites source, build settings, v45 artifact and checksums.
   Check whether a newer Sites release now exists; if so, update the prerequisite and
   reconcile that release too. Do not replace the snapshot with a guessed GitHub tree.
2. Reconcile the Sites auth, confirmation/resend, recovery, support persistence,
   CAPTCHA, download targets and public APK with GitHub's Global/Nest Run/iOS pages.
   Preserve disabled checkout, provider switches and existing backend configuration.
3. Record a reviewed source diff and browser acceptance evidence. Match Supabase URL,
   publishable key, CAPTCHA enablement and Turnstile key to the live source; default
   build values are not proof of parity. Verify download checksums and MIME types.
4. Build the reconciled source with those exact settings. Run the checks below and
   `node scripts/hosting-preflight.mjs digest`. Put that SHA-256 in `assetSha256`, a
   report reference in `evidence`, and only then set `status` to `reconciled`.
   The digest covers all build files, including generated Worker code and policy.
5. Commit and review the resulting change. Configure protected GitHub environments
   `safenest-hosting-preview` and `safenest-hosting-production` with required owner
   review and prevent self-review. After explicit approval for the selected target,
   set that environment's `HOSTING_APPROVED_SHA` to the full reviewed Git commit.
   A different commit or artifact requires renewed review. The manifest is a human
   attestation backed by a digest, not automatic proof of semantic source parity.

No approval variable or environment has been configured by this change.

## Configuration and local verification

`cloudflare/wrangler.toml` has an unreachable default (no routes, workers.dev or
preview URLs), a separate workers.dev preview, and an explicit production Custom
Domain. It does not proxy requests back to the existing domain. `ASSETS.fetch`
serves the built site. `run_worker_first=true` applies the generated header policy
from `scripts/security-headers.mjs` to every returned response, including redirects,
404s and caught 503s. The build still emits the existing `_headers`; the wrapper uses
`Headers.set` so overlapping static rules cannot leave duplicate account policies.
Generated Worker scripts are excluded from public asset uploads via `.assetsignore`.
No SPA fallback is enabled: unknown downloads must not return a successful HTML page.

From `safenest/`:

```sh
npm ci
npm test
npm run build
npx --no-install wrangler deploy --dry-run --config cloudflare/wrangler.toml --env preview
npx --no-install wrangler deploy --dry-run --config cloudflare/wrangler.toml --env production
node scripts/check-local-hosting.mjs
```

The local response test uses the existing `inspectResponse` checker on actual
workerd responses. It also checks redirects, HEAD, private generated scripts, 404
headers and the iOS profile MIME type. Local HTTP can verify the HSTS header text,
not browser HTTPS enforcement or production TLS. It is not live auth acceptance.

The manual workflow defaults to `validate` with no hosting credentials or uploads.
`preflight` performs GET-only Cloudflare API checks. `deploy` additionally requires
the source manifest, matching artifact digest and exact-SHA approval before upload.
Production deploy attaches the custom domain: it is a cutover, not a harmless check.
The workflow serializes operations per target and does not change nameservers,
DNS records directly, mail records, backend migrations or payment settings.

Required hosting access: `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`, and for
production `CLOUDFLARE_ZONE_ID`. Scope the token to the intended account/zone with
Account Settings Read, Zone Read, Workers Scripts Edit and the permissions needed
for Workers Custom Domains (Workers Routes Edit / DNS Edit as required by Cloudflare).
The preflight proves readable account identity and active, unpaused zone ownership;
it does not prove write permission, DNS completeness, registrar control or TLS readiness.
Never print tokens or put them in command arguments.

## DNS/mail preservation and cutover — requires owner approval

1. Save a full export of authoritative DNS and registrar settings, not only a public
   DNS lookup. Record all names, values, priorities, TTLs and proxy flags: A/AAAA,
   CNAME, MX, TXT (SPF, all Brevo DKIM selectors, DMARC, verification), SRV, CAA,
   delegated subdomains and any other records. Record current nameservers
   (`ns01/ns02.dns.nexus` per handoff; verify afresh), DNSSEC/DS and current web targets.
   Keep Sites v45 available and verify the exact restoration method with its host.
2. Obtain approval for the concrete DNS diff and rollback plan. Import all records
   to the owner's Cloudflare zone and compare the export record by record. Keep mail
   hosts DNS-only; preserve MX priorities and SPF/DKIM/DMARC values. Avoid duplicate
   SPF records. Do not recreate the site by copying observed Cloudflare edge IPs.
3. Plan DNSSEC with the registrar: stale DS records can break resolution during a
   nameserver move. Arrange DS removal/replacement and re-enable signed delegation
   following the provider's migration procedure. Lower relevant TTLs in advance,
   accounting for old cached TTLs; do not promise instant rollback.
4. Test an approved preview first, with its real HTTPS origin. Run
   `node scripts/check-live-security.mjs https://<actual-preview-host>` and test
   signup/confirmation/resend, login, recovery, Turnstile and support in a browser.
   Approve any necessary auth redirect / Turnstile hostname additions separately;
   never disable CAPTCHA to pass a preview test. Check APK/profile bytes and hashes.
5. After explicit owner approval, move delegation, verify authoritative DNS and mail
   parity, and wait for the zone to be active. Run production `preflight`. Confirm
   TLS certificate issuance and decide whether www is needed (not attached here).
6. Approve the exact production commit, then dispatch `deploy` / `production`.
   Custom Domain attachment can replace the website DNS target. Do not alter mail.
   Keep all checkout/providers disabled. Run the existing live checker, browser
   acceptance, downloads and real inbound/outbound mail tests (including SPF/DKIM
   authentication). Monitor errors and confirmation/recovery delivery.

## Rollback

Before cutover, retain Sites v45/source/settings, DNS export, registrar nameservers
and DS data, previous Worker version IDs (if any), and the tested host restoration
procedure. Agree on rollback triggers: auth/CAPTCHA failure, missing downloads,
header failures, TLS trouble, or mail delivery regression.

* A Worker-only regression after an established successful deployment: restore the
  recorded known-good Worker version using Wrangler rollback with the production
  config, then repeat header/browser/download checks. Check asset-version rollback
  availability beforehand. No previous Worker version exists on the first cutover.
* First-cutover failure: remove the new Custom Domain binding, restore the exact
  original website DNS targets and host mapping from the snapshot. Removing the
  binding alone does not restore the old host. Keep mail records untouched.
* If delegation itself must be reverted, restore old nameservers and coordinate the
  matching DNSSEC/DS state with the registrar. Retain the old zone and hosting during
  propagation. Verify both authoritative answers and recursive resolver results,
  TLS, inbound/outbound mail and the original site after rollback.

Restoring v45 also restores its known missing-header limitation; report that
explicitly rather than claiming rollback solves the security issue.

## Evidence boundaries

Configuration dry-runs and local runtime checks establish package/routing/header
behavior only. Actual Cloudflare account access, zone ownership, token write scopes,
DNS/mail export, source reconciliation, preview acceptance, TLS and production
headers remain unverified until the approved hosting steps are performed.

References:
- https://developers.cloudflare.com/workers/static-assets/headers/
- https://developers.cloudflare.com/workers/static-assets/binding/
- https://developers.cloudflare.com/workers/configuration/routing/custom-domains/
- https://developers.cloudflare.com/api/resources/zones/methods/get/

### Local verification on 26 October 2026

- Final full suite: 109/109 tests passed, including preflight and Static Assets regressions.
- Build and Wrangler 4.149.0 preview/production dry-runs passed (96 static files).
- Real local workerd responses passed the existing policy checker on six page paths,
  plus redirect, HEAD, iOS MIME, 404 and generated-source exclusion checks.
- This container denies Node network-interface enumeration. The local runtime run used
  a temporary, uncommitted Node preload returning only the loopback interface; the
  Worker, asset configuration and response assertions were unchanged. The normal
  workflow command remains unmodified for GitHub's runner.
- No Cloudflare credentials were used, no remote preflight was claimed, and nothing
  was uploaded or attached to a domain. The committed reconciliation gate is blocked.
