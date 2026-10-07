# SafeNest security hardening — 7 October 2026

## Verified production database review

Project `kflenmeizngmafwnwhgv`, organization Free. All nine public tables have RLS enabled. Profile policies restrict rows to the authenticated owner; UPDATE checks both existing and new ownership. Profile ID is not customer-editable. Entitlements permit owner reads and no customer writes. Operator catalogue access depends on server-owned app_metadata, not user_metadata.

A read-only, rolled-back database session under the authenticated role with a synthetic non-owner JWT subject returned zero profiles, entitlements and admin domain rows. Privilege checks denied profile-ID reassignment, entitlement insertion and anonymous profile reads. No user account or paid entitlement was created. This verifies database isolation, not a fresh browser or Android authentication flow.

Security advisor: zero errors, one warning `auth_leaked_password_protection`. Current Free tier cannot enable leaked-password protection; Supabase documentation requires Pro or higher. No plan upgrade was made.

## Turnstile preparation

Created a managed Cloudflare Turnstile widget named “SafeNest account authentication”, public sitekey `0x4AAAAAAFPyYH0-GfSghhqT`. Authorized hosts: mysafenestbd.com (including its subdomains) and safenest-bangladesh.kabirmdhumaun23.chatgpt.site. Its secret remains in Cloudflare and is absent from source, build and this record.

Website code supports distinct login, signup, password-reset and deletion challenge tokens. Missing/expired challenges fail closed when enabled; retries reset tokens. Supabase will validate CAPTCHA; client checks alone are not protection against direct API traffic. Account deletion forwards the challenge to Supabase password reauthentication, preserves verified identity, revokes sessions before deletion, and rejects failed reauthentication.

**CAPTCHA enforcement is NOT active.** `SAFENEST_AUTH_CAPTCHA_ENABLED=false` is the current rollout setting. The disabled site does not load Turnstile or impose a client-only challenge. Do not mark bot protection complete from widget creation or source tests.

Activation requires:

1. Supabase Auth configuration: choose Turnstile and enter the widget secret through server-side Auth settings. Current connected Supabase tools cannot read/update Auth configuration. The owner authorized browser fallback and routine development access; dashboard sign-in is waiting on owner Google passkey verification and is paused at the owner's request. No further development permission is needed for this work.
2. Android compatibility: SubscriptionClient.kt currently posts email/password to /auth/v1/token without gotrue_meta_security.captcha_token. Implement and test a challenge/token flow for both initial verification and re-verification before enforcing project-wide CAPTCHA. The existing downloadable 0.4.0 APK has not been changed or validated for this flow.
3. Verify deletion reauthentication, then deploy the enabled website and compatible APK, enable project-wide CAPTCHA, and test missing/invalid/replayed token rejection against Supabase directly as well as successful website/app authentication. Do not leave a proxy-only check while direct Auth remains bypassable.
4. Re-run confirmation, recovery, signout and deletion with disposable owned accounts, and update privacy copy for active Cloudflare challenge processing.

## Website policy and hosting limitation

Observed production HTTPS serving, but current static responses lack the prepared CSP/HSTS/nosniff/frame-denial/no-store headers. The host currently ignores dist/_headers. The new build therefore embeds a CSP in all eleven HTML pages to restrict script/resource origins and backend connections. Turnstile's script/frame origin is explicitly allowed. Account HTML retains its stricter meta policy.

A meta CSP does not provide frame-ancestors, HSTS, response MIME hardening or cache control. Those controls remain pending at the actual host. The connected Cloudflare account has no mysafenestbd.com zone, so its zone firewall/response transforms cannot be configured through that account. Do not migrate DNS or hosting to make these settings available without a separate migration task.

Account-wide Cloudflare enforce_twofactor is false; that does not establish whether the owner's individual account has MFA. Owner MFA enrollment/recovery-key handling remains an owner task. Supabase/GitHub administrator MFA was not changed or verified in this run.

## Checks

26 source tests passed, including expired/missing token rejection, token reset, form isolation, disabled-rollout compatibility, provider failure, deletion CAPTCHA rejection, private profiles and entitlements. Production and enabled-configuration builds are checked separately. These checks do not establish live CAPTCHA enforcement, successful browser challenges, Android compatibility or full security certification.

Primary references:
- https://supabase.com/docs/guides/auth/auth-captcha
- https://supabase.com/docs/guides/auth/password-security
- https://developers.cloudflare.com/turnstile/reference/content-security-policy/
- https://developers.cloudflare.com/turnstile/get-started/client-side-rendering/
