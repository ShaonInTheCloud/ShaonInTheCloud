# Card + crypto payments and the Global edition — 16 October 2026

Owner decision (16 Oct 2026): SafeNest ships in two editions — **Bangladesh** (BDT) and
**Global** (USD) — and takes payment by **Mastercard/Visa card** and by **cryptocurrency**,
with crypto settling to the owner's Bybit account. Card processing uses **Stripe**, because
Stripe cannot onboard a business registered in Bangladesh but does onboard the owner's Finnish
sole-trader business, and its EU card fees are the lowest mainstream option available.
Razorpay was ruled out (Indian merchants only). Crypto uses **NOWPayments**, a hosted
non-custodial checkout whose payout wallet is set in its dashboard — SafeNest code never holds
a wallet key. SSLCOMMERZ stays in the codebase as an optional Bangladesh local-wallet method.

Everything in this change is **disabled by default**. No provider is enabled, no USD price is
enabled, checkout stays closed, and the hosted database is unchanged until the owner applies
the migration and sets the secrets below. 85/85 tests pass (`npm test`).

## What changed

| Area | Change |
|---|---|
| `supabase/migrations/20261016050000_card_crypto_gateways.sql` | Provider rows gain `checkout_url_pattern`, `currencies[]`, `checkout_window_seconds`; products gain `region` (`bd`/`global`); sessions gain `provider_payment_id`. Seeds disabled rows for `stripe` and `nowpayments` (sandbox + live) and six disabled USD products (`global_monthly_v1` $4.99, `global_quarterly_v1` $12.99, `global_annual_v1` $39.99). `create_payment_order` now honours the provider's payment window (30 min card, 2 h crypto) and rejects unsupported currencies. `payment_order_lookup` and `payment_reconciliation_batch` take a provider; `payment_session_save` validates the redirect URL against the provider's pattern; new `payment_session_note`. |
| `supabase/functions/_shared/stripe.mjs` | Stripe Checkout adapter: card-only hosted session, webhook signature check (5-minute window), then an **independent re-fetch** of the session and its charge from `api.stripe.com` before anything touches the ledger. |
| `supabase/functions/_shared/nowpayments.mjs` | NOWPayments adapter: hosted invoice, IPN HMAC-SHA512 check over key-sorted JSON, then an **independent re-fetch** of the payment. Only `finished` grants access; `confirming`/`waiting` are acknowledged and left pending; `partially_paid` goes to review. |
| `supabase/functions/_shared/payments.mjs` | Adapter registry. The buyer may choose a configured method; callbacks are routed by signature header (`stripe-signature`, `x-nowpayments-sig`, form-encoded = SSLCOMMERZ) or `?provider=`. |
| `payment-order`, `payment-notification`, `payment-catalogue`, `payment-reconcile` | Accept/route the provider; catalogue returns `providers` with their allowed checkout origins and filters products to open providers; reconcile walks every configured provider. `PAYMENT_RECONCILE_KEY` is the new name for the reconcile header key (`SSLCOMMERZ_RECONCILE_KEY` still works). |
| `web/account.html`, `web/billing.js`, `web/account.css` | Edition picker (Bangladesh ৳ / Global $), payment-method picker (Card / Crypto / SSLCOMMERZ), billing-address fields only for SSLCOMMERZ, redirect allowed only to the chosen provider's known origin. |
| `website/global/` | English-only Global edition: `index.html`, `pricing.html`, `global.css`. Bangladesh pages gained a "Global · USD" switch in the header. |

Not changed: the Android app, entitlement checks, `protection-access`, trials, operator controls.

## Owner checklist (in this order)

1. **Stripe account** — create at stripe.com under the Finnish business. In *Developers → API keys*
   copy the **test** secret key (`sk_test_…`). In *Developers → Webhooks* add an endpoint
   `https://kflenmeizngmafwnwhgv.supabase.co/functions/v1/payment-notification?provider=stripe`
   listening to `checkout.session.completed`, `checkout.session.async_payment_succeeded`,
   `checkout.session.expired`; copy its signing secret (`whsec_…`). Restrict payment methods
   to cards in *Settings → Payment methods* (Mastercard and Visa are on by default; turn off
   anything you do not want).
2. **NOWPayments account** — create at nowpayments.io, verify, then in *Store settings*:
   set the **payout wallet** to your Bybit USDT (TRC-20 is cheapest) deposit address, copy
   the **API key**, set an **IPN secret** and the IPN URL
   `https://kflenmeizngmafwnwhgv.supabase.co/functions/v1/payment-notification?provider=nowpayments`.
   For testing use the sandbox at account-sandbox.nowpayments.io (separate key).
3. **Supabase secrets** (*Edge Functions → Secrets*), sandbox first:
   ```
   STRIPE_ENABLED=true            STRIPE_ENVIRONMENT=sandbox
   STRIPE_SECRET_KEY=sk_test_…    STRIPE_WEBHOOK_SECRET=whsec_…
   STRIPE_CHECKOUT_ENABLED=true
   NOWPAYMENTS_ENABLED=true       NOWPAYMENTS_ENVIRONMENT=sandbox
   NOWPAYMENTS_API_KEY=…          NOWPAYMENTS_IPN_SECRET=…
   NOWPAYMENTS_CHECKOUT_ENABLED=true
   PAYMENT_RECONCILE_KEY=<at least 32 random characters>
   ```
   Live later adds `STRIPE_LIVE_APPROVED=true`, `NOWPAYMENTS_LIVE_APPROVED=true`, live keys and
   `*_ENVIRONMENT=live`. Both providers must be in the same environment as
   `payments.configuration.mode`.
4. **Deploy** — run the *SafeNest deploy backend* workflow with `apply` (it pushes the migration
   and redeploys the six payment functions). Confirm in the dashboard that `payment-notification`,
   `payment-return`, `payment-reconcile` and `payment-catalogue` show *Verify JWT* off.
5. **Enable rows** in the SQL editor (sandbox):
   ```sql
   update payments.configuration set mode='sandbox';
   update payments.providers set enabled=true where environment='sandbox' and provider in ('stripe','nowpayments');
   update payments.products set enabled=true where environment='sandbox';  -- BDT and USD prices
   ```
   Adjust the USD amounts first if $4.99 / $12.99 / $39.99 are not the prices you want:
   `update payments.products set amount_minor=… where product_code='global_monthly_v1';`
6. **Test end to end**: log in at mysafenestbd.com/account, pick *Global*, pay with Stripe test
   card `4242 4242 4242 4242`, then pay a sandbox crypto invoice. Each should show
   "Sandbox payment verified" after *Refresh payment status*. Sandbox never grants real access.
7. **Reconcile scheduler** — set repository secrets `SAFENEST_RECONCILE_URL` and
   `SAFENEST_RECONCILE_KEY` (same value as `PAYMENT_RECONCILE_KEY`) so the 10-minute workflow
   catches a missed webhook. Note: a NOWPayments payment whose IPN never arrived at all has no
   payment id on our side and is reported for review; check the NOWPayments dashboard and use
   `operator_grant_reviewer_access` / `operator_refund_order` if needed.
8. **Go live**: repeat 3–5 with live keys and `mode='live'`, then set `STRIPE_LIVE_APPROVED` and
   `NOWPAYMENTS_LIVE_APPROVED`. Publish final prices, refund terms (crypto is non-reversible) and
   the Global edition pages, and update the "Checkout is closed" copy on both editions.

## Design notes

- The webhook body is never evidence. Both adapters re-fetch the payment from the provider with
  the merchant key and compare amount, currency and the order's `provider_order_id`; a
  tampered-but-signed body is rejected (`payment_order_mismatch`).
- Crypto orders get a 2-hour payment window because on-chain confirmation can be slow; card
  orders keep 30 minutes. The Stripe session expiry mirrors the order expiry.
- Mastercard-only (rejecting Visa) is possible in Stripe's dashboard but costs sales without
  saving fees; the code sends `payment_method_types=card` and leaves brand rules to Stripe.
- Refunds remain an operator action (`operator_refund_order`) after refunding at the provider;
  the webhook does not auto-revoke on `charge.refunded`.
