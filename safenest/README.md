# SafeNest account integration

Prepared for Supabase project `kflenmeizngmafwnwhgv`. The private profiles
migration is now applied in the hosted database (version `20260924212500`).
The account page is published as an unlinked pilot at
`https://safenest-bangladesh.kabirmdhumaun23.chatgpt.site/account.html`.
Supabase Auth redirects and custom SMTP still need configuration before
inviting the public to register.
The GitHub repository currently also holds the owner's profile README, so all
SafeNest files live under `safenest/`.

Included: email signup and confirmation, login, password reset, logout on the
current device, and private name/language preferences. The interface uses white
and baby pink, English above smaller Bangla, with a full Bangla option.

Supabase Auth manages credentials and users. PostgreSQL row-level security
restricts each profile to its owner. Accounts do not activate device blocking.
The Android blocking prototype remains a separate application.

## 1. Configure Supabase Auth

Open your [Supabase project](https://supabase.com/dashboard/project/kflenmeizngmafwnwhgv).

1. In **Authentication → Sign In / Providers**, enable Email and keep email
   confirmation on. Set minimum password length to 10.
2. In **Authentication → URL Configuration**, use the currently working site URL:
   `https://safenest-bangladesh.kabirmdhumaun23.chatgpt.site`.
3. Add these exact Redirect URLs:
   - `https://safenest-bangladesh.kabirmdhumaun23.chatgpt.site/account.html`
   - `https://safenest-bangladesh.kabirmdhumaun23.chatgpt.site/account.html?mode=recovery`
   - `https://mysafenestbd.com/account.html`
   - `https://mysafenestbd.com/account.html?mode=recovery`
4. Under **Authentication → Email / SMTP Settings**, connect an email provider
   before accepting public signups. Supabase's default sender is only suitable
   for testing with project-team addresses. Enter its SMTP credentials in the
   dashboard, not in GitHub. Keep confirmation and recovery links enabled.
5. In **Connect** or **Settings → API Keys**, copy the **Publishable key**
   beginning `sb_publishable_`. The account page does not need any secret key.

After the custom domain works over HTTPS, change the Site URL to
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

Site version 5 now contains the compiled account page at `/account.html` and
the host headers it needs. The homepage does not link to this pilot yet because
confirmation/reset emails are restricted until Supabase custom SMTP and
redirects are configured. After enabling public signups, add an Account link
and update the homepage privacy/account-availability copy in the existing
Sites project. GitHub commits do not automatically publish that website.

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

The domain is already attached to the existing Sites project, but DNS and SSL
validation are pending. In the registrar's **DNS Records → Add DNS Record**, add:

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
cannot fetch account B's row through the API. No real signup emails were sent while preparing this integration.

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
UPDATE grants are absent; Supabase Security Advisor reports no lints. No
real-user authentication test has been completed yet.
