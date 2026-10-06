# SafeNest market launch checklist

Updated: 7 October 2026. Product: https://mysafenestbd.com. Android package: `com.safenest.app`.

Latest continuation: 0.4.6 Test/source CI and thirteen Settings-guard emulator checks passed. 0.4.7 adds encrypted DNS, ordinary-internet/blocked-domain device checks and the manual signed Play bundle workflow. See `encrypted-dns-0.4.7.md` and `release-signing.md`; fresh full Android CI and the owner's signing configuration are required before calling the new release verified. The APK remains a testing artifact and paid/store launch remains blocked.

**Release decision: development website live; paid sales and Play production release are blocked.** A working website, source tests or a debug APK do not establish reliable protection on customer phones.

Legend: `[x]` verified complete, `[ ]` pending. “Prepared” means code/material exists but its live release check is still pending.

## 1. Source, hosting and release ownership

- [x] Recover the full Android source and published website; keep the approved pink/white design, slogans and English above smaller Bangla.
- [x] Restore exact applied Supabase migration history, rather than replaying archive migrations under invented timestamps.
- [x] Synchronize complete application, website, backend, catalogues and release documents to GitHub; verify the resulting branch head. Latest previously verified head: `7ef13f8448032f332c70e79e2684a08e7d34fa07`.
- [x] Publish account deletion, privacy, terms, help and checkout-status pages on the existing domain; inspect the deployment result. Verified 7 October: Sites version 21 succeeded, all five generated URLs resolve over HTTPS with HTTP 200, and all legal-navigation links and anchors passed. See the release-page verification record below.
- [ ] Establish one release owner, a verified customer-support/privacy address and final business/legal identity.
- [ ] Protect `main` with passing checks and reviewed pull requests; require MFA for GitHub, Supabase, email, domain and Play Console accounts.
- [ ] Record production secrets by name and owner; rotate any previously shared private credentials. No service-role, SMTP or merchant secret belongs in the website, APK or Git history.
- [ ] Configure a backup/restore drill and uptime alerting, with explicit incident and customer-contact procedures.

## 2. Authentication and private data

- [x] Keep confirmed Supabase Auth accounts, server verification, private profile RLS and server-owned entitlements.
- [x] Configure production domain and exact confirmation/recovery redirects; preserve custom authentication email delivery.
- [x] Test account-state isolation and profile ownership restrictions with the existing automated tests.
- [x] Implement password-confirmed account deletion: verify the live account, derive identity server-side, revoke sessions, then delete the account-linked profile and entitlement.
- [x] Check deletion scope: current live storage has zero buckets/user-owned objects; profiles and entitlements cascade on account deletion.
- [x] Validate production signup, delivered email confirmation, password login, completed email recovery, signout/session revocation, password-confirmed deletion and repeat-login denial via live Auth/deletion APIs with two disposable owner-controlled accounts. See the 6 October verification record below.
- [ ] Complete a browser-form regression of these flows, including recovery-page rendering and local signout UI; API results do not establish browser UI behavior.
- [ ] Enable leaked-password protection — BLOCKED BY PLAN. On 6 October, the connected organization and live dashboard show Free; the email-provider settings explicitly limit this feature to Pro and above. The switch remains off and the fresh security advisor still reports `auth_leaked_password_protection`. This is not fixed; an authorized plan upgrade is required before enabling and verifying it.
- [ ] Review email/rate limits and abuse controls before opening public signup traffic; add CAPTCHA if the traffic threat model requires it.
- [ ] Define exact operational-log/backup retention and deletion dates, and update the privacy notice accordingly.
- [ ] Review all production write routes for live authorization; a client flag, decoded JWT or payment success redirect is never proof of entitlement.
- [ ] Separate privileged operator accounts from ordinary customer accounts; audit operator access and never derive admin rights from editable user metadata.

## 3. Android release model and permissions

- [x] Prepare `play` and `direct` distribution flavors. Target SDK is 36, meeting the current new-app target requirement.
- [x] In `play`, disable Settings/installer interception, browser address-bar Home redirects and the Device Administrator receiver/setup entry points. Keep the direct development behavior separately.
- [x] Add matching consent/disclosure text and account-deletion/privacy links. Play app guard returns Home only for selected blocked apps and detected VPN apps; Chrome/Firefox stay open.
- [x] Keep no in-app pause during a finite verified protection period and automatic expiry; accurately disclose that Android uninstall and permission controls remain available in the consumer Play build.
- [x] Compile and test Play/direct/lab 0.4.6 debug flavors and the unsigned Play release bundle. Prior CI passed; 0.4.7 must pass its own checks. The signed-release validator checks the Play merged manifest, including absence of Device Administrator.
- [ ] Verify the Play permission disclosures against the shipped artifact, including app visibility, Accessibility, VpnService and foreground-service declarations.
- [ ] Record declaration videos showing consent, permission grant, normal browsing and the blocking feature; describe local DNS rather than an encrypted privacy VPN.
- [ ] Review DNS transport against the VpnService policy with the final design. 0.4.7 prepares certificate-verified HTTPS/strict Private DNS without a plain fallback; this prototype remains a DNS-only interface rather than a full traffic tunnel. Encryption is not proof of eligibility.
- [ ] Confirm ownership/authorization requirements before offering managed-device protection. Device Owner is a separate enrollment process, usually during device setup; payment is not an OS management permission.
- [ ] Do not market permanent uninstall prevention, all-VPN blocking or 100% gambling/adult coverage.

Google Play's Accessibility policy limits prevention of disabling/uninstalling an app to authorized parental control or enterprise management. The consumer `play` flavor is prepared around that boundary. A competitor's listing does not prove SafeNest's implementation will be approved.

## 4. Network and device acceptance tests — release blockers

- [x] Run the standalone DNS/domain/alias/transport/signature/commitment regressions, including malformed-packet fuzzing and real loopback UDP/TCP tests.
- [ ] On Honor and at least one Samsung/Pixel, prove normal browsing works with protection enabled on Wi-Fi and mobile data, including IPv6 and network changes.
- [ ] Prove listed domains fail in Chrome while Chrome stays open; exercise new local rules, subdomains, brand-family rules and already-open/cached connections.
- [ ] Test Chrome Secure DNS, system Private DNS, proxies, embedded browser VPNs and another Android VPN taking over. Report bypasses honestly; do not silently equate a foreground Home redirect with packet filtering.
- [ ] Test detected VPN app IDs, unknown VPNs, rebranded apps, background starts and VPNs configured in Settings.
- [ ] Test permission denial/revocation, reboot, process death, notification denial, battery optimization, offline startup and period expiry.
- [ ] Test customer-safe diagnosis of DNS failure. This DNS-only build must leave Android “Block connections without VPN” off.
- [ ] Verify finite offline entitlement behavior, clock changes, revocation, deleted accounts and expiry. Account deletion is not an instant unlock of an already-cached offline period.
- [ ] Measure battery/memory/CPU and accessibility event handling on real phones; validate reduced motion and Bangla legibility.
- [ ] Document supported phones/OS/browser versions and residual bypasses before taking money.

## 5. Payments and entitlement issuance

Prepared release choice: **consumption-only Play app** for existing paid accounts, with no in-app purchase button or external checkout link. This avoids adding unconfigured Play Billing. If in-app sales are added, implement Google Play Billing and server purchase verification unless an approved exception/program applies.

- [x] Keep public prices proposed and checkout inactive; remove any impression that a prototype payment form or payment reference activates protection.
- [x] Keep `protection-access` authenticated and read-only for customers; client-selected dates/paid flags cannot grant or release access.
- [x] Implement and locally test the credential-independent server payment foundation: server-price snapshots, private order/event/receipt ledger, service-only processing, exact matches, duplicate/replay rejection and transactional finite grants. Sandbox grants are isolated; default processing is disabled and the compiled provider resolver is unavailable. See `payment-foundation.md` for evidence and limits.
- [x] Deploy and verify the disabled foundation: additive migrations, client-denied service-only RPCs and inactive payment Edge handlers. Verified zero production orders/entitlements; 55 account/payment/access tests pass. Genuine gateway validation and sandbox-to-Android acceptance remain pending.
- [ ] Finalize monthly/annual price, currency, taxes, device limits, cancellation/refund policy and whether renewal is manual or automatic. The current pricing page proposes ৳299/month and ৳2,999/year; these are not approved merchant products.
- [ ] Open/approve a merchant gateway account and provide sandbox credentials through server-secret configuration. Confirm international cards and each requested wallet (bKash, Nagad, Rocket, upay) in the merchant's enabled channels.
- [ ] Implement hosted website checkout: authenticate customer, create a server-priced order, create a provider session, and redirect only to an allowlisted provider URL. Never collect card/CVV fields in SafeNest.
- [ ] Implement provider notification validation, exact amount/currency/order matching, idempotency and transactional entitlement issuance; never trust a browser success page or unverified webhook fields.
- [ ] Test duplicate/out-of-order notifications, pending payments, failures, cancellation, timeout, wrong amount, replayed receipts, provider outage, refunds and chargebacks.
- [ ] Add scheduled server reconciliation, expiry/revocation updates, customer receipts, refund/cancellation controls and operator audit logs.
- [ ] If Play purchases are added: configure subscription products/base plans, Play Developer API access and notification delivery; verify receipts server-side, bind purchases to an account, acknowledge valid initial purchases and handle pending/renewed/revoked/refunded states.
- [ ] Decide cryptocurrency only after an appropriate provider and applicable regulatory/merchant requirements are confirmed. It is not a launch dependency and is not currently accepted.
- [ ] Run a complete sandbox payment → server entitlement → app activation → expiry/revocation test before opening live checkout.

See `payment-integration-plan.md` for implementation boundaries and credential requirements. No payment gateway credentials or Play Console access are present in the current session.

## 6. Signing, builds and release delivery

- [x] Add GitHub CI for website/account/backend security tests, core network tests, both Android flavors, Play debug APKs and a Play release AAB artifact.
- [x] Pin CI action revisions and Gradle distribution checksum. Keep build outputs, local SDK settings and upload keys out of source.
- [x] Prepare optional release signing via `SAFENEST_UPLOAD_KEYSTORE`, `SAFENEST_UPLOAD_STORE_PASSWORD`, `SAFENEST_UPLOAD_KEY_ALIAS`, `SAFENEST_UPLOAD_KEY_PASSWORD`.
- [ ] Complete CI on the exact GitHub release commit, inspect warnings and test results, and retain build receipts.
- [ ] Establish a release upload key and backup outside Git. Sign the AAB, enroll Play App Signing and record upload/app-signing fingerprints.
- [ ] Choose an APK signing strategy for website distribution. Existing installs require matching signing certificates; an arbitrary CI debug key is not an upgrade plan.
- [ ] Install the signed candidate on physical phones and verify upgrade, rollback/recovery strategy and persisted rules/consent/expiry.
- [ ] Publish a tested signed APK with version, checksum, install instructions and an accurate support contact; do not replace the existing download with untested source changes.
- [ ] Fix or retire the profile repository's unrelated npm-publish workflows before creating GitHub releases; they currently try to publish a private/non-root package.

Useful commands from `safenest/android`:

```sh
./gradlew :app:testPlayDebugUnitTest :app:testDirectDebugUnitTest
./gradlew :app:assemblePlayDebug :app:assembleDirectDebug
./gradlew :app:bundlePlayRelease
```

Without upload signing configuration, the release AAB is a build-check artifact, not a Play-upload-ready release.

## 7. Play Console submission

- [ ] Create/verify the correct developer account and business/contact details; confirm whether the account is subject to mandatory closed testing.
- [ ] Create SafeNest in Play Console with final package ownership, supported countries, category, content rating and target-audience decisions.
- [ ] Provide store icon, feature graphic and screenshots from the actual `play` build; the current listing copy is a draft.
- [ ] Publish final privacy and account-deletion URLs; complete seller/support contact and retention details first.
- [ ] Complete Data safety answers from the final app/backend behavior and provider contracts, not from the intended marketing description.
- [ ] Submit Accessibility, VPN, foreground-service and any other required declarations and evidence videos.
- [ ] Supply working reviewer credentials and an approved test entitlement so reviewers can activate protection without a real charge or an operator bypass in the app.
- [ ] Start internal testing; then closed testing and production-access application if required. For personal accounts created after 13 November 2023, the current rule requires at least 12 opted-in testers continuously for 14 days before applying.
- [ ] Upload the signed AAB, resolve pre-launch report issues and reviewer feedback, and start a limited staged rollout.
- [ ] Monitor crashes/ANRs, DNS failure reports, support/refund traffic and entitlement issuance after rollout. Define rollback thresholds and a customer incident message.

## 8. Evidence and unresolved items

Verified locally in this work: 12 account/deletion/RLS tests; 8 protection-access tests; standalone core regressions with 20,075 DNS codec, 57 domain, 10,082 alias, 22 upstream transport, 59 signature and 19 commitment/guard checks; website build; npm production-dependency audit reports zero known vulnerabilities. This does not certify the entire product.

Backend deletion endpoint deployed with JWT verification enabled. No real customer was deleted during verification. The live email/API disposable-account flow passed on 6 October; browser-form regression remains pending.

Current public Android download remains the older 0.4.0 debug APK until a new signed, tested artifact is available. New source is 0.4.2/code 19 with Play/direct flavors. Do not describe the new Play source as already installed or published on Play.

The latest Supabase security advisor still reports leaked-password protection disabled. No merchant credentials, release upload key, Play Console access or verified customer-support/legal identity has been supplied. These dependencies prevent paid/store release today even if the public website deployment succeeds.

### Live Auth security verification — 6 October 2026

Verified against production project `kflenmeizngmafwnwhgv`, the deployed `delete-account` function and owner-controlled disposable email aliases. Test run: `20261006-b5b2f73e`. Completed and checked at 2026-10-06T11:32:54+00:00. No admin-generated confirmation/recovery link or direct Auth-table mutation was used.

| Flow | Verified result |
| --- | --- |
| Signup | Two accounts created (HTTP 200), with no authenticated session before confirmation. Unconfirmed password login rejected with `email_not_confirmed`. |
| Confirmation and custom delivery | Both real confirmation emails arrived from `no-reply@auth.mysafenestbd.com`; fresh links established verified account sessions and redirected to `/account.html` on `mysafenestbd.com`. |
| Password login | Both confirmed accounts authenticated with their passwords (HTTP 200). |
| Completed recovery | The real reset email redirected to `/account.html?mode=recovery`; its recovery session accepted a replacement password without the old password (HTTP 200). The old password then failed with `invalid_credentials`, and the replacement succeeded. |
| Signout | Both local signout requests returned HTTP 204; the corresponding refresh tokens were rejected with `refresh_token_not_found`. Fresh password login still worked before deletion. |
| Deletion | Both incorrect-password deletion attempts were rejected with HTTP 401 / `reauthentication_failed`. Correct current passwords returned HTTP 200 / `account_deleted`. |
| Repeat login and deleted sessions | Password login after deletion failed with HTTP 400 / `invalid_credentials`; deleted refresh tokens failed, and live user lookup rejected the deleted accounts with HTTP 403 / `user_not_found`. |
| Private profiles | Each account created/read its own profile. Cross-account reads returned no rows; a cross-account update left the owner's profile unchanged. Anonymous profile access returned HTTP 401. |
| Cleanup | Read-only SQL confirmed zero remaining rows for both test identities in `auth.users`, `auth.identities`, `auth.sessions`, `public.profiles` and `public.protection_entitlements`. No paid entitlements were created, so this run does not verify deletion of an existing paid entitlement or billing cancellation. |
| Expired link | An original, expired confirmation link was denied with `otp_expired`; fresh resend links passed. The live email-provider setting is 60 seconds. |
| Leaked-password protection | Still disabled. Organization is Free; dashboard and current Supabase documentation state Pro or above is required. Fresh advisor has zero errors and one warning: `auth_leaked_password_protection`. |

Scope: real production email/API integration, not mocks or administrator confirmation shortcuts. Browser-form interaction/rendering, Android authentication, load/abuse testing and paid billing were not exercised. Existing production redirects, custom email delivery, private RLS and account-deletion implementation remain recorded complete; the plan restriction is a separate open security item. Passwords, email tokens, sessions and SMTP/admin keys are excluded from this record.

### Release-page publication verification — 7 October 2026

The privacy, terms and help copy now matches the 0.4.7 source in `SafeNestVpnService.kt` and `DnsHttpsTransport.java`: local domain checks; allowed DNS over certificate/hostname-verified Cloudflare HTTPS; validated strict system Private DNS on Android 10+; failure without plain UDP/TCP fallback or switching a strict provider. Android 9 strict Private DNS is unsupported. Cloudflare receives allowed domain names and the network IP; account credentials are not added to resolver requests.

Both English and Bangla explain that this remains a DNS-based local interface, not an all-traffic tunnel or HTTPS content inspector. Browser Secure DNS, direct IP, proxies, cached connections, VPN replacement and permission revocation remain limitations. The website download is still the older 0.4.0 test APK; these pages do not claim it implements 0.4.7 or that a signed customer release/Play approval exists.

Publication: Sites version 21, source commit `abd25f0854430054378bb0a0d2cd2d15ff00753c`, deployment `appgdep_6ac57b46534c8191a74fa145a070b078` succeeded. The existing custom domain remained active with HTTPS.

| Generated URL | Verified final URL | Result |
| --- | --- | --- |
| https://mysafenestbd.com/privacy.html | https://mysafenestbd.com/privacy | HTTP 200; updated DNS copy present |
| https://mysafenestbd.com/terms.html | https://mysafenestbd.com/terms | HTTP 200; development/release limits present |
| https://mysafenestbd.com/support.html | https://mysafenestbd.com/support | HTTP 200; encrypted-DNS troubleshooting present |
| https://mysafenestbd.com/delete-account.html | https://mysafenestbd.com/delete-account | HTTP 200; account deletion instructions linked |
| https://mysafenestbd.com/checkout.html | https://mysafenestbd.com/checkout | HTTP 200; checkout remains inactive |

Verification: `npm test` passed all 18 existing tests; `npm run build` succeeded. Local and live-domain checks covered all 11 HTML pages and 227 local navigation/download links and anchors, with zero errors. Privacy, terms, deletion and help links exist on every page, including the account page. The host redirects .html URLs to extensionless routes; the account deletion fragment resolves to its existing section. This checks publication, copy and navigation, not a new Auth/deletion flow or Android-device acceptance run.

Seller identity, verified support/privacy contact, exact provider/log/backup retention, final commercial terms and payment activation remain unresolved. No seller, contact address, retention period, live payment channel, certification or approval was invented. Final paid/Play privacy and legal readiness remains pending in section 7.

## Primary references checked for this release

- Accessibility API policy: https://support.google.com/googleplay/android-developer/answer/9888170
- Account deletion: https://support.google.com/googleplay/android-developer/answer/13327111
- Payments and consumption-only apps: https://support.google.com/googleplay/android-developer/answer/10281818
- VpnService: https://support.google.com/googleplay/android-developer/answer/12564964
- Target API: https://support.google.com/googleplay/android-developer/answer/11926878
- Testing: https://support.google.com/googleplay/android-developer/answer/14151465
- Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Server-side purchase security: https://developer.android.com/google/play/billing/security
- SSLCOMMERZ hosted checkout/IPN/validation: https://developer.sslcommerz.com/doc/v4/
- Supabase admin deletion: https://supabase.com/docs/reference/javascript/auth-admin-deleteuser
- Supabase leaked-password plan requirement: https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection

This checklist is a release tracker, not a promise of Play approval or a claim that payment/blocking/device QA is complete.
