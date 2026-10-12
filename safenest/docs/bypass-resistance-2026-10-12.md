# Bypass resistance: Gamban and Great Firewall research applied to SafeNest

Date: 2026-10-12. Scope: a voluntarily installed, consent-based self-exclusion app. This is not a network-wide filter and does not try to become one.

## Evidence quality

- **Gamban:** public sources only (help-center articles, Gamban blog and impact report, GambleAware's 2018 evaluation, GamCare forum threads). Reddit and the store listings could not be fetched by the research agent; the App Store and Play findings in `gamban-recon/recon.md` come from separate fetches earlier the same day. No app was installed, decompiled or network-captured.
- **Great Firewall:** a conceptual summary written from the agent's knowledge of the measurement literature (GFW Report, USENIX, FOCI, OONI, Censored Planet). The listed URLs were **not re-opened live** for this write-up. Treat citations as leads to verify, not as checked references. Existing `research-firewall-dpi.md` (checked sources) takes precedence where they differ.

## How Gamban works, from public sources

Documented: account and licence before setup; Android needs Accessibility, Device Admin, VPN permission and a background/battery exemption; iOS install goes through a configuration profile per the help article, while the App Store FAQ says secure DNS replaced MDM in v3.2.0; macOS uses an installer and `check.gamban.com`; one licence covers all of a user's devices; protection cannot be removed before the licence ends; the blocklist is curated and does not cover ads, and the Play listing says third-party VPNs cannot run while Gamban protects the device.

Inferred, not documented: what each Android permission is used for beyond the publisher's short descriptions, the iOS and desktop filtering mechanism, and blocklist size and update cadence. Failure categories seen publicly: coverage gaps (especially crypto and offshore sites), unprotected other devices, connectivity side effects, install failures on some devices, and permission dependencies. Details are in `research-gamban.md` and `gamban-recon/`.

## Which Great Firewall ideas apply on-device

| Idea | On-device verdict |
| --- | --- |
| Answer blocked names locally with NXDOMAIN inside SafeNest's own VPN | Already in SafeNest |
| Block public DoH/DoT resolver names and DoH canary names while protection is active | **Added now, opt-in** |
| Detect another VPN or Private DNS through Android APIs and tell the user | Already in SafeNest (`onRevoke`, Private DNS status, known-VPN package detection, VPN-app blocking) |
| Match TLS SNI on flows | Needs a full-traffic forwarder; not enabled (see `research-to-implementation.md`) |
| IP blocklists from resolved gambling names | Not added; shared CDN IPs cause collateral blocking |
| Active probing, packet injection, resets, entropy or protocol fingerprinting, network-wide inspection | Not appropriate or not feasible for a consent-based phone app. Not implemented. |

## What was implemented

- `EncryptedDnsBypass.java`: exact-name or subdomain matching for public resolver hostnames (Google, Cloudflare, Quad9, OpenDNS, AdGuard, NextDNS) and two published canary names (Mozilla's `use-application-dns.net`, Apple's `mask.icloud.com` and `mask-h2.icloud.com`). Never matches a whole provider domain such as `google.com`.
- `GuardPreferences`: new preference `block_encrypted_dns_bypass`, off by default, with the same enable and disable rules as the VPN-app switch (needs verified access; cannot be turned off while a commitment or managed protection is active; cleared on expiry).
- `SafeNestVpnService`: when the preference is on, a lookup for a listed name gets the same NXDOMAIN reply as a blocked gambling name, before any upstream query.
- `ProtectionSetupScreen`: a switch with an English and Bengali disclosure under the VPN-app switch.
- Tests: `EncryptedDnsBypassRegression` (included in `run-core-tests.sh`, passing) and a JUnit wrapper.

## Limits to keep visible

- A client that connects to a resolver by IP address, or uses a resolver not on the list, is not covered.
- A user whose Private DNS provider uses one of the listed names may lose that provider while the switch is on; the UI says so. Test this on a phone with strict Private DNS before release.
- The Kotlin changes were not compiled and nothing was run on a device in this session. Run the Gradle build, the Android unit tests and the device acceptance plan before shipping.
- Resolver lists go stale. Move the list into the signed catalog if it needs updating between releases.
- No claim of unremovable or unbypassable protection; Android controls still apply on a personal phone.
