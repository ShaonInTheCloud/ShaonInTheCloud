# Android-compatible Auth CAPTCHA — 7 October 2026

## Status and implementation

Based on main `652e9ad1686fbfe674502aaa7f26eb0b2c8f0d70`, which includes the requested `95a9256ac7f34be51bed62766edf007e6ffb95d3` and newer trial/support work. The published review branch is `codex/android-auth-captcha`, with [draft PR #4](https://github.com/ShaonInTheCloud/ShaonInTheCloud/pull/4). GitHub CI results will be recorded separately. No website deployment or APK replacement is claimed.

**CAPTCHA enforcement remains OFF.** `SAFENEST_AUTH_CAPTCHA_ENABLED` defaults to false. The live Supabase Auth-protection dashboard redirected to sign-in; connected Supabase tools cannot update Auth configuration. Both the live `https://mysafenestbd.com/android-captcha` route and its `.html` alias returned HTTP 404, confirming the candidate challenge page still needs deployment. No secret, live entitlement or charging configuration was changed.

Android candidate 0.4.9 / code 26 obtains a fresh WebView challenge before initial verification, committed-period re-verification and consented trial start. `SubscriptionClient` sends `gotrue_meta_security.captcha_token` to Supabase's HTTPS password grant, then uses the resulting JWT for the existing server endpoints. Auth failure cannot reach access/trial endpoints or release a commitment.

The dedicated `android-captcha.html` asset is served at the canonical `/android-captcha` route and contains only the public sitekey. The production host redirects `.html` URLs (observed HTTP 307 for `/account.html`); Android loads the extensionless route directly so the exact-page guard does not reject that redirect. Credentials and JWTs remain native. The origin-scoped WebMessage bridge accepts only the exact HTTPS page, its main frame and the current random nonce. No wildcard origin, `addJavascriptInterface`, custom-scheme callback, TLS bypass or credential URL is used. JavaScript/DOM storage and cookies support Turnstile; default UA is preserved; file/content access and mixed content are disabled. Unsupported WebViews, cancellation, timeout and network failure never fall back to tokenless Auth.

The challenge page renders on explicit app use independently of the website rollout flag. Deploy it before distributing this candidate: without the page, verification fails closed. Earlier APKs will fail password Auth after project-wide enforcement; test and communicate the upgrade first. English/Bangla surrounding copy is provided; Turnstile has no Bangla widget locale, so the embedded widget uses English.

Both clients consume in-memory tokens before the Auth request, including on failure; Android uses a five-minute monotonic expiry. A retry requires a fresh challenge. These checks prevent accidental reuse only. Supabase/Cloudflare must enforce token validity and replay rejection. Never call a separate Siteverify proxy before sending the same token to Auth: that consumes it twice. The secret belongs only in Supabase's server-side Auth configuration.

## Evidence

- 78 Node tests passed after rebasing onto current main, including native delivery/context/missing/oversized/expiry handling and website SDK signup/login/recovery/deletion serialization, provider-fixture success/invalid/missing/replay rejection, RLS, payments, trials and support privacy.
- Core Java regressions passed, including missing/oversized/expired token rejection and concurrent single-attempt consumption.
- Disabled and enabled website builds passed. Both include the challenge page and bundled JS.
- Android Gradle verification passed for the final source: 57 unit tests per flavor (171 total, zero failures/errors/skips), Play/Direct/Lab lint (zero errors/fatal findings), and all three debug APK builds. Ten new CAPTCHA/bridge/request tests pass per flavor. The full command ran `test{Play,Direct,Lab}DebugUnitTest`, `lint{Play,Direct,Lab}Debug` and `assemble{Play,Direct,Lab}Debug` and ended `BUILD SUCCESSFUL`. Candidate metadata is 0.4.9 / code 26 (Lab 0.4.9-test). No APK was installed on a phone or published.
- Lint retains 56/56/57 warnings for Play/Direct/Lab. The new challenge code has a UseKtx suggestion and two RequiresFeature warnings across callbacks; the runtime explicitly checks WEB_MESSAGE_LISTENER support before registration and cleanup. Full lint reports are included with the review evidence. These warnings are not a live WebView acceptance result.
- Five existing signing-verification Python tests passed. No signed Play release or upload was produced.

Provider tests use explicit fixtures, not live enforcement. No completed live challenge, confirmation/recovery/deletion flow or Android device flow was verified. All live acceptance gates remain open.

## Remaining dashboard step and activation order

1. Publish reviewed source and complete Android CI/device QA. Deploy `android-captcha.html`, its JS and CSS at `https://mysafenestbd.com`; verify the canonical `/android-captcha` route returns HTTPS 200 without a redirect, as well as CSP and widget hostname allowlist. Test actual initial verification, re-verification and trial start on Honor and a second supported device, including while protection is active. Confirm the filter permits the challenge host. Do not replace the existing APK with an untested candidate.
2. Deploy website with `SAFENEST_AUTH_CAPTCHA_ENABLED=true` and public `TURNSTILE_SITEKEY=0x4AAAAAAFPyYH0-GfSghhqT`. Exercise real challenges on all website forms and Android before project-wide activation. Client success alone does not prove enforcement.
3. Sign into the SafeNest Supabase dashboard and open **Authentication → Attack Protection** at `/dashboard/project/kflenmeizngmafwnwhgv/auth/protection`. Under **Enable CAPTCHA protection**, select **Turnstile**, enter the existing widget's secret in server-side Auth configuration, enable the toggle and **Save**. Obtain it from authorized Cloudflare widget settings; never include it in source, APK, client config, logs, screenshots or evidence. Record only provider, enabled status and timestamp. No plan upgrade is authorized or required by this implementation.
4. Immediately run the acceptance matrix below with disposable owned accounts. Record sanitized results and versions; leave unchecked items open. If a critical flow fails, stop rollout. Do not silently downgrade clients or claim success. A security-setting rollback requires authorization if needed.

## Acceptance matrix after activation

| Flow | Required verified result |
| --- | --- |
| Direct Auth missing/invalid token | Password grant returns HTTP 400 `captcha_failed`; signup and recovery also reject missing/invalid tokens. |
| Valid token then direct replay | Confirmed account succeeds once; identical token replay returns `captcha_failed`. Dummy keys are not production evidence. |
| Website signup/confirmation/login | Fresh challenges succeed; confirmation email delivered and completed; confirmed password login succeeds. |
| Recovery | Fresh challenge sends email; callback and password change completed; old password denied, new password succeeds with a new token. |
| Signout | Session removed; protected operations cannot use the signed-out session. |
| Deletion | Correct password/consent plus fresh challenge deletes caller only; invalid/missing/replay fails without deletion; sessions revoked and repeat login denied. |
| Android initial verification | Actual challenge returns to app; Auth and existing server-owned window verified without inventing access. |
| Android re-verification | Fresh challenge preserves exact entitlement ID/window; challenge/Auth failure never releases protection. |
| Android trial | Explicit consent/plan preserved; one-time server trial and exact end time unchanged. Existing trial release gates remain closed until separately verified. |
| Device failures | Cancel, process death, challenge expiry, retry and offline failure cannot grant/release protection. |

`scripts/check-live-captcha.mjs` automates only direct Auth/login/read-only-access/signout checks. Opt in with `SAFENEST_LIVE_CAPTCHA_ACCEPTANCE=true`; privately provide `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `SAFENEST_TEST_EMAIL`, `SAFENEST_TEST_PASSWORD`, and two distinct fresh `SAFENEST_TEST_WEB_CAPTCHA_TOKEN` / `SAFENEST_TEST_ANDROID_CAPTCHA_TOKEN` values in the process environment, never command literals or Git. Use a confirmed disposable owned account: it signs out that account globally. Run `node scripts/check-live-captcha.mjs` from `safenest`. It logs labels only, creates no account/entitlement/charge, and does not replace actual device/signup/recovery/deletion checks.

## Primary references

- https://supabase.com/docs/guides/auth/auth-captcha
- https://developers.cloudflare.com/turnstile/get-started/mobile-implementation/
- https://developers.cloudflare.com/turnstile/get-started/server-side-validation/
- https://developers.cloudflare.com/turnstile/reference/supported-languages/
- https://developer.android.com/reference/androidx/webkit/WebViewCompat#addWebMessageListener
