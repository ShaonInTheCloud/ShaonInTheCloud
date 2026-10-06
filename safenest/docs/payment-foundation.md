# SafeNest payment foundation

Prepared 7 October 2026 against GitHub main `829b9b95cbdee1aec0dd3b62942b908e9296da76` and live Supabase project `kflenmeizngmafwnwhgv`.

Checkout remains inactive. No merchant credentials, products, commercial prices or paid accounts are invented. This foundation implements the trusted processing boundary; it does not claim a working gateway integration.

## Database and trust boundary

Migration `20261006215529_payment_foundation.sql` adds a private `payments` schema with RLS on every table, no customer schema/table privileges and service-role-only, security-invoker RPCs. `20261006215803_payment_foundation_hardening.sql` adds covering FK indexes and explicit restrictive client-deny policies. Function execution is revoked from PUBLIC, anon and authenticated. The existing own-entitlement read policy and `protection-access` remain in use.

| Object | Purpose |
| --- | --- |
| `configuration` | Singleton mode starts `disabled`. Explicit operator configuration is required for sandbox or live processing. |
| `providers` | Provider/environment allowlist with enabled=false by default; initially empty. Contains no credentials. |
| `products` | Server-only approved price in integer minor units, ISO currency, existing plan code and finite duration in seconds; initially empty. |
| `orders` | Authenticated buyer, product/price/duration snapshot, pending/paid/failed/cancelled/refunded/chargeback state and account-scoped idempotency key. Technical order TTL defaults to 30 minutes. |
| `receipts` | One provider transaction per order, unique across orders/accounts within provider/environment. Reversal tombstones prevent late payment delivery from restoring access. |
| `events` | Immutable normalized validation evidence and processing result, keyed by provider/environment/event. No raw callback payload, card/CVV fields or customer email. |
| `sandbox_entitlements` | Test-mode windows isolated from the public entitlement table and Android access checks. |
| `protection_entitlements.payment_order_id` | Unique source order for each live grant; existing independent entitlements remain compatible. |

`create_payment_order` accepts a server-derived buyer and product selection, looks up enabled catalogue/provider configuration, snapshots price/duration and preserves the original snapshot on an identical idempotent retry. A different product/provider/environment using the same buyer/key is rejected. Buyers cannot insert orders, choose their price, or call this RPC. The Edge handler verifies a live, confirmed, non-anonymous Auth account; the order FK rejects deleted identities. It does not expand service-role SELECT permissions on `auth.users`.

`process_validated_payment` accepts only a trusted server adapter's normalized output. The database cannot independently verify a gateway signature or merchant credentials. The service role is part of the trust boundary and must never appear in the website, app or request JSON. The generic RPC cannot convert a webhook's `verified:true` or a browser success redirect into trustworthy evidence.

The processor locks the configuration/provider while processing, takes a transaction advisory lock on the event identity, and locks the order row. Exact order/provider/environment/amount/currency matching, event/receipt conflict checks, receipt recording, one finite entitlement insertion, state transition and audit event insertion occur in one PostgreSQL transaction. A failure rolls all of them back. A new notification ID for an already-granted receipt does not extend the window. Cross-order receipt reuse fails its unique constraint. Reusing an event ID with changed normalized contents fails.

Pending, failed and cancelled notifications never grant access and cannot undo a settled payment. An independently validated paid result inside the order window can supersede a failure/cancellation delivery. Paid time must be finite, no earlier than order creation, no later than order expiry, and not in the future. Delayed delivery uses verified provider paid-at plus the snapshotted duration; it never creates a fresh period starting at delivery. Each order represents one finite purchase window. Stacking/automatic renewal, calendar-month semantics and device limits remain commercial/integration decisions; this implementation invents none of them.

Full refund/chargeback evidence matched to the original transaction revokes its window. A reversal delivered before success creates a tombstone that prevents a later success grant. Partial refunds, ambiguous provider states and unvalidated events must go to operator review, not be mapped to full paid or full reversal. Existing cached offline app access still has its documented revocation limits.

Account deletion cascades orders, normalized events, receipts and associated grants alongside existing account-linked deletion. Merchant-side cancellation, legal transaction-retention rules and backup/log retention remain undecided. Do not open checkout before aligning this with the final published policy.

## Provider and function boundaries

`_shared/payments.mjs` defines `createHostedSession`, `validateNotification` and `reconcile` adapter boundaries. The compiled `configuredAdapter()` always fails closed; there is no test/fake adapter, secret-based enable switch or client-selected provider in deployed code. Adding credentials alone cannot activate checkout.

The future concrete adapter must independently contact fixed provider validation APIs (or verify documented signatures as applicable), verify the merchant identity and correct sandbox/live account, determine authoritative status, map the provider's merchant order reference to the stored UUID, parse amounts exactly into minor units, and return a stable transaction/event identity, original paid-at, validation reference and SHA-256 of canonical validation evidence. Do not use raw callback amount/status as authoritative, accept callback-supplied validation URLs, follow arbitrary redirects, or persist provider responses containing sensitive data. Provider success-ACK rules must be implemented from its current documentation; the generic JSON ACK is not a claim of gateway compatibility.

- `payment-order`: JWT verification enabled; live Auth lookup; exact buyer request shape `{product_code,idempotency_key}`; server-selected adapter; server-priced order; hosted session boundary; explicit HTTPS checkout-origin allowlist. Session failure leaves a retryable pending order. A future adapter must use order ID as its provider session idempotency key to avoid multiple payable sessions.
- `payment-notification`: no buyer JWT; provider validation must succeed before any RPC. Unknown/unconfigured provider always returns 503. Request input is bounded to 16 KiB; replies expose no customer/receipt/entitlement data and no raw upstream errors.
- `reconcileOrder`: uses the same validation contract and transaction, including exact order/provider/environment binding. No scheduler has been configured.

There are two independent gates: a real compiled provider adapter and database mode/provider/product configuration. Both stay closed. Sandbox writes use only `payments.sandbox_entitlements`; switching mode does not turn sandbox receipts into live grants.

## Tests and verification

Run from `safenest`:

```sh
npm test
node --test supabase/functions/protection-access/test/*.test.mjs
npm run build
```

The added payment suites cover default-off configuration; server-price snapshot/retry; disabled products/providers; buyer/amount/provider override rejection; anonymous/authenticated write denial; exact matches; safe integer money; malformed evidence; forged HTTP callbacks; provider/session outages; checkout-origin checks; duplicate deliveries; cross-order/account receipt reuse; late delivery/expiry; pending/failure/cancellation ordering; refund/chargeback tombstones; finite live and sandbox windows; isolated sandbox access; account-deletion cascade; reconciliation binding; and injected failure after grant insertion proving transactional rollback.

All payment fixture accounts, prices and receipts exist only in a fresh local PGlite PostgreSQL database. Even the live-branch test runs there, without network access or project credentials. PGlite uses one connection, so these tests verify transaction/constraint/replay behavior, not genuine multi-session race scheduling. A real multi-connection sandbox test remains part of integration acceptance, along with genuine provider notifications and payment-to-Android activation.

No test creates or revokes a production entitlement. Live deployment verification may inspect structure/privileges, count rows and call disabled endpoints; it must not seed paid production fixtures.

### Verified deployment — 7 October 2026 (Helsinki)

Both migrations are applied to the existing project, with the exact server-recorded versions above preserved in source. Existing migration versions remain unchanged. `payment-order` version 1 is deployed with JWT verification on; `payment-notification` version 1 is deployed with JWT verification off and its fail-closed provider-validation boundary.

- 46 account/security/payment tests and 9 protection-access tests passed (55 total; 24 new payment tests). Website build and both Edge entrypoint bundles passed. The 12 ledger tests passed again after the FK-index/client-policy hardening.
- Live SQL verified all seven payment tables have RLS, customer schema access is denied, both RPCs are security invoker with empty search_path, service execution is allowed and anonymous/customer execution is denied. Service SELECT on `auth.users` remains false.
- Live service-role probes verified both RPCs reject `live` processing with `payment_processing_disabled`, before writes. The probe transaction was rolled back and created no orders or grants.
- An HTTP request without a buyer JWT to `payment-order` returned 401. A forged `verified:true` paid notification returned 503 / `payment_provider_not_configured` with `Cache-Control: no-store`.
- Live mode is `disabled`. Providers, products, orders, events, receipts, sandbox grants and production entitlements all remain zero. No live buyer checkout or real merchant sandbox purchase was attempted.
- The final security advisor has no payment findings; its existing [leaked-password protection warning](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection) remains unchanged. Performance reports only [unused indexes](https://supabase.com/docs/guides/database/database-linter?lint=0005_unused_index) on empty/new or existing tables and existing catalogue [multiple-policy warnings](https://supabase.com/docs/guides/database/database-linter?lint=0006_multiple_permissive_policies). Payment FK indexes are covered. Keep the required constraint/FK/reconciliation indexes despite their initial unused status.

## Remaining before checkout activation

Approved merchant/sandbox credentials in server secret settings; final seller identity, support and retention/refund policy; approved products/currencies/durations and payment channels; a concrete provider adapter; genuine sandbox hosted checkout and notification/reconciliation evidence; independent-session race testing; customer receipts and operator tooling; scheduled reconciliation/expiry operations; Android activation/expiry/revocation checks; explicit live-mode approval. The consumption-only Play app remains unchanged.

References: https://supabase.com/docs/guides/database/functions ; https://supabase.com/docs/guides/functions/auth-headers ; https://www.postgresql.org/docs/current/explicit-locking.html ; https://developer.sslcommerz.com/doc/v4/ .
