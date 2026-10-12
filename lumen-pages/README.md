# Lumen Pages

Cinematic website templates you can rebuild in any stack. A static site plus a
catalogue of original single-file landing pages, each with the prompt to recreate
it in React, Astro or plain HTML.

## Layout
- `templates/<slug>/` — `index.html` (the demo), `prompt.md` (rebuild spec), `meta.json`
- `src/` — product site styles, the WebGL hero, favicon, social image
- `build.mjs` — generates `dist/` (home, catalogue, detail pages, pricing, docs, 404,
  sitemap, robots), copies templates, renders thumbnails with headless Chromium,
  and fails on any broken internal link
- `site.config.json` — name, URL, price, checkout URL, contact, analytics token
- `TEMPLATE-SPEC.md` — the rules every template follows

## Build
```
npm install          # playwright, for thumbnails
npm run build        # dist/
npm run thumbs       # re-render thumbnails after changing a template
```
Set `CHROMIUM_PATH` if Playwright's browsers are not installed.

## Selling
Set `pro.checkoutUrl` in `site.config.json` to a Lemon Squeezy or Paddle checkout
link; until then every Pro button opens the waitlist mailto. Deliver the paid
bundle (all `templates/*` with `prompt.md`) as the product file on the
merchant-of-record side; the site itself stays static and keyless.

## Deploy
`dist/` is a static site. `_headers` is picked up by Cloudflare Pages and Netlify.
