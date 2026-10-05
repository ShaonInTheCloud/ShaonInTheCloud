# SafeNest account integration

Prepared for Supabase project `kflenmeizngmafwnwhgv`. The private profiles
migration is now applied in the hosted database (version `20260924212500`).
The personal account portal is published with Log in links in the public
header and footer at
`https://mysafenestbd.com/account`. On 5 October 2026 the Auth Site URL,
exact redirect allowlist and Resend custom SMTP were configured. Signup,
confirmation-email delivery, email verification and private profile saving
were verified with an owner-controlled test account. Password reset and
email/password login still require an end-to-end test. The owner requested
a visible login entry on 5 October 2026; navigation availability does not
mean that the complete registration flow or paid protection is verified.
The GitHub repository currently also holds the owner's profile README, so all
SafeNest files live under `safenest/`.

Included: email signup and confirmation, login, password reset, logout on the
current device, and private name/language preferences. The signed-in personal
portal also provides the Android development APK and bilingual installation
instructions. Saving a profile leaves the portal open with a clear next step. The interface uses white
and baby pink, English above smaller Bangla, with a full Bangla option.

Supabase Auth manages credentials and users. PostgreSQL row-level security
restricts each profile to its owner. Accounts do not activate device blocking.
The Android blocking prototype remains a separate application.

## 1. Configure Supabase Auth

Open your [Supabase project](https://supabase.com/dashboard/project/kflenmeizngmafwnwhgv).

1. In **Authentication → Sign In / Providers**, enable Email and keep email
   confirmation on. Set minimum password length to 10.
2. In **Authentication → URL Configuration**, use the currently working site URL:
   `https://mysafenestbd.com`.
3. Add these exact Redirect URLs:
   - `https://safenest-bangladesh.kabirmdhumaun23.chatgpt.site/account.html`
   - `https://safenest-bangladesh.kabirmdhumaun23.chatgpt.site/account.html?mode=recovery`
   - `https://mysafenestbd.com/account.html`
   - `https://mysafenestbd.com/account.html?mode=recovery`
   - `https://mysafenestbd.com/account`
   - `https://mysafenestbd.com/account?mode=recovery`
   - `https://safenest-bangladesh.kabirmdhumaun23.chatgpt.site/account`
   - `https://safenest-bangladesh.kabirmdhumaun23.chatgpt.site/account?mode=recovery`
4. Under **Authentication → Email / SMTP Settings**, custom SMTP is configured:
   sender `no-reply@auth.mysafenestbd.com`, name `SafeNest`, host
   `smtp.resend.com`, port `465`, username `resend`, minimum interval 60 seconds.
   The sending-only key is restricted to the verified `auth.mysafenestbd.com`
   domain and saved only in Supabase. Keep credentials out of Git. Confirmation
   delivery to Gmail passed SPF and DKIM; recovery still needs live testing.
5. In **Connect** or **Settings → API Keys**, copy the **Publishable key**
   beginning `sb_publishable_`. The account page does not need any secret key.

The custom domain works over HTTPS and the Site URL is
`https://mysafenestbd.com`. Keep both origins on the allowlist while both are used.
Do not use wildcard production redirect URLs. Supabase has a default password
recovery email template; retain its confirmation URL unless deliberately
implementing a different email verification flow.

## 2. Private profile migration

The hosted database already contains the migration at
`supabase/migrations/20260924212500_private_profiles.sql` (version `20260924212500`).
Do **not** run the SQL or `supabase db push` a second time on this project.
Check that Supabase's migration list shows this version before turning on
GitHub production deployment. The migration enables RLS and grants each
signed-in user only the permissions needed for their own profile.

The `auth.users` table belongs to Supabase. New accounts appear in
**Authentication → Users**. A row in `public.profiles` appears when a logged-in
user first saves their name/language. There is no signup trigger to fail and
block new accounts. User IDs and creation timestamps cannot be changed by users.

## 3. Build the account page

Use Node.js 22 or newer. Copy `.env.example` to `.env`, then fill in only your
publishable key. `.env` is ignored by git. The publishable key is included in
the browser bundle by design; it is not a secret and grants no access beyond RLS.

```sh
npm ci
npm test
npm run build
```

The result is `dist/account.html`, `dist/account.js`, and `dist/account.css`.
Without a key, the page clearly says account setup is in progress and all
account forms remain hidden. A secret/service-role key is rejected by the build.

The existing Sites project serves the compiled account portal at `/account`
(`/account.html` redirects there). Each public page now includes a bilingual
Log in link in its header and footer. Password-reset and password-login checks
remain pending; keep that verification status explicit. GitHub commits do not
automatically publish that website.

## Android download and installation

The portal links to `/downloads/SafeNest-0.4.0-debug.apk`. This is the existing
Library development artifact, not a new release build. Its SHA-256 is
`09b18a7149c70bbdb707d9328d85f063d197eb916d05c31075b86bba6902d77c`.
It requires Android 8.0 or newer and is 18,728,260 bytes. APK bytes are distributed
by the existing Site; no signing keys are stored here.

Open the portal on an Android phone, download and open the APK, complete
Android's installation prompt, then use the app's Account icon to log in with
the same confirmed SafeNest credentials. An update must use the same signing
certificate as the installed app. Windows and iPhone builds are not available.

The 0.4.0 source and verification notes describe a paid-period requirement for
protection. Checkout and payment-to-entitlement issuance are not connected;
the live `protection_entitlements` table had no rows on 5 October 2026. The
portal therefore does not claim that login/profile saving activates protection.
Device validation remains separate from these web account checks.

The account HTML's Content Security Policy allows only this Supabase project.
If changing project, update that policy as well. Keep third-party scripts off
the account page because email verification uses the browser-only implicit flow.
The Supabase SDK consumes the token fragment; referrers are disabled. Serve
HTTPS in production. A local test origin must be explicitly added in Supabase
before testing email callbacks locally; remove it from production when done.

## 4. Connect GitHub to Supabase (optional for login)

GitHub holds code and migrations; it does not create a working login by itself.

In **Supabase → Project Settings → Integrations → GitHub Integration**:

1. Authorize the Supabase GitHub app for the intended repository.
2. Select `ShaonInTheCloud/ShaonInTheCloud`.
3. Set **Working directory** to `safenest` (the parent of `supabase/`).
4. Use `main` as production branch when the reviewed draft has been merged.
5. Keep automatic branching off unless you deliberately want paid previews.
6. Enable production deployment only after the migration has been reviewed and
   its history agrees with the hosted database.

Production deployments can apply migrations, declared Edge Functions and
storage buckets. Auth/URL/SMTP settings must still be configured in the dashboard;
the integration ignores those settings by default for production. A dedicated
SafeNest repository can be used later by moving this folder's contents into its
root and setting Working directory to `.`.

## 5. Connect mysafenestbd.com

The domain is attached to the existing Sites project and serves HTTPS. The
previously issued DNS records are recorded below for reference; preserve the
current working DNS rather than reapplying these as a new migration:

| Type | Name / Host | Value |
| --- | --- | --- |
| A | @ | 162.159.143.30 |
| A | @ | 172.66.3.26 |
| TXT | _openai-site-verification | openai-site-verification=BS9nb_7-m3p5H8pR5oDWvJikvIf6K2-stwnepAe9vZ4 |
| TXT | _cf-custom-hostname | 18a479d9-fbfa-466a-b8b4-e48929a8f1e0 |

Use the default TTL (1 hour is fine). Preserve the existing NS and SOA records.
Use these short Host values if the registrar appends `.mysafenestbd.com`.
These records were returned by Sites for this exact domain; refresh domain
status after saving to confirm DNS validation and HTTPS issuance. This does not
configure `www`, email delivery DNS, or a custom Supabase API domain.

## Verification and remaining launch checks

`npm test` runs the actual profile migration on embedded PostgreSQL (PGlite),
with Supabase roles and `auth.uid()` simulated. It checks anonymous denial,
cross-user access denial, owner-only writes, immutable ownership/timestamps,
field validation and deletion cascade. Unit tests also check account/recovery
state and rejection of stale profile responses after an account switch.

These tests do not verify the hosted Auth configuration, email delivery,
browser rendering, deployed callback URLs or an actual device. Before enabling
accounts for visitors, test with two owner-controlled email addresses: confirm
signup, log in, save profile, reset a password, log out, and verify that account A
cannot fetch account B's row through the API. The owner-controlled signup/confirmation/profile test succeeded on 5 October
2026; recovery and password-login tests remain pending.

On 24 September 2026: all 7 local tests passed, including the embedded
PostgreSQL isolation test; the account bundle built successfully with the
unconfigured account forms disabled. Hosted verification is still pending.

Official references:
- https://supabase.com/docs/guides/auth/passwords
- https://supabase.com/docs/guides/auth/auth-smtp
- https://supabase.com/docs/guides/database/postgres/row-level-security
- https://supabase.com/docs/guides/deployment/branching/github-integration
- https://supabase.com/docs/guides/getting-started/api-keys

Hosted verification on 24 September 2026: `public.profiles` exists with RLS
and three owner-scoped policies; anonymous SELECT and authenticated owner-ID
UPDATE grants are absent; Supabase Security Advisor reports no lints. The historical check above predates the owner-controlled live test on
5 October 2026; it does not imply that recovery/password-login are verified.
