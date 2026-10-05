> Version note (0.3.12): This older test plan contains historical pause/emergency/release steps. Those customer buttons were removed. Verify repair through an authorized administrator process before managed rollout.

# SafeNest 0.3.1 device test plan

Record Android version, manufacturer, browser version, connection type, and build version for every run. Use a test phone/emulator and harmless test domains. Do not announce a public launch until the required flows pass on actual target devices.

## Personal mode

1. With DNS and guard off, open example.com, YouTube and Facebook. Check Wi-Fi and mobile data separately.
2. Add example.org to My list. Enable SafeNest DNS. example.com must load; example.org and a subdomain must fail lookup. Reopen the browser between tests to reduce stale DNS/connection effects.
3. Open several ordinary sites quickly, then switch Wi-Fi/mobile data and airplane mode off/on. Normal sites must recover; status must reflect missing network and errors.
4. Verify the app has no pause or stop action. On a disposable personal test device, recover through Android VPN settings if browsing fails; record DNS and app-guard status.
5. Check Android Lockdown detection. This DNS-only version must show an error; turn Lockdown off after checking. Do not leave the test device without a recovery route.
6. Enable Accessibility only after disclosure consent. Reject it once and check the setup state reports permission missing, not protected.
7. Select a harmless installed user app, enable guard and open it. It should return to Home. SafeNest, Settings, permission controls, launcher and calls must stay usable.
8. Enable detected-VPN blocking. Open an installed VPN app and check Home behavior. Attempt a settings-based VPN switch separately: personal mode must not claim that it is prevented. If SafeNest is replaced, DNS status must turn off.
9. In Chrome and Firefox, open listed example.org with the guard enabled. The browser must remain open. A DNS failure may block navigation; browser Secure DNS may bypass it. Verify example.com and a lookalike name still load. Record behavior with hidden toolbars and incognito/private mode as coverage gaps if it fails.
10. Revoke Accessibility and return to SafeNest: it must report the guard off. No automatic permission clicking or interference with revocation is permitted.
11. Activate and deactivate optional Device Admin. Confirm no wipe/password/camera policies are requested and deactivation remains possible.
12. Verify the app guard has no pause control and selected app rules have no removal control while active. Confirm the grounding timer counts down.
13. Import valid JSON, an invalid JSON file, a file over 2 MiB, Unicode domains, duplicate domains and at most 25,000 entries. UI must remain responsive.
14. Confirm stored check-ins correspond to real data and no domain total appears in the UI. No fabricated money or protected-day figures should appear.

## Managed mode — separate fresh test device/emulator

1. Provision device owner through Android's supported test/enrollment path. Personal Device Admin alone must not enable managed controls.
2. First pass ordinary/blocked browsing tests. Save current VPN, Private DNS, app suspensions and Chrome managed-policy state.
3. Apply managed controls from SafeNest. Verify each policy in Android, not merely the successful return from its API.
4. Try changing VPN and Private DNS settings. Verify the configuration restriction applies. Confirm new and already-installed visible VPN apps are suspended; system/hidden exceptions must be reported as coverage limits.
5. Check Chrome `chrome://policy` for `DnsOverHttpsMode=off` and its actual enforcement. Verify normal browsing still works.
6. Confirm unknown-source installation restrictions; document that ADB/trusted stores are separate. Check other profiles and browser types separately; they are not universal guarantees.
7. Reboot and change networks. Confirm SafeNest restarts and normal websites still load.
8. Reapply controls and verify previous recovery data was preserved.
9. Verify an authorized administrator can recover controls through a supported support procedure before customer enrollment. Compare against the saved original settings. Other pre-existing app suspensions/policies should remain.
10. Test failure/retry while a saved Private DNS hostname is unreachable. Recovery should report pending settings and permit retry, not claim full release.

## Release evidence still required

### Additional 0.3 cases

- Verify Private DNS Off, Automatic, working strict provider and unreachable strict provider separately. API 29+ must preserve platform DNS behavior; API 28 encrypted Private DNS must show incompatibility and remove the local filter. Capture queries only on your own test network to verify no silent plaintext fallback.
- Resolve ordinary CNAME, DNAME and HTTPS/SVCB fixtures; block a reachable alias target and verify a denial. Unrelated additional records must not block the original site. Check latency during an upstream outage and cancellation during restart/network change.
- Configure a trusted test publisher. Import a valid signed file and check DNS/browser rules update. Reject wrong keys, altered bytes, expired lists and old revisions while retaining the last good rules.
- Restart after import, during a failed download and after a key change. Check expiry/stale warnings, the previous trusted snapshot and high-water revision. Test 30-second download cancellation and oversized content.
- Verify a newer signed revision can correct a wrongly listed site without deleting personal rules. Confirm no private publisher key is installed on the device.
- On a managed device, verify Chrome URLBlocklist at chrome://policy and test ordinary/blocked navigation, DoH enabled/disabled, subdomains, lookalikes, existing allowlist exceptions and more than 1,000 rules. Omitted count must be visible. Test in-page navigation and requests as separate unsupported paths.
- Modify Chrome policies outside SafeNest on your own test device. Refresh/release must preserve that change and report a conflict, not overwrite it silently. Verify exact restoration of original policy type/value after normal release.
- Exercise search and pagination with a large catalog; UI must remain responsive.

### Release gates

- Full Gradle unit-test and debug/release builds.
- Real screenshots/logs proving both allowed and blocked traffic.
- OEM battery/process-death/reboot behavior.
- Accurate privacy disclosure and store Accessibility/VPN declarations.
- Signed release, maintained blocklist update system, enrollment/recovery support and distribution testing.


## 0.3.1 managed enforcement and recovery

These are pending device tests, not claims of completed testing.

1. On a non-owner personal installation, confirm Setup explicitly reports no Device Owner and unconfirmed managed controls even with VPN/Device Administrator enabled.
2. Enroll a dedicated test emulator via docs/device-owner-setup.md. Confirm policies still read incomplete before Apply; ownership alone is not success.
3. Complete ordinary/blocked browsing tests; apply with a new recovery code held outside the device. Check Android policy readbacks all confirm. On Android8–9, lack of Lockdown readback must remain unverified.
4. Test actual system Disconnect: button visibility is not the assertion. After using it, confirm the DNS service and policies remain active and normal/blocked test sites behave correctly. Repeat from notification and Settings surfaces.
5. Test VPN switching, Always-on modification, uninstall, restart and app update with matching signature. Capture device/OEM/version and policy values for failures.
6. Wrong recovery code must not invoke repair/release. Repeated attempts show bounded cooldown; a correct code after cooldown permits recovery. Reopen/rotate during hashing and policy changes; confirm only one operation completes and credentials do not appear in restored UI state or logs.
7. A repair requires the existing code because failure can roll policies back. Partial rollback/release must retain the code and recovery journal. A complete release restores prior settings and removes the code. Simulate interrupted mutation only on a disposable emulator with external recovery available.
8. Upgrade an old managed installation with no code: explicit legacy release remains possible; Apply/repair stores a new code and subsequent release requires it. Unconfigured or corrupt credential state must never be confused.
9. In a debug build on a disposable emulator, test the documented external recovery after confirming the saved normal code works. Do not present developer ADB recovery as a release customer service.
10. Ensure an ordinary ACTION_STOP request while managed is refused; after authenticated release it works. Android onRevoke/fatal-error cleanup must still release network resources.
11. Subscription purchase/expiry is not implemented. Do not show a paid term, claim the lock follows payment, or ship a paid product based on these local controls alone.

## 0.3.2 browser regression checks (pending physical testing)
1. Upgrade while Chrome was previously selected as a blocked app. Chrome must remain open.
2. Enable protection; visit a listed blocked domain in Chrome. The page must fail or show a browser policy block without returning Home.
3. Open an allowed site in another Chrome tab; it must load.
4. Open a selected blocked nonbrowser app; Home enforcement must still work.
5. Test Wi-Fi and mobile data, IPv4/IPv6 and Secure DNS settings. Record bypasses separately; do not count browser exemption as proof of website enforcement.
6. On an enrolled Device Owner device, verify Chrome URL policy and Always-on restrictions from actual policy readback. Keep VPN lockdown OFF for this DNS-only implementation.
