# SafeNest SSLCOMMERZ readiness

Prepared 6 October 2026. Checkout remains closed. The gateway migration and six Edge handlers are deployed, without merchant secrets, provider rows, products, orders or paid entitlements. This is technical preparation, not merchant certification or permission to charge customers.

## Implemented

- Authenticated, confirmed, non-anonymous buyer orders with server-selected provider, environment, price and duration. Requests cannot supply a buyer ID, price or paid evidence. Customer email comes from Auth; name, phone, address and city are sent only to the hosted gateway, not stored in the ledger.
- Hosted SSLCOMMERZ v4 checkout using fixed sandbox/live origins and a 30-character merchant transaction reference. Session creation is claimed once in PostgreSQL; successful sessions are reused. An ambiguous timeout enters review instead of blindly making a second payable session. Orders expire after 30 minutes. Ten new orders per account per hour is a technical abuse bound; retries retain their key and price snapshot.
- Public IPN and POST return independently call the merchant validation API. Browser success, callback amount and `verified=true` never grant access. Exact order, merchant when returned, environment, BDT amount/currency, original transaction time, validation ID and risk level are checked. Risky, malformed or ambiguous responses require review. No card details or raw gateway payloads enter the ledger.
- Existing transactional ledger grants at most one finite entitlement per order. Replay never extends it; cross-account/transaction reuse fails. Sandbox grants cannot activate Android. The existing Android account verification consumes the real server entitlement; no new payment SDK or fabricated access is placed in the app.
- Public, gated catalogue; owner-authenticated status; website payment form with account-scoped retry key and saved order reference; fixed return destination. Redirects only cause status checking. The user can refresh status after an IPN arrives; a return does not display confirmed payment by itself.
- Protected reconciliation endpoint with a separate secret, a three-order batch and bounded concurrent provider checks. Merchant lookup must produce exactly one successful transaction, then the validator is called again before the existing processor runs. It is deployed but not scheduled yet.

## Files and endpoints

Migration: `supabase/migrations/20261006225937_sslcommerz_gateway.sql`. The CLI-generated migration was renamed to match the server-recorded applied version. Earlier deployed migrations are unchanged.

| Function | JWT gateway | Other authorization / trust boundary |
| --- | --- | --- |
| payment-order | On | Live confirmed Auth lookup; server catalogue |
| payment-status | On | Live Auth lookup and server-derived order owner |
| payment-catalogue | Off | Read-only approved catalogue; returns unavailable while disabled |
| payment-notification | Off | Independent merchant API validation |
| payment-return | Off | Fixed redirect; POST uses the same independent validator |
| payment-reconcile | Off | `x-reconcile-key`, minimum 32 characters; never a browser secret |

Base URL: `https://kflenmeizngmafwnwhgv.supabase.co/functions/v1/`.
Success/failure/cancel return URL: base + `payment-return`.
IPN URL: base + `payment-notification`.
Website destination: `https://mysafenestbd.com/account.html?payment=checking`.

## Merchant onboarding and activation sequence

1. Obtain the merchant contract, sandbox store ID/password, allowed payment channels, fee/tax invoice, settlement schedule, account/refund conditions and seller identification. Confirm the API transaction timestamp is Asia/Dhaka. Confirm actual transaction/reference formats and whether fixed outbound IP allowlisting is needed. Supabase Edge does not promise static egress; do not invent an allowlisted IP.
2. Complete the merchant's verification of the website: seller identity, support contact, privacy, price/currency, subscription period, refund/cancellation and retention disclosures. Existing launch policies are placeholders for unpaid development. The existing account deletion cascades account-linked payment records; decide and implement financial retention before sales. Do not promise automatic renewal or subscription stacking; neither is implemented. Approve overlap/device rules and a finite period explicitly.
3. Add secrets in Supabase server settings, never Git, the website, APK, a cost sheet or chat. Required sandbox settings are below. Keep live settings absent and checkout disabled until the sandbox products and buyer account are ready. Product/provider rows are maintained only by a trusted operator, not the customer API. Do not seed guessed commercial values.
4. In sandbox only, configure `payments.configuration.mode='sandbox'`, the enabled `sslcommerz/sandbox` provider and approved test product row. Enable the sandbox adapter and checkout. Verify one real merchant sandbox purchase, all enabled channels, cancellation/failure, delayed IPN, return before IPN, duplicate `VALID`/`VALIDATED` delivery, amount/currency/reference tampering, risk level 1, validation outage, session timeout and account switching. Compare merchant statement to the private receipt/order. Sandbox success must leave production entitlements empty.
5. Test real multi-connection retries/session races against sandbox PostgreSQL; local PGlite tests use one connection and cannot certify scheduling races. Load-test account, status, checkout and catalogue traffic; the financial model does not prove capacity for 10,000 users. Test logged-in website forms on desktop/mobile and real payment-to-Android account verification, expiry and revocation on an owner-controlled device.
6. Set the protected scheduler's secret securely (for example Vault + pg_cron/pg_net or a trusted backend scheduler); call `payment-reconcile` with POST, an empty body and `x-reconcile-key` every five minutes. Do not store this key in source, a public schedule URL or client code. At three orders per run the theoretical pending capacity is 864 per day; IPNs are the primary path. Monitor backlog and scale the batch/worker before launch if real demand requires it. Keep count-only Edge responses; operators investigate IDs through private ledger queries. Test the scheduler and alert delivery, provider outages, a reconciliation backlog and restart recovery before turning on sales.
7. For live, use separate live store credentials and an independently approved live product catalogue/mode. Set `SSLCOMMERZ_LIVE_APPROVED=true` only after the owner approves launch. Run a small genuine owner-controlled purchase and verified reversal with the merchant, then Android activation/expiry/revocation. Only open public checkout after merchant, commercial, security and release acceptance pass. Never generate fake paid production entitlements to make this test look complete.

## Server settings

| Setting | Default / requirement |
| --- | --- |
| SSLCOMMERZ_ENABLED | Absent = disabled; `true` only for approved integration |
| SSLCOMMERZ_ENVIRONMENT | `sandbox` or `live`; never customer-selected |
| SSLCOMMERZ_CHECKOUT_ENABLED | Absent = closed; separates intake from notification recovery |
| SSLCOMMERZ_TIMESTAMP_ZONE | `Asia/Dhaka` only after merchant confirms it |
| SSLCOMMERZ_SANDBOX_STORE_ID / STORE_PASSWORD | Real sandbox credentials, server-only |
| SSLCOMMERZ_LIVE_STORE_ID / STORE_PASSWORD | Separate real live credentials, server-only |
| SSLCOMMERZ_LIVE_APPROVED | Absent = live rejected |
| SSLCOMMERZ_RECONCILE_KEY | Secret of at least 32 characters; backend only |

Runtime supports Supabase legacy keys and the new default `SUPABASE_SECRET_KEYS` / `SUPABASE_PUBLISHABLE_KEYS` JSON settings. Client builds accept only a publishable key; service and merchant keys must never appear in compiled browser output. Disable new checkouts first during incidents; keep the validator available for in-flight payments. Setting SQL mode disabled also prevents ledger processing; reconcile verified settlements before reopening.

## Refunds, receipts and operations still requiring merchant evidence

The foundation supports a verified full refund/chargeback revocation and reversal tombstone. This concrete adapter currently normalizes successful payments only. The public refund-status example lacks refund amount, so `status=refunded` alone is insufficient proof of a full reversal. Partial refunds, chargeback evidence and their subscription effect require an approved policy and merchant-tested normalization. Until that exists, a trusted operator verifies reference, merchant, original receipt and exact refunded amount before using the service-only reversal boundary; never treat browser refund input as proof. No automated refund request or claim of unattended dispute handling is implemented.

The website exposes server payment status; it does not issue a tax invoice. Confirm customer invoice/receipt requirements and add compliant seller/tax data before sales. Establish daily settlement reconciliation, review queue ownership, support, refunds, incident alerts and restore exercises. Upgrade Supabase as planned before enabling leaked-password protection; the current Free plan still has that known advisor warning.

The Play distribution remains consumption-only for existing paid accounts. Website checkout can serve direct/web distribution. Google Play digital subscriptions generally require Play Billing unless a verified exception/program applies; do not add a prohibited alternative-payment link to the Play app. Review privacy/deletion navigation and the final submitted artifact with current policy.

## Verification evidence

56 account/security/payment tests and 9 protection-access tests passed; website build and all six Edge entrypoint bundles passed. Ten new adapter/gateway tests include actual migration execution in isolated PGlite, private sessions, environment isolation, one-time session claims, retry reuse, account-owned status, order abuse bounds and independent validation. Rate-bound SQL tests passed after the final migration change. Local fixtures are not production accounts.

Live SQL after deployment confirmed mode disabled; zero providers, products, orders, sessions and production entitlements; RLS enabled on sessions; anon SELECT/customer INSERT denied; customer RPC execution denied and backend execution allowed. The security advisor has no payment findings; existing leaked-password protection remains disabled. Performance retains existing catalogue multiple-policy warnings and unused-index informational notices; no new payment FK/RLS findings were reported.

Official references: https://developer.sslcommerz.com/doc/v4/ ; https://sslcommerz.com/pricing/ ; https://supabase.com/pricing ; https://support.google.com/googleplay/android-developer/answer/10281818 .

Live HTTP checks also passed: catalogue 200/unavailable; order and status without JWT 401; forged notification 503/provider not configured; reconciliation without its secret 401; browser return 303 to the fixed checking URL. Disabled public handlers set no-store. The JWT gateway's own 401 headers are platform-managed.
