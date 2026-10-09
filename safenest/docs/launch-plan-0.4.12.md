# SafeNest 0.4.12 launch plan

Two launch tracks. The direct (website) track can go live in about a day. Google Play has fixed waiting periods that nobody can shorten.

## Track A: website (direct) launch, about 1 day

| # | Step | Who | Status |
|---|---|---|---|
| A1 | Signed direct APK built (`SafeNest-0.4.12-direct.apk`, direct key `E1:72:5D…64:62`) | Claude | Done |
| A2 | Install it on **your own phone**. Sign in, start the 72-hour trial, turn protection on, check that a blocked site (e.g. `1xbet.com`) fails and an ordinary site loads, then turn on WARP and confirm the alert appears | Shaon | To do |
| A3 | Upload the APK to the website as `/downloads/SafeNest-direct.apk` (the Strong lock QR already points there) and replace the old 0.4.10 debug download | Shaon (website hosting) | To do |
| A4 | Strong lock check on a **spare** phone: factory reset, tap the Welcome screen 6 times, scan `SafeNest-0.4.12-strong-lock-qr.png`, apply Strong lock, try WARP and uninstall, then let a test period expire and use **Remove SafeNest** | Shaon | To do |
| A5 | Turn on website trial enrolment (`releaseReady`) only after A2 passes | Claude + Shaon | Waiting on A2 |

Payments stay closed until SSLCOMMERZ merchant credentials exist; the 72-hour trial works without them.

## Track B: Google Play, at least about 3 weeks

1. **Play Console account** (Shaon). If it is a personal account created after 13 Nov 2023, Google requires a **closed test with at least 12 testers opted in for 14 days in a row**, then a production-access review of usually up to 7 days.
2. **Create the app**: package `com.safenest.app`, enable Play App Signing, and upload `SafeNest-0.4.12-play-release.aab` (signed with the upload key `64:59:CC…CA:D9`) to **Internal testing** first, then **Closed testing**.
3. **Recruit 12+ testers** now (cricket club, friends, family). Each tester needs a Google account added to the closed-test list and must install the app.
4. **Declarations** (text below): VpnService, Accessibility, foreground service `specialUse`, Data safety, content rating, target audience 18+, privacy policy URL `https://mysafenestbd.com/privacy`, account deletion URL `https://mysafenestbd.com/delete-account`.
5. **Reviewer access**: a test account with an active trial, plus written steps.
6. **Policy risk**: Google's VpnService policy lists permitted uses and does not explicitly mention on-device self-control filters. SafeNest fits closest to *parental control / device security (firewall)*. Describe it as a user-chosen self-exclusion filter, keep all filtering on the device, and expect possible follow-up questions from Google.

## Store listing (paste-ready)

**App name:** SafeNest: Gambling Blocker

**Short description (EN):** Block betting, casino and adult sites on your phone. Built for Bangladesh.

**Short description (BN):** ফোনে বেটিং, ক্যাসিনো ও প্রাপ্তবয়স্ক সাইট ব্লক করুন। বাংলাদেশের জন্য তৈরি।

**Full description (EN):**
SafeNest helps you stay away from gambling. It blocks hundreds of thousands of betting and casino websites, including the brands most active in Bangladesh, plus adult sites. You can also choose gambling apps to keep closed.

How it works:
• A local filter checks each website name on your phone. Blocked sites don't load; everything else works normally.
• Your browsing is not sent to SafeNest. Allowed lookups use encrypted DNS.
• Choose a protection period. SafeNest stays on until it ends, so a weak moment can't switch it off with one tap.
• If another VPN app turns SafeNest off, you're alerted immediately and can turn it back on.
• English and Bangla throughout. Prices in taka.

SafeNest uses Android's VpnService to receive DNS lookups on the device. It does not route your web traffic through a remote server, read page contents or sell data. The optional app guard uses Accessibility, only after you agree to a clear in-app explanation, to return selected gambling apps to the Home screen.

Start with a free 72-hour trial.

**Full description (BN):**
SafeNest জুয়া থেকে দূরে থাকতে সাহায্য করে। এটি লক্ষাধিক বেটিং ও ক্যাসিনো ওয়েবসাইট ব্লক করে, যার মধ্যে বাংলাদেশে সবচেয়ে সক্রিয় ব্র্যান্ডগুলোও আছে, সঙ্গে প্রাপ্তবয়স্ক সাইট। চাইলে নির্দিষ্ট জুয়ার অ্যাপও বন্ধ রাখতে পারেন।
• আপনার ফোনেই প্রতিটি ওয়েবসাইটের নাম যাচাই হয়; ব্লক করা সাইট খোলে না, বাকি সব স্বাভাবিক চলে।
• আপনার ব্রাউজিং SafeNest-এ পাঠানো হয় না।
• একটি সুরক্ষার মেয়াদ বেছে নিন; মেয়াদ শেষ না হওয়া পর্যন্ত SafeNest চালু থাকে।
• অন্য VPN অ্যাপ SafeNest বন্ধ করলে সঙ্গে সঙ্গে জানানো হয়।
• পুরোটা ইংরেজি ও বাংলায়, দাম টাকায়। ৭২ ঘণ্টা বিনামূল্যে চেষ্টা করুন।

## Declaration drafts

**VpnService:** SafeNest is a user-installed gambling self-exclusion and content filter. VpnService routes only DNS lookups (a single /32 route, no default route) to an on-device filter that refuses listed gambling and adult domains. Allowed lookups are resolved over certificate-verified HTTPS DNS. No web traffic is tunnelled to a SafeNest server, no traffic is redirected or manipulated for monetization, and no browsing history is collected. The in-app disclosure and affirmative consent appear before the VPN permission request.

**Accessibility (isAccessibilityTool = false):** Optional "app guard", enabled only after an in-app prominent disclosure and consent. It reads the foreground app's package name to return apps the user chose to block (and detected VPN apps, if the user opts in) to the Home screen. In the Play build it does not interfere with Settings, permissions or uninstalling. No screen content is stored or uploaded.

**Foreground service (specialUse):** Keeps the user-started DNS filter running and visible with an ongoing notification while protection is active.

## Data safety

Use the working inventory in `play-submission-pack.md`. Summary: email and account ID (account management, encrypted in transit, deletable); optional display name; DNS names of allowed lookups go to the DNS provider for resolution (not stored by SafeNest); no location, contacts, browsing history upload or advertising ID.

## Owner security tasks

1. Save both keystores and `secrets.env` in a password manager **and** an offline backup (USB). The direct key can never be replaced without re-enrolling every Strong lock phone.
2. GitHub → repo Settings → Environments → `android-release`: add `SAFENEST_UPLOAD_KEYSTORE_BASE64`, `SAFENEST_UPLOAD_STORE_PASSWORD`, `SAFENEST_UPLOAD_KEY_ALIAS`, `SAFENEST_UPLOAD_KEY_PASSWORD`, and variable `SAFENEST_UPLOAD_CERT_SHA256 = 6459ccec363200317ae0adacc65f64d4b1d6a297bcbad2a2ce0d32fd73e5cad9`. (Claude cannot set repository secrets from its session.)
3. Merge order: review PR #7, then PR #8, then the main-branch workflow update.
