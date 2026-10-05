# Great Firewall, DPI and practical lessons for SafeNest

Research date: 28 September 2026. Purpose: improve a voluntarily installed gambling/adult-content blocker while keeping ordinary Internet access working.

## Scope and evidence

October 2026 update: the [Shadowsocks measurement study](https://gfw.report/publications/imc20/en/) observed passive traffic classification followed by active probes of suspected servers. A [later encrypted-traffic study](https://gfw.report/publications/usenixsecurity23/en/) measured additional real-time blocking independent of those probes; [QUIC measurements](https://gfw.report/publications/usenixsecurity25/en/) show selective handshake filtering and its failure modes. Reddit accounts are useful for finding user-visible failures, but they do not reveal the exact filtering rules. China's system sits on network paths, with infrastructure and visibility a locally installed Android app does not own. Copying packet signatures into a DNS-only tunnel would not inspect the packets it does not route.

**Implementation decision:** retain exact domain/subdomain DNS rules, signed catalog updates, reviewed VPN-package detection and device-owner policy where actually enrolled. Do not enable full Android Lockdown or broad protocol/IP blocking until SafeNest has a tested full-traffic forwarder and collateral-damage tests. These research papers do not establish a universal VPN fingerprint or a way to inspect encrypted website paths.

This is a focused review of original measurement research, protocol specifications and Android documentation. It does not claim to cover every article on the Internet. The studies below describe observations during particular experiments; they do not reveal every current rule or prove that China blocks every prohibited website. Search-engine publication labels can reflect page updates: the publication year and the measurement period in the paper are the meaningful dates.

Recommendations in this document are engineering conclusions for SafeNest, not claims that the cited researchers endorse this product. This research document does not certify a completed DPI engine, an APK build or a device test.

## What DPI means

**Deep packet inspection (DPI)** means examining protocol data inside network packets, beyond their source/destination IP address and port. A filter can parse a DNS question, an unencrypted HTTP Host field, or a visible TLS ClientHello server name. Stateful inspection also tracks a connection and may need to reconstruct a message split across packets. The GFW literature documents these distinct inspection methods; it is not one all-knowing detector. [R3, R6, R7]

**DPI is not automatic decryption.** TLS encrypts application data. A passive observer normally cannot read an HTTPS page, password, message or complete URL merely by adding a DPI library. Packet sizes and timing remain visible, and fingerprints may support uncertain classification. [R9]

**SNI** is the server-name field used while establishing TLS. Conventional visible SNI often reveals the hostname, not the page path. **ECH**, standardized in RFC 9849 in March 2026, encrypts the inner ClientHello and its server name. The observer may still see an outer provider name and an IP shared by many websites. Encrypted DNS hides another source of hostname information. Treat “unknown hostname” as unknown, not as evidence of gambling. [R11]

**QUIC** is commonly used for HTTP/3 over UDP. Its Initial packet protection uses information an observer can derive; this can expose a conventional ClientHello. That does not make the later application traffic readable. Properly protected handshake/application packets have different secrecy properties. [R10]

## How the layers differ

| Layer | What a network filter can use | Important limitation | Lesson for SafeNest |
|---|---|---|---|
| DNS | Queried hostname and locally maintained domain rules | Encrypted/custom DNS and cached results can avoid a DNS-only filter | Keep DNS fast and correct; add complementary controls |
| IP/port filtering | Destination IP range and transport port | Shared hosting, changing addresses and common ports cause collateral blocking | Avoid importing broad cloud/CDN ranges as gambling rules |
| Plain HTTP | Host header and other unencrypted fields | Most modern browsing uses HTTPS | Optional precise parsing, never assume it covers HTTPS |
| TLS handshake | Visible SNI and protocol characteristics | ECH, fragmentation and proxies can hide or change the signal | Use hostname evidence only when available and validated |
| QUIC handshake | Parsable Initial packets and visible inner handshake fields | Reassembly, new versions, ECH and migration complicate coverage | Requires a tested transport implementation and resource limits |
| Traffic classification | Sizes, timing and protocol fingerprints | A probabilistic signal does not prove a website category | Do not block arbitrary encrypted traffic by default |
| Endpoint control | Consented app restrictions or supported browser address bar | Depends on OS privileges and browser integration | A phone can use app identity that an ISP cannot see |

The first six rows synthesize [R1–R11]; the last is an engineering comparison with Android's VPN model [R12]. It is a comparison of capabilities, not an assertion that all these layers are implemented in SafeNest.

## What was actually measured

### DNS injection is a separate mechanism

The 2020 Triplet Censors study distinguished multiple DNS response injectors from packet characteristics. It illustrates that the GFW can observe a plaintext DNS query in transit and race a forged answer; controlling the user's chosen DNS server is not the only way to interfere with DNS. [R1]

SafeNest should return a clear local denial for an explicitly blocked name, rather than imitate poisoned replies that redirect innocent traffic to somebody else's public IP. This is a product design conclusion.

GFWatch's 2021 study found 311,000 censored domains in its nine-month dataset and approximately 41,000 innocuous domains matching overbroad filtering rules. These are historical study results, not current coverage numbers. The important product lesson is precise matching and false-positive review. A keyword such as `bet` must not indiscriminately block every hostname containing those letters. [R2]

### HTTP and TLS filtering do not share one universal list

GFWeb studied HTTP/HTTPS censorship over 20 months, reporting different blocked-domain sets and direction-dependent behavior. It also observed changes to parsing and fragmentation handling over time. A result from one protocol, location or direction cannot certify every other path. [R3]

For SafeNest, each supported path needs its own test: ordinary DNS, supported browser observation, selected app restriction, and any future full-traffic filtering engine. The protection screen should identify which layers are working.

### VPN detection combines evidence and still makes mistakes

The IMC 2020 Shadowsocks study measured a combination of passive suspicion and active connections to candidate servers. Its authors distinguish measured behavior from inferred purpose and architecture. **Active probing** means making a separate connection to investigate a suspected endpoint; it is not decrypting the user's encrypted session. [R4]

The USENIX 2023 study inferred simple traffic-exemption heuristics for one fully encrypted-traffic detector. Applying the inferred rules broadly to its university-network sample would have affected about 0.6% of normal connections. That is a sample-based estimate, not a universal false-positive rate or a measure of VPN detection accuracy. [R5]

SafeNest should prefer explicitly selected app packages, OS-supported VPN restrictions in an enrolled managed device, and validated domain rules. “Looks random” is an unsuitable default reason to break an unknown connection. Scanning users' remote servers is unnecessary for the present app.

### QUIC adds both visibility and substantial limits

The USENIX 2025 paper measured SNI-based QUIC blocking first observed in April 2024. It found a QUIC-specific blocklist, processing delays and limitations in reassembly and flow tracking. The study's January 2025 ECH observation is historical, not a guarantee about today's network. [R6]

PETS 2026 QUICstep evaluated how connection migration changes a handshake-based censor's view and demonstrated a practical limitation of a deployed censor. The lesson here is simply that inspecting the first visible packet is not proof that the rest of a connection remains classifiable. [R8]

SafeNest must not advertise “QUIC blocked” as equivalent to “gambling blocked.” Blanket UDP/443 denial can affect legitimate services and may only make browsers try another transport. Any future QUIC support needs bounded reassembly, IPv6 coverage and explicit tests of healthy traffic.

### There is no single uniform national appliance

The S&P 2025 regional-censorship study measured a separate Henan system with policy and implementation differences from the national GFW. It reinforces the distinction between network vantage points and the danger of treating one location's result as universal. [R7]

Independent GFW Report measurements also documented a roughly 74-minute period of indiscriminate TCP/443 reset injection on 20 August 2025. The report establishes an observed disruption, not the operator's intention. Even extensive filtering infrastructure can cause broad outages. [R13]

## Why an Android app has a different position

| Deployment | Where it sees traffic | What it can enforce | What it cannot promise |
|---|---|---|---|
| ISP/border equipment | Traffic crossing an operator-controlled network path | Filtering for that path, independent of one installed app | Perfect classification of encrypted contents or every alternative path |
| Ordinary Android DNS VPN | Only routes configured into its TUN interface | DNS decisions for traffic that reaches it | Visibility into HTTPS or a second replacement VPN |
| Android full-traffic VPN | Routed IPv4/IPv6 packets before its chosen forwarding path | IP/transport policy and visible protocol parsing, with a working forwarder | Arbitrary HTTPS plaintext or universally identifiable proxies |
| Enrolled managed Android device | Endpoint policy plus the app's network path | Stronger OS-backed configuration restrictions where Android supports them | Every bypass on rooted devices, every OEM behavior, or knowledge of unseen content |

Android supports one active VPN service per user/profile. Starting a new one stops the existing service. Therefore “SafeNest's local VPN keeps inspecting another independent VPN” is not a valid consumer-mode design. `VpnService.protect()` is required for outbound tunnel sockets to avoid routing them back into the same tunnel. Routes determine which traffic reaches the TUN interface; receiving packets also requires a correct forwarding implementation. [R12]

The full-traffic and managed-device rows are architecture choices. Availability of an API is not evidence that SafeNest has successfully exercised it on a phone.

## Recommended implementation order

1. **Ordinary access must work first.** Fix DNS response validation, timeouts, network transitions, socket protection and shutdown. Test both a deliberately blocked harmless domain and an allowed domain. A completely broken connection is a failed test.
2. **Keep domain rules exact and reviewable.** Normalize IDNs and hostnames, match label boundaries, record list provenance, support corrections and reject malformed imports. A maintained category feed is necessary to discover new gambling/adult domains; DPI does not create that feed.
3. **Give each layer an honest health state.** Distinguish VPN process running, DNS request succeeded, selected-app guard enabled, permission missing, and managed policy applied. Never infer all-site protection from a VPN key icon.
4. **Use consented endpoint controls.** On ordinary phones, expose the scope of selected-app and supported-browser blocking. Offer a separate managed-device enrollment process for users who intentionally choose stronger OS policy. Preserve a documented recovery route.
5. **Treat a full-traffic forwarder as a separate engineering milestone.** Audit a mature TCP/UDP transport implementation, its license and maintenance status. Cover IPv4, IPv6, fragmented traffic, DNS over TCP, cancellation, network changes and bounded memory. A default route added to a DNS-only reader will black-hole healthy traffic.
6. **Add visible-metadata inspection only after forwarding passes tests.** SNI/HTTP parsing must handle segmentation safely and return “not enough information” for ECH or unsupported formats. Do not silently convert uncertainty into total denial.
7. **Make gateway deployment an explicit product choice.** A managed gateway would need authenticated clients, capacity planning, privacy controls, operation and separate testing. Its existence must not be implied by the present local-VPN code.

These are proposed priorities derived from the evidence. See the release verification and device-test documents for what this particular bundle actually implements and verifies.

## Acceptance checks suggested by the research

- Allowed browsing, messaging, updates and calls remain usable with protection enabled; measure DNS latency and battery cost.
- Listed domains and their genuine subdomains are denied, while lookalike suffixes and unrelated words are allowed.
- Test Wi-Fi/mobile transitions, airplane mode, captive portal, IPv6-only/NAT64 networks and resolver outage.
- Exercise Chrome Secure DNS, Android Private DNS, an alternate browser and a replacement VPN separately; report the actual result for each mode.
- Exercise large/TCP DNS replies, split protocol handshakes and unsupported formats without crashes or unbounded queues.
- Distinguish transport blocking from content-list accuracy: a working network engine cannot reject a new unlisted site based solely on a hidden HTTPS payload.
- Record permission revocation, reboot and policy-release behavior. Stronger restrictions should never conceal loss of filtering or strand the user without a disclosed recovery path.

## Primary reading list

All links were checked during this review. Years below identify the work, not a crawler's date. No proprietary Gamban code or leaked government implementation was copied.

- **[R1]** Anonymous et al., *Triplet Censors: Demystifying Great Firewall's DNS Censorship Behavior*, FOCI 2020. Original measurement paper and dataset: <https://gfw.report/publications/foci20_dns/en/>.
- **[R2]** Hoang et al., *How Great is the Great Firewall? Measuring China's DNS Censorship*, USENIX Security 2021. Original GFWatch study: <https://www.usenix.org/conference/usenixsecurity21/presentation/hoang>.
- **[R3]** Hoang et al., *GFWeb: Measuring the Great Firewall's Web Censorship at Scale*, USENIX Security 2024. Original HTTP/TLS longitudinal study: <https://www.usenix.org/conference/usenixsecurity24/presentation/hoang>.
- **[R4]** Alice, Bob, Carol, Beznazwy and Houmansadr, *How China Detects and Blocks Shadowsocks*, ACM IMC 2020. Original experiments and artifacts: <https://gfw.report/publications/imc20/en/>.
- **[R5]** Wu et al., *How the Great Firewall of China Detects and Blocks Fully Encrypted Traffic*, USENIX Security 2023. Inferred detector and measured/simulated limits: <https://gfw.report/publications/usenixsecurity23/en/>. Historical deployment caveat: the authors' artifact repository says dynamic blocking stopped in March 2023; do not represent these exact heuristics as a verified September 2026 deployment: <https://github.com/gfw-report/usenixsecurity23-artifact>.
- **[R6]** Zohaib et al., *Exposing and Circumventing SNI-based QUIC Censorship of the Great Firewall of China*, USENIX Security 2025. Original QUIC measurements: <https://gfw.report/publications/usenixsecurity25/en/>.
- **[R7]** *A Wall Behind A Wall: Emerging Regional Censorship in China*, IEEE S&P 2025. Original regional comparison: <https://gfw.report/publications/sp25/en/>.
- **[R8]** Lee et al., *QUICstep: Evaluating connection migration based QUIC censorship circumvention*, PETS 2026. Original study; project page updated September 2026: <https://gfw.report/publications/pets26a/en/>.
- **[R9]** IETF, RFC 8446, *The Transport Layer Security (TLS) Protocol Version 1.3*, especially record protection and Appendix E.3 traffic analysis: <https://www.rfc-editor.org/rfc/rfc8446>.
- **[R10]** IETF, RFC 9001, *Using TLS to Secure QUIC*, especially sections 5 and 7: <https://www.rfc-editor.org/rfc/rfc9001>.
- **[R11]** IETF, RFC 9849, *TLS Encrypted Client Hello*, March 2026, especially introduction and security considerations: <https://www.rfc-editor.org/rfc/rfc9849.html>.
- **[R12]** Android Developers, *VPN*, service lifecycle, route configuration and protected sockets: <https://developer.android.com/develop/connectivity/vpn>.
- **[R13]** Wu / GFW Report, *Analysis of the GFW's Unconditional Port 443 Block on August 20, 2025*. Primary incident measurements, not a peer-reviewed paper: <https://gfw.report/blog/gfw_unconditional_rst_20250820/en/>.
