# How I built a DNS-filtering VPN on Android

*Md Humaun Kabir · November 2026*

Online gambling has become a quiet crisis in Bangladesh. Betting sites advertise on social media, pay out through mobile wallets, and when one domain gets blocked, the same site reappears under a new name the next week: `babu88a.com`, `jeetbuzz-bd.live`, `mbaji999.net`.

People trying to quit tell the same story. The decision to stop is made on a good day. The relapse happens on a bad day, at 2 a.m., one tap away.

So I built **SafeNest**, an Android app that lets someone lock themselves out of gambling for a period they choose. It blocks gambling websites and apps on their own phone, it keeps working when they are tempted to switch it off, and it never sends their browsing anywhere. This is how the core of it works.

## The trick: a VPN that only carries DNS

Android gives apps one supported way to sit between the phone and the network without root: `VpnService`. The obvious design is to route all traffic through it and inspect everything. I decided against that early. A full tunnel means reimplementing a TCP/IP stack in user space, it slows down every video and download, and it puts the user's entire internet life inside my app. That is the opposite of private.

Instead, SafeNest claims one private address, `10.91.0.53`, tells Android to use it as the DNS server, and routes **only that single /32 address** into the tunnel. There is no default route. YouTube, banking and messaging go out on the normal network at full speed and the app never sees them.

Every website visit starts with a DNS lookup, though, so every lookup now arrives at SafeNest as a raw IPv4 packet. A small loop reads them:

1. Parse the packet strictly: IPv4, UDP, addressed to the tunnel resolver, exactly one question, valid checksums. Anything else is dropped, never guessed at.
2. If the name, or any parent domain, is on the blocklist, write back `NXDOMAIN` immediately. The site simply does not exist on this phone.
3. Otherwise, forward the query to the real resolver over the physical network, check that the reply matches the question and transaction ID, and pass it back.

One detail mattered more than I expected: **blocked lookups never wait behind allowed ones.** Blocked names are answered on the reader thread itself; allowed names go to a bounded worker pool with a 4.5-second budget for the whole request. A slow resolver can delay YouTube, but it can never delay a block.

## Fitting 541,485 domains into 4 MB

The blocklist combines several sources and has **541,485 gambling domains**. The early versions kept rules in a `HashSet<String>`, which is fine for a few thousand names and falls over at this scale on a cheap phone: Java strings carry so much overhead that half a million of them need roughly 100 MB of memory.

The fix was to stop storing names at all. At build time, each normalised hostname becomes a **64-bit fingerprint** (FNV-1a followed by a SplitMix64 finaliser to spread the bits), and the fingerprints are written as one sorted array of `long`s. On the phone, checking `a.b.casino.com` means fingerprinting `a.b.casino.com`, `b.casino.com`, `casino.com` and `com`, and binary-searching for each. That is four lookups of about 19 comparisons each, whatever the list size.

The whole list is now about 4 MB. The file format has a magic number, a version and a SHA-256 of the payload, so a truncated or corrupted asset fails loudly at startup instead of silently blocking nothing. With 64-bit fingerprints, the chance that a normal site collides with a listed one is about one in 30 trillion for each name checked.

Updates come as **signed catalogs**. The app pins an ECDSA P-256 public key, verifies the signature before it parses a single rule, requires the revision number to increase, and keeps the last verified list if an update is broken. A man in the middle cannot push an empty list to switch protection off.

## Closing the bypasses

A DNS filter is easy to build and easy to get around. Most of the work in SafeNest went into the ways around it.

- **New mirror domains.** Lists are always a week behind. SafeNest also treats a known brand plus a short tail or lead as that brand, so `babu88a.com` or `bd-jeetbuzz.live` is blocked on day one. An exclusion list keeps ordinary words that happen to contain a brand name from being caught.
- **Encrypted DNS in the browser.** Chrome and Firefox can send lookups straight to Cloudflare or Google over HTTPS, skipping system DNS. To use those resolvers, the browser first has to look up their hostnames, so SafeNest answers those names with `NXDOMAIN` and the browser falls back to the filter. It also answers the canary domains Mozilla and Apple publish, which tell Firefox and iCloud Private Relay to stand down on this network.
- **Aliases.** An innocent-looking domain can answer with a CNAME, DNAME or HTTPS record pointing at a blocked one. SafeNest follows the alias chain inside each reply (with depth and loop limits) and blocks the lookup if any target is listed.
- **Raw IP addresses.** A site opened as `http://203.0.113.7` never asks DNS at all. Known gambling IP ranges are added as extra routes into the tunnel; since the loop only answers DNS, packets to those ranges are simply dropped.
- **Other VPN apps, and uninstalling.** Android allows only one VPN at a time, so the cheapest bypass is to start another one. With the user's explicit consent, an Accessibility service watches which app is in the foreground and sends VPN apps and gambling apps back to the home screen. During the paid period, it does the same for the destructive buttons on SafeNest's own settings page: Uninstall, Force stop, and switching Always-on VPN off. Everything else in Settings stays usable. It checks the app's identity only and never reads what is on the screen.

## Testing it for real

Unit tests can prove the DNS codec parses packets correctly. They cannot prove that tapping Uninstall on a real Android 16 phone sends you to the home screen. So every push to the repository runs an end-to-end test in GitHub Actions:

1. Boot a fresh Android 16 emulator and install SafeNest, plus small stand-in apps that pretend to be a gambling app, a VPN app and a browser with a built-in VPN.
2. Drive the real setup with UI Automator: grant Accessibility, start the VPN, switch on Always-on VPN through Android's own settings screen.
3. Check that a listed test domain fails to resolve, that ordinary HTTPS sites still load, and that the gambling, VPN and browser stand-ins all bounce to the home screen while a normal notes app stays open.
4. Open SafeNest's own App info page and tap Uninstall, Force stop and Clear storage; each must return Home, while the rest of Settings stays usable.
5. End the test and confirm every lock is released.

The run saves a screenshot and a UI dump at every step, which turned out to matter. After a redesign with new fonts, the test started failing with "Missing visible button: Review and enable test guard". The button was definitely being drawn. The saved screenshot showed the page scrolled to the very bottom, and the test had searched the whole page twice. The cause was the test's own scrolling: each swipe was fast enough that Jetpack Compose treated it as a fling, and after the layout change one fling carried the page straight past the card holding the button, between two visibility checks. Making the drag slower and shorter, so the page moves exactly as far as the finger, fixed it, and the pipeline went green again with all 26 device checks passing.

It is a small bug, but it taught me to trust evidence over assumptions: the screenshot answered in a minute what reading the code had not.

## What I learned

- **Pick the smallest tunnel that does the job.** Routing one /32 address instead of everything removed a whole category of performance, battery and privacy problems.
- **Data structures matter on cheap phones.** The same list went from about 100 MB to about 4 MB by changing what is stored, not how fast it is read.
- **Security work is mostly about the bypass, not the feature.** The basic block was the quick part; the mirrors, encrypted DNS, aliases, raw IPs and competing VPNs took most of the time.
- **Be honest about limits.** SafeNest raises the cost of relapse a great deal, but a determined person with a factory reset can get around it, and the app says so to its users. The goal is that every bypass takes deliberate effort, which is usually enough to get someone through the urge.

The code, architecture diagrams and CI pipeline are in [this repository](../README.md), and the project site is [mysafenestbd.com](https://mysafenestbd.com).

I'm an ICT engineering student at JAMK University of Applied Sciences in Jyväskylä, looking for 2027 internships and summer trainee roles in Android, backend or AI. If you're working on something similar, I'd like to hear from you on [LinkedIn](https://www.linkedin.com/in/md-humaun-kabir-590976236/).
