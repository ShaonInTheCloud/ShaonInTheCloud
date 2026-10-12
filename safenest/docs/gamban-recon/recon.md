# Recon map: Gamban (Android, iOS, Windows, macOS; web account portal)

Scope: the core loop only. Blocking gambling sites and apps on the user's own devices, the account and licence that gate it, and the setup flows. Excludes GAMSTOP, partner programs, the blog and the B2B distribution side.
For: a self-exclusion / gambling-harm-reduction product (user's own business).
Date: 2026-10-12

Method note: public pages and help-center articles only. No account was created, no app installed, no screenshots taken, so screens are inferred from help text and the login page. Anything marked guess needs checking against the user's own trial account.

## Sources

| # | source | URL | notes |
| --- | --- | --- | --- |
| 1 | login / payment return page | https://gamban.com/payment | Only a login page: email and password, Google and X sign-in, forgot password, create free account, language selector. No plan or payment details. Login returns to /payment after sign-in, so payment likely follows login. |
| 2 | marketing home | https://gamban.com | Pitch, feature claims, nav, certifications, 7-day trial |
| 3 | pricing | https://gamban.com/pricing | Monthly and annual plans |
| 4 | blocking page | https://gamban.com/blocking | Blocklist categories, exclusions, friction claim |
| 5 | Android install | https://kb.gamban.com/en-gb/8-gamban/26-android-gamban | Four-step setup, permissions |
| 6 | iOS 14+ install | https://kb.gamban.com/en-gb/8-gamban/22-apple-ios-14-gamban | Configuration profile and Remote Management |
| 7 | macOS install | https://kb.gamban.com/en-gb/8-gamban/28-macos-gamban | DMG installer, check.gamban.com |
| 8 | device compatibility | https://kb.gamban.com/en-gb/11-/15-gamban | OS minimums |
| 9 | uninstalling | https://kb.gamban.com/en-gb/10-/46-gamban | No early removal |
| 10 | App Store listing | https://apps.apple.com/us/app/-/id1459803064 | Fetch failed; not read |
| 11 | Also exist, not read | https://kb.gamban.com/en-gb/8-gamban/27-samsung-gamban7, https://kb.gamban.com/en-gb/8-gamban/21-google-play-google-play-android-gamban | Samsung and no-Play-Services Android installs |

## Core loop

A person with a gambling problem pays for a licence, installs Gamban on every device, and the app blocks gambling sites and apps for the whole licence period, with no way to remove it early.

## Screens

| ID | screen | route / how to reach | purpose | key components | states seen |
| --- | --- | --- | --- | --- | --- |
| S01 | Marketing home | gamban.com | pitch, start trial | nav, hero, testimonials, certifications, CTA | filled |
| S02 | Pricing | /pricing | choose plan | two plan cards, "save 49%" badge, trial CTA | filled |
| S03 | Login | /payment (login form) | sign in | email, password, Google, X, forgot password, create account, language selector | filled; error guess |
| S04 | Create account | link from S03 | register | form (fields not seen) | guess |
| S05 | Payment / licence purchase | after login, return param /payment | start trial or pay | plan choice, payment form | guess, not seen |
| S06 | Account / device manager | web portal after login | manage devices, get installers, resources | device list, download links | guess; login page mentions managing devices and resources |
| S07 | Mobile app login | app | sign in with licence | email/password | medium |
| S08 | Android setup wizard, 4 steps | app | grant permissions | step list with checkmarks, skip for done steps | medium |
| S09 | Accessibility permission prompt | app + Android settings | enable service | instructions, warning not to enable the shortcut | medium |
| S10 | Device admin activation | app + Android settings | prevent removal | activate button | medium |
| S11 | VPN permission | app + system prompt | start local VPN | start button, OS prompt | medium |
| S12 | Background / battery permission | app + system prompt | keep alive | option, OS prompt | medium |
| S13 | Test protection screen | app, final step | verify blocking | prompt to open random gambling sites | medium |
| S14 | iOS setup + profile install tutorial | app, then Safari, then Settings | install config profile | tutorial screens, external Safari page | medium |
| S15 | Dashboard | app (login page shows an app dashboard image) | progress | days without gambling, estimated time and money saved | medium, from marketing text only |
| S16 | Self-exclusion layers | app | extra exclusion levels | "layered self-exclusion section" | guess; contents not seen |
| S17 | Mac installer | DMG from account | install | setup file, restart prompt | medium |
| S18 | Protection check page | check.gamban.com | confirm protected | status message | medium |
| S19 | Blocked page / block notice | when a blocked site is opened | stop access | unknown | guess |
| S20 | Support / contact | site | live install support | contact form | guess |

## Flows

```
F01 Visitor starts free trial
    S01 -> S02 -> S04 -> S05 -> S06
    happy path clicks: unknown (payment not observed)
    edge: trial terms and auto-renewal unstated; Ohio residents free via a state program

F02 Install on Android
    S07 -> S08 -> S09 -> S10 -> S11 -> S12 -> S13
    happy path steps: 7 per help article (register, log in, accessibility, device admin, VPN, background, test)
    edge: Android below Lollipop; no Google Play Services (separate article); Samsung-specific article; setup fails -> contact support

F03 Install on iOS
    S07 -> S14 -> (Safari page) -> Settings > General > Profile -> trust Remote Management -> return to app
    edge: Safari must be default browser; fails on work-managed devices; iOS below 14 uses a different article

F04 Install on macOS
    S06 (download) -> S17 -> restart -> S18
    edge: macOS below 10.10; page doesn't describe permissions

F05 Check progress
    S15 (days clean, money and time saved)

F06 Try to remove Gamban
    no early removal; protection ends by itself when the licence period ends
    edge: no override or waiting-period flow described; struggling users are pointed to a get-help page

F07 Blocked access
    user opens gambling site or app -> blocked (S19, unseen)
```

## Components

| component | variants | states | used on |
| --- | --- | --- | --- |
| Button | primary, secondary, social login | default, loading | S01-S07 |
| Text input | email, password | default, error | S03, S04, S07 |
| Step list with checkmarks | setup wizard | pending, done, skippable | S08 |
| Permission instruction card | accessibility, device admin, VPN, background | pending, done | S09-S12 |
| Plan card | monthly, annual | default, badge | S02 |
| Language / country selector | n/a | n/a | S03 |
| Stat tiles | days clean, time saved, money saved | n/a | S15 |

## Inferred data model

```
User         id, email, password hash or social identity (Google, X), country, language
             evidence: S03 login form, S04 link
             confidence: high

Licence      id, user_id, plan (monthly | annual), started_at, ends_at, trial flag, status
             evidence: pricing page, uninstall article (licence period is the only timeframe), 7-day trial
             confidence: high for plan and period; medium for the rest

Device       id, user_id, platform (android | ios | windows | macos), name, activated_at, last_seen
             evidence: login page mentions managing devices; one licence covers all devices
             confidence: medium

Blocklist    domains and app identifiers by category (sports betting, casino, poker, bingo, lottery,
             social casino, crypto, NFT, affiliates, trading, esports and skins)
             evidence: blocking page, updated regularly
             confidence: medium

ExclusionLevel / Settings   user_id, enabled layers
             evidence: "layered self-exclusion" in the app and on the marketing site
             confidence: guess

ProgressStat user_id, start date, derived days clean, estimated time and money saved
             evidence: marketing site feature list
             confidence: medium; how "saved" is computed is unknown
```

Relationships: User 1-n Licence (likely one active), User 1-n Device, Licence 1-n Device (one licence covers all), Blocklist global.

## Feature matrix

See `features.csv`. Must: 16, should: 10, could: 4, skip: 5.

## Out of scope (cannot or should not be cloned)

- The curated blocklist itself as a data asset. Gamban says it is maintained continuously and spans tens of thousands of sites; that is their data. A clone needs its own sources.
- Certifications (B Corp, ISO 9001, ISO 27001, Investors in People) and partner or government programs (Time Out Ohio free access, GAMSTOP and banking-block guides).
- The brand, copy, logos, testimonials and the Gamban name.
- Their private blocking implementation. The help articles name permissions (VPN, accessibility, device admin, configuration profile) but not how filtering works; do not try to reverse that from the app or network traffic.

## Size

Screens 20, flows 7, entities 6. Hard parts: reliable blocking per OS (local VPN on Android, a configuration profile with Remote Management on iOS, a system-level installer on desktop), tamper resistance and removal friction, keeping the blocklist fresh, billing with trial, and platform review for apps that use accessibility and device admin. Size: L (a quarter). The blocking engine and blocklist are the long pole; account, payment and dashboard alone would be M.

Open questions to check on a trial account: the payment screens and trial terms, how many devices a licence allows, what the self-exclusion layers contain, what the dashboard shows, and what a blocked page looks like. The App Store listing was not read.
