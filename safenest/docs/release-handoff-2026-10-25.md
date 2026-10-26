# SafeNest release handoff — 25 October 2026

**Status: not ready for public release.** The launch gates are listed below. This report changes no code, no providers, no DNS and no deployment.

## 1. Two source tracks

| Track | What it is | Exact reference | Live? |
|---|---|---|---|
| **A. Sites (website)** | Source for the live mysafenestbd.com, published through Sites hosting | Sites source `4081fe8ac308609a7cf8b4aa5ac79f2080f3847c`, published as **v45**: deployment `appgdep_6add6ae0c6608191b4e030cf8a4cca62`, saved version `appgprj_6ab4d7e9b7f881919fce6caac5d93115~appgver_1d5f78a5a23c8191b0f84d8ad650196b`. (`b9d5b4cd…` is an older Sites source.) It contains the PR #21 design plus newer auth, CAPTCHA and download changes. **This commit is not on GitHub.** | Yes, v45 is live |
| **B. GitHub PR #17** | Global edition, card/crypto payment code (disabled), Play Billing, Android guard, iPhone DNS filter, Expo app, and the website as merged from `main` | Branch `safenest-global-card-crypto`, head `b540d97f3396581a7e575221daa8ec2b0df158e0`. `main` is at `972eb3ff6b6b371d4844d3a22cb2a8c25d70bec1`, which includes PR #21. | No, not merged to `main` |

The two tracks are separate. Merging or deploying PR #17 must not overwrite the Sites source or the published v45 without someone first reconciling it against Sites `4081fe8a`. That source contains auth, CAPTCHA and download work that GitHub does not have.

### What is on PR #17 now
- `ea9f0a7`: `launch/play-billing-and-full-guard` (PR #18) merged into the Global branch, version **0.5.16 / versionCode 45**.
  - Owner rule (17 Oct, confirmed 25 Oct): SafeNest's own Settings pages stay open, and only the destructive tap bounces Home.
  - The Play accessibility config now includes `typeViewClicked`.
  - The Play disclosure text was updated to match.
- `b540d97`: `main` merged in. It brings the hot-pink S/rabbit theme, written relative to `base` so `global/` pages work, and the বেটিং wording. PR #17's checkout-disabled copy and Play Store launch switch are kept.
- The Android source tree is identical at `ea9f0a7` and `b540d97`: `safenest/android` = `49396f921ff922651d37024465d1ff0fc583d52d`.

## 2. Evidence

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

- **0.5.16 is a lab APK** (`com.safenest.app.lab`, "SafeNest Test"). It is for testing only. It is not signed for release and not published.
- **The current public website download remains 0.4.10** (the direct development APK).
- **Full real-phone protection is unverified.** No 0.5.16 or 0.5.15 build has completed browsing and blocking acceptance on a real phone. Emulator browser acceptance is also unverified: launching Chrome on the emulator was blocked by an approval review and has not been retried.
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
4. **Android CI green at the PR #17 head.** Includes the emulator guard flow.
5. **Real-phone acceptance of a signed 0.5.16 build.** Check that:
   - blocked sites in Chrome step back, and mirror domains are blocked
   - gambling, VPN and Tor apps bounce Home
   - Settings pages open, but Uninstall, Force stop, Clear storage and Forget VPN taps bounce
   - Always-on VPN can be turned on but not off
   - reboot restores protection
6. **Open observation OBS-1** (project doc `claude/android-open-observations.md`). The lab v1 "allfeatures" 0.5.15 APK (SHA-256 `0d1b0060…71ddcb`) showed "Accessibility permission missing" while dumpsys listed the service as bound. It is unresolved, and **not** shown to be a defect in current source. Reproduce it on a CI-built APK whose commit is known.
7. **Release signing and Play submission.**
   - Upload and direct keystores go into secrets.
   - Play listing, data-safety form and Accessibility/Device Admin declarations must match the updated disclosure.
   - The Play policy review of the full guard in the Play build is still to be done.
8. **Payments (owner accounts).**
   - Gateway accounts and keys, live webhook verification and reconciliation against real transactions.
   - Supabase Pro before taking money.
   - Until all of that is done, checkout stays disabled.
9. **Open PRs to triage.**
   - Android: #20 (uptime clock), #23 (protection restore), #24 (VPN catalogue) and #25 (Always-on leave) target `safenest-global-card-crypto`, and #22 (recovery tools) is also open.
   - #18 is superseded by the merge in `ea9f0a7`.
   - #12–#16 are older drafts.
   - Each needs a rebase onto `b540d97` and its own CI run.
10. **Wording.** `website/play/index.html` (Nest Run) still uses জুয়া. Change it if the বেটিং wording is meant to cover the whole site.

## Hosting configuration follow-up — 26 October 2026

A locally testable Workers Static Assets configuration and fail-closed manual workflow
are prepared in `docs/security-headers-cloudflare.md`. This does not clear any live
release gate: source reconciliation remains blocked, and no DNS, hosting or checkout
cutover is authorized by configuration validation. Follow the source-preservation,
account/zone preflight, mail-preservation and rollback requirements in that runbook.
