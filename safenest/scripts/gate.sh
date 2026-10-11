#!/usr/bin/env sh
# SafeNest loop gate: the single verifier the agent loop runs after every change.
# Exit 0 only when every check below passes. Mirrors the host-side CI steps and
# adds the intelligence package, which CI does not run. Android Gradle and
# emulator checks stay in CI; they need the SDK.
set -eu
safenest_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$safenest_root"

step() { printf '\n=== %s ===\n' "$1"; }

step 'web: unit tests'
npm test

step 'web: protection-access edge function tests'
node --test supabase/functions/protection-access/test/*.test.mjs

step 'intelligence: unit tests'
(cd intelligence && npm test)

step 'web: build'
SUPABASE_PUBLISHABLE_KEY="${SUPABASE_PUBLISHABLE_KEY:-$(grep -o "sb_publishable_[A-Za-z0-9_]*" ../.github/workflows/safenest-ci.yml | head -1)}" npm run build

step 'scripts: python release-signing tests'
python3 -B -m unittest discover -s scripts/test -p 'test_*.py'

step 'android: core regression tests (no SDK)'
sh run-core-tests.sh

printf '\nGATE PASSED\n'
