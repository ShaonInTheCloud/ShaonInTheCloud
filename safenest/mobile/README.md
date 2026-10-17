# SafeNest mobile (Expo — Android + iOS)

The cross-platform SafeNest app: one codebase for Android and iOS, talking to the same Supabase
project as the website and the Kotlin Android app. Expo SDK 58, Expo Router, TypeScript.

What is in it today

- Welcome: language (English / বাংলা) and edition (Bangladesh ৳ / Global $), remembered on the device.
- Account: email + password sign-up (confirmation email goes through the website's allowlist), log in,
  password reset, log out, link to account deletion.
- Home: verified protection status from `protection-access` (active / trial / none, with end time),
  one-tap 72-hour trial via `start-trial`, auto-refresh every minute and on focus.
- Check a site: asks `intelligence/check` whether a hostname is on the signed SafeNest blocklist.
- Plans: live catalogue from `payment-catalogue` filtered to the chosen edition, hosted checkout for
  card (Stripe), crypto (NOWPayments) or SSLCOMMERZ through `payment-order` in an in-app browser,
  payment status recovery through `payment-status`. Shows the proposed prices while checkout is closed.
- Settings: language, edition, account, privacy and support links.

What is deliberately not in it yet

- The blocking engine. The DNS filter, app guard and uninstall lock are native code. They do not run in
  Expo Go, and the Home screen says so. Android: the existing Kotlin `VpnService` becomes a local Expo
  module (`modules/safenest-engine`) and ships in a development build. iOS: needs a Network Extension
  (DNS proxy / packet tunnel) with Apple's entitlement, so it comes after the Apple developer account.
- Store billing. Google Play Billing / Apple IAP come with the native build; the hosted web checkout
  works everywhere in the meantime.

## Run it on your phone in two minutes

1. Install **Expo Go** from Google Play or the App Store.
2. On your computer (Node 20+):
   ```sh
   cd safenest/mobile
   npm install
   npx expo start
   ```
3. Scan the QR code the terminal shows — Android: inside Expo Go; iPhone: with the Camera app.
   Phone and computer must be on the same Wi-Fi. If they are not, run `npx expo start --tunnel`.

`.env` holds only the public Supabase URL and publishable key (copied from `.env.example`).

## Checks

```sh
npx tsc --noEmit                                   # types
CI=1 npx expo export --platform android --platform ios   # full Metro bundle for both platforms
```

## Next steps toward the store build

1. `npx expo install expo-dev-client`, then `npx eas-cli build --profile development --platform android`
   (needs a free Expo account) — a build of this app with native modules, replacing Expo Go.
2. Move `android/` blocking code into a local Expo module and expose `start/stop/status` to JS.
3. Add Google Play Billing (`expo-iap` or RevenueCat) behind the same entitlement model.
4. iOS: Network Extension target + entitlement request, then the same module interface.
