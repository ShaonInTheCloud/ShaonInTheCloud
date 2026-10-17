# Merge guide: `launch/play-billing-and-full-guard` → `safenest-global-card-crypto`

Goal: one branch with **everything**. From launch: Google Play Billing, the full guard in the Play
build, the strong-lock QR enrollment and the signing setup. From global: guard 0.5.1–0.5.13, the 541k
blocklist, gambling-app/VPN-browser/Tor/proxy/IP blocking, the iPhone DNS filter, the Help screen, the
Expo app and the Neon Nest redesign.

Checked with `git merge-tree` on 2026-10-18 (global at `26b62e8`). **8 files conflict.** One more file,
`SafeNestVpnService.kt`, merges with a duplicate constant that will not compile; see step 5.
Everything else merges cleanly.

## 0. Start

On GitHub: **Pull requests → New** → base `safenest-global-card-crypto`, compare
`launch/play-billing-and-full-guard` → **Create** → **Resolve conflicts**. Or locally:

```sh
git checkout safenest-global-card-crypto && git pull
git merge origin/launch/play-billing-and-full-guard
```

## 1. `.github/workflows/safenest-deploy-backend.yml`. Combine both lists.

```yaml
          for fn in payment-notification payment-return payment-reconcile payment-catalogue payment-order payment-status dns-query play-billing-verify play-billing-rtdn; do
```

## 2. `safenest/android/app/build.gradle.kts`. Take global's version, then bump it.

```kotlin
        versionCode = 43
        versionName = "0.5.14"
```
(Launch's `0.5.0` is older. Its Play flavor change — `ALLOW_SYSTEM_GUARD = "true"`, `MANAGED_CONTROLS = "true"`,
the billing dependency and the signing blocks — merges in automatically. Check that it is there.)

## 3. `safenest/supabase/config.toml`. Keep both blocks.

Keep global's `[functions.dns-query] verify_jwt = false` **and** all of launch's blocks
(`play-billing-verify`, `play-billing-rtdn`, `payment-order`, `payment-status`). Delete only the markers.

## 4. `.../app/RulesStore.kt`. Take global's line.

```kotlin
        return DomainRules.isBlocked(host, rules) || BundledGamblingRules.isBlocked(context, host) || BypassDomains.isBlocked(context, host)
```

## 5. `.../app/SafeNestVpnService.kt`. Conflict plus a duplicate.

Both branches added a "protection interrupted" alert.
- Conflict near `startProtection`: keep **both** lines (cancel both alerts):
  ```kotlin
              try { getSystemService(NotificationManager::class.java)?.cancel(TAKEOVER_NOTIFICATION_ID) } catch (_: Exception) {}
              getSystemService(NotificationManager::class.java)?.cancel(ALERT_ID)
  ```
- **Not marked, but breaks the build:** `ALERT_CHANNEL` is declared twice in the companion object.
  Delete launch's line `private const val ALERT_CHANNEL = "safenest_alerts"` and keep
  `"safenest_protection_alerts"`.
- Optional tidy-up: in `alertInterrupted()` use `TAKEOVER_NOTIFICATION_ID` instead of `ALERT_ID`, so a takeover shows one
  alert, not two. Also remove the doubled `dnsTransport.set("none")` in `disposeSession()`.

## 6. `.../app/SystemScreenGuard.java`. 4 conflicts.

1. **App-store list:** combine. Keep launch's longer list (Galaxy Store, Xiaomi, Oppo, Vivo, Transsion,
   Aurora) **and** global's line `public static final String VPN_DIALOGS = "com.android.vpndialogs";`.
2. **Action sets:** keep **both**. That means global's `DESTRUCTIVE_CONTROLS` and `CONFIRM_BUTTONS`, and launch's
   `STORAGE_ACTIONS` and `PROTECTED_ACTIONS`, with the comment line after them. Launch's `blocksOwnStorage` and other methods need its
   sets, and global's tap guard needs its own.
3. and 4. **`blocksSafeNestControl`:** take **global's** version (the `recentlyOnOwnPage` parameter, returning
   `confirmationReason(...)`). This is the "pages open, only the tap bounces" behavior the emulator tests check.
   Launch's version bounced whole pages. If launch code still calls the titles overload, either keep launch's
   5-argument method under the name `blocksSafeNestControlByTitle` or delete that call.

## 7. `.../app/SafeNestAccessibilityService.kt`. 1 conflict: keep both, in this order.

```kotlin
            if (BuildConfig.ALLOW_SYSTEM_GUARD && SystemScreenGuard.blocksSafeNestControl(name, screen.labels, screen.actions, screen.checkedToggle, recentlyOnOwnPage(now))) {
                hardExit(now, "safenest_control", "SafeNest commitment is active until your paid period ends.")
                return true
            }
            if (guardActive && GuardPreferences.blocksVpnApps(this) &&
                SystemScreenGuard.blocksVpnInstall(name, screen.titles.ifEmpty { screen.labels }, screen.actions)) {
                returnHome(now, "vpn_install", "SafeNest blocked this VPN installation screen.")
                return true
            }
            if (guardActive && GuardPreferences.blocksVpnApps(this) &&
                SystemScreenGuard.blocksOtherVpnControl(name, screen.labels, screen.actions)) {
                returnHome(now, "other_vpn", "SafeNest blocked another VPN while your protection period is active.")
                return true
            }
            if (guardActive && GuardPreferences.blocksEncryptedDnsBypass(this) &&
                SystemScreenGuard.blocksPrivateDns(name, screen.labels, screen.actions)) {
                returnHome(now, "private_dns", "Private DNS stays as it is during your protection period.")
                return true
            }
```
Then keep global's next block (`BlockedAppCatalog.blocksInstall`, the gambling/VPN-browser install pages) unchanged.

## 8. `.../test/.../CommitmentGuardRegression.java`. 1 conflict: keep both, but drop launch's last loop.

Keep global's lines ("Pages stay open; the tap…" loop) **and** launch's `blocksVpnInstall`, `blocksPrivateDns`
and `blocksOtherVpnControl` checks. **Delete** launch's final loop
`for (String action : List.of("Disconnect", "Forget VPN", "Uninstall", "Force stop")) { check(SystemScreenGuard.blocksTestControl(...)) }`.
It expects whole pages to bounce, which you replaced on 17 Oct with "only the tap bounces".

## 9. One fix for the Play build after merging

Launch's `src/play/res/xml/safenest_accessibility_service.xml` has no `typeViewClicked`, so the Play build
can't see taps on Uninstall, Force stop or Forget VPN. Make its event types match `main`:
```xml
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewClicked"
```
Then update `src/play/res/values/strings.xml` and the Play text in `DistributionDisclosure.kt` to mention gambling
apps, browsers with a built-in VPN and sites opened by IP address. Google checks that the disclosure matches what
the app does.

## 10. Check and push

```sh
cd safenest
npm test && sh run-core-tests.sh
cd android && ./gradlew :app:testPlayDebugUnitTest :app:testDirectDebugUnitTest :app:testLabDebugUnitTest :app:assemblePlayDebug :app:assembleDirectDebug
git commit && git push
```
CI then runs the emulator guard test, including the Opera, gambling-app, VPN-app and Notes checks.
After that, Claude can pick it up again: fix anything CI reports, build the APKs and the Play bundle.
