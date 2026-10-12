# Payment wiring notes — 12 October 2026

Found while reviewing the payment path (67/67 existing tests pass; no payment logic changed).

1. **Gateway callbacks need `verify_jwt = false`.** SSLCOMMERZ calls `payment-notification` and `payment-return` without a Supabase JWT, and the scheduler calls `payment-reconcile` with a key header. `config.toml` did not list them, so a config-based deploy would have rejected every callback with 401. They are now listed; each handler authenticates itself (provider validation API, constant-time reconcile key). `payment-catalogue` returns only public prices. Check the live dashboard shows "Verify JWT" off for these four functions, since `config.toml` only applies when the functions are deployed from it.
2. **Nothing scheduled `payment-reconcile`.** A missed IPN would leave a paid order pending. `.github/workflows/safenest-payment-reconcile.yml` calls it every 10 minutes. It does nothing until the owner sets repository secrets `SAFENEST_RECONCILE_URL` (the function URL) and `SAFENEST_RECONCILE_KEY` (the same value as the function's `SSLCOMMERZ_RECONCILE_KEY`, at least 32 random characters).
3. **Still owner inputs:** merchant sandbox store ID/password, final products and prices in `payments.products`, enabling the provider and mode rows, then a sandbox payment end to end. Do not seed paid rows in production.
