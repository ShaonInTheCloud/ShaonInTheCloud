# SafeNest Play submission pack — 0.5.0 (17 October 2026)

Submit only the `play` flavor (`./gradlew :app:bundlePlayRelease` with the upload keystore). The direct, lab and owner-test flavors are not Play artifacts.

**Owner decision (17 Oct 2026):** the Play build carries the full self-exclusion guard: uninstall/force-stop/Settings bounce, VPN-app bounce, VPN install-page bounce, Private DNS guard and Device Administrator. Google Play's *Permissions and APIs that Access Sensitive Information* policy says the Accessibility API may not "prevent the ability for users to disable or uninstall any app or service" unless authorized by a parent/guardian (parental-control app) or an administrator (enterprise software). SafeNest is a self-exclusion app. A competitor's presence on Play does not establish that this implementation will be approved. Mitigations in this build: the lock is finite, chosen and paid for by the user, fully disclosed in-app before Accessibility is enabled, ends automatically, and Safe Mode / factory reset / adb still remove the app. If Google rejects it, the rollback is one flag: `ALLOW_SYSTEM_GUARD=false` and `MANAGED_CONTROLS=false` in the `play` flavor, plus the earlier Play strings and the `tools:node="remove"` Device Admin manifest override, all in git history (commit before `3e794ec`).

## What the app does (for the declarations and the listing)

| Feature | Mechanism | Disclosed in-app |
|---|---|---|
| Gambling website blocking | Local `VpnService` DNS filter; allowed names resolve over Cloudflare DNS-over-HTTPS or the user's strict Private DNS; no plain-DNS fallback | DNS disclosure dialog before VPN consent |
| Gambling-app and VPN-app bounce to Home | `AccessibilityService` reads foreground package ID; any installed app declaring `android.net.VpnService` counts as a VPN | Accessibility prominent-disclosure dialog, tap-to-accept |
| VPN install-page bounce | Reads Play Store / OEM store page title and Install button label | Same dialog |
| Uninstall / force-stop / disable bounce | Reads Settings, installer and package-installer labels that name SafeNest | Same dialog |
| Private DNS and other-VPN dialog bounce | Reads the Private DNS dialog and VPN connect dialogs in Settings | Same dialog |
| Device Administrator | Empty policy set; adds Android's deactivation step with a lock-aware message | Admin activation screen text |
| Paid lock period | Server-issued entitlement (Google Play subscription) cached on device with clock-rollback protection; no in-app pause; auto-release at expiry | Activation dialog shows the exact end date |
| Google Play Billing | `safenest_protection` subscription, base plans `monthly`, `quarterly`, `annual`; purchase bound to the SafeNest account id; server verifies with the Play Developer API and acknowledges | Subscription screen |

What it never reads: browser page bodies, messages, passwords, keyboard input, notification content. What it never uploads: Accessibility observations, DNS history, installed-app lists.

## Listing

**Name:** SafeNest — Gambling Blocker

**Short description:** Lock yourself out of gambling sites and apps for a period you choose.

**Full description:**

SafeNest is a self-exclusion tool for people who want to stop gambling. Choose a protection period, and SafeNest keeps it for you.

During your period SafeNest:
• blocks gambling websites on this phone with a local DNS filter (not a privacy VPN);
• returns you to the Home screen when you open a gambling app or a VPN app;
• stops VPN apps from being installed;
• keeps itself running: the Settings screens that would uninstall, force-stop or switch SafeNest off send you back Home until your period ends.

Everything you chose ends automatically on the date you picked. There is no pause button, by design. Safe Mode, a factory reset or a computer can still remove the app; SafeNest does not claim to be impossible to remove, and no filter catches every site.

Two editions in one app: Bangladesh (Bangla first, local support) and Global (English). Prices are set by Google Play for your country. A subscription renews until you cancel it in Google Play; each paid period stays locked.

Privacy: lists, check-ins and what the app guard sees stay on your phone and are never uploaded. Your account uses Supabase authentication. Allowed DNS lookups go over encrypted HTTPS to Cloudflare, which receives the domain name and your network IP.

Requires Accessibility access (to return Home from blocked apps and guarded screens), VPN permission (local DNS filtering) and optional Device Administrator (an extra step before uninstall). SafeNest is not a medical service.

**Category:** Health & Fitness. **Content rating:** complete the IARC questionnaire honestly; gambling references are the subject, not simulated gambling. **Target audience:** 18+.

**Website:** https://mysafenestbd.com · **Privacy:** https://mysafenestbd.com/privacy · **Account deletion:** https://mysafenestbd.com/delete-account · **Support email:** OWNER MUST SUPPLY a monitored address before submission.

## Play Console configuration the owner must do

1. **Developer account.** Verify identity and the business/seller profile. If the account is a personal account created after 13 Nov 2023, Google requires a closed test with at least 12 opted-in testers for 14 continuous days before production access can be requested. Plan the testers now.
2. **App signing.** Generate the upload keystore (see `release-signing.md`), set the four `SAFENEST_UPLOAD_*` secrets in GitHub Actions, enroll Play App Signing, store the keystore backup off-site.
3. **Monetization → Subscriptions.** Create product id `safenest_protection` with base plans `monthly` (P1M), `quarterly` (P3M) and `annual` (P1Y), auto-renewing, with per-country prices (Bangladesh in BDT, everywhere else in local currency). Activate the product.
4. **Monetization → Monetization setup.** Enable Real-time developer notifications with a Cloud Pub/Sub topic. Add a push subscription whose endpoint is `https://kflenmeizngmafwnwhgv.supabase.co/functions/v1/play-billing-rtdn?key=<PLAY_RTDN_SECRET>`.
5. **API access.** Link a Google Cloud project, create a service account, grant it *View financial data* and *Manage orders and subscriptions* on this app in Play Console → Users and permissions, and download its JSON key.
6. **Supabase secrets** (`supabase secrets set`): `PLAY_PACKAGE_NAME=com.safenest.app`, `PLAY_SERVICE_ACCOUNT_JSON=<the JSON>`, `PLAY_RTDN_SECRET=<48+ random characters>`, and `PLAY_ALLOW_TEST_PURCHASES=true` while licence testers are buying. Apply migration `20261017060000_google_play_billing.sql` and deploy `play-billing-verify` and `play-billing-rtdn`.
7. **Licence testing.** Add the reviewer and your own test accounts under Settings → Licence testing so purchases are free during review.
8. **App content.**
   - *Accessibility declaration:* not an accessibility tool. Purpose: self-exclusion from gambling during a user-chosen period. Describe foreground app-ID matching, Settings/installer/store label matching, and the Home action. Upload the video (below).
   - *VpnService declaration:* local DNS filtering for user-selected content; allowed lookups use Cloudflare DNS-over-HTTPS or the user's strict Private DNS; not an all-traffic or privacy VPN.
   - *Foreground service:* type `specialUse`, reason: the DNS filter must stay running while the user browses; the persistent notification is the filter status.
   - *App visibility (`QUERY_ALL_PACKAGES` is NOT requested):* the manifest lists exact gambling/VPN package IDs plus the `android.net.VpnService` intent query. State that.
   - *Device Administrator:* used only for the deactivation step; no camera, password, wipe or lock policies.
   - *Data safety:* use the inventory below.
   - *App access:* a confirmed email/password with an active licence-tester subscription, entered in the private field only.
9. **Release tracks.** Internal test → closed test (12 testers × 14 days if required) → production, staged 10 %.

## Declaration video script (record on a real phone, play build)

1. Open SafeNest, sign in, choose a plan, complete the Google Play purchase (licence tester, 0 charge).
2. Show the Accessibility disclosure dialog in full; tap Decline once (nothing happens), reopen, tap Accept, grant Accessibility in Settings.
3. Show the DNS disclosure, grant VPN permission, activate protection; show the end date.
4. Browse a normal site (works). Browse a gambling site (blocked). Open a gambling app (bounces Home). Open a VPN app (bounces Home). Open the Play Store page of a VPN app (bounces Home).
5. Open Settings → Apps → SafeNest → Uninstall (bounces Home). Show the Device Admin deactivation message.
6. Show the Subscription screen text that the period ends automatically and cannot be paused.

## Data safety inventory

| Data | Collected? | Shared? | Purpose | Notes |
|---|---|---|---|---|
| Email address, password (in transit), account ID | Yes | No | Account management | Supabase Auth; password never stored by SafeNest |
| Purchase history (Google Play purchase token, plan, expiry) | Yes | No | App functionality, account management | Stored in `payments.play_subscriptions`; required to grant and revoke access |
| Display name, language, edition | Yes (optional) | No | App functionality | Private profile and device preference |
| DNS query names (allowed lookups only) | No (ephemeral) | Yes, to the resolver | App functionality | Cloudflare receives domain and network IP; nothing retained by SafeNest |
| Installed-app / foreground-app observations | No (on-device only) | No | App functionality | Never leaves the device |
| Custom rules, check-ins | No (on-device only) | No | App functionality | Local storage, excluded from backup |
| Crash logs | No | No | — | No crash reporter in the build |

Data is encrypted in transit. Users can request deletion at https://mysafenestbd.com/delete-account (password-confirmed; removes account, profile, entitlements and ledger rows).

## Release gates before pressing Publish

- [ ] Fresh CI run green on the release commit (Android unit tests both flavors, backend tests incl. `play-billing.test.mjs`, website build).
- [ ] Sandbox purchase on a real phone → `play-billing-verify` grants → protection activates → cancel in Play → RTDN marks it and the app releases at expiry.
- [ ] Device matrix: one Samsung, one Xiaomi/Redmi, one Pixel/AOSP: uninstall bounce, force-stop bounce, VPN install bounce, Private DNS bounce, another VPN takeover alert, reboot, battery optimisation, Safe Mode (expected to bypass; documented).
- [ ] Support email live and answered.
- [ ] Privacy policy updated for Google Play purchase data and the Accessibility guard scope.
