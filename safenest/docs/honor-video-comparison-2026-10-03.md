# Honor phone recording: SafeNest 0.3.11 versus Gamban

The supplied 40-second recording was inspected at multiple points. It shows one Honor Android device, not a controlled blocking test. It does not show a gambling URL, an active SafeNest DNS request, a paid subscription, or Device Owner enrollment.

| Time | Observed on phone | Meaning |
|---|---|---|
| 0–4 s | SafeNest App info, version 0.3.11, has Uninstall. | This installation can reach the ordinary uninstall flow. |
| 6–10 s | Gamban App info also has Uninstall; its app opens a menu with account, profile, devices, support and report options. | The visible Uninstall button alone does not prove whether Android would complete removal. The support/account product surface is more mature than SafeNest. |
| 14–16 s | SafeNest Setup says DNS service Stopped, allowed-site DNS Not running, app guard Off, VPN app blocking selected but app guard not connected. | No SafeNest website or VPN-app protection is evidenced at that moment. The VPN-app selection is an intent, not enforcement. |
| 20–24 s | SafeNest Device administrator panel says active and offers Deactivate and uninstall. | Device Administrator is not Device Owner and does not prevent a user from starting deactivation. |
| 30–39 s | Android VPN list contains Gamban (Connected), 1.1.1.1, and SafeNest (not connected). | Gamban is visibly using Android's VPN list on this phone. SafeNest is not the active VPN. The recording contradicts the premise that Gamban necessarily has no VPN entry. |

## Changes made in 0.3.12 source

The Android home illustration, domain totals, app-guard pause, selected-app remove button, customer emergency restore, and customer managed release button were removed. Deep pink and white replace the previous accent colors. The Protect page still lists and searches domains. Guard intent remains selected if Android disconnects Accessibility, while setup reports the missing permission. Supabase now has a read-only-to-user entitlement schema for future weekly, monthly and annual billing; payments and automatic expiry are not connected.

## Next phone verification

Build and install version 0.3.12 with the same signing key. In Setup, enable DNS filtering and grant its Android VPN consent, then confirm a normal site loads while a listed test site fails. Separately enable app guard and Accessibility, then test a selected harmless app and a visible VPN app. Confirm Chrome stays open. Check that Setup reports both services running before judging blocking. Device Owner enforcement requires an eligible provisioned test phone; the recording shows only ordinary Device Administrator. Never turn on VPN Lockdown with this DNS-only implementation because ordinary traffic is not tunneled.

## Owner's follow-up observation

The owner reports that tapping Gamban's Uninstall or VPN Disconnect returns to Home, while the corresponding SafeNest action proceeds. The recording shows the buttons and service states, but does not clearly capture both completed attempts; treat the different behavior as an owner-reported phone test. It does not establish which mechanism Gamban uses internally.

SafeNest's ordinary Device Administrator installation cannot set Android's `setUninstallBlocked`, `DISALLOW_CONFIG_VPN`, or managed Always-on policies. The supported route is eligible Device Owner provisioning, applying the policies, and checking the policy readback on that phone. Accessibility-driven Home redirection on system uninstall/VPN screens would depend on OEM UI text and could race the action; it is not a trustworthy substitute. Google Play's accessibility policy also limits preventing users from disabling or uninstalling apps outside authorized parental or enterprise management. The app-guard Home action remains scoped to selected third-party apps and does not hijack system settings.
