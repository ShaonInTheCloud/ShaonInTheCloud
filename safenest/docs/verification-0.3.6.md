# SafeNest 0.3.6 verification and release status

## Completed locally

- The DNS filter records its most recent blocked hostname in volatile process memory. The setup screen presents it for diagnosis. An added rule is checked against the local matcher immediately; this does not invalidate Chrome caches or existing TCP connections.
- On a personal Android phone, a second VPN can replace SafeNest. Device Owner enrollment is required for its VPN-configuration restriction and uninstall policy; this version is still DNS-only and cannot safely turn on full-traffic Lockdown.

- VPN app-block switch now requires the app-guard disclosure if guard was disabled; its status distinguishes selection from an active Accessibility connection. This is a source change, not phone-tested enforcement.

- The 15 reviewed VPN package IDs appear in Android package visibility and in the detector. VPN-service discovery also remains enabled.
- The Android manifest parses as XML.
- Intelligence Node tests pass. The 48 gambling research domains remain in the local research seed; the database has nine confirmed active gambling domains and 39 review-required candidates.
- Supabase has the normalized intelligence schema and an Edge Function. There are zero signed catalog releases, so a live automatic Android update is not available.

## Not verified

- No APK was built here: Android SDK is absent, and offline Gradle cannot resolve the Android Gradle plugin. Android Studio on a computer with SDK 36 must run `:app:assembleDebug`, then install `android/app/build/outputs/apk/debug/app-debug.apk` on a real phone for testing.
- Normal browsing, Chrome listed-domain blocking, app guard, known VPN launches, always-on behavior, uninstall controls, and the refreshed visual layout need physical-device testing. Verify a permitted site and a temporary test domain, then retry with Private DNS, Chrome Secure DNS and another VPN before describing coverage publicly.
- No payment entitlement, automatic paid-term lock, release APK signing, Play distribution, or website APK download is configured.

## Android enforcement boundary

An ordinary app cannot remove Android's system-owned VPN Disconnect action or make itself impossible to uninstall. Device Owner enrollment on an eligible fully managed device can apply always-on VPN, restrict VPN configuration, suspend discoverable VPN apps and block ordinary uninstall. This DNS-only VPN must leave Android's full traffic Lockdown off because enabling it interrupts ordinary internet. System reset, root, independent encrypted DNS, proxies, unknown domains, and other profiles remain boundaries. No general HTTPS DPI is implemented.

The subscription term is not a device-management privilege. Customer enrollment, billing webhooks, entitlement verification, recovery policy and signed releases must be built and validated before a paid launch.
