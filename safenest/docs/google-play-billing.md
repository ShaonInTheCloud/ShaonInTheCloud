# Google Play Billing — how SafeNest sells and verifies subscriptions

Added 17 October 2026 (0.5.0). Applies to the `play` flavor only; the direct, lab and owner-test flavors have no store.

## Product shape

| Play Console | Value |
|---|---|
| Product type | Subscription |
| Product ID | `safenest_protection` |
| Base plans | `monthly` (P1M), `quarterly` (P3M), `annual` (P1Y) — auto-renewing |
| Prices | Set per country in Play Console (BDT for Bangladesh). The app shows Play's localized price; nothing is hard-coded. |

The base plan ID **is** the SafeNest `plan_code`. Adding a base plan with any other ID is rejected by the server (`unknown_play_plan`).

## Flow

1. User signs in inside the app (`SubscriptionClient.signIn`): a short-lived Supabase session; neither password nor JWT is persisted.
2. `PlayBillingProvider.purchase` launches Play's purchase sheet with `setObfuscatedAccountId(<SafeNest user id>)`. This binds the purchase to the account at Google's side.
3. On success the app posts the purchase token to `play-billing-verify` (JWT-authenticated). The server:
   - mints a service-account access token and calls `purchases.subscriptionsv2.get`;
   - rejects tokens whose `obfuscatedExternalAccountId` is another user (`409 purchase_bound_to_other_account`), test purchases unless `PLAY_ALLOW_TEST_PURCHASES=true` (`403`), unknown tokens (`404`), pending payments (`active:false, pending:true`);
   - calls `process_play_subscription`, which upserts `payments.play_subscriptions` (one row per token, bound to one user forever) and upserts `public.protection_entitlements` (`provider='google_play'`, `provider_reference=<token>`);
   - acknowledges the purchase with Google (required within 3 days or Google refunds it) — only after the ledger write succeeded;
   - replies in the same shape as `protection-access`, so `ProtectionCommitment.cacheVerified` is reused unchanged.
4. Renewals, cancellations, grace, hold, revocation and refunds arrive as Real-time Developer Notifications → Pub/Sub push → `play-billing-rtdn?key=<PLAY_RTDN_SECRET>`. The handler ignores the notification body except for the token, re-fetches from Google and re-runs the same RPC with `p_user_id = null` (the ledger already knows the owner). Unknown tokens return 200 so Pub/Sub stops retrying.
5. "Restore purchase" = sign in → `queryPurchasesAsync` → verify each owned token.

Entitlement status mapping: `ACTIVE`, `IN_GRACE_PERIOD`, `CANCELED` (still paid until expiry) → `active` while `expires_at > now`; `EXPIRED`/`ON_HOLD`/`PAUSED` → `expired`; developer-initiated cancellation (refund) → `revoked`.

The device-side commitment never extends itself: when a period ends, protection releases, and the user re-verifies (sign in → Restore purchase) to start the renewed period. This keeps the consent-per-period rule from `subscription-enforcement.md`.

## Secrets (Supabase → Edge Functions → Secrets)

| Name | Value |
|---|---|
| `PLAY_PACKAGE_NAME` | `com.safenest.app` |
| `PLAY_SERVICE_ACCOUNT_JSON` | Full JSON key of a Google Cloud service account granted, in Play Console → Users and permissions, *View financial data* and *Manage orders and subscriptions* for this app |
| `PLAY_RTDN_SECRET` | ≥ 48 random characters; the same value goes into the Pub/Sub push endpoint URL |
| `PLAY_ALLOW_TEST_PURCHASES` | `true` only while licence testers / the Play reviewer need free purchases; unset for production |

`config.toml`: `play-billing-verify` has `verify_jwt = true`; `play-billing-rtdn` has `verify_jwt = false` (Google's push carries no Supabase JWT; the secret in the URL is the gate).

## Deploy

`safenest-deploy-backend.yml` (manual) applies `20261017060000_google_play_billing.sql` and deploys both functions. Then in Play Console → Monetization setup, enable RTDN with the Pub/Sub topic, and add a push subscription in Google Cloud to `https://kflenmeizngmafwnwhgv.supabase.co/functions/v1/play-billing-rtdn?key=<PLAY_RTDN_SECRET>`. Send the test notification; the function logs `ignored: not_subscription`.

## Tests

`tests/play-billing.test.mjs`: normalizer, ledger RPC against the real migration (PGlite), verify handler (auth, binding, pending, acknowledge, test-purchase gate, missing config) and RTDN handler (secret gate, envelope decoding, unbound tokens). A disposable RSA key exercises the real JWT signing path; no Google credentials are used.
