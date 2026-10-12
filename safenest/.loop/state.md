# Loop state

One line per iteration. Newest at the bottom. The loop reads this first and never repeats an approach marked FAILED.

## Open
- intelligence database test does not apply the later RLS-performance policy migration; consider loading both in order.

## Log
- 2026-10-12 run 1, iter 0: manual baseline. CI gate green (67 web, 9 edge, 5 py, java core). intelligence/test/database.test.mjs FAILS: reads supabase/schema-intelligence.sql, file no longer exists (schema moved to migrations/20260929092635_gambling_intelligence.sql).
- 2026-10-12 run 1, iter 1: pointed intelligence/test/database.test.mjs at migrations/20260929092635_gambling_intelligence.sql. Gate PASSED (67+9+17 node, 5 py, java core 147 checks). Checker REJECTED first SKILL.md: never-touch list omitted the gate itself and production function paths. Fixed; checker ACCEPTED on re-review.
- 2026-10-12 run 1, iter 1b: checker step now cross-model: codex exec (GPT via Codex CLI) when installed, Claude subagent fallback otherwise. Reviewer for iter 1 was the Claude fallback (codex not installed in the cloud workspace).
- 2026-10-12 run 1, human-directed: website launch pass (20-point checklist). Gate PASSED. Checker REJECTED once (404 relative URLs, robots hiding noindex, analytics text, edits in never-touch files), fixed, ACCEPTED. security-headers.mjs + release-pages.mjs edited on Shaon's explicit instruction; flagged in PR for sign-off.
- 2026-10-12 run 1, human-directed: security pass (17-point checklist). Gate PASSED. Checker REJECTED once (aal2 policy would lock admins out before any MFA UI exists; store_id strictness unverified against SSLCommerz), fixed: migration split, MFA policy staged in supabase/pending-migrations/, store_id reverted. ACCEPTED.
- 2026-10-12 run 1, human-directed: Android checks on Shaon's PC via Android Studio (47x5 unit tests, lint x3 0 errors, assembleDebug x3, bundlePlay). Lint cleanup + backup rules committed. connectedLabDebugAndroidTest FAILED on API 37 AVD (Accessibility toggle label); CI API 36 is reference. Scheduled weekly loop: Mondays 08:52 Helsinki (trig_01BTjV1c1EFmYMT6Ut9nHH1T).
- 2026-10-12 run 1, human-directed: MFA enrol/step-up UI (web/mfa-step-up.js) for support operators and the admin console; 6 unit tests. Checker REJECTED once (unverified factors live only in listFactors().all; admin prompt could mount hidden), fixed, ACCEPTED. Gate PASSED (73 web tests).
- 2026-10-12 run 2 (scheduled), iter 0: gate green, no changes. 73 web + 9 edge + 17 intelligence node tests, build ok, 5 py, java core all pass. No fix, no checker run, no PR.
