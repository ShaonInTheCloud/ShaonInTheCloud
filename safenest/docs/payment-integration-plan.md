# SafeNest payment implementation plan

Current state: credential-independent order/receipt processing and transactional entitlement issuance are implemented and tested in an isolated PostgreSQL runner. No concrete merchant adapter, credentials, approved products or live checkout exist. Checkout remains inactive; unverified HTTP callbacks cannot issue entitlements. Do not create sample paid rows in production or treat client flags as payment evidence.

See `payment-foundation.md` for the schema, server trust boundary, disabled deployment and test evidence. Provider-specific hosted checkout/notification validation, genuine sandbox integration, scheduling and commercial approval remain open.

## Website path

Use a hosted Bangladesh gateway checkout after merchant onboarding. SSLCOMMERZ is one candidate with server session/IPN/validation APIs; confirm its currently enabled international cards and wallet channels with the merchant account. The documentation's channel names are not proof that every wallet is enabled for this business.

The implementation should authenticate the buyer, price an order server-side, request a gateway session, validate provider notifications independently, match the stored order/amount/currency and issue one finite entitlement transactionally. Idempotency must reject duplicate grants. Refunds, chargebacks and provider reconciliation must update the entitlement. Store no card/CVV data. Keep sandbox and live credentials/routes separate.

Required operator inputs: approved merchant identity, sandbox store ID/password, final products/prices, refund/cancellation policy, support address, allowed channels and a live-mode approval after sandbox evidence. Store secrets only in Supabase secret settings.

## Play path

The prepared `play` build is consumption-only: it signs into existing accounts and has no purchase/checkout button. If in-app subscription sales are desired, configure Play products and Billing, then verify purchase tokens through the Play Developer API on the server. Bind a purchase to one authenticated account; handle pending, renewal, cancellation, expiry, grace/hold and refund/revocation; acknowledge valid initial purchases. Real-time notifications and scheduled reconciliation are required for reliable expiry/revocation.

Required inputs: Play Console app/products, API service account permission, server secrets, test licenses, notification delivery, signing certificate and approved reviewer/test accounts. Client receipt text or a successful billing callback alone must never grant paid access.

Crypto is deferred until provider onboarding and applicable requirements are established. It does not block the first release.

Official references: https://developer.sslcommerz.com/doc/v4/ ; https://developer.android.com/google/play/billing/security ; https://support.google.com/googleplay/android-developer/answer/10281818 .
