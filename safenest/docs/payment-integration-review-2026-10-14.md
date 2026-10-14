# Payment PR consolidation — 14 October 2026

The integration branch combines the compatible changes in drafts #13, #14 and #15 against `main` at `7009556e6f861b7242fd6f3f2f55c51b62449b63`. It uses one Node reconciliation runner and preserves separate function deployment and migration controls. This review performs no hosted payment calls, workflow dispatches, deployments, migrations, activation or credential changes.

## Reviewed versions

GitHub metadata and fetched Git refs agree on the following versions. All three PRs remain open drafts targeting the same main commit; no discussion/review comments were returned on inspection.

| PR | Head SHA | Unique contribution retained |
| --- | --- | --- |
| [#13](https://github.com/ShaonInTheCloud/ShaonInTheCloud/pull/13) | `d5540ad9046549d477315ac0a80409121e549ca7` | Accurate callback auth comments, incomplete-configuration failure, URL suffix attack cases, mixed provider/ledger failure isolation, production-fixture reversal burst and SQL batch rotation/cooldown/cutoff tests; historical audit |
| [#14](https://github.com/ShaonInTheCloud/ShaonInTheCloud/pull/14) | `8df1b493a6b2d7a0eeaedfde98a1e42af7047389` | Node scheduler, explicit enable variable, stronger header-safe key checks, redirect rejection, sanitized aggregate reporting, retryable handler failures, legacy response handling, delayed recovery/duplicate tests; historical verification |
| [#15](https://github.com/ShaonInTheCloud/ShaonInTheCloud/pull/15) | `458679c8fd3579fcc18f14c26cadedd88086559b` | Independent manual function and migration workflows, local preview, production/main/action gates, reviewed-SHA migration guard, shared non-cancelling deployment concurrency, mock CLI/auth tests; historical deployment review |

## Overlap and conflict resolution

| Area | Comparison | Integrated decision |
| --- | --- | --- |
| Reconciliation workflow | #13 improves shell/curl; #14 replaces it with Node. Both enforce the exact endpoint and key safety. | Keep #14 runner; discard duplicate shell implementation. Port #13 attack fixtures and partial-configuration behavior into runner/workflow tests. Requests remain bounded by the Node runner's 60-second abort deadline; #13's curl-specific 10-second connection flag does not apply to this implementation. No automatic retry. |
| Missing credentials | #13 distinguishes both missing (skip) from partial (fail); #14 skips if either is missing. | Without explicit enablement, skip. With enablement and both missing, skip. With enablement and only one present, fail before any request. |
| TOML | #13 changes comments only. #14/#15 both add `payment-order` and `payment-status` with `verify_jwt=true`, at different positions. | The raw combination silently produces duplicate TOML tables. Keep exactly one of each with `true`, retain #13's accurate comments, and add a uniqueness regression. All four callback/catalogue/reconcile settings remain `false`. Parse TOML independently. |
| CI path filters | #14 adds reconciliation; #15 adds function deployment and migrations. | Use their union for push/main and pull_request checks. |
| Reconciliation response | #13 tests expect `{processed:1,review:2}`/HTTP 200 for provider+ledger outages; #14 separates `failed` and returns 503. #15's empty-batch auth test also expects the older shape. | Keep #14 failure contract; adapt #13's mixed-failure assertion and #15's empty-batch assertion to `failed`. Retain assertions that successful orders are processed despite other failures. |
| Launch notes | All three edit the same file; #13/#14 conflict on scheduler description. | Document Node enablement, partial-configuration failure, recovery limits, separate migration controls and links to all evidence. Retain historical reports as dated evidence. |

The integration additionally scopes reconciliation URL/key environment variables to the invocation step, excluding checkout/setup. Checkout does not persist Git credentials. CLI smoke tests execute the actual workflow command with disabled or invalid configuration; all enabled success-path requests in unit tests use injected fetch mocks, never production credentials.

## Preserved security and payment behavior

- The exact endpoint is pinned; userinfo, queries, fragments, attacker suffixes, HTTP and other projects fail before sending a key. Keys must be 32–200 printable ASCII characters without whitespace. Redirects fail. Error reporting omits keys, configured URLs and arbitrary response bodies.
- Enabled requests are empty POSTs. The handler checks its own key before any provider/RPC access. Provider validation still precedes ledger writes. Callback bodies, redirects and browser status flags do not authorize paid access.
- Provider/network/ledger outages produce a non-success response and scheduler failure. Ambiguous evidence is counted for review without a grant. Legacy responses with nonzero review fail because they cannot distinguish outages. New review-only responses generate warnings. Counts must be nonnegative safe integers summing to at most three.
- Notification and return handlers, provider adapters, ledger SQL, migration files, checkout/live gates and entitlement rules are unchanged. Config leaves unauthenticated gateway callbacks reachable and retains JWT plus handler authentication for customer order/status endpoints. This matches Supabase's per-function configuration model; live delivery/preflight is not proven by local tests.
- #15's function and migration workflow files and mock deployment tests are byte-identical to the reviewed head. Function preview has no hosted credentials/calls. Function deployment never links or pushes a database or receives its password. Migration application remains manual, main-only, production-environment gated, dry-run first, with exact reviewed SHA and no Vault synchronization. Neither workflow repairs history.

## Safe sequence

1. Review this single draft integration diff and its automated checks. Keep all three source drafts open until the replacement is accepted. If main or any source PR advances, compare those new changes and rerun affected checks before approval.
2. Prefer one separately authorized merge of the consolidated PR. If separate PRs are retained instead, integrate #15 first, then #14 with TOML/CI/auth-test resolution, then only #13's unique tests/comments/evidence. Do not merge #13's shell workflow on top of the Node runner.
3. Repository integration does not authorize production operations. Keep reconciliation enablement and checkout gates unchanged. After separate authorization, use only the function workflow for the six payment functions; verify versions/auth behavior after deployment. Its sequential deploy can leave mixed versions if one fails. The runner understands the old response shape until the handler change is separately deployed.
4. Resolve the migration-history drift documented in #15 and review every pending SQL file before separately authorizing migration application. No migration is required merely to deploy the handler/configuration changes. The reviewed SHA binds repository SQL, not an immutable hosted database snapshot.
5. Only with separate authorization, verify matching keys without exposing values and exercise sandbox IPN, duplicate replay, missed-IPN recovery and outage behavior. Explicitly enabling reconciliation or checkout is a separate decision.

After the consolidated replacement is accepted, #13, #14 and #15 would all be redundant at the reviewed heads. #13's implementation is mostly subsumed by #14, but its unique tests/evidence must not be dropped. This task leaves the original PRs open and does not merge anything.

## Validation and limits

Validation is local and uses fixture secrets, mock requests/CLI and disposable PGlite databases. Historical hosted claims in the three October 13 reports were not reverified by this consolidation; they are not assertions about current production state.

- Node 24.19.0; `npm ci --ignore-scripts`; full `npm test`: 100/100 pass.
- Protection-access suite: 9/9 pass; `npm run build`: pass.
- `actionlint` 1.7.12 on all four changed workflows: pass (ShellCheck unavailable; workflow shell bodies are exercised by mock-CLI tests).
- YAML parsing of all four workflows and Python `tomllib` parsing of config: pass; `git diff --check`: pass.
- PGlite tests exercise batch claims, cooldown, seven-day cutoff, duplicate replay, refund/chargeback tombstones and sandbox isolation. PGlite queues one backend; it does not establish independent-session PostgreSQL concurrency or production load tolerance.
- Android sources are unchanged; Android builds/device acceptance are outside this payment consolidation and were not run locally. Real gateway delivery, hosted browser preflight, environment reviewer/branch protections and operational recovery still require separate verification before launch.

References: [Supabase function configuration](https://supabase.com/docs/guides/functions/function-configuration), the three PRs above, and the dated [deployment review](backend-deployment-review-2026-10-13.md), [callback verification](payment-callback-verification-2026-10-13.md) and [callback audit](payment-callback-audit-2026-10-13.md).
