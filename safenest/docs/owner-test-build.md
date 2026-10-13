# SafeNest Owner Test build (debug only)

A separate test app, **SafeNest Owner Test** (`com.safenest.app.ownerlab`), for trying the device-owner lock without a trial or subscription. It installs beside the real app and the Lab build.

- Build variant: `ownerlabDebug`. A release version cannot be built.
- Paid check: replaced by 60-minute local test sessions, like the Lab build.
- Device-owner controls: included (always-on VPN, VPN settings restricted, uninstall blocked).
- Ending the test (Stop test, expiry or reboot) also releases the device-owner controls, so the test phone never stays locked.

## Steps

1. Use a clean emulator with no Google account (Device Manager, then + and a system image without Play Store, or Wipe Data on a spare one).
2. In Android Studio, Build Variants: set `:app` to `ownerlabDebug`. Run.
3. In PowerShell (replace the serial with the one from `adb devices`):

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adb devices
& $adb -s emulator-5556 shell dpm set-device-owner com.safenest.app.ownerlab/com.safenest.app.SafeNestAdminReceiver
```

4. In SafeNest Owner Test: Test tools, enable its Accessibility service, Start local test, accept the VPN prompt.
5. Setup: Device Owner should read Enrolled. Check `example.com` loads, then Apply / repair controls and set a recovery passphrase.
6. Try Uninstall, Forget VPN, turning off Always-on, and starting another VPN. Android should refuse or grey them out.
7. Stop test releases everything.

To remove device owner from the emulator completely, wipe its data in Device Manager.
