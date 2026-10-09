#!/bin/sh
# Rebuilds SafeNest's bundled gambling blocklist assets from data/blocklists/.
# Run from the safenest/ directory with a JDK 17 on PATH:  sh tools/blocklist/build.sh
set -eu
ROOT=$(pwd)
APP=android/app/src/main/java/com/safenest/app
ASSETS=android/app/src/main/assets
DATA=data/blocklists
BUILD=$(mktemp -d)
trap 'rm -rf "$BUILD"' EXIT

javac -d "$BUILD" "$APP/DomainRules.java" "$APP/CompactDomainSet.java" tools/blocklist/BlocklistCompiler.java

# 1) Core list: SafeNest research + MIT / Unlicense feeds.
java -cp "$BUILD" BlocklistCompiler \
  --out "$ASSETS/gambling_core.snbl" \
  --manifest "$DATA/gambling_core.manifest.json" \
  --exclude "$DATA/exclusions.txt" \
  "$DATA/sources/gambling_bangladesh_research_2026-10_web.tsv" \
  "$DATA/sources/gambling_bangladesh_researched.txt" \
  "$DATA/sources/gambling_south_asia_researched.txt" \
  "$DATA/sources/gambling_hosts_vn.txt" \
  "$DATA/sources/gambling_hosts_sinfonietta.txt" \
  "$DATA/sources/gambling_brand_families.txt" \
  "$DATA/sources/gambling_hosts_blocklistproject.txt"

# 2) Priority names for Chrome's capped managed-policy list (Bangladesh research only).
java -cp "$BUILD" BlocklistCompiler \
  --out "$BUILD/priority.snbl" \
  --manifest "$BUILD/priority.json" \
  --exclude "$DATA/exclusions.txt" \
  --priority-out "$ASSETS/gambling_bangladesh_priority.txt" \
  "$DATA/sources/gambling_bangladesh_research_2026-10_web.tsv" \
  "$DATA/sources/gambling_bangladesh_researched.txt" \
  "$DATA/sources/gambling_south_asia_researched.txt"

# 3) GPL-3.0 layer: HaGeZi-derived data, kept as its own asset with its licence.
java -cp "$BUILD" BlocklistCompiler \
  --out "$ASSETS/gambling_hagezi_gpl3.snbl" \
  --manifest "$DATA/gambling_hagezi_gpl3.manifest.json" \
  --exclude "$DATA/exclusions.txt" \
  --minus "$ASSETS/gambling_core.snbl" \
  "$DATA/gpl3/gambling_hagezi_medium.txt" \
  "$DATA/gpl3/gambling_bangladesh_hagezi_brandmatch_2026-10.tsv"
cp "$DATA/licenses/LICENSE-HaGeZi-GPL-3.0.txt" "$ASSETS/third_party_hagezi_gpl3.txt"
echo "Done. Commit the assets and manifests together."
