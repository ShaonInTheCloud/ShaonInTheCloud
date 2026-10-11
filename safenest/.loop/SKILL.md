---
name: safenest-green-loop
description: Keep the SafeNest gate green. Run this on the ShaonInTheCloud/ShaonInTheCloud repo when asked to run the SafeNest loop, fix failing SafeNest tests, or keep the gate green.
---

# SafeNest green loop

GOAL: `sh safenest/scripts/gate.sh` exits 0 on the loop branch, with no check weakened.

## Setup (every run)
1. Clone or update `ShaonInTheCloud/ShaonInTheCloud`; work in `safenest/`.
2. `npm ci` in `safenest/` and `safenest/intelligence/`.
3. Read `safenest/.loop/state.md` — it says what the last run did, what it tried, and what is still open. Do not retry an approach already marked as failed there.

## Each iteration (max 8)
1. Run `sh scripts/gate.sh` and read every failure.
2. Pick the single highest-impact failure (a failing test beats a warning; a shared cause beats a symptom).
3. Make the smallest change that fixes it. Prefer fixing the test's assumption over weakening the test; never delete or skip a test to make the gate pass.
4. Re-run the gate.
5. Append one line to `state.md`: iteration number, what changed, result.

## Checker (separate agent, required before any PR)
Hand the diff to a second agent with different instructions: "Review this diff against the gate goal. Reject if it weakens, skips, or deletes a check, touches a never-touch path, or fixes a symptom while the cause remains." The maker does not grade its own work. A rejected diff goes back to step 2.

## Never touch
- The loop itself: `scripts/gate.sh` and `.loop/SKILL.md`. The verifier is not a lever. If the gate is wrong, stop and report it; a human changes it.
- `.loop/state.md` is append-only: add lines, never edit or remove earlier ones.
- `supabase/migrations/**` and `supabase/config.toml` (applied to production; changes go through a human)
- `supabase/functions/**` (payment, trial and account-deletion code that runs in production)
- `android/app/src/main/**`, `android/app/build.gradle.kts` (signing setup), and signing scripts (`scripts/*release*`, `scripts/VerifySignedBundle.java`)
- `scripts/check-live-security.mjs`, `scripts/security-headers.mjs`
- `.github/workflows/**`
- Any secret, key, `.env`, or publishable-key value
- Dependency versions in `package.json` / `package-lock.json` unless the failure is itself a dependency error

If the highest-impact failure can only be fixed by touching one of these, that is a LIMIT stop: report it, do not work around it.

## Stop
- SUCCESS: gate passes. If files changed, commit on a branch `loop/green-<date>`, push, open a PR titled "loop: <what was fixed>" with the gate output in the description. Never push to `main`.
- LIMIT: 8 iterations reached, the same failure appears 3 times in a row, or the fix would need a never-touch path. Stop, do not open a PR, write the remaining failures to `state.md`.

## On stop (always)
Report in under 150 words: gate status, what changed, what still fails, PR link if any, iterations used.
