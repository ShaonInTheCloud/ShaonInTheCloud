# SafeNest release handoff — 25 October 2026 (updated 27 October)

**Status: not ready for public release.** The launch gates are listed below. This report changes no code, no providers, no DNS and no deployment.

## 1. Two source tracks

| Track | What it is | Exact reference | Live? |
|---|---|---|---|
| **A. Sites (website)** | Source for the live mysafenestbd.com, published through Sites hosting | **27 Oct: published v46** from Sites source `917a76025ae9bef5219ffea733f8f46c746cca46` (owner-approved mobile screenshot, phone rabbit restored, SVG S fill wedges fixed). Before that, v45 from `4081fe8ac308609a7cf8b4aa5ac79f2080f3847c` (deployment `appgdep_6add6ae0c6608191b4e030cf8a4cca62`, saved version `appgprj_6ab4d7e9b7f881919fce6caac5d93115~appgver_1d5f78a5a23c8191b0f84d8ad650196b`); `b9d5b4cd…` is older still. These contain the PR #21 design plus newer auth, CAPTCHA and download changes. **These commits are not on GitHub.** | Yes, v46 is live |
| **B. GitHub PR #17** | Global edition, card/crypto payment code (disabled), Play Billing, Android guard, iPhone DNS filter, Expo app, and the website as merged from `main` | Branch `safenest-global-card-crypto`; on 27 Oct the head is the commit that adds this update (after `023bdd21`, plus the PR #20/#23/#25 merges below). `main` is at `972eb3ff6b6b371d4844d3a22cb2a8c25d70bec1`, which includes PR #21. | No, not merged to `main` |

The two tracks are separate. Merging or deploying PR #17 must not overwrite the Sites source or the published v45 without someone first reconciling it against Sites `917a7602` (v46). That source contains auth, CAPTCHA and download work that GitHub does not have.

### What is on PR #17 now
- `ea9f0a7`: `launch/play-billing-and-full-guard` (PR #18) merged into the Global branch, version **0.5.16 / versionCode 45**.
  - Owner rule (17 Oct, confirmed 25 Oct): SafeNest's own Settings pages stay open, and only the destructive tap bounces Home.
  - The Play accessibility config now includes `typeViewClicked`.
  - The Play disclosure text was updated to match.
- `b540d97`: `main` merged in. It brings the hot-pink S/rabbit theme, written relative to `base` so `global/` pages work, and the বেটিং wording. PR #17's checkout-disabled copy and Play Store launch switch are kept.
- The Android source tree is identical at `ea9f0a7` and `b540d97`: `safenest/android` = `49396f921ff922651d37024465d1ff0fc583d52d`.

### Added on 27 October (version **0.5.17 / versionCode 46**; 0.5.16 / 45 was already built and installed as a lab APK)
- Merged, in this order, with no conflicts:
  - PR #20 at `3c9d3074d8686d8c354854aa3ffa02465af1f90a` (merge `ee0cd55`). The paid commitment counts on uptime only. Before this, the wall clock was trusted after a reboot, so rebooting and changing the date could end a paid lock early. Trade-off: time while the phone is off is not counted until the next server check, so release can come later, never earlier.
  - PR #23 at `b21c6704541364cc0a7980f9e9cdbcf8dba27023` (merge `205ca09`). Restarts the filter after a reboot or app update when VPN permission still holds, and alerts when Android stops it.
  - PR #25 at `6ac0c64b79ab972af5df3d802f6cc0ba938a0260` (merge `9086ede`):
    - any tap on the Always-on row leaves SafeNest's own VPN page, which closes the same-visit "on, then off" path
    - three more VPN browsers
    - the encrypted-DNS bypass block is on by default
    - Android's "Stop … app guard?" dialog bounces
- **Always-on exception (owner instruction, 18 Oct; kept).** SafeNest's own VPN page opens while Always-on is off, so it can be switched on. Once Always-on is SafeNest, the page goes Home on entry, and any Always-on tap leaves it. This is the one deliberate exception to "own pages stay open".
- **Dialog match tightened.** PR #25's original check treated any text containing `app guard?` (or starting with `stop safenest`) as SafeNest's own dialog, so another app's "App Guard" service could not be stopped. Now only a question naming SafeNest's own untranslated service labels ("SafeNest app guard", "SafeNest Test app guard", "SafeNest Owner Test app guard") as a whole phrase counts (`SystemScreenGuard.isOwnServiceQuestion`). New regression checks: seven other-app dialogs stay usable (for example "Stop Norton App Guard?" and "Stop SafeNestPlus app guard?"); own dialogs in English, Bangla and Finnish bounce; the plain service row does not.
- **Direct APK certificate pin.** The workflow used to only print the signer. Now `scripts/verify-direct-signing.py`:
  - checks the supplied keystore **before** the build
  - checks every signer `apksigner` reports for the **actual signed APK** **before** anything is copied or uploaded
  - pins both to the documented direct certificate `E1:72:5D:0F:C6:23:C0:2A:69:93:FD:21:23:EA:8B:B1:C5:26:21:14:7F:5B:AC:9E:04:F1:A2:F7:0E:FF:64:62` (this doc's section on two release keys; it is the only direct certificate recorded in the repo)

  A mismatch exits non-zero and names both fingerprints. No new secret or variable is needed. `scripts/test/test_direct_signing.py` adds 8 tests:
  - the pin matches the documented value
  - wrong signer, extra signer or rotation entry, unsigned APK
  - a source stamp is not mistaken for the signer
  - the command fails without writing a certs file
  - the keystore check, with no password echoed

## 2. Evidence

**Claude, Linux, 27 Oct, at the commit adding this update:**
- `run-core-tests.sh`: the guard regression passes 4,221 checks (4,199 before the merges; +7 from PRs #20/#25, +15 dialog checks).
- `npm test`: 105/105.
- Python signing tests: 13/13 (5 existing, 8 new).
- **Not run here** (no Android SDK): the Kotlin compile, Gradle unit tests, lint and the emulator flow. These need fresh CI and Codex's Windows run at the new head.

**CI at `023bdd21`** ([run 40156061159](https://github.com/ShaonInTheCloud/ShaonInTheCloud/actions/runs/40156061159)): `web-and-security` and `android` succeeded, including `connectedLabDebugAndroidTest` on Android 16 (1 test). **What that run does and does not show:**
- The filtering checks in `safenest-guard-device-QA` use `InetAddress` and `HttpsURLConnection` from the test process. They are not a real Chrome or Firefox session.
- The Opera, gambling and VPN "apps" are stand-in packages. No real third-party app was exercised.
- It ran on an emulator, not a phone, and it predates the 27 Oct merges.
- A passing run is evidence that the code does what the test asserts, not proof of the intended product behaviour on real devices.

**Claude, Linux, at `b540d97`:**
- `npm test`: 105/105 pass.
- `npm run build`: succeeds.
- `run-core-tests.sh`: the core guard regression passes 4,199 checks.
- Built pages: the hot-pink layer is on the root, account and `global/` pages, with no liquid-pink or raspberry assets left.

**GitHub CI** ([run 40155790726](https://github.com/ShaonInTheCloud/ShaonInTheCloud/actions/runs/40155790726), at `b540d97`):
- `web-and-security`: success.
- `android` (unit, lint, build, emulator guard): still in progress when this report was written. Check that run for the final result.

**Codex, Windows:**
- lab, play and direct debug builds pass.
- 189 unit tests pass.
- All three lint tasks pass. Warnings remain.
- A traced **lab 0.5.16** APK was built and installed without clearing data. The local emulator test was then stopped and the emulator shut down.

**Lint review notes. Neither is proven exploitable:**
- `VpnPackageAddedReceiver` does not validate the intent action, but the manifest declares it `android:exported="false"`.
- `RulesStore.activeContext` stores a context, but it explicitly stores `applicationContext`.

**Live site** (`curl -I https://mysafenestbd.com/` on 25 Oct):
- HTTP 200 with `cache-control: public, max-age=0, must-revalidate`.
- **No** HSTS, `X-Content-Type-Options`, CSP header or `frame-ancestors`.

**Sites v45 (Codex):**
- Checksum verified.
- Live confirmation resend verified.
- Signup and confirmation, API password login, real-email recovery, and browser support submission with persistence all passed.

## 3. Versions — what is and is not released

- **0.5.16 is a lab APK** (`com.safenest.app.lab`, "SafeNest Test"). It is for testing only. It is not signed for release and not published. **0.5.17 (versionCode 46) is the current source version; no build of it is released.**
- **The current public website download remains 0.4.10** (the direct development APK).
- **Full real-phone protection is unverified.** No 0.5.17, 0.5.16 or 0.5.15 build has completed browsing and blocking acceptance on a real phone. Emulator browser acceptance is also unverified: launching Chrome on the emulator was blocked by an approval review and has not been retried.
- **Payments are not live.** Stripe, NOWPayments and SSLCommerz checkout are disabled. Every page says checkout and payments are not open. Play Billing exists only in source.

## 4. Remaining launch gates

1. **HTTP security headers (host-level).**
   - The Sites host ignores `dist/_headers`.
   - `mysafenestbd.com` is on `ns01/ns02.dns.nexus` and uses Cloudflare edge addresses that are not in the owner's Cloudflare account, so the existing Worker route cannot attach.
   - Recommended fix: move the zone to the owner's own Cloudflare account and serve `dist/` with Workers Static Assets. Static assets apply `_headers` and allow 25 MiB per file, so the 19.8 MB APK fits as a static file.
   - Copy every DNS record first, including mail (MX/SPF/DKIM for Brevo).
   - Then confirm `node scripts/check-live-security.mjs` passes on all paths.
   - Owner action; not done.
2. **Re-test browser auth after headers go live.** Run recovery, signup confirmation and support once more, because the enforced CSP and `frame-ancestors` affect Supabase `connect-src` and the Turnstile frame.
3. **Reconcile the website tracks.** Bring Sites `4081fe8a` (auth, CAPTCHA, downloads) and PR #17's website (Global edition pages, Nest Run, iOS profile page) into one source before either is deployed over the other.
4. **Android CI green at the new PR #17 head** (0.5.17, after the #20/#23/#25 merges), plus Codex's Windows build, test and lint. Includes the emulator guard flow, with the stand-in limits described above.
5. **Real-phone acceptance of a release-signed 0.5.17 build**, in real Chrome and Firefox with real third-party apps. Check that:
   - blocked sites step back, mirror domains are blocked, and Firefox/Chrome Secure DNS cannot bypass the filter
   - real gambling, VPN and Tor apps bounce Home
   - Settings pages open, but Uninstall, Force stop, Clear storage and Forget VPN taps bounce
   - the Always-on page opens while it is off, can be switched on, leaves at once, and bounces on later visits
   - reboot restores protection
   - changing the date or time does not end the paid period early
6. **Open observation OBS-1** (project doc `claude/android-open-observations.md`). The lab v1 "allfeatures" 0.5.15 APK (SHA-256 `0d1b0060…71ddcb`) showed "Accessibility permission missing" while dumpsys listed the service as bound. It is unresolved, and **not** shown to be a defect in current source. Reproduce it on a CI-built APK whose commit is known.
7. **Release signing and Play submission.**
   - Both signed workflows run only from `main`, in the `android-release` environment. Secrets referenced:
     - `SAFENEST_UPLOAD_KEYSTORE_BASE64`, `SAFENEST_UPLOAD_STORE_PASSWORD`, `SAFENEST_UPLOAD_KEY_PASSWORD`, `SAFENEST_UPLOAD_KEY_ALIAS`
     - `SAFENEST_DIRECT_KEYSTORE_BASE64`, `SAFENEST_DIRECT_STORE_PASSWORD`, `SAFENEST_DIRECT_KEY_PASSWORD`, `SAFENEST_DIRECT_KEY_ALIAS`
     - plus the variable `SAFENEST_UPLOAD_CERT_SHA256`

     **Verified 27 Oct (Codex, signed-in GitHub Settings): they are not configured.**
     - The repository has only the `copilot` and `Production` environments; there is no `android-release` environment.
     - There are no repository or environment Actions secrets.
     - The owner said the signing keys are in "Supabase". Codex checked the SafeNest Supabase project `kflenmeizngmafwnwhgv`: no Storage buckets, no matching Vault secret names or tables, and the Edge Function Secrets page shows "No custom secrets created". Only the built-in Supabase API keys exist, and those are not Android keystores.
     - No secret value was read or copied, and no new key was created.
   - **Signing blocker:** the keystores behind the documented fingerprints (upload `64:59:CC:EC:…:CA:D9`, direct `E1:72:5D:0F:…:64:62`) have not been located.
     - Without the **direct** keystore, the pinned direct workflow cannot produce an APK, by design. Choosing a new direct key would mean changing the pin in `scripts/verify-direct-signing.py` and the documented fingerprint through review. It would also break updates and QR enrollment for any phone already holding an APK signed with the old key.
     - A lost **upload** key can be reset through Play Console support.
     - Next step: the owner finds the original `.jks` files and their backups (the 0.4.12 notes call for two offline backups). Then they create `android-release` restricted to `main` and add the secrets and `SAFENEST_UPLOAD_CERT_SHA256`.
   - Neither signed workflow has ever run. The direct-APK workflow exists only on this branch until PR #17 is merged.
   - Play listing, data-safety form and Accessibility/Device Admin declarations must match the updated disclosure.
   - The Play policy review of the full guard in the Play build is still to be done.
8. **Payments (owner accounts).**
   - Gateway accounts and keys, live webhook verification and reconciliation against real transactions.
   - Supabase Pro before taking money.
   - Until all of that is done, checkout stays disabled.
9. **Open PRs.**
   - #20, #23 and #25 are merged into this branch (27 Oct).
   - Kept out on purpose:
     - #24 (VPN catalogue; check the licence of `data/vpn-apps-playstore-2021.csv` first)
     - #22 (recovery tools; it changes `web/support-desk.js`, so reconcile it with the Sites source first)
   - #18 is superseded by `ea9f0a7`.
   - #12–#16 are older drafts.
10. **Wording.** `website/play/index.html` (Nest Run) still uses জুয়া. Change it if the বেটিং wording is meant to cover the whole site.

## Hosting configuration follow-up — 26 October 2026

A locally testable Workers Static Assets configuration and fail-closed manual workflow
are prepared in `docs/security-headers-cloudflare.md`. This does not clear any live
release gate: source reconciliation remains blocked, and no DNS, hosting or checkout
cutover is authorized by configuration validation. Follow the source-preservation,
account/zone preflight, mail-preservation and rollback requirements in that runbook.
