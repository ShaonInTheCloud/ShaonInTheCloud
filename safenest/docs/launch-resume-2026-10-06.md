# Launch continuation — 6 October 2026

Release status: development only. No paid launch, production Play upload, or new APK was completed.

Recovered the prepared Android 0.4.2/code 19 source from local commit dc4f651bc58ce9169226bd8ad26c3688842a5f48. Reconciled it with the account changes in the website's version 20 checkout: subscription status and account isolation, profile-load recovery, canonical account redirects, build cleanup, security-header generation, CORS regression and accurate public release wording. Existing profile README and all four applied migration versions are preserved. Added lint checks for both Android flavors to CI; CI has not run on GitHub.

Fresh local verification:

- 18 account, deletion, entitlement, RLS and security-header tests passed.
- 9 authenticated protection-access/CORS tests passed.
- Website build passed.
- Standalone core regressions passed: DNS codec 20,075 checks; domain rules 57; aliases 10,082; upstream transport 22; catalogue signatures 59; paid-window/guard 19.
- Diff whitespace check passed. No environment files, SDK-local settings, signing material or compiled Android artifacts are in the tracked/pending source. The private-key scan match is a deliberately invalid AA== parser rejection fixture, not a usable key.

These checks do not compile Android or replace real phone testing. Android assembly failed before compilation while fetching Gradle 8.13: Network is unreachable. No Android SDK is available in this runtime. Existing public download remains 0.4.0 debug.

Fresh service checks: Sites reports public website version 20 at mysafenestbd.com; Supabase is healthy. Applied migrations remain private_profiles (20260924212500), gambling_intelligence (20260929092635), protection_entitlements (20261003095341), launch_rls_performance (20261005035025). Functions are protection-access v3, delete-account v1 and intelligence v1. The security advisor still reports leaked-password protection disabled.

The apex account route returned HTTP 200, but the observed response lacked configured CSP, HSTS, no-referrer and no-store headers; Cache-Control was public, max-age=0, must-revalidate. HTML has an account CSP/referrer meta policy, but that does not enforce every missing response header. Fix host-level delivery and verify GET responses before closing this gate. The www request returned a proxy 502; origin DNS/HTTPS status remains unconfirmed.

GitHub main was observed at 4b89e494535ca02aa798ea5ae2ca7cd43a20decd. Automatic approval review rejected pushing the prepared broad source update to main, citing insufficient explicit authorization in the resume request. No connector or alternate branch push was attempted to bypass the rejection. Changes are committed locally for review; upstream remains unchanged.

Next steps: approve the concrete source push; run GitHub Android CI; supply release upload signing and Play Console access; configure an approved merchant's sandbox and final prices; validate legitimate purchase-to-entitlement activation; run the documented Honor/device tests and full login/reset/deletion tests. Finalize support/legal identity, privacy/retention answers and store declarations before submission.

DNS-only Lockdown must remain off. Play/direct separation is prepared source, not proof of Play approval. VpnService eligibility and encryption review remain unresolved. The full tracker is market-launch-checklist.md.

## Second resume

The second main-branch push was also rejected: automatic review explicitly says that "resume" is not approval for the broad GitHub update. Upstream was not changed and no alternate GitHub route was used.

Added scripts/check-live-security.mjs to evaluate actual GET headers, follow bounded HTTPS canonical redirects, reject redirects outside the expected origin and fail when account no-store/no-referrer or enforced framing/object policies are missing. It reports failures without logging cookies or raw headers. Four additional regression tests passed; the account suite now has 22 passing tests and the access suite has 9.

The live audit confirms missing HSTS, nosniff and enforced CSP on the root/account responses, and missing account no-store/no-referrer. HTML-suffix URLs redirect to canonical paths. This remains a deployment blocker; generated _headers is present but not enforced by the current delivery path. The Sites static configuration allows directory/not_found_handling only; no supported response-header management capability was found. No speculative hosting conversion was deployed.

Added a Play-specific Accessibility XML override: only window-state events and no view-ID reporting. Root retrieval remains enabled for current foreground package verification. The direct flavor retains content-change events for its separate guard. XML parsed and source attributes were verified; merged resources and actual app behavior still require Android CI and phone tests. Source remains a prepared 0.4.2 release; no new APK is available.
