# Payment wiring notes — 12 October 2026

Found while reviewing the payment path (67/67 existing tests pass; no payment logic changed).

1. **Gateway callbacks need `verify_jwt = false`.** SSLCOMMERZ calls `payment-notification` and `payment-return` without a Supabase JWT, and the scheduler calls `payment-reconcile` with a key header. `config.toml` did not list them, so a config-based deploy would have rejected every callback with 401. They are now listed; each handler authenticates itself (provider validation API, constant-time reconcile key). `payment-catalogue` returns only public prices. Check the live dashboard shows "Verify JWT" off for these four functions, since `config.toml` only applies when the functions are deployed from it.
2. **Nothing scheduled `payment-reconcile`.** A missed IPN can leave a paid order pending. `.github/workflows/safenest-payment-reconcile.yml` is scheduled every 10 minutes. The reviewed follow-up requires the owner to explicitly set repository variable `SAFENEST_RECONCILE_ENABLED=true` as well as secrets `SAFENEST_RECONCILE_URL` (exactly `https://kflenmeizngmafwnwhgv.supabase.co/functions/v1/payment-reconcile`) and `SAFENEST_RECONCILE_KEY` (matching the function's `SSLCOMMERZ_RECONCILE_KEY`, 32–200 random printable ASCII characters without whitespace). Do not enable it or configure production credentials without owner authorization. See [the 13 October verification](payment-callback-verification-2026-10-13.md) for hosted evidence, failure reporting and recovery limits.
3. **Still owner inputs:** merchant sandbox store ID/password, final products and prices in `payments.products`, enabling the provider and mode rows, then a sandbox payment end to end. Do not seed paid rows in production.

## Operator controls (migration `20261012120000_operator_controls.sql`, not yet applied to the hosted database)

The SSLCOMMERZ adapter only reports successful payments, so refunds, and the reviewer test access that Play requires, had no controlled path. This adds three functions callable only by `service_role` (Supabase SQL editor), each writing to the append-only `payments.operator_actions` audit table:

```sql
-- Play reviewer account: finite 1–90 day access, no payment created
select public.operator_grant_reviewer_access('<user uuid>', 14, 'Play Console reviewer account');
-- End one entitlement now
select public.operator_revoke_entitlement('<entitlement uuid>', 'reason');
-- After refunding at the gateway: marks order/receipt refunded and revokes the access it issued
select public.operator_refund_order('<order uuid>', 'Customer refunded at gateway');
```

Tests (`tests/operator-controls.test.mjs`) run the real migrations in PGlite: duration and reason limits, audit rows, double-refund rejection, a late "paid" proof cannot restore refunded access, anon/authenticated cannot call or read, and the log cannot be edited even by the table owner. Apply with `supabase db push` after review; the hosted history otherwise already matches the repo.
