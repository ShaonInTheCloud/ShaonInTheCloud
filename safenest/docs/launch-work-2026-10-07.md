# SafeNest launch integration — 7 October 2026

The latest rose-chrome UI is integrated with Android CAPTCHA and trial acceptance as candidate 0.4.11/code 28. The public website still offers direct debug 0.4.10/code 27; its APK inspection SHA-256 is `0f290b2c37bc353f9826cb4e055801146f430a9a3d9ced974539e325527ba496`. No signed production APK or Play publication is claimed.

## App integration

PR #5 integrates the existing CAPTCHA and trial branches with current main. The optional owned-account test no longer calls obsolete tokenless authentication: first start and retry use the actual app security-check flow. Ordinary CI has no live credentials and must report that owned flow as skipped. Cached-window tests and emulator fixtures do not prove real 72-hour expiry, actual provider challenges or Honor acceptance.

Local verification passed 80 Node tests, 9 protection-access tests, core DNS/domain/alias/upstream/HTTPS/signature/commitment/CAPTCHA regressions, 5 signing tests, 171 Android unit tests and all three debug APK builds. The local lint/instrumentation attempt could not download uncached Compose Android-test dependencies. CI run 37569576336 is the authoritative full runner check for implementation commit `4d4662092c53b9fad159a7020f9f76c80f4e4e0a`; record its terminal result before merging.

## Website

Existing public Site version 40 succeeded, source `fbb38afc293e6a161ada93b90e439a5c3ef28ca3`. Live `/android-captcha` returns 200 without a redirect, has a CSP allowing Cloudflare script/frame/connections, contains noindex and uses its own quiet layout. The account and pricing pages also return 200 with embedded CSP and one canonical URL. `/robots.txt` and `/sitemap.xml` return 200. Search descriptions and the sitemap exclude account, checkout, deletion and native-challenge pages. No analytics tracker or advertising spend was added.

Website `SAFENEST_AUTH_CAPTCHA_ENABLED` stays false; server enforcement and actual website/Android acceptance are still pending. Publishing a widget page does not establish direct Auth API protection. Website trial signup remains disabled pending customer-build acceptance. The existing app/source trial endpoint is separate from that website gate.

The static host still ignores `_headers`. Live responses lack HSTS, frame denial, nosniff and sensitive-page no-store. Meta CSP is a partial resource-loading control and does not fix those response-level controls. The connected Cloudflare account has no domain zone. Preserve the current website and mail DNS while preparing any host/zone migration.

## Database and payment configuration

Applied migration `20261007040839_trial_claim_entitlement_index` adds an index on `payments.trial_claims(entitlement_id)`, improving the entitlement relationship/deletion path. The table was 32 kB with zero claims at review. Index existence is verified, and the missing-FK-index advisor finding is gone. No customer data or entitlement was created. RLS and payment/trial rules were unchanged.

Trial/support migration filenames now match server history: `20261007001933_trial_subscriptions.sql` and `20261007005106_support_desk.sql`. Update fixture references with the renames; do not replay the prior invented timestamps. Restored the six payment JWT gateway declarations to match the deployed functions, preserving trial/account declarations. Public provider callbacks validate the merchant independently; order/status routes require JWTs; reconciliation uses its separate backend secret.

Live SQL confirms payment mode disabled, zero enabled providers/products and zero orders. Genuine sandbox/live credentials are still unavailable. The SSLCOMMERZ activation runbook is restored in `sslcommerz-readiness.md`; full/partial refund and chargeback normalization, protected scheduling, load/race checks, financial retention and genuine merchant acceptance remain pending.

## Security items requiring owner access

Security advisor reports one warning: leaked-password protection disabled on Free. Current project is PostgreSQL 17.6. Supabase's 25 September 2026 release describes an available 17.11 security upgrade. Installed `ltree` and `btree_gist` counts are zero, and its affected-custom-operator detection returns zero. No public/payments function or current application source calls the legacy PGP cipher routines. This is a scoped preparation check, not a guarantee that all external historical client use is absent.

Use Settings → General → Upgrade project in the authenticated owner dashboard after reviewing the backup and downtime estimate. No upgrade was performed: the connected tools do not expose it and dashboard access needs owner verification. Then verify the version, rerun advisors and exercise Auth, profile/support isolation, deletion, trial/access and disabled payment endpoints. Reference: https://supabase.com/changelog/postgres-15-19-17-11-breaking-changes and https://supabase.com/docs/guides/platform/upgrading.

Remaining owner-dependent items include upload signing configuration, verified inbound/reply business mail, registrar/Play Console access, seller/legal/refund/retention details, merchant credentials and physical-phone acceptance. Preparation does not substitute for passkey/MFA or invent a certificate, merchant account, seller identity or test result.
