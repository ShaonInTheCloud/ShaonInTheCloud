# SafeNest Play submission pack — draft

Use only the `play` flavor. Do not submit the direct development APK as the consumer Play release. This pack needs verified support/legal details, actual screenshots, reviewer credentials and completed device tests.

## Listing draft

Name: SafeNest

Short description: Build safer habits with gambling filters and a personal app guard.

Full description:

SafeNest helps you create a safer digital space with local website rules and a personal app guard. Choose a finite protection period using your verified SafeNest account. The app guard can return you to the Home screen when selected blocked apps or detected VPN apps are opened. Website filtering uses Android's local VPN interface to check DNS requests; it is not a privacy VPN.

English and Bangla interfaces are available. Device rules and check-ins stay on your phone. Account authentication uses Supabase. Protection requires an active server-verified period and explicit permission consent.

This release serves existing SafeNest accounts and has no in-app purchase flow. Coverage depends on the rules, Android version, browser settings and enabled permissions. It cannot guarantee every gambling site, adult site, VPN or proxy is blocked. Android uninstall and permission controls remain available. This DNS-only build must leave Android “Block connections without VPN” off.

Website: https://mysafenestbd.com

Privacy: https://mysafenestbd.com/privacy.html

Account deletion: https://mysafenestbd.com/delete-account.html

Support email: **required before submission; no verified support address supplied**.

## App access for reviewers

Provide a dedicated confirmed email/password through Play Console's private App access field. Never put these credentials in GitHub or this document. It must have an approved test entitlement and be usable without a real payment or unavailable operator action.

Reviewer flow: sign in → verify entitlement → review Accessibility disclosure/consent → grant permission → review local-DNS disclosure → grant VPN permission → activate → verify ordinary browsing and a listed test domain → open a detected blocked app → verify Android settings/uninstall remain available. Provide exact test domains/app package IDs and supported device version after QA.

## Declarations and evidence

Accessibility: not an accessibility tool. Describe foreground app-ID matching and the consented Home action. Explain that Play does not inspect Settings/installer labels or browser address bars. Provide the separate disclosure and permission flow video. Do not claim a disability-assistance purpose.

The Play XML override subscribes only to window-state changes and omits view-ID reporting. Window-content retrieval remains enabled solely to verify the active root's package before returning Home, avoiding stale events. This is a source configuration check; verify the merged XML and behavior in the built artifact. Do not describe the service as lacking window-content capability.

VpnService: describe local DNS filtering for user-selected content. Declare actual upstream transport and whether any data is sent to resolvers. Do not claim all traffic is encrypted. Confirm final implementation eligibility with the policy and reviewer.

Foreground service: state why the ongoing local filter needs background operation and what the persistent notification represents. Use the shipped manifest's service type/declaration.

## Data safety working inventory — requires final answers

| Data/processing | Current behavior | Final review needed |
|---|---|---|
| Email, password authentication, account ID | Sent over HTTPS to Supabase Auth | Collection categories, required purpose, provider processing contracts |
| Optional display name and language | Private profile stored on Supabase | Personal info and app preference disclosures |
| Paid period and entitlement ID | Read from server; finite state cached locally | Purchase history/account data classification once real payments start |
| Foreground blocked-app observations | Play matches app ID locally; no observation history upload | Ephemeral processing and installed-app visibility declaration |
| DNS queries | Local rule check; allowed queries sent to network/fallback resolver, sometimes configured DNS-over-TLS | Resolver processing/retention and browser-history/ephemeral classification |
| Check-ins and custom rules | Local device storage | Verify no export/backup/telemetry uploads in the shipped build |
| Infrastructure logs | Hosting, Auth/email and resolver providers may keep operational data | Retention periods and provider contracts |
| Card and wallet data | Not collected now; planned hosted provider checkout | Update disclosures before live payments |

Do not answer “no data collected” based on local blocking alone: authentication and resolver requests leave the device. Do not assume operational logs are absent. User account deletion removes live account-linked rows, but configured provider backups/logs and local device data require separate disclosure.

## Assets and release gates

Supply an original launcher/store icon, feature graphic and phone screenshots from the final Play build. Use the existing approved SafeNest branding; no fabricated endorsement or claim of complete protection. Complete content rating, target audience, countries, testing eligibility and staged rollout settings in the owner's Play Console.

Reference policies: `market-launch-checklist.md`. None of these draft materials is evidence of Google approval.
