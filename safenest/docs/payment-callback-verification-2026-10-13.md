# SafeNest payment callback verification — 13 October 2026

Reviewed PR #10, merge `b3ffb06e925afa96b51e02ce257ce0a34ed47704`, against current main `7009556e6f861b7242fd6f3f2f55c51b62449b63`. All hosted operations in this review were reads or unauthenticated negative/reachability checks. No payment functions or database migrations were deployed, no credentials or provider switches were changed, and the reconciliation workflow was not dispatched.

## Hosted settings: confirmed, no JWT mismatch

Supabase project `kflenmeizngmafwnwhgv` (SafeNest) was inspected through authenticated management reads. Both function listing and individual function reads report:

| Function | Hosted version | Hosted `verify_jwt` | Repository setting | Independent protection |
| --- | ---: | --- | --- | --- |
| `payment-notification` | 3 | false | false | Fixed SSLCOMMERZ validation API and stored order matching before a service-only ledger RPC |
| `payment-return` | 2 | false | false | POST uses the same validation; GET only redirects to a fixed checking page |
| `payment-reconcile` | 2 | false | false | Server-side key check before any provider call or RPC |
| `payment-catalogue` | 2 | false | false | Public projection of prices; disabled gates return no products |

The deployed shared endpoint, orchestration, adapter, notification handler and environment-reader sources match reviewed main. The order/status functions report hosted JWT verification enabled; this follow-up also makes their existing default explicit in TOML and its regression test.

Without a JWT, API key or reconcile key, live checks returned:

| Request | Result | Meaning |
| --- | --- | --- |
| GET catalogue | 200, `available:false`, empty products | Public handler reached; checkout unavailable |
| Empty POST notification | 503, `payment_provider_not_configured` | Application handler reached; provider gate closed |
| GET return | 303 to `https://mysafenestbd.com/account.html?payment=checking` | Fixed redirect, no paid assertion |
| Empty POST reconcile | 401, `unauthorized` | Handler's independent key authorization rejects access |

The notification response establishes that platform JWT verification is not blocking this request; it is not proof of successful gateway delivery. Return POST intentionally ignores the notification response and redirects to “checking”; the redirect never certifies payment or guarantees backup processing succeeded.

Read-only SQL confirmed `payments.configuration.mode='disabled'`, zero provider rows, six product rows, zero orders and zero payment-linked production entitlements. Products alone cannot enable checkout. The deployed processing, batch, lookup and catalogue RPCs are SECURITY INVOKER with EXECUTE restricted to postgres/service_role, not anon/authenticated. Environment credential values were neither retrieved nor modified; catalogue unavailability plus disabled database gates are evidence of disabled payment availability, not a claim about every environment variable.

## Independent validation and duplicate safety

Notification data selects only a stored SSLCOMMERZ order and validation reference. The adapter fixes sandbox/live API origins and credentials from the server environment, refuses provider redirects, uses bounded request/response sizes and timeouts, and independently validates status, references, amount, BDT currency and risk. Live mode needs explicit approval; checkout has a separate enable flag. Untrusted amounts, status flags, URLs and user IDs cannot authorize an entitlement.

The processor checks database mode/provider gates and immutable order values, serializes event identity with a transaction advisory lock and locks the order. It stores whitelisted evidence only. Event/receipt/source-order uniqueness prevents repeated callbacks or callback/reconciliation overlap from granting access twice. Changed evidence under one event ID fails; reversal tombstones prevent later paid evidence restoring refunded/chargeback access. Sandbox grants remain separate from production access.

The current SSLCOMMERZ adapter normalizes successful payments only. It does not implement automatic refund, chargeback or partial-refund validation. Existing ledger reversal tests prove processor behavior with trusted fixture evidence, not delivery of those events from SSLCOMMERZ. Refund policy and provider/operator integration remain separate launch work. The repository's operator-controls migration is absent from hosted migration history; this review did not apply it.

## Scheduler and recovery

Observed scheduled run [38336017089](https://github.com/ShaonInTheCloud/ShaonInTheCloud/actions/runs/38336017089), created 13 October at 02:15:26 UTC, completed successfully because its job log explicitly says `Reconciliation secrets are not configured; nothing to do.` At least one required secret was absent at that run. This is evidence of an inert run, not an operational recovery service or an assertion that credentials can never change afterwards.

Repository defects corrected on the review branch:

1. The shell pattern `https://*.supabase.co/functions/v1/payment-reconcile` also accepts `https://attacker.example/.supabase.co/functions/v1/payment-reconcile`, because `*` can include slashes. If misconfigured, the script would send its credential to that host. Replace it with an exact project endpoint allowlist, refuse redirects, validate header-safe credentials, bound requests and avoid logging arbitrary upstream bodies or secret values.
2. `handleReconcile` caught provider/network/ledger errors per order and returned HTTP 200 with `review`, so an outage looked like a successful workflow. Add a separate `failed` count and HTTP 503 for retryable failures. Keep invalid/ambiguous evidence in `review` without granting; report it as a workflow warning. The scheduler rejects unresolved legacy `review` responses until the reviewed handler is authorized for deployment, so the existing deployed handler cannot silently hide those outages.

The workflow now additionally requires explicit owner enablement via `SAFENEST_RECONCILE_ENABLED=true`; absent enablement remains inert even if secrets exist. Its cron and shared concurrency group are retained. CI path filters include this workflow. Checkout and charging gates are unchanged.

The deployed SQL batch selects up to three pending/failed/cancelled SSLCOMMERZ orders from the last seven days, oldest lease first, and records `reconciled_at` before network work. A five-minute lease plus row locks/`SKIP LOCKED` avoids immediate reclaims. After a crash or outage the order remains eligible once the lease expires; the next actual scheduler run can retry it. The lease is not a permanent lock on an order, so the idempotent transactional processor is still essential.

Limits requiring launch monitoring: at most 18 attempts/hour at the nominal cron frequency; unpaid/ambiguous orders are repeatedly eligible and consume capacity; orders older than seven days need operator recovery; counts are aggregate and review is not a durable per-order support queue. Scheduled execution timing and throughput are not guaranteed. Validate an expected launch backlog before enabling recovery. A late delivery is accepted only when independently verified payment time falls within the stored order's payment window; recovery does not extend subscriptions from the recovery date.

## Validation and remaining owner checks

Baseline: 76/76 Node tests passed. Targeted configuration, adapter, endpoint, scheduler and SQL recovery tests: 19/19 passed. Final full suite: 83/83 passed; protection-access suite: 9/9 passed; `npm run build` and `git diff --check` passed. Android code was unchanged and Android release checks were not run locally for this payment-only change.

New regression coverage includes unintended URLs with zero credential transmissions, disabled scheduler gates, redirect/error handling without leaked detail, independent reconcile-key rejection with zero backend calls, mixed successful/review/outage outcomes, ledger outage reporting, lease rotation, expired-order delayed payment recovery and twenty duplicate calls yielding one sandbox grant. PGlite uses one queued connection: the burst test does not prove independent-session lock contention or production load tolerance. Existing SQL locks/constraints were inspected; live callback success and production concurrency were not exercised.

No dashboard-only JWT verification step remains for the four inspected functions: management reads and unauthenticated handler responses establish the intended settings. Before any explicitly authorized activation:

- Check that GitHub and Supabase reconcile keys match without copying them into logs; inspect repository secret presence and the enable variable. These secret values were not read here.
- Obtain merchant sandbox inputs and confirm timestamp semantics; deliberately authorize sandbox configuration, then record a successful IPN, duplicate replay, missed-IPN reconciliation and provider/ledger outage recovery against real sandbox orders.
- Verify the provider console points to this project's fixed callback URLs and review any historical failed deliveries; zero orders currently provide no successful-payment delivery evidence.
- Review/apply only separately authorized function/database changes. Recheck hosted JWT settings after deployment. The handler failure-reporting fix in this branch is not deployed by this audit.
- Review refund/chargeback handling, queue age/capacity and independent-session race/load evidence before production. Keep checkout, live charging and production reconciliation disabled until the owner authorizes activation.

Sources: [PR #10](https://github.com/ShaonInTheCloud/ShaonInTheCloud/pull/10), Supabase management function/source reads and read-only SQL on 13 October, the linked scheduler job log, repository migrations/tests and [Supabase function configuration](https://supabase.com/docs/guides/functions/function-configuration).
