# Hosting headers and owner security — 7 October 2026

Status: preparation and read-only preflight, NOT completed production hardening.

## Hosting

The live Site remains version 41, source `e9642242aa113e40a73f394f29a2e3bdbfa2c39e`. Actual GETs still lack the prepared response security headers. `_headers` is ignored by this static host; meta CSP cannot enforce HSTS, frame ancestors, MIME hardening or cache policy. The connected Cloudflare account returns zero zones for `mysafenestbd.com`. No DNS records, nameservers, TLS settings, hosting audience or live build were changed. A same-host Worker conversion was investigated but not deployed: the current supported static configuration has no custom header middleware, and complete asset/APK preservation through a Worker was not established.

`node scripts/prepare-edge-security.mjs` emits disabled, credential-free rule fragments. This is not an API request body or a deployment script. It covers:

- HTTPS baseline: one-year HSTS (no subdomain/preload commitment), nosniff, frame denial, restricted CSP, referrer and permissions policies.
- Browser no-store/no-referrer on all 18 extensionless, trailing-slash and `.html` variants of account, delete-account, Android challenge and legacy login/dashboard/checkout routes.
- Separate request-phase edge-cache bypass on those same paths. Changing a response `Cache-Control` header alone does not change Cloudflare cache eligibility.
- Account JS revalidation, leaving marketing pages and the public development APK outside sensitive-route cache bypass.

Before applying: obtain an owner-approved hosting/zone solution; inspect the real zone and existing phase entrypoints; export current rules; verify origin, TLS, CAA and mail records; append rules without overwriting unrelated entries; put the baseline before sensitive overrides. Review overlapping rules in both phases. Enable cache bypass before enabling sensitive header rules. Check and, if necessary, separately approve a targeted purge of preexisting sensitive objects. Confirm all public sensitive routes, query-bearing recovery links, resource loading, downloads and native CAPTCHA work. Test the full CSP before calling this production-ready. Remote rule-expression validation has not occurred because there is no accessible target zone.

`node scripts/check-live-security.mjs` checks 21 real GET routes including all aliases and requires actual response headers. Active routes must return 200; intentionally removed login/dashboard aliases may return 404 but their error responses still require security/no-store headers. The live audit found those six legacy aliases 404; no new pages were added. It logs fixed diagnostic labels only, not cookies, tokens or bodies. It now rejects sub-one-year HSTS and permissive first CSP directives masked by duplicate restrictive directives. Also run `node scripts/check-launch-surfaces.mjs`. A positive `Cache-Control` audit is not proof of edge-cache bypass: verify active cache rules/trace and repeated requests do not produce a cached HIT for sensitive responses. HEAD, browser DevTools, real Auth recovery and Android acceptance remain additional checks.

Rollback: disable only the SafeNest rules by their recorded IDs/refs and restore their previous values/order from the export. Do not replace whole entrypoints, migrate DNS or downgrade existing stronger policies. A host migration needs its own plan and approval; this work does not authorize it.

## Supabase owner settings

Fresh project inspection: ACTIVE_HEALTHY, PostgreSQL `17.6.1.166`. Security advisor has one warning: leaked-password protection disabled. Read-only upgrade preflight returned 12 MB database size, zero replication slots, no installed ltree/btree_gist/timescaledb/plv8 extensions, and zero ordinary tables without RLS across public/payments/supportdesk. Installed extensions: pg_stat_statements 1.11, pgcrypto 1.3, plpgsql 1.0, supabase_vault 0.3.1, uuid-ossp 1.1. These are scoped checks, not a backup or a guarantee of upgrade compatibility.

Current documentation puts **Upgrade project under Settings → Infrastructure**, correcting older General-settings instructions. Inspect the dashboard's available target/version, release caveats and downtime estimate. Verify a restorable backup first. Free-tier documentation recommends regular off-site logical dumps; do not assume paid daily backups are available. Database backups exclude Storage object contents and custom-role passwords. Do not export customer/Auth/Vault data into the repository, this report or an unapproved destination. Record only backup verification metadata. No backup was created or verified in this task.

Owner must complete account/passkey/MFA access, approve the displayed upgrade/downtime after backup review, and retain recovery material privately. Do not use pause/restore as a shortcut around the upgrade decision. After upgrade, verify installed version and healthy status, run advisors and CI, then exercise owned disposable-account confirmation, recovery, signout, deletion and RLS isolation. Preserve disabled payments and closed website trials; acceptance testing must not invent paid grants or claim trial launch readiness.

Leaked-password protection requires Pro or higher according to current Supabase documentation. Do not purchase or change organization billing implicitly. After an explicit plan decision, enable it in Auth password security and use non-customer disposable tests to verify new/changed weak or known-compromised passwords are rejected; successful strong-password confirmation/recovery still needs acceptance. Avoid storing passwords or full Auth responses. User-account MFA, administrator MFA and leaked-password protection are distinct controls.

## Owner MFA and access

Cloudflare account-level `enforce_twofactor` is false, but this does not establish individual-owner MFA status. Verify the owner's enrollment and private recovery method first; do not enable account-wide enforcement before checking every administrator can still sign in. Supabase and GitHub owner MFA are unverified. Review members and access tokens through the authenticated owner dashboards; do not revoke or rotate keys until legitimate Site/app integrations and recovery paths are identified. This task did not alter billing, membership, tokens, server CAPTCHA or owner authentication.

Connected tools cannot inspect/change Supabase Auth configuration, billing or database upgrade settings. Browser/dashboard fallback requires applicable owner permission and may stop for owner passkey/MFA. No credentials or owner identity were fabricated. CAPTCHA activation still depends on compatible owned-live-account/Android acceptance, not this header task.

## References verified 7 October 2026

- https://developers.cloudflare.com/rules/transform/response-header-modification/
- https://developers.cloudflare.com/rules/transform/response-header-modification/create-api/
- https://developers.cloudflare.com/cache/how-to/cache-rules/
- https://developers.cloudflare.com/workers/static-assets/headers/
- https://supabase.com/docs/guides/platform/upgrading
- https://supabase.com/docs/guides/platform/backups
- https://supabase.com/docs/guides/auth/password-security

No default-branch merge, main ref change, production signing, charge, outreach or advertising occurred. This review work does not remove pending launch gates.
