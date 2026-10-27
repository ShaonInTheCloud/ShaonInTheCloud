#!/usr/bin/env python3
"""Derive gambling brand candidates from the blocklist sources already in data/blocklists/sources.

    python -I tools/derive_gambling_brands.py            # writes data/gambling-brand-candidates.tsv
    python -I tools/derive_gambling_brands.py --apply    # also appends accepted stems to gambling_app_brands.txt

A brand is the stem of a domain's second-level label with mirror digits and common tails removed
(1xbet-bd.com, 1xbet88.net -> 1xbet). It is ACCEPTED only when it has at least MIN_HOSTS mirror
domains across the sources AND looks like a gambling brand (a digit, or a gambling token inside),
so ordinary words that gambling mirrors borrow (madrid, fresh, mario) never reach the app matcher,
which blocks any app whose name or package contains a brand as a whole word.
Everything else with >= MIN_HOSTS hosts goes to the candidates file for human review.
"""
from __future__ import annotations

import collections
import glob
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "data" / "blocklists" / "sources"
BRANDS = ROOT / "android" / "app" / "src" / "main" / "assets" / "blocked_apps" / "gambling_app_brands.txt"
CANDIDATES = ROOT / "data" / "gambling-brand-candidates.tsv"
MIN_HOSTS = 10
TAILS = ("bd", "bet", "app", "apps", "official", "login", "casino", "online", "live", "game", "games",
         "win", "vip", "pro", "plus", "club", "mobile", "sports", "sport", "play")
TOKENS = ("bet", "casin", "slot", "poker", "jili", "bong", "nohu", "lotto", "lotte", "jackpot", "rummy",
          "teenpatti", "satta", "matka", "baji", "jeet", "bingo", "roulette", "bacc", "wager", "spin",
          "gamble", "gambling", "sportsbook", "bookie", "1win", "pari", "stake", "ludo", "fortune", "lucky")
ASIA_TLDS = (".bd", ".in", ".pk", ".lk", ".np", ".ph", ".id", ".vn", ".th", ".my", ".kh", ".mm", ".asia", ".bet")
# Short brand words that the stem rule would strip or that are too generic on their own.
NEVER = {"paris", "casino", "casinos", "slot", "slots", "bets", "poker", "game", "games", "lucky", "fortune", "spin",
         "stake", "ludo", "pari", "bong", "jili", "nohu", "lotto", "bingo", "sports", "sport"}


def stem(host: str) -> str:
    parts = host.split(".")
    sld = parts[-2] if len(parts) >= 2 else parts[0]
    if sld in ("co", "com", "net", "org") and len(parts) >= 3:
        sld = parts[-3]
    s = re.sub(r"^[0-9]+|[0-9]+$", "", sld.replace("-", ""))
    for tail in TAILS:
        if s.endswith(tail) and len(s) - len(tail) >= 4:
            return s[: -len(tail)]
    return s


def gambling_like(s: str) -> bool:
    return any(ch.isdigit() for ch in s) or any(tok in s for tok in TOKENS)


def main(apply: bool) -> int:
    hosts: set[str] = set()
    for path in glob.glob(str(SOURCES / "*.txt")):
        for line in open(path, encoding="utf-8", errors="replace"):
            line = line.strip()
            if line and not line.startswith("#") and "." in line.split()[-1]:
                hosts.add(line.split()[-1].lower())
    count: collections.Counter[str] = collections.Counter()
    asia: collections.Counter[str] = collections.Counter()
    for host in hosts:
        s = stem(host)
        if len(s) >= 5 and re.fullmatch(r"[a-z][a-z0-9]*", s):
            count[s] += 1
            if host.endswith(ASIA_TLDS):
                asia[s] += 1
    existing = {line.split("#")[0].strip().lower().replace(" ", "") for line in open(BRANDS, encoding="utf-8")}
    accepted, review = [], []
    for s, n in count.most_common():
        if n < MIN_HOSTS or s in existing or s in NEVER:
            continue
        (accepted if gambling_like(s) else review).append((s, n, asia[s]))
    with open(CANDIDATES, "w", encoding="utf-8", newline="\n") as out:
        out.write("stem\tmirror_hosts\tasia_tld_hosts\tdecision\n")
        for s, n, a in accepted:
            out.write(f"{s}\t{n}\t{a}\taccepted\n")
        for s, n, a in review:
            out.write(f"{s}\t{n}\t{a}\treview\n")
    print(f"{len(accepted)} accepted, {len(review)} for review -> {CANDIDATES.relative_to(ROOT)}")
    if apply and accepted:
        with open(BRANDS, "a", encoding="utf-8", newline="\n") as out:
            out.write(f"# Derived by tools/derive_gambling_brands.py from data/blocklists/sources ({len(accepted)} stems,"
                      f" each with >= {MIN_HOSTS} mirror domains and a gambling-shaped name).\n")
            for s, _, _ in sorted(accepted):
                out.write(s + "\n")
        print(f"appended {len(accepted)} brands to {BRANDS.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main("--apply" in sys.argv[1:]))
