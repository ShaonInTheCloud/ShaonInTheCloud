# Trial acceptance — 7 October 2026

Enrollment remains disabled. The supplied artifact cannot run a customer trial;
the live backend's email/API acceptance portion passed. Device acceptance and
natural 72-hour expiry are still open. No paid entitlement or charge was issued.

## Supplied artifact

Source: `8457e300b9df596ee7fca61312f040fcf8dfee53`.
[Successful CI run](https://github.com/ShaonInTheCloud/ShaonInTheCloud/actions/runs/37551842395).
Artifact ID `11452914204`, `safenest-test-apk`.

- ZIP SHA-256: `69f8314e44bc9fa2920bb42806b011224c2e086f48c9cd8e6d3402acf8c7706f` (matches GitHub's digest).
- APK SHA-256: `b534000ec4bb3ee82c0cf6c4efe44d3f78cfca6559827f88540ca29d2ca6d903`.
- Actual DEX BuildConfig: `com.safenest.app.lab`, `0.4.8-test`, version code 25, `LOCAL_TEST_BUILD=true`.
- Lab mode shows Test tools instead of `SubscriptionScreen`; `ProtectionCommitment.cacheVerified` rejects lab sessions. This is a compatibility failure, not a failed server login and not evidence that the customer build passed.

`python3 scripts/inspect-trial-apk.py APK` inspects the actual binary without
Android SDK dependencies. It exits 2 for the supplied lab artifact. A compatible
result only permits proceeding to device tests; it never means acceptance passed.

## Verified production API portion

Project: `kflenmeizngmafwnwhgv`. Deployed `start-trial` v1 and `protection-access`
v3 were used through their real HTTPS endpoints. A fresh owned email alias was
created, confirmed via its real delivered email and deleted afterward. No admin
confirmation shortcut, paid receipt or service credential was used by the client.

| Check | Evidence |
| --- | --- |
| Email/login | Signup 200; unconfirmed login 400 `email_not_confirmed`; delivered confirmation 303 with a session; confirmed password login 200. |
| Read-only lookup | No active access before explicit start; login/lookup did not issue an entitlement. |
| Server authority | Client `ends_at`/`paid` overrides rejected with 400. |
| Plan/duration | Quarterly selected; trial started `2026-10-07T01:12:55.707888+00:00`, ending `2026-10-10T01:12:55.707888+00:00`, exactly 72 hours. `automatic_charging:false`. |
| Retry | Same-plan and changed-plan retries returned the original ID, selected quarterly plan and exact original end; `already_claimed:true`. |
| Access | `protection-access` verified the same active entitlement; this does not assert a running Android VPN. |
| Forced expiry | Only the disposable trial row, matched by user, ID, owned alias, trial plan and provider, was moved to an expired fixture. Live access then returned `active:false`. This is not 72 hours of real elapsed time. |
| Repeat-start denial | Endpoint returns 200 with the spent original claim, not a new grant. Android rejects it because access is inactive. Do not describe this as an HTTP 403. |
| Cleanup | Password-confirmed deletion 200; repeat password login denied. SQL found zero test users/identities/sessions/entitlements/trial claims. Global orders, non-trial entitlements and enabled products remained zero. |

Test credentials, email links, JWTs and refresh tokens are excluded from evidence
and Git. `scripts/check-live-trial.mjs` automates this API portion in two phases:

```sh
node scripts/check-live-trial.mjs start /private/trial-report.json
node scripts/check-live-trial.mjs expired-cleanup /private/trial-report.json
```

Provide `SAFENEST_TRIAL_QA_EMAIL`, `SAFENEST_TRIAL_QA_PASSWORD`,
`SAFENEST_TRIAL_QA_PLAN` and explicit `SAFENEST_TRIAL_QA_CONSENT=72-hour-disposable-test`
through private environment configuration. Only owned disposable
`+safenest-trial-…@gmail.com` aliases are accepted. Start expects a fresh confirmed
account with no active access. The later phase refuses to run before the original
end, unless `SAFENEST_TRIAL_QA_EXPIRY_MODE=forced-disposable-fixture` is explicitly
set. That mode does not perform a privileged mutation; its report labels the
external forced fixture and cannot claim natural expiry. Delete only the owned
test account. If a start-phase call fails, use the saved report/account identity
to complete cleanup rather than blindly creating another account.

## Android automation

Automation is proposed in [draft PR #3](https://github.com/ShaonInTheCloud/ShaonInTheCloud/pull/3),
source `1b49588ff33bf28021c34779f6f56dae99d379af`. Its web/security job passed in
[CI run 37556517360](https://github.com/ShaonInTheCloud/ShaonInTheCloud/actions/runs/37556517360);
Android unit/lint/build, Settings guard and the new Play trial checks also passed.
Commands below refer to that review branch.

`TrialDeviceAcceptanceTest` targets compatible builds. Proposed CI runs its
Play/debug suite after the lab Settings-guard flow. Tests cover:

- Real trial screen: email/password alone cannot enable Start; explicit plan
  selection and checkbox consent do. Removing consent disables Start again.
  Selecting a plan/consent alone caches no entitlement and starts no VPN.
- Isolated app-private trial fixture: verification alone is not activation;
  the exact end boundary is inactive. Advancing only its cached server anchor
  exercises checkpoint cleanup, guard release and activation denial.
- Optional live owned-account test: actual Android login/start/retry, plan and
  checkbox consent, Accessibility disclosure plus Android permission, DNS
  disclosure plus VPN permission, running commitment/VPN and accelerated device
  cleanup. It requires a private credentials file on a disposable emulator.
  Missing credentials cause a skip. It does not mutate the production period,
  wait 72 hours or claim natural expiry. The runner rejects skipped/missing
  result evidence. Its account still requires server-expiry/deletion cleanup.

Build/install a matching `playDebug` target and instrumentation APK from the same
CI/debug signing key. Never substitute the lab APK or expose credentials in
Gradle instrumentation arguments. A private JSON file must contain `email`,
`password`, `plan`, and `consent:"72-hour-disposable-test"`:

```sh
python3 scripts/run-trial-device-qa.py --serial emulator-5554 --credentials /private/trial-qa.json
```

This transfers credentials through stdin into app-private storage and erases that
copy in teardown. It suppresses raw instrumentation output that might include
text-entry semantics. No credential screenshots/UI trees are collected. The
optional test report explicitly sets `acceptance_passed:false` until the remaining
natural-expiry and owner-device gates are verified.

## Remaining gate

The new default Android tests have run. The actual Play JUnit report contains
3 tests, 0 failures/errors, and 1 skipped:

| Android check | Verified result |
| --- | --- |
| `explicitConsentAndSelectedPlanAreRequired` | Passed; real Compose UI gating and quarterly plan selection. No live credentials or trial start. |
| `expiredCachedTrialReleasesGuardAndCannotBeReactivated` | Passed; isolated cached server-anchor fixture, guard cleanup and activation denial. No real VPN activation or 72-hour wait. |
| `ownedLiveTrialConsentActivationAndAcceleratedDeviceCleanup` | Skipped; no private disposable credentials supplied to CI. This is still pending. |

The lab suite passed its existing guard flow and skipped all three trial methods
because lab mode cannot test customer trials. Those skips are not trial evidence.
Device evidence artifact `11454609990`, ZIP SHA-256
`d150479b45a4e5d48aa43566ab6990d3392967bea0eea3fb80e19cbc0ec1b83f`,
matches GitHub's digest and passes ZIP integrity. Both flavor JUnit reports and
the lab guard's actual passing result were inspected. Artifact retention is 14
days; this document retains the verified results without private credentials.

Run the skipped live-account Android test. Then test a compatible
release/owner phone through a real server-ended trial, with idle/background/reboot
coverage and no forced database or cached-clock edits. Confirm the app's guard,
VPN and managed policies are released when required; device settings, permissions
and normal internet must remain usable. Device-owner cleanup is not established
by the Play/debug fixture. Enrollment stays `releaseReady:false` in `web/account.js`.
The public APK and payment configuration were not replaced or enabled.

Local verification: 68 source/account/security/payment tests, 9 protection-access
tests, 5 signing-script tests, standalone Java regressions, web build,
Python/JavaScript syntax, workflow YAML and diff checks passed. Exact boundary integration tests use real migrations and
handlers with fixture Auth/REST transport; they are not real Android evidence.
