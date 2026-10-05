# Network changes in SafeNest 0.3

This is a source-level reliability and DNS-policy update. It is not a verified full-tunnel firewall or a claim of Gamban-equivalent coverage.

## Changes in the running DNS service

- Android 10 and later now use cancellable `DnsResolver.rawQuery` on the selected physical network. The old blocking `Network.getAllByName` path is removed.
- Requests share a 4.5-second budget beginning when queued, with bounded resolver attempts and a bounded worker queue. Closing a VPN session cancels pending Android DNS operations and closes tracked sockets.
- The fallback uses only DNS servers configured on that physical network, with protected and explicitly network-bound UDP/TCP sockets. The former silent Google/Cloudflare fallback is removed.
- Active encrypted Private DNS or a configured strict provider prevents SafeNest from issuing its own unencrypted fallback. Android still controls its own automatic/opportunistic resolver behavior. No provider hostname is written to SafeNest logs or displayed in diagnostics.
- On Android 9, encrypted Private DNS is unsupported by this transport implementation. SafeNest declines/stops the DNS filter and explains the compatibility issue, leaving ordinary network routes available. It does not silently downgrade the user's encrypted resolver choice.
- Responses preserve their original records, TTLs and DNSSEC flags. The old fabricated 60-second A/AAAA responses and blanket HTTPS/SVCB suppression are no longer used by the service.
- Reachable answer-section CNAME and DNAME chains and SVCB/HTTPS service targets are checked against the current blocklist before reply. Unrelated answer records and authority/additional records cannot create blocking decisions. Invalid reachable chains return a DNS failure.
- The original queried hostname is checked again after resolution, so a rule added during an in-flight lookup takes effect before the response is returned.

## DNS transaction IDs

SafeNest passes the original wire query to Android and validates the returned transaction ID and question. It does not overwrite an unvalidated reply ID to make it pass. AOSP's `ResNSendHandler` randomizes the upstream ID internally and explicitly restores `original_query_id` before returning the answer. The checked Android API35 bytecode also passes the query blob unchanged into `resNetworkSend`.

Sources:

- [Android DnsResolver API](https://developer.android.com/reference/android/net/DnsResolver)
- [AOSP DnsResolver Java source](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/+/refs/heads/androidx-media-release/android-34/android/net/DnsResolver.java)
- [AOSP Android16 DNS proxy source: ID restoration](https://android.googlesource.com/platform/packages/modules/DnsResolver/+/refs/tags/android-16.0.0_r1/DnsProxyListener.cpp)
- [AOSP tests covering Private DNS and cancellation](https://android.googlesource.com/platform/cts/+/973c586e3252223be925e2ab03e079ddf564710a/tests/tests/net/src/android/net/cts/DnsResolverTest.java)
- [LinkProperties transport/privacy requirements](https://developer.android.com/reference/android/net/LinkProperties)
- [DNAME: RFC6672](https://www.rfc-editor.org/rfc/rfc6672.html)
- [SVCB/HTTPS: RFC9460](https://www.rfc-editor.org/rfc/rfc9460.html)

## Verification performed

The new alias inspector passed 10,082 assertions, including 10,000 malformed-response cases. The new upstream transport passed 22 assertions using real loopback UDP/TCP sockets: wrong IDs, unchanged reply bytes/TTL, NXDOMAIN, UDP truncation and TCP fallback, invalid TCP frames, nonresponsive servers, slow trickled replies, cancellation and expired queued work. These tests use no public resolver or Internet connection.

The service, RulesStore and actual Java helpers compiled against real AOSP Android15/API35 classes with Kotlin2.0.21. The only compiler warning in this check was the pre-existing `ConnectivityManager.allNetworks` deprecation. This is not an APK build, Android emulator execution, runtime permission test or phone-network validation.

## Remaining transport boundary

The VPN still routes only its local DNS address. It does not carry arbitrary application TCP/UDP traffic. Therefore:

- Keep Android's **Block connections without VPN** setting OFF in this version.
- Client-to-local-resolver TCP DNS remains unimplemented. Rare answers that exceed the client's UDP limit can fail when the client retries over TCP. Upstream TCP support does not remove this limitation.
- Cached addresses, direct IP connections, arbitrary DoH/DoT/DoQ, other browsers, embedded proxies and a replacement VPN remain outside this DNS path.
- The alias inspector does not inspect HTTPS content, validate DNSSEC signatures, traverse additional-section alias records or classify IP hints/ECH payloads.
- The deadline includes queueing time. Expired queued queries fail without opening new sockets; full queues fail promptly with SERVFAIL rather than growing without limit.

## Full-tunnel next stage

A DNS-only route cannot become a full firewall by adding `0.0.0.0/0`: every captured TCP/UDP flow would then need an actual forwarding implementation. The official Android VPN documentation assigns packet reads/writes and gateway transport to the VPN app. Android permits one active VPN service per user/profile.

An existing packet-forwarding engine can reduce this implementation burden, but integration still needs verified socket protection, IPv4/IPv6, TCP state and retransmissions, UDP/QUIC, MTU and fragmentation, network switching, DNS interception, resource limits, lifecycle recovery and physical-device tests. `hev-socks5-tunnel` is an MIT-licensed dual-stack engine with Android support; its SOCKS egress and application filtering still need integration. Intra's original source is another reference for Android TUN forwarding and DNS transport. Neither engine was silently added or enabled in this update.

- [Android VPN development](https://developer.android.com/develop/connectivity/vpn)
- [Android managed VPN policies](https://developer.android.com/work/dpc/network-telephony)
- [HevSocks5Tunnel original repository](https://github.com/heiher/hev-socks5-tunnel)
- [Intra original tunnel implementation](https://github.com/Jigsaw-Code/Intra/blob/master/Android/app/src/go/intra/tunnel.go)
