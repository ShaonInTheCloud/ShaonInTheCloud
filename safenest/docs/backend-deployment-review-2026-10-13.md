# Payment deployment separation — 13 October 2026

Historical review at the stated baseline. The [14 October consolidation](payment-integration-review-2026-10-14.md) records the integrated implementation and newer local validation; it does not reverify these hosted observations.

Reviewed source baseline: `7009556e6f861b7242fd6f3f2f55c51b62449b63` on `main`, including merged [PR #10](https://github.com/ShaonInTheCloud/ShaonInTheCloud/pull/10) and [PR #11](https://github.com/ShaonInTheCloud/ShaonInTheCloud/pull/11).

No production workflow was dispatched. No functions, secrets, SQL, checkout configuration or entitlements were changed in the hosted project. Hosted inspections used read-only function metadata/source and migration history.

## Separate operations

| Workflow | Default | Explicit production action | Credentials |
| --- | --- | --- | --- |
| `safenest-deploy-backend.yml` | Local config/authentication tests and function scope preview; no hosted calls | `deploy=deploy-payment-functions`, dispatched from `main`, after preview succeeds | Supabase access token only |
| `safenest-migrate-backend.yml` | Linked migration history and `db push --linked --dry-run --skip-vault` | `apply=apply-migrations` plus `reviewed_sha` equal to the run's full commit SHA | Supabase access token and database password |

Both are manual only, use `contents: read`, keep the `production` environment and pinned Actions/CLI, disable persisted checkout credentials, and share non-cancelling concurrency `safenest-deploy-backend`. The function preview has no production environment or secrets. Production jobs run only from `main`. A legacy `apply=apply` no longer authorizes either operation.

Function deployment redeploys only the six named payment functions via `functions deploy <name> --project-ref ... --use-api`. It has no database link, database credentials, migrations or global JWT override. Deployments are sequential and stop on failure; they are not atomic across six functions. Check versions/configuration before retrying a partial deployment.

Migration runs print the immutable source SHA and SQL checksums, list history, and preview pending files before any apply step. A failed preview/history check stops application. No history repair, include-all, roles, seeds or Vault secret synchronization is enabled. The dry run lists pending files; it does not execute SQL or establish that the SQL will succeed. Review every listed file and test it in a disposable database before separately authorizing application. The SHA guard binds reviewed repository content, not a frozen remote database snapshot: rerun/review the preview if hosted history changes.

## Authentication verified against repository and hosted source

The downloaded notification v3 and reconciliation v2 bundles match the checked-in entrypoints, handlers and shared modules at the baseline. No production payment requests were sent.

| Function | Hosted `verify_jwt` observed | Authorization in source |
| --- | --- | --- |
| `payment-notification` | false | POST only; bounded body; fixed SSLCOMMERZ adapter; provider validation must complete before ledger processing |
| `payment-return` | false | GET only redirects to a fixed checking page; POST delegates to notification validation; redirects never prove payment |
| `payment-reconcile` | false | POST only; configured key must be at least 32 characters; equal length and full XOR comparison; empty body; then provider validation for each order |
| `payment-catalogue` | false | Public prices only; disabled/unconfigured provider returns unavailable |
| `payment-order` | true | Also validates Bearer token with `/auth/v1/user`, requires a confirmed non-anonymous account, derives buyer identity server-side |
| `payment-status` | true | Also validates live customer authentication and supplies authenticated user ID to the owner-scoped status RPC |

The four settings from PR #10 are already active in hosted metadata; a redeploy is not needed merely to turn those four flags off. Explicit `true` entries now preserve the two customer endpoints' observed gateway restrictions instead of relying on defaults. Their handler CORS paths do not establish hosted browser preflight behavior; validate the actual browser flow separately before enabling checkout, without weakening authentication as part of this deployment change.

In `_shared/sslcommerz.mjs`, notification form fields select a stored order and validation reference, not trusted amount/status. The fixed provider API must report VALID/VALIDATED and DONE, match transaction and validation references, match the configured store ID when returned, match both BDT amount fields, and have risk level zero. Merchant credentials and sandbox/live environment are selected on the server. Live mode requires `SSLCOMMERZ_LIVE_APPROVED=true`; session creation separately requires `SSLCOMMERZ_CHECKOUT_ENABLED=true`. Validated evidence is whitelisted before the private ledger RPC. SQL access restrictions and duplicate/competing transition protections remain unchanged.

The reconciliation comparison traverses every character for equal-length keys; length mismatches return early. This is a source-level check, not a runtime timing guarantee. Negative tests verify that missing, short, mismatched-length and equal-length wrong keys never reach RPC/network processing.

## Hosted migration history drift

Read-only inspection confirms `20261012120000_operator_controls.sql` is absent. Contrary to the older October 12 note, other history entries do not match the repository:

| Purpose | Repository version | Hosted version |
| --- | --- | --- |
| Trial subscriptions | `20261007001224` | `20261007001933` |
| Support desk | `20261007004620` | `20261007005106` |
| Trial entitlement index | No matching migration file | `20261007040839` |
| Intelligence admin policy consolidation | No matching migration file | `20261007061659` |
| Operator controls | `20261012120000` | Absent |

These are history observations, not proof of equivalent schemas. Reconcile deployed SQL and historical versions in a separate reviewed change before approving a push. Do not blindly repair migration history or reapply trial/support SQL. The migration workflow should fail closed on incompatible history; this review did not link or push the hosted database.

## Remaining owner/dashboard checks

- GitHub Settings → Environments → `production`: verify required reviewers, prevention of self-review/bypass as appropriate, and `main` deployment restrictions. Naming an environment in YAML does not configure these protections. Repository/environment secret presence and access-token scope were not inspected. Keep the DB password confined to migration operations; the functions job does not receive it.
- Supabase Edge Function secrets: verify the existing provider/checkout/live approval flags and reconciliation key without exposing values. Hosted metadata/source reads do not reveal secret values or demonstrate a successful gateway callback. Do not enable checkout or change these flags as part of this PR.
- GitHub reconciliation secrets: verify URL ownership and that its key matches the hosted key, with at least 32 random characters. Secret presence and actual scheduled operation remain unverified; no scheduled run was dispatched.
- Review current database migration drift and the operator-controls SQL before any separate authorization to apply migrations. Reviewer access and refunds are separate operator actions; this change invokes neither.

## Non-production validation

- `npm ci --ignore-scripts` succeeded; `npm test`: 84 tests passed, including PGlite execution of existing SQL, callback/provider validation, owner scoping and new workflow/authentication regressions.
- Local function preview: 17 tests passed; full targeted deployment/config/authentication suite: 22 passed.
- Mock CLI executes the exact workflow shell bodies: six functions only, stop on deployment failure, migration preview remains dry-run, missing/stale reviewed SHA is rejected (including shell metacharacters).
- `actionlint` v1.7.12 validated both manual workflows; YAML and TOML parsed locally; `git diff --check` passed.
- Pinned Supabase CLI 2.120.0 help confirmed deploy/project/API flags and migration dry-run/linked/Vault flags. No live deployment, migration dry run, gateway payment, callback, reconciliation or entitlement grant was executed.

References: [Supabase per-function configuration](https://supabase.com/docs/guides/functions/function-configuration), [Supabase CLI](https://supabase.com/docs/reference/cli/supabase-db-push). Config-based JWT settings affect hosted functions when deployed; SQL application is an independent operation.
