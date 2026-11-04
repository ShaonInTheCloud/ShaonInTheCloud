# Audit-fix graph, 5 Nov 2026

**Task:** implement the open items from the 1 Nov security audit that can be done in code, as a graph of
bounded nodes, each with its own check.
**Branch:** `launch/audit-fixes-graph-2026-11-05`, from `main` `c969927`.
**Done means:** every node's check passes against the saved files, the full suites pass, and the PR is open.
**Limit:** code and tests only. No deploys, no secrets, no live systems.

## Baseline (before any change)
- `npm test`: 105/105. Intelligence: 23/24 (schema test fails). Intelligence `npm audit`: critical `tar` via Supabase CLI 2.81.2.
- **Finding:** none of the fixes the 1 Nov audit lists as "fixed in branch `launch/security-audit-2026-11-01`"
  were on `main`. That branch was never pushed. N1–N6 re-apply them.

## Nodes

| Node | Job | Check | Depends on |
|---|---|---|---|
| N1 | NOWPayments paid-at = payment `created_at` (fallback `updated_at`) | new test; fails on old code | – |
| N2 | `intelligence`: constant-time key compare, `If-None-Match` → 304 | new API test | – |
| N3 | `payments.play_subscriptions` restrictive `deny_clients` policy | new PGlite test; fails without migration | – |
| N4 | Intelligence schema test loads the real migration; Supabase CLI 2.122.0 | intelligence 25/25, audit 0 | – |
| N5 | Website never uses জুয়া (Nest Run page) | new copy test; fails on old page | – |
| N6 | CI runs intelligence tests and both `npm audit`s | workflow diff | N4 |
| N7 | H-1: DoH rate limit (`allow` hook), Edge per-instance limiter, Worker `DNS_LIMITER` binding, Worker parity (brands, iOS profile) | DNS tests 18/18; Worker bundles at 2.28 MB gzipped | – |
| N8 | L-3: SSLCOMMERZ `verify_sign` checked before lookup/validator | MD5 matches Node on 309 inputs; L-3 test: 0 lookups for 6 bad callbacks | – |
| N9 | Full checks | `npm test`, protection-access, intelligence, `run-core-tests.sh`, build | N1–N8 |

Code nodes (one right answer, run as scripts, not judgment): the MD5 cross-check, the bundle-size measure,
every test run. N1–N8 touch separate files, so they were independent; they were run in one session because
each was small.

## Decisions
- **Play `play-billing-verify` confirmed-email check: not applied.** In Play billing, Google takes the money
  before our server sees the purchase. Refusing an unconfirmed account there leaves a customer who paid without
  access, and the RTDN path cannot bind an unbound token. With hosted "Confirm email" ON, an unconfirmed user
  cannot sign in at all, so the check adds nothing. Card and crypto orders keep the check, because there it runs
  *before* payment.
- **DNS limit 1,200/min per address**, not lower: Bangladeshi mobile carriers put many phones behind one NAT
  address.
- **A limiter that fails lets the query through.** Availability of DNS wins over metering.
- **iOS profile not switched to the Worker yet.** The Worker is not deployed; switching first would break DNS for
  every iPhone that installs the profile. Steps in `../dns-worker-handover-2026-11-05.md`.

## Learning (rules this run confirms)
- An audit's "fixed" list is only true once the branch is on GitHub. Check `git ls-remote` before relying on it.
- Every security test here was checked by reverting the fix and watching it fail.
