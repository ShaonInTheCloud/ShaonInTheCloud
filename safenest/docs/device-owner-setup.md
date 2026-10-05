> Version note (0.3.12): The customer release and emergency buttons described in older steps below were removed. Keep an authorized administrator recovery procedure for failed protection. This project does not have paid entitlement enforcement.

# SafeNest 0.3.1: Android disconnect and device-owner setup

## What the screenshot establishes

The Android VPN dialog offers **Disconnect**. That button can remain visible even with always-on enforced; its presence alone does not establish that disconnection succeeds. The source version visible in the supplied screenshot is **0.2.0**. Gradle sync does not install an update: run the new app on the selected device.

A VPN connection, Device Administrator permission, work profile, and Device Owner enrollment are different. This build uses full-device owner enrollment for its managed controls. A payment does not grant these Android privileges. SafeNest 0.3.1 does **not** implement paid subscriptions, a billing entitlement backend, or automatic customer QR enrollment.

## Developer setup on the user's Windows emulator

1. Extract this ZIP and open **SafeNest/android** in Android Studio.
2. Select the correct emulator and press **Run**. Do not uninstall a managed older copy to update it; install the update with the same signing key. A different signing key requires a separate migration plan.
3. Use a dedicated emulator or spare test phone with no accounts, other owner, or conflicting profiles. Do not factory-reset a personal phone without backing up its data. These instructions do not reset a device.
4. Open PowerShell. Use the Android SDK location from Android Studio if it differs from the default below:

```powershell
$adbPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adbPath devices
```

Choose the serial printed for the intended test device, then replace `YOUR_DEVICE_SERIAL`:

```powershell
$safeNestSerial = 'YOUR_DEVICE_SERIAL'
& $adbPath -s $safeNestSerial shell dpm set-device-owner com.safenest.app/.SafeNestAdminReceiver
```

The expected successful response identifies `com.safenest.app/.SafeNestAdminReceiver` as device owner. If Android reports existing accounts, an existing owner or another eligibility problem, stop and address that error on the test device. Do not try to overwrite someone else's management. Android Studio can create a separate clean emulator without erasing your current one.

5. Reopen SafeNest → **Setup**. **Device Owner** must read **Enrolled**. An ordinary administrator approval popup is insufficient.
6. Start website protection. Verify `https://example.com` loads. Add `example.org` to My list and verify it is blocked while `example.com` still loads. Test before applying stronger controls.
7. Choose **Apply / repair controls**. Set a unique recovery passphrase of at least 12 characters, retype it, and store it outside the phone. A trusted person can hold it if the phone user should not control release. Confirm the disclosed controls and completed browsing tests.
8. Apply. Reopen Setup and confirm:

| Check | Expected |
|---|---|
| Device Owner | Enrolled |
| SafeNest is managed Always-on VPN | Confirmed |
| VPN setting changes restricted | Confirmed |
| SafeNest uninstall restricted | Confirmed |
| Administrator recovery code | Configured |
| Block connections without VPN / Lockdown | Off |

On Android 8–9 this version cannot independently read back managed Lockdown with the API used, so it does not claim complete verification. Prefer Android 10 or newer for this test.

## Test the effect, not just the button

After applying verified controls, try Android's Disconnect action. Check whether SafeNest actually remains connected and blocked domains remain blocked. Try changing Always-on, switching to the installed GambleGuard or another VPN, uninstalling SafeNest, and restarting the device. Repeat allowed and blocked browsing tests after restarting. Some system dialogs can still display a button whose operation Android rejects. OEM behavior needs actual testing.

**Keep “Block connections without VPN” off.** This build routes DNS only. Turning on full-traffic Lockdown can break ordinary internet access. Managed Always-on and the restriction on changing VPN settings do not require Lockdown. There can still be coverage gaps during startup or service failure, and encrypted DNS/proxies/unknown domains remain separate limitations.

## Administrator recovery and upgrades

- New managed setups require a recovery code. Apply/repair and release both verify the existing code; repair may roll back policies if applying them fails.
- The 0.3.12 customer interface has no Release controls action. The underlying policy recovery journal remains for an authorized administrator or future support workflow. Do not provision customers without an operational recovery procedure.
- Existing 0.2/0.3 managed installations without a code need administrator review before upgrade. Apply/repair with a new code upgrades that installation. Updating the APK alone does not secretly lock an existing user out.
- The in-app emergency stop was removed; the VPN service refuses an ordinary stop request while managed controls remain. Android revocation, fatal-error cleanup and administrative recovery are not suppressed.
- Releasing controls does not unenroll Device Owner. Do not sell this as an unremovable subscription or promise it survives reset, root, privileged debugging or every Android/OEM behavior.

## Lost or damaged recovery code: development builds only

The code cannot be displayed or emailed back. A trusted person's saved copy is the normal recovery method. This source project has no production support backend. A debug build is intentionally not a tamper-resistant release: an authorized developer with an already authorized ADB connection can recover its local credential.

On your **dedicated test device only**, with its intended serial, these commands remove only the debug credential file, retaining the managed-policy recovery journal. This will permit the old no-code recovery route after restarting the test device:

```powershell
& $adbPath -s $safeNestSerial shell run-as com.safenest.app rm -f shared_prefs/safenest_managed_recovery.xml shared_prefs/safenest_managed_recovery.xml.bak
& $adbPath -s $safeNestSerial reboot
```

After reboot, reopen SafeNest and use **Release controls**. Do not delete the app's policy journal or clear all app data: that can lose original settings needed for restoration. `run-as` requires a debuggable build and authorized ADB; it does not work as a consumer release recovery service. If it fails, retain the error and use your development administrator's assistance. Do not enable stronger restrictions on your only phone while recovery is untested.

## Sources

- [Android enterprise VPN policies and readbacks](https://developer.android.com/work/dpc/network-telephony)
- [Android developer device-owner enrollment](https://developer.android.com/work/dpc/dedicated-devices/cookbook)
- [AOSP VPN system dialog](https://android.googlesource.com/platform/frameworks/base/+/master/packages/VpnDialogs/src/com/android/vpndialogs/ManageDialog.java)
- [AOSP Settings VPN management](https://android.googlesource.com/platform/packages/apps/Settings/+/master/src/com/android/settings/vpn2/AppManagementFragment.java)

This guide is for the included prototype. Its Android policy behavior and recovery flows still need validation on the user's emulator/phone.
