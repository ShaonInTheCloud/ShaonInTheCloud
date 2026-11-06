# SafeNest skills review — 6 November 2026

Checks taken from the community library `mukul975/Anthropic-Cybersecurity-Skills` (not an Anthropic project). Only the checklists were used; none of its scripts were run. The review was done against PR #30 (`716ccd7`) and covers what the 1 Nov audit did not.

| Skill | Result |
|---|---|
| securing-github-actions-workflows | **Fixed.** Actions were already SHA-pinned with read-only tokens, inputs passed via env, and no `pull_request_target`. Checkouts kept the GitHub token in `.git/config` while Gradle and npm ran, so `persist-credentials: false` was added to all 6 checkouts. There was nothing to keep the SHA pins current, so `.github/dependabot.yml` was added (actions, npm root, intelligence). |
| exploiting-insecure-data-storage-in-mobile | **Fixed.** `allowBackup="false"` does not stop Android 12+ device-to-device transfer, and the app targets SDK 36. Commitment clock anchors, device-owner records and the "save privately on this device" reflections could be copied to a new phone. `data_extraction_rules.xml` now excludes every domain for cloud backup and device transfer. `verify-release-signing.py` refuses a release without it, and a test was added. |
| testing-android-intents-for-vulnerabilities | PASS. Every exported component except the launcher is bound to a system permission (VPN, accessibility, device admin, job). The receivers for internal broadcasts are not exported. PendingIntents are explicit and immutable. |
| testing-api-for-mass-assignment / BOLA | PASS. `payment-order` whitelists keys, and price and duration come from the server. Adapters pick named customer fields. `payment_order_status` filters on `user_id`. |
| detecting-malicious-npm-packages | PASS. All three lockfiles resolve only from registry.npmjs.org with integrity hashes. The only install scripts belong to esbuild and unrs-resolver. |

Owner, optional: add a `CODEOWNERS` entry for `.github/workflows/`. In repo Settings → Actions, require approval for fork PRs from outside contributors.

Evidence: `npm test` 113/113, signing tests 14/14 (one new negative case), `run-core-tests.sh` passes, XML is well-formed. Gradle and lint were not run here, so CI on the branch must confirm `lintPlayRelease` and the manifest merge.
