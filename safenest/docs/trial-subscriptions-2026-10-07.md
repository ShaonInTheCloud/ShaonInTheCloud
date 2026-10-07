# Trial and subscription implementation — 7 October 2026

Owner requested a three-day trial followed by payment-method verification and automatic subscription charges. Prices: BDT 379 monthly; BDT 999 quarterly and BDT 3,799 annual selected as the implementation defaults. Paid catalogue windows are explicitly 30, 90 and 365 days. Existing issued orders/windows retain their original snapshots.

## Implemented

- One 72-hour server-issued trial per confirmed account. Start is explicit, never triggered by signup, login, profile editing or plan selection alone.
- A private trial ledger stores the chosen future plan. Concurrent starts and retries return the same entitlement and end time; expired/revoked trials are not restarted. Clients cannot supply ownership, start/end times, prices or paid status.
- `start-trial` requires a user JWT and verifies the live Auth identity and confirmed email before using its service-only RPC. The privileged row lock is in the unexposed payments schema, with fixed search_path and execution revoked from PUBLIC/anon/authenticated. The public entry point is service-only SECURITY INVOKER. Every new table has RLS and clients have no table access.
- Updated private paid catalogue for both sandbox and live. All new offers remain disabled; payment configuration remains disabled. No seller, provider approval, wallet verification, mandate, receipt or real charge is fabricated.
- Android 0.4.8 source adds trial-start consent and plan choice, verifies trial/quarterly windows and uses the existing finite commitment/expiry flow. Expiry stops the DNS service and releases expired app/managed guard state; server-time anchoring and clock rollback checks are preserved. Rooted/private-storage tampering remains outside its trust model. The lab build remains explicitly local QA only.
- Website and generated terms/privacy/support/checkout explain trial status, exact durations, expiry, and inactive automatic billing. Existing DNS/full-traffic limits and unresolved business/support/retention items remain.

## Release gates and actual limits

The current public download is 0.4.0, not the new trial build. Website trial enrolment stays disabled (`releaseReady:false`) until a compatible Android build passes trial start, confirmed login, consent, exact expiry and cleanup tests. A CI-produced debug artifact is not a signed customer or approved Play release. Local Android build was blocked downloading Gradle by network access; GitHub CI is the alternative validation path.

Automatic billing is **not implemented or active**. Existing SSLCOMMERZ hosted checkout validates one-time receipts, not a reusable recurring mandate. bKash separately offers Subscription Payment with explicit authorization; tokenized checkout alone still requires PIN and must not be treated as unattended debit. No recurring API/merchant credentials or capability confirmation is available for this SafeNest project. Nagad and SSLCOMMERZ recurring capability for this merchant remain unconfirmed.

Before recurring billing can ship: confirm the merchant/provider recurring contract and actual API, implement hosted payment-method verification and provider-verified mandate storage (no wallet PIN/OTP/card credentials in SafeNest), record separate authorization with exact amount/cycle/first charge date, implement cancellation and failure handling, idempotent charge scheduling and independently verified settlement/reversal callbacks, test end-to-end in provider sandbox, then enable live settings. At trial end without a verified paid window, access expires. No unverified, failed or pending payment extends access. App renewals require fresh verification/consent; an offline cached period does not extend itself.

The trial claim is account-scoped and cascades on account deletion. Recreating another account can obtain another trial; this is not a one-person anti-abuse guarantee. Final retention periods and transaction/deletion obligations remain unresolved before paid launch.

## Verification

62 local source tests passed, plus a focused 11-test run after the service-only private function change. Production migration and JWT-protected start-trial deployment succeeded. A rolled-back production fixture verified the exact 72-hour duration, unchanged retry ID/end time and selected plan, no restart after expiry, and denied client RPC/table access. No test account, entitlement or transaction from that check remains. Anonymous live start-trial requests receive 401. Security advisor reports no new errors; its existing leaked-password-protection warning remains on the Free plan.

Primary sources:
- https://www.bkash.com/page/subscription-payment-terms-conditions
- https://www.bkash.com/en/page/tokenized_checkout
- https://developer.sslcommerz.com/doc/v4/
- https://supabase.com/docs/guides/functions/auth
- https://supabase.com/docs/guides/database/functions
