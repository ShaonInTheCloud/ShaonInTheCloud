// Builds dist/ from src/, templates/ and site.config.json. No framework.
//   node build.mjs            full build (thumbnails only if missing or --thumbs)
//   node build.mjs --thumbs   re-render template thumbnails with headless Chromium
import { readFile, writeFile, mkdir, rm, cp, readdir, stat } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import path from 'node:path';

const root = path.dirname(new URL(import.meta.url).pathname);
const cfg = JSON.parse(await readFile(path.join(root, 'site.config.json'), 'utf8'));
const out = path.join(root, 'dist');
const esc = s => String(s).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');

const templates = [];
for (const slug of (await readdir(path.join(root, 'templates'))).sort()) {
  const dir = path.join(root, 'templates', slug);
  if (!(await stat(dir)).isDirectory()) continue;
  const meta = JSON.parse(await readFile(path.join(dir, 'meta.json'), 'utf8'));
  const prompt = await readFile(path.join(dir, 'prompt.md'), 'utf8');
  const html = await readFile(path.join(dir, 'index.html'), 'utf8');
  templates.push({ ...meta, slug, prompt, bytes: Buffer.byteLength(html) });
}
const categories = [...new Set(templates.map(t => t.category))];
const buyHref = cfg.pro.checkoutUrl || cfg.waitlistUrl;
const buyLabel = cfg.pro.checkoutUrl ? `Get Pro, ${cfg.pro.price}` : 'Join the Pro waitlist';

function page({ title, description, body, path: p, depth = 0, current = '' }) {
  const base = '../'.repeat(depth);
  const url = cfg.url + '/' + (p === 'index.html' ? '' : p);
  const nav = [['templates/', 'Templates'], ['pricing.html', 'Pricing'], ['docs.html', 'Docs']]
    .map(([href, label]) => `<a href="${base}${href}"${current === label ? ' aria-current="page"' : ''}>${label}</a>`).join('');
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(title)}</title>
<meta name="description" content="${esc(description)}">
<link rel="canonical" href="${url}">
<meta property="og:type" content="website"><meta property="og:site_name" content="${esc(cfg.name)}">
<meta property="og:title" content="${esc(title)}"><meta property="og:description" content="${esc(description)}">
<meta property="og:url" content="${url}"><meta property="og:image" content="${cfg.url}/social.png">
<meta name="twitter:card" content="summary_large_image">
<meta name="theme-color" content="#0b0c14">
<link rel="icon" href="${base}favicon.svg" type="image/svg+xml">
<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght,SOFT@9..144,400..600,0..100&family=Inter+Tight:wght@400;500&family=JetBrains+Mono:wght@400&display=swap">
<link rel="stylesheet" href="${base}site.css">
</head>
<body>
<a class="skip" href="#main">Skip to content</a>
<header class="top"><a class="brand" href="${base}"><i aria-hidden="true"></i><span>${esc(cfg.name)}</span></a><nav>${nav}<a class="button primary" href="${cfg.pro.checkoutUrl ? base + 'pricing.html' : buyHref}">${cfg.pro.checkoutUrl ? 'Get Pro' : 'Waitlist'}</a></nav></header>
<main id="main">
${body}
</main>
<footer class="site"><span>© ${new Date().getFullYear()} ${esc(cfg.name)}. ${esc(cfg.owner)}.</span><nav><a href="${base}templates/">Templates</a><a href="${base}pricing.html">Pricing</a><a href="${base}docs.html">Docs</a><a href="${base}docs.html#licence">Licence</a><a href="mailto:${cfg.contact}">${cfg.contact}</a></nav></footer>
<script src="${base}light.js" defer></script>
${cfg.analyticsToken ? `<script defer src="https://static.cloudflareinsights.com/beacon.min.js" data-cf-beacon='{"token": "${cfg.analyticsToken}"}'></script>` : ''}
</body>
</html>`;
}

const tile = (t, base) => `<a class="tile" href="${base}t/${t.slug}.html" data-category="${esc(t.category)}" data-free="${!!t.free}">
  <span class="tag ${t.free ? 'free' : ''}">${t.free ? 'Free' : 'Pro'}</span>
  <img src="${base}thumbs/${t.slug}.jpg" alt="${esc(t.name)} template preview" width="1200" height="750" loading="lazy">
  <figcaption><b>${esc(t.name)}</b><span>${esc(t.category)}</span></figcaption>
</a>`;

// --- pages ---------------------------------------------------------------
const home = page({ title: `${cfg.name}: cinematic website templates`, description: cfg.description, path: 'index.html', body: `
<section class="hero">
  <canvas aria-hidden="true"></canvas>
  <h1>Websites that feel lit, not laid out.</h1>
  <p class="lead">${esc(templates.length)} original landing pages where one thing moves and everything else stays quiet. Each ships as a single HTML file plus the prompt to rebuild it in React, Astro or whatever you use.</p>
  <div class="actions"><a class="button primary" href="templates/">Browse the templates</a><a class="button quiet" href="t/aurora.html">Try Aurora, it's free</a><span class="note">No account. No build step.</span></div>
</section>
<section>
  <header><h2>Pick a mood, keep the structure.</h2><p>Every template has the same bones (nav, hero, proof, product, offer, close) so you can swap one for another without rewriting your copy.</p></header>
  <div class="grid">${templates.slice(0, 6).map(t => tile(t, '')).join('')}</div>
  <p style="margin-top:1.6rem"><a class="button" href="templates/">See all ${templates.length}</a></p>
</section>
<section>
  <header><h2>How a template becomes your site.</h2></header>
  <ol class="steps">
    <li><h3>Open the demo</h3><p>It runs as-is: one file, fonts from Google, nothing to install.</p></li>
    <li><h3>Take the prompt</h3><p>Each template comes with a written spec of its tokens, layout and motion. Paste it into your coding tool to rebuild it in your stack.</p></li>
    <li><h3>Retheme in one place</h3><p>Colours, fonts and motion timings are CSS variables on <code>:root</code>. Change five lines, keep the choreography.</p></li>
  </ol>
</section>
<section>
  <header><h2>What you get with Pro.</h2></header>
  <div class="row">
    <div><p class="lead">${esc(cfg.pro.price)}, ${esc(cfg.pro.period)}. All ${templates.length} templates with their prompts and source, every template released later, and a licence to use them on unlimited client sites.</p><p><a class="button primary" href="${buyHref}">${buyLabel}</a></p></div>
    <div><p>Two templates stay free forever: Aurora and Forge. Use them to judge the quality before paying anything.</p></div>
  </div>
</section>` });

const catalogue = page({ title: `Templates, ${cfg.name}`, description: `All ${templates.length} cinematic landing page templates by category.`, path: 'templates/index.html', depth: 1, current: 'Templates', body: `
<section>
  <header><h1 style="font-size:clamp(2.4rem,6vw,4.6rem)">Templates</h1><p>${templates.length} originals. Open any demo; the free ones download straight away.</p></header>
  <div class="filters" role="group" aria-label="Filter templates"><button data-filter="all" aria-pressed="true">All</button><button data-filter="free" aria-pressed="false">Free</button>${categories.map(c => `<button data-filter="${esc(c)}" aria-pressed="false">${esc(c)}</button>`).join('')}</div>
  <div class="grid">${templates.map(t => tile(t, '../')).join('')}</div>
</section>` });

const detailPages = templates.map(t => {
  const locked = !t.free && !cfg.pro.checkoutUrl ? true : !t.free;
  const promptBlock = t.free
    ? `<pre class="prompt" id="prompt-text">${esc(t.prompt)}</pre><p style="margin-top:0.8rem"><button class="button" data-copy="#prompt-text" data-label="Copy the prompt">Copy the prompt</button></p>`
    : `<div class="locked"><pre class="prompt">${esc(t.prompt.split('\n').slice(0, 14).join('\n'))}\n…</pre></div><p style="margin-top:0.8rem">The full prompt and source are part of Pro.</p>`;
  return { path: `t/${t.slug}.html`, html: page({ title: `${t.name}, a ${t.category} template`, description: t.tagline, path: `t/${t.slug}.html`, depth: 1, current: 'Templates', body: `
<section>
  <div class="detail-hero">
    <div>
      <h1 style="font-size:clamp(2.4rem,6vw,4.6rem)">${esc(t.name)}</h1>
      <p class="lead">${esc(t.tagline)}</p>
      <div class="frame"><iframe src="../templates/${t.slug}/index.html" title="${esc(t.name)} live demo" loading="lazy"></iframe></div>
      <p style="margin-top:0.8rem"><a class="button" href="../templates/${t.slug}/index.html" target="_blank" rel="noopener">Open the demo full screen</a></p>
    </div>
    <aside class="buy">
      <div class="price">${t.free ? 'Free' : esc(cfg.pro.price)}${t.free ? '' : `<small>with Pro, ${esc(cfg.pro.period)}</small>`}</div>
      <ul><li>Single HTML file, ${Math.round(t.bytes / 1024)} KB, no build step</li><li>The rebuild prompt (tokens, layout, motion)</li><li>Reduced-motion and phone-width safe</li><li>${t.free ? 'Use it anywhere, credit appreciated' : 'Unlimited personal and client use'}</li></ul>
      <div class="actions">${t.free
        ? `<a class="button primary" href="../templates/${t.slug}/index.html" download="${t.slug}.html">Download the HTML</a><a class="button" href="../templates/${t.slug}/prompt.md" download>Download the prompt</a>`
        : `<a class="button primary" href="${buyHref}">${buyLabel}</a><a class="button quiet" href="../t/aurora.html">Try a free one first</a>`}</div>
      <dl class="facts" style="margin-top:1.4rem"><dt>Category</dt><dd>${esc(t.category)}</dd><dt>Tone</dt><dd>${esc(t.tone)}</dd><dt>Fonts</dt><dd>${esc((t.fonts || []).join(', '))}</dd><dt>Sections</dt><dd>${esc((t.sections || []).join(', '))}</dd></dl>
    </aside>
  </div>
</section>
<section>
  <header><h2>The prompt</h2><p>What to paste into your coding tool to rebuild this page in your own stack.</p></header>
  ${promptBlock}
</section>` }) };
});

const pricing = page({ title: `Pricing, ${cfg.name}`, description: 'Two free templates. Pro is a one-time payment for everything.', path: 'pricing.html', current: 'Pricing', body: `
<section>
  <header><h1 style="font-size:clamp(2.4rem,6vw,4.6rem)">Pricing</h1><p>One payment. No subscription, no seats, no per-site fees.</p></header>
  <div class="plans">
    <div class="plan"><h3>Free</h3><div class="price">€0</div><ul><li>Aurora and Forge, with their prompts</li><li>Single-file HTML, retheme with CSS variables</li><li>Use on your own and client sites</li></ul><a class="button" href="templates/">Open the free templates</a></div>
    <div class="plan featured"><h3>Pro</h3><div class="price">${esc(cfg.pro.price)}<small>${esc(cfg.pro.period)}</small></div><ul><li>All ${templates.length} templates, prompts and source</li><li>Every template released later, no extra charge</li><li>Unlimited personal and client sites</li><li>Email support within two working days</li></ul><a class="button primary" href="${buyHref}">${buyLabel}</a>${cfg.pro.checkoutUrl ? '' : '<p style="margin:1rem 0 0;font-size:0.92rem">Checkout opens soon. Join the waitlist and you will get the launch price.</p>'}</div>
  </div>
</section>
<section>
  <header><h2>Questions people ask before paying.</h2></header>
  <details><summary>Can I use a template for a client?</summary><p>Yes. Pro covers unlimited personal and client sites. You cannot resell or redistribute the templates themselves, or offer them as part of a template marketplace.</p></details>
  <details><summary>Do I need a build tool or framework?</summary><p>No. Each template is one HTML file with inline CSS and JS, and fonts from Google Fonts. The prompt tells a coding tool how to rebuild it in React, Astro, Vue or anything else.</p></details>
  <details><summary>What is the difference between the demo and what I buy?</summary><p>The demo is the same file. Pro adds the written prompt for each template, the organised source with comments, all future templates, and a licence that allows client work.</p></details>
  <details><summary>Refunds?</summary><p>If a template does not work as shown within 14 days of purchase, write to ${esc(cfg.contact)} and you get your money back. Digital goods are otherwise non-refundable, as stated at checkout.</p></details>
  <details><summary>Who makes these?</summary><p>${esc(cfg.owner)}. Templates are original work; invented brands in the demos are fictional.</p></details>
</section>` });

const docs = page({ title: `Docs, ${cfg.name}`, description: 'How to use, retheme and rebuild a Lumen Pages template.', path: 'docs.html', current: 'Docs', body: `
<section class="docs">
  <aside><a href="#start">Start</a><a href="#retheme">Retheme</a><a href="#rebuild">Rebuild with the prompt</a><a href="#motion">Motion and accessibility</a><a href="#licence">Licence</a></aside>
  <article>
    <h2 id="start">Start</h2>
    <p>Download a template's HTML and open it in a browser. That is the whole install. To put it on a site, upload the file to any static host (Netlify, Cloudflare Pages, GitHub Pages, Vercel) or drop it into your project's public folder.</p>
    <pre><code>npx serve .        # preview locally
# or just double-click the file</code></pre>
    <h2 id="retheme">Retheme</h2>
    <p>Every colour, font and timing is a CSS custom property at the top of the file. Change those, nothing else.</p>
    <pre><code>:root {
  --bg: #0b0c14;        /* page base */
  --accent: #fff1d0;    /* the one light */
  --font-display: "Fraunces", serif;
}</code></pre>
    <p>Replace the invented brand copy by searching the file for the brand name; it appears in the nav, hero, footer and the <code>&lt;title&gt;</code>.</p>
    <h2 id="rebuild">Rebuild with the prompt</h2>
    <p>Each template's prompt describes tokens, layout, and motion precisely enough for a coding tool to recreate it. Paste it into Claude Code, Cursor, Codex or similar and add one line about your stack:</p>
    <pre><code>Rebuild this in Next.js 15 with Tailwind. Keep the motion exactly as described.
[paste prompt.md]</code></pre>
    <p>Keep the original HTML open beside the result and compare the hero first; that is where the template earns its keep.</p>
    <h2 id="motion">Motion and accessibility</h2>
    <p>All motion is gated behind <code>prefers-reduced-motion: no-preference</code>. With reduced motion on, every page shows its complete final state with nothing moving. Canvas scenes pause when off-screen or when the tab is hidden. Text contrast meets WCAG AA; focus is visible; each page has one <code>h1</code> and proper landmarks.</p>
    <h2 id="licence">Licence</h2>
    <p><b>Free templates</b> (Aurora, Forge): use on any site, personal or client, modified or not. Credit is appreciated, not required. You may not sell or redistribute the template files themselves.</p>
    <p><b>Pro</b>: one licence per buyer covers unlimited personal and client sites and all future templates. You may not resell, sublicense, or redistribute the templates, prompts or source, or include them in another template product. Demo brand names and copy are fictional and are not for reuse as real brands.</p>
    <p>Questions: <a href="mailto:${cfg.contact}">${cfg.contact}</a>.</p>
  </article>
</section>` });

const notFound = page({ title: `Page not found, ${cfg.name}`, description: 'That page is not here.', path: '404.html', body: `
<section><h1 style="font-size:clamp(2.4rem,6vw,4.6rem)">That page is not here.</h1><p class="lead">The link may be old or mistyped.</p><p><a class="button primary" href="/">Go to the home page</a> <a class="button" href="/templates/">Browse templates</a></p></section>` });

// --- write ---------------------------------------------------------------
await rm(out, { recursive: true, force: true });
await mkdir(path.join(out, 't'), { recursive: true });
await mkdir(path.join(out, 'templates'), { recursive: true });
await mkdir(path.join(out, 'thumbs'), { recursive: true });
await writeFile(path.join(out, 'index.html'), home);
await writeFile(path.join(out, 'templates', 'index.html'), catalogue);
await writeFile(path.join(out, 'pricing.html'), pricing);
await writeFile(path.join(out, 'docs.html'), docs);
await writeFile(path.join(out, '404.html'), notFound.replaceAll('href="/', 'href="/'));
for (const d of detailPages) await writeFile(path.join(out, d.path), d.html);
await cp(path.join(root, 'src', 'site.css'), path.join(out, 'site.css'));
await cp(path.join(root, 'src', 'light.js'), path.join(out, 'light.js'));
await cp(path.join(root, 'src', 'favicon.svg'), path.join(out, 'favicon.svg'));
if (existsSync(path.join(root, 'src', 'social.png'))) await cp(path.join(root, 'src', 'social.png'), path.join(out, 'social.png'));
for (const t of templates) {
  await mkdir(path.join(out, 'templates', t.slug), { recursive: true });
  await cp(path.join(root, 'templates', t.slug, 'index.html'), path.join(out, 'templates', t.slug, 'index.html'));
  if (t.free) await cp(path.join(root, 'templates', t.slug, 'prompt.md'), path.join(out, 'templates', t.slug, 'prompt.md'));
}
const pages = ['', 'templates/', 'pricing.html', 'docs.html', ...templates.map(t => `t/${t.slug}.html`), ...templates.map(t => `templates/${t.slug}/`)];
await writeFile(path.join(out, 'sitemap.xml'), `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${pages.map(p => `  <url><loc>${cfg.url}/${p}</loc></url>`).join('\n')}\n</urlset>\n`);
await writeFile(path.join(out, 'robots.txt'), `User-agent: *\nAllow: /\n\nSitemap: ${cfg.url}/sitemap.xml\n`);
await writeFile(path.join(out, '_headers'), `/*\n  X-Content-Type-Options: nosniff\n  Referrer-Policy: strict-origin-when-cross-origin\n  X-Frame-Options: SAMEORIGIN\n  Permissions-Policy: camera=(), microphone=(), geolocation=()\n`);

// --- thumbnails ------------------------------------------------------------
const wantThumbs = process.argv.includes('--thumbs') || templates.some(t => !existsSync(path.join(root, 'thumbs', `${t.slug}.jpg`)));
if (wantThumbs) {
  const { chromium } = await import('playwright');
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined });
  const ctx = await browser.newContext({ viewport: { width: 1200, height: 750 }, deviceScaleFactor: 1 });
  await mkdir(path.join(root, 'thumbs'), { recursive: true });
  for (const t of templates) {
    const p = await ctx.newPage();
    await p.goto('file://' + path.join(root, 'templates', t.slug, 'index.html'), { waitUntil: 'load' });
    await p.waitForTimeout(2600);
    await p.screenshot({ path: path.join(root, 'thumbs', `${t.slug}.jpg`), type: 'jpeg', quality: 82 });
    await p.close();
  }
  await browser.close();
}
await cp(path.join(root, 'thumbs'), path.join(out, 'thumbs'), { recursive: true });

// --- link check ------------------------------------------------------------
const problems = [];
for (const rel of (await readdir(out, { recursive: true })).filter(f => f.endsWith('.html'))) {
  const html = await readFile(path.join(out, rel), 'utf8');
  for (const [, href] of html.matchAll(/\s(?:href|src)="([^"#?]+)/g)) {
    if (/^(https?:|mailto:|data:|\/\/)/.test(href)) continue;
    const target = href.startsWith('/') ? path.join(out, href) : path.resolve(path.dirname(path.join(out, rel)), href);
    const file = target.endsWith('/') ? path.join(target, 'index.html') : target;
    if (!existsSync(file) && !existsSync(path.join(target, 'index.html'))) problems.push(`${rel}: ${href}`);
  }
}
if (problems.length) throw new Error('Broken links:\n  ' + problems.join('\n  '));
console.log(`Built ${pages.length} pages, ${templates.length} templates${wantThumbs ? ', thumbnails rendered' : ''}, links verified.`);
