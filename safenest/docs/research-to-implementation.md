# Research applied to SafeNest 0.3

## The central finding

Gamban and China's Great Firewall operate at different places. Gamban's Android documentation describes a combination of local VPN, Accessibility, administration permissions and maintained site classification. Firewall research describes equipment on network paths combining several filtering techniques. Neither source establishes an infallible detector of every gambling page inside arbitrary encrypted traffic.

**DPI means Deep Packet Inspection:** parsing protocol information inside packets. Visible DNS names, unencrypted HTTP fields or exposed TLS handshake names can support a decision. DPI cannot automatically read HTTPS page content or the inside of a VPN. ECH further hides the inner TLS server name. See the sourced explanations in research-firewall-dpi.md and research-gamban.md.

## Implemented after the review

| Research lesson | Actual 0.3 change | Practical boundary |
| --- | --- | --- |
| Reliability and precise rules matter as much as denial | Cancellable raw DNS, bounded queue-inclusive timeout, preserved DNS records and checked reachable aliases | Local UDP DNS route, not arbitrary application traffic |
| Encrypted DNS is a separate transport decision | Private DNS diagnostics and no silent plaintext/provider fallback | Browser DoH and other clients remain separate; Android 9 encrypted DNS is unsupported by this filter |
| Endpoint visibility complements network visibility | Managed Chrome URL navigation blocklist synchronized with effective rules | Requires device-owner enrollment; policy capacity and browser exceptions are visible |
| A changing database is essential | Signed publisher catalogs, increasing revision, expiry, atomic install and retained good rules | No live classification provider, automatic polling or universal category list is supplied |
| Blocking mistakes need recoverability | Preserved personal lists, publisher correction via newer revision, policy journals and explicit stale/error status | An authenticated list can still contain incorrect classifications |
| Measurement must be reproducible | Protocol, live loopback transport, signature and Chrome-policy regressions; device acceptance plan | Core tests are not a substitute for phone/OEM tests |

## How to use the new features

1. Open SafeNest/android in Android Studio, build and Run the updated app.
2. In Protect, use Permissions and app guard setup. Check DNS/Private DNS status and verify ordinary browsing and a harmless blocked fixture.
3. Personal mode can use the existing optional app/browser guard. Android permission controls still apply.
4. On an intentionally enrolled device-owner test device, review managed controls. Check Chrome policy submission counts and verify the actual Chrome policies at chrome://policy. The 1,000-entry limit is shared with existing rules; allowlist exceptions remain.
5. In Protect → Verified catalog and updates, configure a trusted publisher's public key and optional HTTPS URL. Use the included publisher tool to make signed catalogs under your control. Check for updates explicitly. Do not put the private signing key in the app or website.

## What the research did not turn into a finished feature

There is no new general-purpose DPI toggle, TLS interception certificate, government network access, proxy-scanning service or production VPN gateway in this build. A functioning full-traffic forwarding engine is a prerequisite for reliable SNI/HTTP/QUIC inspection across application flows. Adding a default VPN route without that engine would reproduce the all-internet outage.

An Android full-tunnel implementation remains a separate milestone: integrate and review a maintained engine, cover IPv4/IPv6, TCP state, UDP, MTU, network transitions and socket lifecycle, then test on phones before enabling Lockdown. Even then, unknown encrypted proxies and mixed-content sites require classification choices and acknowledged limits.

The current source still needs a complete APK build and device tests. The evidence and exact limitations are in verification-0.3.md. This research is broad and source-led, not a claim to have read every article online or recovered proprietary Gamban code.
