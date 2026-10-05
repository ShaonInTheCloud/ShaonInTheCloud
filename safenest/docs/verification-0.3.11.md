# Managed protection review — SafeNest 0.3.11

The supplied screenshots show iOS Configuration Profiles named Beanstalk/BetBlocker and an iOS VPN page marked Not Connected. A configuration profile can supply DNS settings without appearing as a connected VPN. The screenshots do not reveal their payloads, install authority, whether removal is locked, or prove what Gamban currently enforces. Gamban's Android Play listing says it uses a local VPN, Accessibility and ordinary Device Administrator permissions to resist removal; it does not document that every personal device becomes a Device Owner. Apple's documentation says profile removal depends on who installed it and on management.

This project is Android-first. Its opt-in Device Owner path already calls Android DevicePolicyManager for Always-on VPN, VPN configuration restriction, unknown-source installation restriction, VPN app suspension and uninstall block. It deliberately leaves VPN Lockdown off: this DNS-only tunnel does not relay all traffic and would cut ordinary internet in that mode. Device Owner enrollment generally happens during Android setup; a purchase or normal app permission cannot silently grant it. Factory reset, OS recovery, OEM differences and revoked Accessibility can still affect protection. Root is not requested and cannot be assumed.

## Changed

1. The managed Apply action requires enabled app guard and Android Accessibility permission in addition to DNS and browsing checks. The backend repeats the permission check at execution time.
2. Managed mode hides the local app-guard pause, disables its VPN-app switch, and guards internal preference changes against local pause or removing selected app rules while policies remain installed. Losing Accessibility is reported by status and does not silently reset the intended guard state.
3. The release workflow still requires an administrator recovery code. Give it to a trusted person outside the device if the customer wants a commitment barrier; do not make it impossible to recover from broken connectivity.

## Verification and remaining work

Source changes were reviewed and the existing pure Java network/domain regression suite and Node catalogue suite run on this workspace. Android SDK/Gradle build and Android Device Owner phone or emulator tests were unavailable here. The Honor device must verify that Android actually rejects uninstall and VPN reconfiguration, that normal browsing works, that a blocked site fails, and that known gambling/VPN apps return Home or are suspended. Test Android's visible Disconnect action and reboots. These source changes do not guarantee that all devices hide or reject the Android system control.

Subscriptions are not wired up. A future payment entitlement may trigger the enrollment flow but cannot create Device Owner privileges. iOS requires a separate app/profile/MDM implementation; SafeNest's Android APK cannot install an iPhone profile.
