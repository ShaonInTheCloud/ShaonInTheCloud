# SafeNest 0.3.10 verification and research coverage

Date: 2026-10-03. Android versionCode 13. Source update; no APK produced here.

## Catalogue

35,155 distinct bundled gambling hostnames across four assets. The new brand-family snapshot contains 28,526 distinct hostnames extracted from the 342,623-hostname Blocklist Project gambling snapshot. These are third-party classified domains with textual matches to the Bangladesh-focused seed, not 28,526 independently visited Bangladesh sites. Ownership and current Bangladesh accessibility remain unverified. A brand tag can include a fan site, affiliate, imitation or scam; it does not prove operator ownership. New mirrors and APK variants still need ongoing updates.

Data files:
- `data/safenest-gambling-domains.txt`: full deduplicated bundled list.
- `data/safenest-gambling-import.json`: app-compatible category/domain import.
- `data/gambling-brand-families.json`: per-domain provenance, brand-text tags, upstream hashes and counts.
- `data/bangladesh-gambling-research.json`: ten separately researched website/download hosts, including newspaper investigation destinations and observed Krikya/Baji hosts.
- `data/known-gambling-packages.json`: ten exact Android package IDs with evidence.

Examples of new feed text tags (overlap possible): 1xBet 19,052; Melbet 1,701; 1win 900; Mostbet 588; 22Bet 379; Baji 77; Linebet 48; Jaya9 17; JeetBuzz 14; Babu88 12; Krikya 12. These are feed tags, not measured Bangladesh market shares or independent brand endorsements.

The source feed is https://github.com/blocklistproject/Lists/blob/main/gambling.txt, Git blob `701eb4384ae3d65cfc25d9334f7eb0ee34fb4ef9`, SHA256 `d583202e86eb80f8cb6311e65d10f36e472d921d7cac5ff18e98d7dd0b9cf439`. The retrieved file's header is dated 2026-07-20; retrieval does not certify every entry is current. License: Unlicense, reproduced in `docs/blocklistproject-UNLICENSE.txt`. The extractor rejects an unexpected upstream checksum so future snapshots require review.

## App evidence and behavior

Krikya's public site redirected to www.krikya11.live. Its public JavaScript links app.krikya.tech. The downloaded APK manifest identifies `com.krikya.krikya`, version 1.1. Baji's help centre links an APK whose manifest identifies `com.application.yongbao.bj`, version 1.35. Both were inspected statically; neither was executed, installed or redistributed. SHA256 hashes and source links are recorded in the package evidence file. Publisher certificate identity is not independently verified.

Other exact package IDs come from public analysis/listing records: 1xBet, 22Bet, Linebet, Melbet, Mostbet, Megapari, a Jaya9 listing and a Jeetbuzz listing. Third-party listing evidence is weaker and can become stale. Several namesake puzzle/game apps were excluded. Package-name equality does not authenticate the publisher or detect repackaged APKs.

Known package rules activate with the user's Accessibility app-guard consent. They request Home only for the matching foreground package. Chrome/Firefox and essential system apps remain exempt. App guard cannot prevent every background connection; domain filtering is separate. The UI now discloses the automatic list and does not offer a misleading Remove control for automatic entries.

## Validation

- Node test runner: all four test files pass, including database/API tests and catalogue provenance/export/package-visibility consistency.
- Pure Java matcher checked every one of the 35,155 listed hostnames and a subdomain of each. Normal sites including Facebook, YouTube, bKash, Nagad and SafeNest passed negative checks. Hostname-boundary checks passed.
- Full Java core regression suite passed: DNS codec 20,075 assertions, domain rules 57, DNS alias parsing 10,082, loopback UDP/TCP forwarding 22, signed catalogue verification 59. Loopback tests required network-enabled execution; no gambling sites were contacted by the tests.
- Bundled loader preserves hostname labels instead of stripping a www-only source rule at runtime.
- No Android SDK build, emulator run, Honor test, APK signing or installation was performed. Kotlin/manifest integration still requires Android Studio compilation and device tests.

## Remaining limitations

The new list does not fix VPN takeover, encrypted-DNS/proxy bypass or Android uninstall controls. Those earlier limitations remain documented in the README. Managed Chrome has a 1,000-rule policy budget; the full catalogue applies to the DNS filter when it owns the connection. Website/application blocking must be verified on the target device. No subscription or live website deployment was changed.

Before release: build the app, verify ordinary internet with protection on, test blocked root/subdomain entries, test Krikya/Baji foreground blocking with Accessibility enabled, verify Chrome stays open, test a newly added domain, test other-VPN takeover diagnostics, and review false positives.
