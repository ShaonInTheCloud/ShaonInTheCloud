# SafeNest 0.5.0 phone test plan — Play build with the full guard and Google Play Billing

Run on the `play` flavor from CI (`app-play-debug.apk` for guard tests; the signed release bundle via Play internal testing for billing). Record phone model, Android version, OEM skin version, browser version and SafeNest version for every run. Use a disposable test phone: there is no in-app stop during a commitment, and these tests put the phone into that state on purpose. Keep a recovery route (Safe Mode or a computer with adb) before you start.

Pass criteria are written as the user would see them. A "bounce" means: the screen closes and the Home screen appears within about a second, with a short SafeNest toast.

## A. Purchase and lock (needs Play Console product + service account + secrets)

| # | Step | Expected |
|---|---|---|
| A1 | Fresh install from the internal-test track. Sign in with a confirmed account that is a licence tester. | Subscription screen lists Monthly / Three months / Yearly with Play's localized prices. |
| A2 | Choose Monthly → Subscribe. Complete Play's sheet (test card, no charge). | "Verifying" then the setup screen opens. Supabase: one row in `payments.play_subscriptions` for this user, one `protection_entitlements` row `provider=google_play`, `status=active`. Google: purchase acknowledged within a minute. |
| A3 | Kill the app, reopen, go to Subscription. | Shows "committed until <date>", no pause/stop control. |
| A4 | On a second phone, sign in with a *different* account and tap Restore purchase with the same Google account. | "This Google Play purchase belongs to a different SafeNest account." Nothing granted. |
| A5 | In Play Store → Subscriptions, cancel the subscription. | RTDN arrives (`play-billing-rtdn` log), ledger `state=CANCELED`, entitlement stays `active` until expiry. Protection continues. |
| A6 | (Test-track clock: Play's 5-minute test renewals.) Wait past expiry. | Phone releases protection by itself: VPN off, guard off, Device Admin removed. Subscription screen offers Subscribe/Restore again. |
| A7 | Refund the test purchase from Play Console (Order management). | RTDN → `REVOKED` → entitlement `revoked` → the app releases on its next checkpoint (≤ 30 s while the Accessibility service is connected, otherwise on open). |
| A8 | Airplane mode on, then attempt Subscribe. | "No connection" message; nothing granted. |

## B. Consent, activation, disclosure

| # | Step | Expected |
|---|---|---|
| B1 | Open Setup. Tap the Accessibility card. | The disclosure dialog appears first and mentions: blocked gambling apps, VPN apps, VPN install pages, Settings screens that would uninstall/force-stop/disable SafeNest, Private DNS; nothing recorded or uploaded; Safe Mode/reset/computer can still remove the app. |
| B2 | Tap Decline. | Returns to Setup; Accessibility still off; status says permission missing, not protected. |
| B3 | Accept, enable SafeNest in Android Accessibility settings, come back. | Card shows enabled. |
| B4 | Enable Device Administrator. | Android's admin screen shows only the deactivation-step description; no password/camera/wipe policies listed. |
| B5 | Activate protection. | DNS disclosure dialog, VPN consent, then the home screen shows the end date. Notification "SafeNest DNS filter" is present. |

## C. The guard (protection active)

| # | Step | Expected |
|---|---|---|
| C1 | Open Chrome → a listed gambling domain (use a domain from My list, e.g. `example.org`). | Page fails to load (DNS). Chrome stays open. `example.com` loads. |
| C2 | Chrome → Settings → Privacy → Use secure DNS → choose a provider. | In Chrome this setting can be changed (Chrome is not Settings), **but** the resolver names (`dns.google`, `cloudflare-dns.com`, …) are NXDOMAIN'd by the filter, so Chrome falls back and the listed domain still fails. Record if any provider still resolves it. |
| C3 | Install any gambling app from the known list (or add a harmless app to My apps). Open it. | Bounce. Toast "SafeNest blocked this app." |
| C4 | Open an installed VPN app (any brand, including one not in SafeNest's list, e.g. a freshly sideloaded one). | Bounce. |
| C5 | Play Store → search "VPN" → open any VPN app's page. | Bounce before Install can be tapped. Also test Samsung Galaxy Store / Xiaomi GetApps on those phones. |
| C6 | Play Store → open WhatsApp's page → Install. | No bounce. Installs normally. |
| C7 | Sideload a VPN APK from a browser download. | Install dialog may or may not bounce (package-installer labels differ by OEM: record which). After install, opening it bounces (C4). |
| C8 | Settings → Apps → SafeNest. | Bounce (App info page names SafeNest with Uninstall/Force stop). Record if the page briefly shows before the bounce. |
| C9 | Long-press the SafeNest icon on the launcher → Uninstall / App info. | Uninstall confirmation bounces. App info bounces. |
| C10 | Settings → Accessibility → SafeNest → toggle off. | Bounce when the detail page with the toggle opens. |
| C11 | Settings → Security → Device admin apps → SafeNest → Deactivate. | Bounce; if the OEM shows the confirmation first, the lock-aware message is displayed. |
| C12 | Settings → Network → VPN → SafeNest (gear) → Disconnect / Forget. | Bounce. |
| C13 | Settings → Network → VPN → another VPN → Connect. | Bounce ("blocked another VPN"). |
| C14 | Settings → Network → Private DNS. | The dialog bounces; the Network list itself stays usable. |
| C15 | Force another VPN to take over from adb (`adb shell`) or via its own always-on setting enabled *before* SafeNest. | High-priority notification "SafeNest protection interrupted"; opening SafeNest restores the filter; the other VPN app bounces. |
| C16 | Settings → Apps → WhatsApp → Force stop. | No bounce: other apps' pages stay usable. |
| C17 | Settings home, Accessibility list, VPN list, Wi-Fi, Bluetooth, Display. | All usable. Only SafeNest's own pages and VPN/Private DNS dialogs bounce. |
| C18 | Reboot. | VPN restarts (always-on recommended), guard reconnects, end date unchanged. |
| C19 | Set the clock back 10 days in Settings → Date & time (manual). | Lock does not shorten. Set the clock forward 2 years. Lock does not end early on the same boot; after a reboot with a clock far in the future, the app asks to re-verify rather than trapping the phone (documented behaviour). |
| C20 | Battery saver on, leave the phone idle overnight. | Filter still on in the morning (notification present); record OEM process kills. |
| C21 | Boot into Safe Mode. | SafeNest is disabled (expected, documented). Normal reboot restores it. |

## D. Release and edition

| # | Step | Expected |
|---|---|---|
| D1 | Settings tab → Edition: switch Bangladesh ↔ Global. | Default language suggestion changes only for a fresh install; the running language toggle still works. |
| D2 | Bangladesh SIM, fresh install. | First launch in Bangla. |
| D3 | No SIM, device locale en-GB, fresh install. | First launch in English. |
| D4 | Wait for natural expiry (A6). | Everything releases: VPN off, Accessibility guard inactive (service may stay enabled but does nothing), Device Admin removed, Settings pages usable, uninstall possible. |

## Phones to cover before production

Samsung One UI (most common in Bangladesh), Xiaomi/Redmi MIUI or HyperOS, Realme/Oppo ColorOS, Vivo Funtouch, one Pixel/AOSP. Record every OEM where a C-row needed a second attempt or showed the page before bouncing; those become label additions in `SystemScreenGuard.java`.

## Evidence to keep

Screen recordings of A2, B1-B5, C3, C4, C5, C8, C9, C15 (these double as the Play declaration video), Supabase row screenshots for A2/A5/A7, and the RTDN function logs.
