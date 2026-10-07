# Payment recovery hardening — 7 October 2026

Based on PR #6 head `1c20234f37f2c33d2d92b8bda6b1dfe6e3309e4d`. This is a reviewable code change. No production migration or Edge deployment, scheduler installation, provider secret change, checkout activation, charge or entitlement issuance is included.

## Recovery ledger

`process_validated_recovery` is service-role-only, security-invoker, with an empty search path. The private RLS-protected recovery table records one economic recovery identity per provider/environment/reference. Event locks, recovery-identity locks and an order row lock serialize idempotent transitions with the payment processor. Reused events with changed evidence, receipt reuse, cross-order recovery reuse, altered known amounts and excessive cumulative refunds roll back atomically.

Independent verification must establish the original order, merchant/environment, original purchase amount/currency, transaction and paid-at. A recovery has its own kind, reference, pending/completed/cancelled state, and nullable verified amount. It stores only normalized fields and an evidence hash; no raw payload/card data.

- Completed recoveries never regress to processing or cancelled. A later independently verified completion may supersede cancellation.
- Full completed refunds (including the sum of distinct verified completed refund references) revoke once. Duplicate observations never double-count or extend a subscription.
- A verified full completed chargeback is terminal and cannot be downgraded by a late refund/payment/failure event.
- Partial, pending, or amount-unknown reversals set `recovery_review=true`. Existing access and its finite window stay unchanged. If no entitlement was issued yet, a new grant is held for review. No proration, subscription reduction, automatic refund initiation, reinstatement or policy resolution is invented.
- A cancelled pending recovery can clear its own review reason; any remaining partial/unknown recovery keeps the review hold. The same independently verified payment event may resume a withheld grant after all review reasons clear; it preserves the original paid-at/end time and never requires a synthetic transaction. Full reversals still prevent any grant.

The existing full-reversal payment boundary remains compatible, and now preserves chargeback precedence. All future partial/unknown recovery integrations must use the separate recovery boundary, not represent a partial amount as a legacy full reversal.

## SSLCOMMERZ evidence limits

The callback path accepts a refund reference only as a lookup hint, then independently validates the original transaction and queries the fixed authenticated refund-status endpoint. Both transaction IDs, merchant identity when returned, and the refund reference must match. `success` at refund initiation is not completion; only the status query's `refunded` maps to completed. Callback amount/status never proves the amount or reversal. Missing `refund_amount` remains null/review. An optional amount returned by the authenticated provider query is parsed strictly, with no binary floating-point rounding.

The published refund-query example omits amount. Real merchant fixtures must establish whether a verified amount is returned or how an independently verified amount source is obtained. No undocumented response field or event delivery guarantee is assumed.

Published documentation does not define an authenticated chargeback-status contract. The provider-neutral chargeback normalization/ledger path is tested with disposable evidence; SSLCOMMERZ callbacks claiming a dispute/chargeback are rejected until a merchant-tested independent verifier exists. No claim of automatic live chargeback handling is made.

Reconciliation queries the original transaction and independently validates it again. It also checks known pending or completed-amount-unknown recovery references, applying recoveries before any payment grant. It cannot discover unreported refund/dispute references from an undocumented list API. Merchant-tested ingestion/discovery remains a launch gate.

## Protected scheduler and retries

The new private singleton worker configuration defaults off and bounds a batch to 12. Existing `payment_reconciliation_batch` now uses `FOR UPDATE SKIP LOCKED` and 120-second leases persisted before returning. Competing runs cannot claim the same unexpired order. Stale completions require the original lease token and cannot acknowledge a replacement lease. Crashed workers recover through lease expiry.

The worker requires the separate >=32-character `x-reconcile-key`, POST, and an empty body or `{}`. It accepts no caller order/environment/batch overrides, returns counts only, limits concurrency to three, and gives provider calls a 45-second deadline. Work stops making ledger calls when that deadline expires. Completion uses the ordinary bounded RPC timeout so the retry/lease record can still be saved. Upstream exceptions and secrets never enter ledger error text or responses.

Verified success advances `reconciled_at`; failed attempts do not. Provider/DB outages persist retry with bounded exponential backoff from five minutes to six hours. Verification ambiguity and policy holds persist review. Paid orders remain eligible for a daily recovery sweep. Unknown completion/finish failures return 503 and allow the lease to expire. There is no seven-day cutoff that silently drops unsettled orders. No financial retention period was chosen.

`payments.enqueue_reconciliation()` is database-owner-only, security-invoker, reads a fixed Vault secret name at dispatch, permits only a canonical Supabase HTTPS endpoint, and returns a request ID. It does nothing when disabled or the provider/payment mode gate is closed. The migration creates no extensions, cron job or secrets. `supabase/operations/install-payment-reconciliation.sql` installs a dormant five-minute job only after confirming the worker is disabled; cron text contains no key.

Before activation: deploy migration and compatible Edge handlers together; provision the same random reconcile key in Vault and Edge secrets through secure tooling; install pg_cron/pg_net if approved; configure the own-project endpoint/environment; verify genuine sandbox status queries and refund evidence; test missing/wrong secret, outages, crash/lease replacement and count-only monitoring. Keep checkout off. Activation is separate from code review. Roll back by disabling the worker, then unscheduling its named cron job; stop new checkout first if later enabled, and preserve independently verified in-flight recovery evidence.

Private monitoring (do not expose IDs or raw request headers publicly): inspect `reconciliation_result`, retry failures, due/expired leases and oldest backlog; review `recovery_review` orders and incomplete recovery amounts. Inspect cron run results and pg_net response status/timeouts (not secret-bearing request headers); alert on failed dispatch, repeated 503, stale backlog and review items. Alert destination/owner and sandbox dispatch acceptance are pending operator inputs.

## Validation

The local test suite exercises actual SQL through PGlite, independently queried refund fixtures, server evidence validation, client permission denials, default-off scheduler dispatch, Vault/pg_net call boundaries, leased retries, stale completion fencing, deadline/batch/concurrency bounds and invalid inputs. These are isolated fixtures; no production payment rows are used.

`tests/integration/payment-races.test.mjs` runs against a fresh loopback PostgreSQL database with 32 connections. It requires `SAFENEST_PAYMENT_RACE_TEST=local-fixture-only`, rejects non-loopback hosts and databases outside `safenest_payment_test*`, and refuses an existing ledger. The CI service is disposable PostgreSQL 17.6. It tests 128 duplicates; conflicting event replay; 128 competing paid/refund/chargeback/failure events; 128 duplicate partial refunds; 64 copies of the original payment resuming a cancelled hold without extending the window; cross-order receipt contention; and concurrent scheduler claims/expired-worker fencing. Tests observe blocked PostgreSQL sessions before releasing a held row lock, so concurrent Promise calls do not masquerade as independent database transactions. Counts, terminal state, immutable window and zero production-table grants are asserted. A skipped integration runner is not acceptance evidence.

Evidence and exact-head CI outcome are recorded in the PR. These tests do not establish genuine merchant behavior, production throughput capacity or refund/dispute policy acceptance.

## Remaining decisions

Approved partial-refund/subscription policy and its trusted resolution workflow; merchant-tested chargeback verification and refund-reference discovery; financial-record retention/account-deletion/backups; protected secret provisioning; scheduler/alert owner and genuine sandbox delivery acceptance remain open. Existing account deletion continues to cascade ledger rows. This change implements neither a retention purge nor indefinite retention/legal promises. Sales remain disabled until those decisions and merchant acceptance are complete.

References: https://developer.sslcommerz.com/doc/v4/ ; https://supabase.com/docs/guides/functions/schedule-functions ; https://supabase.com/docs/guides/database/vault .
