## Hi, I'm Md Humaun Kabir

ICT engineering student at **JAMK University of Applied Sciences** in Jyväskylä, Finland. I build Android apps, backends and AI-assisted tools, and I like shipping things that real people use.

### Featured project: [SafeNest](safenest/)

An Android self-exclusion app that blocks gambling sites and apps on the user's own phone, privately and on-device.

- DNS-only `VpnService` filter for **541,485 gambling domains**, stored as 64-bit fingerprints in about 4 MB
- Catches new mirror domains, closes encrypted-DNS and raw-IP bypasses, and follows CNAME/HTTPS aliases
- Signed, versioned blocklist updates (ECDSA P-256, pinned key)
- Supabase backend with Postgres row-level security, Play Billing and payment webhooks
- CI on every push: about 550 JVM assertions, 138 Node tests, and an **Android 16 emulator end-to-end test** driven by UI Automator

[Read the SafeNest README →](safenest/) · [mysafenestbd.com](https://mysafenestbd.com)

### What I work with

**Mobile:** Kotlin, Jetpack Compose, Android `VpnService` and Accessibility APIs, Expo / React Native
**Backend & cloud:** Supabase, PostgreSQL (RLS), TypeScript Edge Functions, Cloudflare Workers, GitHub Actions
**Data & automation:** Python, SQL, data analytics, Robot Framework, UiPath RPA, Linux
**AI:** Claude API integrations, AI coding agents with automated test and quality gates

### Also built

- **CricNation**: cricket scoring and tournament web app with AI-generated live commentary (Claude API), wagon-wheel and Manhattan charts, and a stream overlay
- Live scoring, draw consoles and broadcast overlays for the Finn-Bangla Cricket Tournament 2026 (30 teams)

Off the keyboard I'm a fast bowler for the Finland men's national cricket team.

📫 [LinkedIn](https://www.linkedin.com/in/md-humaun-kabir-590976236/) · kabirmdhumaun23@gmail.com · Open to 2027 internships and summer trainee roles
