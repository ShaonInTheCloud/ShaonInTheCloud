# Gamban research and SafeNest engineering decisions

Research date: 28 September 2026. This is a targeted review of publicly available first-party documentation, an independent evaluation, and user reports. It is not a claim to have read every internet article or to know Gamban's proprietary implementation. Article dates, product versions, platforms and evidence quality matter.

## What is publicly established

| Finding | Evidence | Meaning for SafeNest |
| --- | --- | --- |
| Android protection combines a local VPN, Accessibility and Device Administrator. Background permission is part of setup. | [Android installation guide](https://kb.gamban.com/en-gb/8-installing-gamban/26-installing-gamban-on-an-android-device), dated 2 April 2026 | A VPN toggle alone is not a comparable product. Verify the enabled state of every required component after returning from Settings. |
| The publisher describes local network reconfiguration, screen-content detection and removal resistance. It says a third-party VPN cannot operate concurrently. | [Google Play publisher listing](https://play.google.com/store/apps/details?id=com.gamban.beanstalkhps.gambanapp), displayed update 5 April 2026 | This supports separate network and device-interface controls. It does not establish HTTPS decryption, arbitrary VPN detection, or a public DPI engine. |
| Samsung setup includes turning Private DNS off. | [Samsung installation guide](https://kb.gamban.com/en-gb/8-installing-gamban/27-installing-gamban-on-a-samsung-device), dated 2 April 2026 | Display Private DNS diagnostics and guide users through settings. Test Samsung independently of Pixel/emulator behaviour. |
| Xiaomi/Huawei have an extra device-optimization step. | [Xiaomi/Huawei guide](https://kb.gamban.com/en-gb/8-installing-gamban/25-installing-gamban-on-a-xiaomi-or-huawei-device), dated 2 April 2026 | Background-service reliability and OEM differences are product requirements. The guide does not disclose the exact optimization implementation. |
| The category database is continuously maintained, yet newly appearing sites can be missed. Adult content is outside Gamban's stated scope. | [What does Gamban block?](https://kb.gamban.com/en-gb/11-/19-gamban), dated 8 April 2026 | SafeNest needs its own maintained gambling and adult databases, with separate category definitions and reviewed updates. A small starter list cannot establish broad coverage. |
| Users can report missing sites/apps and incorrect blocking. | [Reporting guide](https://kb.gamban.com/en-gb/9-during-your-protection/40-hvordan-kan-jeg-rapportere-et-nettsted-og-eller-en-applikasjon-som-er-feilkategorisert), dated 2 April 2026 | Build a correction workflow with review, provenance and update state; a report is not automatically reliable classification. |
| Report review happens repeatedly during the day; changes may not be visible immediately. | [Pending blocking reports](https://kb.gamban.com/en-gb/9-during-your-protection/31-i-have-requested-for-a-website-and-or-app-to-be-blocked-but-it-is-still-accessible) | Keep last-known-good rules and expose when an update was actually installed. Do not invent a service-level promise for SafeNest. |
| Gamban describes removal resistance as friction and recommends multiple forms of self-exclusion. | [Blocking overview](https://gamban.com/blocking) and [layered self-exclusion](https://kb.gamban.com/en-gb/11-frequently-asked-questions/5-what-is-a-layered-self-exclusion) | Measure delays and common bypass resistance without promising an impossible permanent lock. Technical blocking is one component of the user experience. |
| Installation on another person's device requires their consent. | [Installation consent](https://kb.gamban.com/en-gb/11-frequently-asked-questions/11-can-i-install-gamban-on-someone-else-s-device), dated 2 April 2026 | SafeNest's strong mode must have an explicit, understandable enrollment flow and recovery arrangements. |
| A secure-DNS feature in another product can conflict with protection. | [Avast Real Site compatibility](https://kb.gamban.com/en-gb/9-during-your-protection/38-resolution-des-problemes-avec-la-version-payante-d-avast) | DNS bypass and product coexistence deserve explicit compatibility tests. This source is about Avast; it does not prove identical behaviour in every Android browser. |
| Account, device, technical and payment information may be processed. | [Privacy policy](https://gamban.com/privacy), updated 9 July 2026 | Treat recovery-related data as sensitive. Accessibility privacy claims must not be misread as a promise that an entire subscription service processes zero personal information. |

## October 2026 removal-resistance update

Gamban's [current uninstall guidance](https://kb.gamban.com/en-gb/10-uninstallation/46-uninstalling-gamban) says it does not remove installations before the licence ends and describes removal at expiry. That is a support and licence policy statement, not documentation of an unbreakable operating-system control. Its [Android installation guide](https://kb.gamban.com/en-gb/8-installing-gamban/26-installing-gamban-on-an-android-device) visibly asks for Accessibility and VPN. [Community reports](https://www.reddit.com/r/problemgambling/comments/13yraxf/advice_on_gamban/) vary by Samsung, Xiaomi and iPhone; a commenter also discusses a different product, Gamblock. These anecdotes do not establish Gamban's proprietary implementation.

Android's [enterprise network documentation](https://developer.android.com/work/dpc/network-telephony.html) identifies an enforceable route on properly enrolled managed devices: device-owner always-on VPN, VPN configuration restriction and uninstall blocking. Ordinary installation and payment do not grant those privileges. Apple's [supervision overview](https://support.apple.com/en-us/102291) and [managed-app documentation](https://support.apple.com/guide/deployment/distribute-managed-apps-dep575bfed86/web) distinguish supervised management from removable personal profiles. SafeNest has Android code only; there is no iOS app or MDM server.

## What remains unknown

No reviewed public source provides Gamban's complete source code, category data, matching algorithm, remote update format, VPN routing table, certificate-pinning configuration, full DNS transport behaviour, or exact anti-removal implementation. Public references to a local VPN are insufficient to conclude whether every platform/version uses identical DNS interception. The useful engineering pattern is layered enforcement, maintained classifications and verified setup; copying undocumented internals is not possible from these sources.

The Reddit description of being returned to Home does not establish an operating-system crash. It is consistent with a UI intervention, but the precise mechanism remains an inference. An Android API that can perform a Home action does not grant enterprise-level network or uninstall control.

## Independent evaluation: useful, but historically bounded

[Winning Moves / GambleAware, Evaluating online blocking software](https://www.gambleaware.org/media/2yljgspz/blocking-software-evaluation-findings-reportt.pdf), final report 9 October 2018, evaluated six products. The main automated sample comprised 2,417 domains associated with UK Gambling Commission licences. Testing was principally desktop-based; Android and iOS received limited checks rather than the full automated exercise. Published products were anonymized. None achieved total coverage. The approximately 99% adjusted figure often quoted elsewhere refers to a particular tested sample, after excluding sites where gambling was unavailable; it is not a 2026 worldwide Android guarantee.

**SafeNest decision:** define a reproducible harmless fixture set, ordinary-service regression set, and separate category-coverage sample. Record platform, browser, network, rule version and date. Report false positives and false negatives independently. A DNS parser unit test cannot establish whole-device protection.

[Vita CA, Fundamental standards for gambling blocking software](https://web-cdn.gamban.com/Fundamental_Standards_for_Gambling_Blocking_Software.pdf), 29 March 2021, develops user-oriented quality expectations. Gamban commissioned the work; the authors state that editorial control remained with Vita CA. Its consultation and self-selected user samples are useful product evidence, not an independent packet-level audit of current Gamban.

**SafeNest decision:** usability, dependable ordinary browsing, clear support and accurate protection state are release requirements alongside blocking. A malfunction that prevents all browsing is not successful filtering.

## Reading community posts responsibly

- [Advice on Gamban](https://www.reddit.com/r/problemgambling/comments/13yraxf/advice_on_gamban/) contains the thread in the user's screenshot. Different comments concern Android, iPhone, Samsung, Xiaomi and even a different product, Gamblock. These cannot be merged into one verified specification. The useful finding is that users experience materially different removal resistance across devices.
- [Gamban: a review](https://www.reddit.com/r/problemgambling/comments/1mcipsf/gamban_a_review/), July 2025, includes mutually different accounts of offshore coverage and proxy resistance. The initial post partly repeats hearsay. Use these accounts to design tests, not to assign a success percentage or infer source code.
- The [Play listing's user reviews](https://play.google.com/store/apps/details?id=com.gamban.beanstalkhps.gambanapp) include both positive reports and a June 2026 connectivity complaint acknowledged by support. No device logs or reproducible configuration accompany that complaint. SafeNest should test compatibility before asking users to enter stronger protection.

No reported bypass was executed against someone else's device or service in this research.

## Platform constraints relevant to implementation

[Android's VPN guide](https://developer.android.com/develop/connectivity/vpn) documents one active VPN service per user/profile: starting another replaces the existing service. It also distinguishes a selected route from a full-traffic tunnel and explains that Lockdown blocks traffic outside the VPN. Therefore SafeNest's existing DNS-only route cannot truthfully offer both complete network forwarding and Android Lockdown. Turning on a default route without implementing a functioning transport for the intercepted traffic would recreate the reported internet outage.

[Google Play's AccessibilityService guidance](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en) requires declared, understandable functionality and prominent consent where the service is not an eligible accessibility tool. Deterministic user-defined actions and narrow scope are materially different from unrestricted automation. Another application's approval does not automatically approve SafeNest's use. Retain system recovery and review the actual declaration before publishing.

## Prioritized changes suggested by the evidence

These are engineering recommendations based on this review, not claims that all are implemented in the accompanying build.

1. **Restore and prove ordinary connectivity.** Use physical-network DNS transport without recursion, correct response framing, finite timeouts and bounded concurrency. Test Wi-Fi/mobile changes, IPv4/IPv6, large DNS responses and cold start. Confirm actual data-plane results before showing a healthy label.
2. **Separate coverage from service health.** A running foreground service, a successful ordinary lookup and a verified blocked fixture answer different questions. Expose each result with its measurement time; display unknown when not checked.
3. **Deliver authenticated, versioned rules.** Validate schema and limits, preserve the last good list, reject tampering and unintended rollback, record license/provenance, and show update age. A valid signature establishes publisher identity, not classification quality.
4. **Guide encrypted-DNS setup honestly.** Diagnose Private DNS and explain browser Secure DNS. Apply supported device-owner policies only after eligible enrollment; do not represent an ordinary permission prompt as device-owner enrollment.
5. **Keep local browser/app checks narrow.** Verified browser address fields and selected packages add an independent barrier. They cannot promise pre-render blocking, all embedded browsers or network-service termination. Measure those differences on devices.
6. **Test OEM reliability.** Cover Samsung, Pixel, Xiaomi/Huawei where supported, battery saver, reboot and permission revocation. Maintain explicit support boundaries instead of silent assumptions.
7. **Add reviewed correction/report operations.** Provide local immediate additions plus a separate reviewed publication path. Do not auto-download arbitrary user reports into every customer's rules.
8. **Gate stronger networking on a proven transport.** Future full-tunnel filtering needs an established IP/TCP/UDP forwarding implementation, IPv6/QUIC handling and device tests. A hostname parser alone is not a complete firewall. Unknown encrypted proxy endpoints remain a classification problem even with forwarding.

SafeNest's current README is the authority for implemented capabilities. This research does not establish Gamban equivalence, clinical effectiveness, complete encrypted-traffic inspection or prevention of every bypass.
