# DNS transport in 0.4.7

The previous default path used Android's network resolver or raw UDP/TCP fallback. Version 0.4.7 checks rules before forwarding, then sends allowed DNS wire messages as HTTPS POST requests to Cloudflare's resolver. Literal IPv4/IPv6 endpoints avoid circular or plain DNS bootstrap lookups. Android opens the connection on the selected non-VPN network and retains its default certificate/hostname verification. No account token, cookie or SafeNest password is added to the resolver request.

Strict user-selected Private DNS is respected on Android 10+: a validated strict provider is used through Android's resolver; validation/resolution failure returns a DNS failure instead of switching providers or sending plain DNS. Android 9 strict Private DNS remains unsupported and reports that condition before starting. Automatic/opportunistic DNS is replaced by certificate-verified HTTPS rather than treated as guaranteed encryption.

HTTPS redirects, non-wire content types, encoded/oversized/truncated/mismatched replies and incomplete bodies fail. A shared monotonic deadline bounds queued requests and attempts; connection cancellation is tracked by the VPN session. Local domain/alias checks still run again when replies arrive. The old raw transport remains only as a standalone regression utility; the service no longer calls it.

The English/Bangla activation disclosure names Cloudflare, explains that it receives allowed domain names and the network IP, and distinguishes encrypted DNS from encryption of ordinary internet traffic. Setup and test feedback identify the selected DNS transport. Cloudflare is not a SafeNest partner or endorsement.

This remains a DNS-only local VPN interface. It does not tunnel web traffic or prevent independent browser DoH, direct-IP connections, proxies, cached connections, all VPN replacements or Android revocation. Android Lockdown must stay off. Encrypted DNS alone is not evidence of Google Play VpnService eligibility/approval; the final design and declarations still need review.

Verification: 50 new SDK-free transport checks cover wire requests, cleanup/cancellation, invalid endpoints, redirects/statuses, MIME, size, DNS question/ID, truncation and trickled responses. Existing DNS/domain/alias/guard regressions pass. The connected lab test now also checks an actual harmless blocked query reaching the filter, allowed resolution over HTTPS, and an ordinary HTTPS page while protection is active. The connected result must be checked for the exact published source; physical Honor and Wi-Fi/mobile handover tests remain required.

Primary references: [Cloudflare DoH API](https://developers.cloudflare.com/1.1.1.1/encryption/dns-over-https/make-api-requests/), [Android Network connections](https://developer.android.com/reference/android/net/Network), [Google Play VpnService policy](https://support.google.com/googleplay/android-developer/answer/12564964).
