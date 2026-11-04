# Checks, 5 Nov 2026 (branch `launch/audit-fixes-graph-2026-11-05`)

All results are from the saved files on this branch, run in a cloud workspace (Node 22+).

| Node | Requirement | Verdict | Evidence |
|---|---|---|---|
| N1 | Crypto payment confirmed after the order window still activates | pass | `tests/card-crypto-gateways.test.mjs` 9/9; with the old `nowpayments.mjs` restored: 8 pass, 1 fail |
| N2 | Catalogue key compared in constant time; 304 for current ETag | pass | `intelligence` 25/25, new test "catalogue answers 304…": 304 with empty body, 200 for other ETag, 401 for 4 near-miss keys |
| N3 | Clients cannot read `payments.play_subscriptions`, even after an accidental grant + permissive policy | pass | `tests/play-billing.test.mjs` 5/5; without the migration: 4 pass, 1 fail |
| N4 | Intelligence schema test runs against the real migration | pass | intelligence 23/24 → 25/25 |
| N4 | No high/critical advisories in intelligence deps | pass | `npm audit --audit-level=high`: 0 vulnerabilities (Supabase CLI 2.81.2 → 2.122.0) |
| N5 | Website uses বেটিং, never জুয়া | pass | `tests/website-copy.test.mjs` 1/1; with the old page: 0 pass, 1 fail |
| N6 | CI runs intelligence tests and audits | unresolved | workflow edited; runs only once the branch is pushed |
| N7 | Over-limit DNS answered 429, nothing forwarded; preflight never limited; broken limiter fails open | pass | `tests/dns-query.test.mjs` 18/18 |
| N7 | Worker uses `DNS_LIMITER` per client address, blocks brand mirrors, serves iOS profile | pass | same file, "Worker: uses the DNS_LIMITER binding…" |
| N7 | Worker fits the Workers Free size limit (3 MB gzipped) | pass | esbuild bundle 3,036,972 bytes; gzipped 2,278,421 bytes |
| N7 | Worker live behind `dns.mysafenestbd.com` | unresolved | not deployed (owner step, see `../dns-worker-handover-2026-11-05.md`) |
| N8 | MD5 is correct | pass | matches `node:crypto` on 9 fixed strings (incl. Bangla) and 300 random byte arrays: 0 mismatches |
| N8 | Unsigned, tampered, wrong-password, val_id-uncovered, store_passwd-in-key and malformed callbacks refused with no lookup or validator call | pass | `tests/sslcommerz.test.mjs` 11/11 (0 lookups, 0 fetches for 6 bad callbacks) |
| N8 | Real SSLCOMMERZ callbacks pass the check | unresolved | algorithm follows SSLCOMMERZ's hash-validation article; needs one sandbox payment before enabling |
| N9 | Root suite | pass | `npm test` 113/113 (baseline 105/105) |
| N9 | protection-access | pass | 9/9 |
| N9 | Core Kotlin/Java checks | pass | `run-core-tests.sh` exit 0 |
| N9 | Website build | pass | `npm run build` OK |
| N9 | Root runtime deps | pass | `npm audit --omit=dev --audit-level=high`: 0 |
| N9 | Edited Edge Functions parse | pass | esbuild on `dns-query/index.ts`, `intelligence/index.ts` |
| N9 | Gradle, emulator, real phones | unresolved | not run here; CI on the PR |
