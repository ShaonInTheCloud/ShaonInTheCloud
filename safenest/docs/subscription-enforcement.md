# Paid protection in SafeNest 0.4.0

The Android client signs in with an existing, confirmed SafeNest email/password account, then calls the authenticated Supabase `protection-access` function. The function validates the account and queries only that account's server-owned entitlement with its JWT and row-level security. It ignores client payment flags, plan claims and user metadata. Anonymous accounts cannot activate. No password, access token or private server key is persisted in the APK.

## Billing is not connected yet

The weekly, monthly and annual entitlement schema and access endpoint exist, but no checkout or verified payment webhook creates paid entitlements. The live table currently has no active paid period. An unpaid account cannot start this build's guard or DNS service. Do not advertise this build as a working purchase-to-activation product. A trusted payment backend must validate a real receipt, write an idempotent entitlement and handle revocations/renewals using a server-side credential. Customers have SELECT access to their own records and cannot INSERT, UPDATE or DELETE them.

## Activation and finite consent

1. Sign in and verify the server-confirmed paid period.
2. Within five minutes of verification, prepare the app guard and enable Accessibility through Android. If setup takes longer, verify again. Permission preparation alone does not start protection.
3. Review the separate activation disclosure showing the expiry date and the precise UI observations. Approve Android's VPN permission.
4. The app commits to the verified period, enables detected VPN app blocking and starts the DNS service. App and service both enforce the paid gate; managed controls also require an active commitment.

During the consented period, there is no customer-facing pause/stop, removal of chosen app/domain rules, or publisher trust-key change. Adding rules and accepting authentic publisher updates remain available. Renewals do not silently extend an already consented period; a new activation consent is required after it ends.

The cached verified period runs offline until its finite expiry. The same-boot elapsed clock and a persisted high-water time prevent a clock rollback from extending it. A large backward clock jump across reboot ends enforcement and requires online verification rather than creating an indefinite lock. Forward and backward wall-clock changes are ignored within a boot. Across a reboot, a forward clock change can still end the local period early. The first checkpoint re-anchors elapsed time for the new boot. This is a private-data trust model, not a hardware-backed license, root-proof lock or continuously polled account session.

A persisted expiry job and service/application checkpoints end the app guard, stop the DNS service, request deactivation of ordinary Device Administrator and restore journaled managed policies. Device Owner enrollment itself is not removed. Partial managed restoration retries and remains available to an authorized administrator. Expiry is best effort while Android schedules jobs or the phone is powered off; enforcement checks reject expired periods immediately when executing.

## Technical support

The Account page can authenticate the same account and recheck the exact committed entitlement. An active reply leaves protection unchanged. Only a successful server reply confirming that exact period has ended or been revoked authorizes local cleanup. Incorrect credentials, another account, malformed replies and network failures do not release protection. Support revocation must be performed by the trusted backend; customers have no write permission for entitlements. There is no automatic background revocation polling in this build.

## UI guard coverage

Accessibility receives foreground events. It returns Home for selected user apps, recognized installed gambling packages and detected VPN apps. It checks only supported Chrome/Firefox address-bar fields for listed domains and returns Home for those addresses, as requested in the latest brief. DNS denial itself returns NXDOMAIN and cannot send a browser to Home.

On recognized Settings and installer packages, the guard matches SafeNest labels plus disconnect/uninstall/disable actions or the enabled SafeNest Accessibility toggle. It also matches a small list of known VPN titles with Install/Update actions in supported installer/Play Store screens. It does not block all Settings, all installations, or ordinary browser content. Only English and selected Bangla/Finnish control labels are covered. Manufacturer layouts, WebViews, changed view IDs, inaccessible windows and unknown VPN titles can escape detection. No page body, messages, password fields, screenshot, history log or browsing upload is used.

A Home action is a best-effort UI barrier, not an operating-system uninstall ban. Android permission revocation, Safe Mode, reset, root, another profile and fast UI races remain possible. Device Owner policies provide stronger restrictions only after supported eligible provisioning; payment or an ordinary Device Administrator prompt cannot grant Device Owner.

## Networking

The existing DNS-only split tunnel remains. Normal web traffic stays on the physical network. Leave Android's **Block connections without VPN** OFF; full Lockdown would break normal internet because this app has no general packet forwarder. Another Android VPN can replace an ordinary personal-phone filter, and an in-browser proxy can hide its destination. Accessibility address matching can still catch supported visible blocked addresses, but does not inspect encrypted tunnels or guarantee every bypass is blocked. This version does not implement general full-traffic DPI or decrypt TLS.

The local VpnService remains visible in Android VPN settings. An iOS configuration profile cannot be installed on Android. The test APK is not a Play Store-approved release or a tested universal nonremovable client.
