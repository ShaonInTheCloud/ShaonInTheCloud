# Enforcing security headers on mysafenestbd.com

Live check on 12 October 2026 (`node scripts/check-live-security.mjs`): `/`, `/account`, `/account.html` and
`/delete-account.html` all returned HTTP 200 **without** HSTS, `X-Content-Type-Options`, an enforced CSP
(`frame-ancestors`, `object-src`), and the account pages were served `Cache-Control: public` with no `no-referrer`.
The host ignores `dist/_headers`; only the HTML meta CSP applies. The domain is served through Cloudflare, so the
same policy can be enforced there.

`npm run build` now also writes `dist/cloudflare-security-headers-worker.js`, generated from the same policy as `_headers`
(tested in `tests/cloudflare-worker.test.mjs`).

Owner steps (Cloudflare dashboard, zone `mysafenestbd.com`):
1. Workers & Pages → Create Worker → paste `dist/cloudflare-security-headers-worker.js` → Deploy.
2. Worker → Settings → Domains & Routes → add routes `mysafenestbd.com/*` (and `www.mysafenestbd.com/*` if used).
3. Run `node scripts/check-live-security.mjs`; all four paths must print `PASS`.
4. Re-run step 3 after every site deployment, and rebuild/redeploy the Worker if `scripts/security-headers.mjs` changes.

Rollback: remove the route. The Worker only adds response headers; it does not change page content.
