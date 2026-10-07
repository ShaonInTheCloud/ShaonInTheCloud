# Contact and support rollout · 7 October 2026

SafeNest now has an English/Bangla contact page at https://mysafenestbd.com/contact and a private request area at https://mysafenestbd.com/account#support. Footer navigation, site search, help, privacy and account pages link to it. The mobile footer keeps its compact payment layout.

## Working channels

Confirmed account holders can send app, account, billing, privacy/deletion and business enquiries. Requests have subjects, reference IDs, conversations and open/closed status. Customers return to My SafeNest to read replies; no email notifications or response-time guarantee is claimed. Unauthenticated visitors cannot submit or read requests. A person unable to sign in can try the existing password-reset flow, but an independent email recovery/support channel is still pending.

Support operators use the same account page. The business owner's connected, verified inbox was configured in the private database operator allowlist, without publishing it in website copy or committing the address here. No matching SafeNest account existed at configuration time. The owner needs to create/confirm a SafeNest account using that inbox, then sign in; the support area will display the staff inbox automatically. Configuration alone does not prove that the owner has logged in or begun responding. No customer notification email was sent.

## Business email setup still required

Proposed roles, **not active mailboxes**:

| Address | Purpose |
| --- | --- |
| support@mysafenestbd.com | App/account support and access problems |
| billing@mysafenestbd.com | Subscription, cancellation and payment questions |
| privacy@mysafenestbd.com | Personal-data and deletion requests |
| hello@mysafenestbd.com | General business enquiries |

At inspection, authoritative nameservers were `ns01.dns.nexus` and `ns02.dns.nexus`; the apex had no MX records. Domain registration mail identifies Register.Domains. The domain is absent from the connected Cloudflare account, so its DNS and email routing cannot be configured through that connector. The authentication sender `no-reply@auth.mysafenestbd.com` is not a support mailbox.

Complete setup in the account that manages this domain's DNS/email: select an actual mailbox service supporting receiving **and branded replies**, configure aliases to one monitored inbox, install the provider's exact MX/SPF/DKIM records and reviewed DMARC policy, and verify receiving and sending for every alias. Preserve the existing website and authentication-subdomain records; do not migrate nameservers or replace the authentication sender's configuration. No provider purchase, email account signup or new recurring expense was made. Publish mailto links only after verification. No phone number, WhatsApp, social account, seller identity or support hours were invented.

## Security and data

`support_tickets` and `support_messages` have RLS and authenticated SELECT only; direct client INSERT/UPDATE/DELETE is revoked. Message reads explicitly check ticket ownership or a current support operator. Privileged writers live in the unexposed `supportdesk` schema; public entrypoints use SECURITY INVOKER. Writers derive identity from the confirmed Auth user and a current session, check ownership, mark staff replies server-side, lock the account row and enforce five new requests and forty messages per rolling 24 hours per account. Client UUIDs make identical retries preserve one message/request. These account limits are not a per-person anti-abuse guarantee. Operator access is a fresh private allowlist lookup rather than user-editable metadata or a stale JWT role.

Messages render as text, not HTML, and are never stored in browser persistence. Account deletion cascades live support conversations. Final infrastructure backup/log retention periods remain unconfirmed. No card fields, attachments or browsing-history collection was added.

The first migration attempt was rejected by automatic approval review over the original message policy's implicit dependence on ticket RLS. The policy was changed to include an explicit ownership/operator predicate, privacy tests rerun, and the revised migration accepted. Local PostgreSQL tests verify known-ticket-ID isolation, forged-staff denial, operator replies/status, idempotency, limits, revoked-session denial and deletion. The live database verified owner reads/retries, cross-account read/write denial and revoked-session denial using rolled-back fixtures. Security advisor found no new database warnings; the existing leaked-password protection warning remains.

The existing checkout and automatic-debit gates remain closed. Contact changes do not establish merchant approval, Android trial QA or payment availability.
