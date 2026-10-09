# Strong lock (direct build)

Strong lock is SafeNest's strongest protection mode. During a paid period it:

- makes SafeNest the phone's **Always-on VPN** and blocks changes to VPN settings, so 1.1.1.1/WARP or any other VPN cannot replace it;
- **suspends VPN apps** already installed and any VPN app installed later;
- **blocks uninstalling SafeNest** and installing apps from unknown sources;
- applies the gambling list to Chrome's managed policy as a second layer;
- **releases these restrictions automatically** when the paid period ends (and a pending or failed payment never extends it).

After the period ends and the restrictions are released, **Setup → Remove SafeNest from this phone** gives up device-owner rights (`clearDeviceOwnerApp`) and opens Android's app page so the customer can tap Uninstall. No factory reset is needed. The button only appears when no paid period is active and every restriction is released. To use Strong lock again afterwards, the phone needs a fresh QR setup.

It only exists in the **direct** build. The Play build cannot offer it: Google Play does not allow apps that stop their own removal.

## Why the phone has to be set up for it

Android only lets a *device owner* app block its own uninstall or hold the VPN against other apps. An app becomes device owner only during phone setup, so Strong lock needs a phone that is **factory reset** and enrolled with the SafeNest setup QR. A normal install from a link can never get these powers; that is an Android rule, not a SafeNest limitation.

## Customer steps

1. Back up the phone. Factory reset it (Settings → System → Reset).
2. On the first **Welcome** screen, tap the same empty spot **six times**. A QR scanner opens (Android 7+; on some phones it first installs a reader).
3. Scan the SafeNest Strong lock QR and connect to Wi-Fi. Android downloads SafeNest from mysafenestbd.com, checks its signature, and finishes setup.
4. Open SafeNest, sign in, and start the trial or a paid plan.
5. In **Setup**, turn on protection and the app guard, then choose **Strong lock**. Set a recovery passphrase of 12+ characters and give it to a trusted person.

Enrollment alone changes nothing. Restrictions start only at step 5, only during a verified paid period.

## What the owner must do before offering it

1. **Production signing for the direct build.** The QR names the signing certificate; every update must use the same key. Never use a debug key for customers.
2. **Host the signed direct APK** over HTTPS on mysafenestbd.com (for example `/downloads/SafeNest-direct.apk`).
3. **Generate the QR** for that exact file:

   ```sh
   python3 tools/provisioning/make_qr.py \
     --apk SafeNest-direct-release.apk \
     --url https://mysafenestbd.com/downloads/SafeNest-direct.apk \
     --out build/strong-lock
   ```

   The tool refuses an HTTP URL, a Play or lab APK, and a debug-signed APK (`--allow-debug` exists only for a dedicated test phone).
4. **Test on a spare phone** (Android 10+ preferred): enrollment, Strong lock on, WARP blocked, uninstall blocked, reboot, period expiry releases everything.
5. **Support process** for lost recovery passphrases and failed releases. Releasing controls does not remove device-owner status; a later factory reset does.

## Honest limits

- A **factory reset** removes SafeNest and Strong lock. Android keeps that escape route on purpose.
- The recovery passphrase holder can release Strong lock at any time. Choose that person deliberately.
- Root, custom firmware and some OEM quirks are outside what any app can control.
- **Europe:** consumer law requires a working way to cancel. Present Strong lock as "locked until your chosen paid period ends, then released automatically", never as "can never be removed".

## Developer path (adb, test phones only)

`adb shell dpm set-device-owner com.safenest.app/.SafeNestAdminReceiver` still works on a phone with no accounts. See `device-owner-setup.md`.
