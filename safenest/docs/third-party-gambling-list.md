# Bundled gambling-domain feed attribution

SafeNest includes two normalized domain-only MIT-licensed feeds:

- `android/app/src/main/assets/gambling_hosts_vn.txt`: [hostsVN gambling extension](https://github.com/bigdargon/hostsVN/blob/master/extensions/gambling/hosts-VN), snapshot 23 September 2026, upstream Git blob `eab4c99a18fe8e445a5b190b8ce18592494b57eb`, 3,983 hostnames, Vietnamese focus.
- `android/app/src/main/assets/gambling_hosts_sinfonietta.txt`: [Sinfonietta gambling hosts](https://github.com/Sinfonietta/hostfiles/blob/master/gambling-hosts), upstream Git blob `fa4277dfd75dd9ed080ceaf5942365e8729c0e1d`, 2,690 hostnames. The [StevenBlack gambling-only aggregator](https://github.com/StevenBlack/hosts/blob/master/alternates/gambling-only/readme.md) also identifies these two source feeds and reports 6,673 unique entries as of 2 October 2026.

These 6,673 hostnames are third-party categorized data, not 6,673 independently verified SafeNest investigations or a Bangladesh-specific coverage claim. Users can report false positives. Exact domains and their subdomains are matched; a different country TLD is a separate entry.

Upstream licences: MIT, copyright (c) 2026 BigDargon; MIT, copyright (c) 2016 Sinfonietta. The following permission and warranty notice applies to both copies:

> Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Before each refresh, review upstream licence, diff, false positives and domains with important shared-service dependencies. Do not create `1xbet.*` wildcard rules, blindly block a whole top-level domain, or treat a text similarity as proof of ownership. Known country sites such as `1xbet.fi` require an independent source and an explicit exact-domain rule.

## 0.3.10 brand-family addition

`gambling_brand_families.txt` adds 28,526 unique hostnames from Blocklist Project's gambling feed, selected using text aliases in the existing Bangladesh-focused seed. Source provenance and per-domain research tags are in `data/gambling-brand-families.json`; Unlicense notice is in `docs/blocklistproject-UNLICENSE.txt`. It is not a Bangladesh availability census. Combined with the older feeds and ten individually sourced additions, the assets contain 35,155 distinct hostnames. See `docs/verification-0.3.10.md` for evidence and limitations.
