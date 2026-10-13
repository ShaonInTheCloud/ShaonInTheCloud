# Payment callback and reconciliation audit — 13 October 2026

Audited main `7009556e6f861b7242fd6f3f2f55c51b62449b63`, including PR #10 merge
`b3ffb06e925afa96b51e02ce257ce0a34ed47704`, against hosted SafeNest project
`kflenmeizngmafwnwhgv`. Hosted inspection used read-only function metadata, source,
database queries and logs. No payment functions were deployed, no migrations applied,
no credentials changed, and neither checkout nor reconciliation was activated.

## Hosted JWT settings and code

| Function | Repository verify_jwt | Hosted verify_jwt | Hosted version | Independent safeguard |
| --- | --- | --- | --- | --- |
| payment-notification | false | false | 3 | Fixed SSLCOMMERZ validation API, server-selected merchant/environment, stored order and amount matching |
| payment-return | false | false | 2 | POST uses the notification validator; GET only redirects to the fixed account checking page |
| payment-reconcile | false | false | 2 | At least 32-character secret, equal-length XOR comparison, empty POST body, independent provider validation |
| payment-catalogue | false | false | 2 | Public allowlisted product fields only; provider, checkout and database gates |

All files in the four downloaded hosted function bundles match the audited repository
source, including their shared dependencies. There is no demonstrated hosted JWT mismatch.
The hosted payment-order and payment-status functions retain verify_jwt=true and also
perform live Auth user lookups. Status access is scoped to the authenticated order owner.
Dashboard screenshots are unnecessary for these already verified settings.
Unauthenticated hosted GET probes also reached application behavior: notification
and reconciliation returned 405 method_not_allowed, return sent the fixed 303 redirect,
and catalogue returned 200 with available=false/products=[]. None required a JWT or
performed a payment operation. These are reachability checks, not provider callbacks.

Notifications use form data only to locate an order and validation reference. A forged
callback status, amount, browser success flag or redirect is not payment evidence.
The provider response must match the transaction reference, validation reference,
merchant when supplied, BDT amount/currency and zero risk level. Validation calls use
fixed HTTPS provider endpoints, timeouts, bounded response bodies and no redirects.
The ledger is called only after this independent check. Browser return POST discards
the notification handler's response and redirects even on verification failure;
the account page must obtain actual order status. Its 303 is not a payment ACK.

The hosted ledger RPC definitions and permissions inspected match the repository:
security invoker, empty search_path, and execution limited to postgres/service_role.
All hosted payments tables have RLS enabled. Ledger transactions lock event/order
identities, reject conflicting replay and receipt reuse, and give one finite grant
per order. Refund/chargeback tombstones prevent later paid evidence restoring access.

## Checkout and scheduler state

Hosted database snapshot: mode=disabled; no providers; six products, all disabled;
zero orders, events or payment-derived entitlements. These gates independently prevent
charging and grants. Runtime secret values were not retrieved or modified.

[Scheduled run 38336017089](https://github.com/ShaonInTheCloud/ShaonInTheCloud/actions/runs/38336017089)
completed successfully on 13 October at 02:15 UTC. Its job logs show both reconciliation
environment values empty and the message “Reconciliation secrets are not configured;
nothing to do.” This proves a skipped run, not successful recovery. The inspected
24-hour hosted edge-log window ending 13 October 08:37:36 UTC contained no payment
function requests; absence in that window does not establish historical delivery.

## Repository defect corrected

The scheduler's shell wildcard accepted
`https://attacker.invalid/#.supabase.co/functions/v1/payment-reconcile`. Curl would
send its secret header to attacker.invalid. Replace that suffix match with an exact
SafeNest URL comparison, reject short or multiline keys, flag partial configuration,
and bound connection establishment. Both missing secrets still produce an inert run.
No redirects or automatic retries are enabled. Regression tests execute the actual
workflow shell with curl replaced by a local mock; they make no network requests.

## Recovery limits and validation

The batch selects up to three pending/failed/cancelled orders, younger than seven days,
whose last attempt is older than five minutes. FOR UPDATE SKIP LOCKED and an attempt
timestamp limit overlapping batch claims. Selection does not mark the payment successful.
Provider or database failure leaves it retryable after cooldown. Other selected orders
continue. At the nominal schedule this is at most 18 order attempts/hour, including
rechecks; a growing backlog or older unresolved order needs operator attention.

HTTP 200 with processed/review counts means the batch handler ran. It does not prove
every payment recovered; review includes provider errors, ambiguous/no successful
transaction and ledger failures, and is not a persistent operator queue. The adapter
currently normalizes successful payments only. Refunds, chargebacks and partial refund
policy still require a reviewed operator/provider process. The hosted operator-controls
migration is absent; it was not applied by this audit.

Added coverage exercises unauthorized keys, methods and bodies before any provider/RPC
call; authorized mixed-success batches; disabled provider rejection; real SQL batch
rotation/cooldown/seven-day cutoff; and duplicate/reversal bursts with one receipt and
revoked access. PGlite uses one queued backend: these tests verify state transitions,
not real multi-connection PostgreSQL contention or provider delivery.

Validation: `npm test` passed 85/85 tests on Node 24.19.0, including nine added tests.
The pre-change payment/config/operator subset passed 40/40. The scheduler regression
suite separately passed 5/5, and `git diff --check` passed. All financial data in tests
was disposable local fixture data; no hosted payment records were created.

## Remaining owner checks before any activation

- JWT configuration is already verified; no dashboard JWT correction is needed.
- After separate authorization, configure sandbox merchant settings and match the
  repository scheduler key to SSLCOMMERZ_RECONCILE_KEY. Confirm random key quality;
  length alone cannot prove entropy. Secret presence/equality remains an owner check.
- In sandbox, verify authentic notification delivery, failed/forged notification
  rejection, browser return followed by owner-authenticated status, and a recovered
  payment when IPN is deliberately withheld. Confirm failures/review counts are monitored.
- Exercise duplicates and competing callbacks on a disposable multi-connection
  PostgreSQL environment. Set operational backlog/age alerts and a manual recovery
  procedure for orders outside the seven-day window before relying on scheduling.
- Review reversal policy and operator migration separately. Apply/deploy only with
  authorization, and keep checkout and production scheduling disabled meanwhile.

Repository sources: config.toml; functions/_shared/{payment-endpoints,payments,sslcommerz}.mjs;
payment-notification/handler.mjs; payment-order/handler.mjs; the payment foundation and
gateway migrations; tests/payment-*.test.mjs; tests/sslcommerz.test.mjs; and
.github/workflows/safenest-payment-reconcile.yml.
