// Launch-readiness pass over dist/: canonical + social meta, robots.txt,
// sitemap.xml, the 404 page, optional cookieless analytics, and a hard
// internal link check that fails the build. Runs last in build.mjs.
import { readFile, writeFile, readdir, stat } from 'node:fs/promises';

export const siteOrigin = (process.env.SAFENEST_SITE_URL || 'https://mysafenestbd.com').replace(/\/$/, '');
// Pages that are personal, transactional or generated for signed-in users
// stay out of search engines and the sitemap.
const privatePages = new Set(['account.html', 'delete-account.html', 'checkout.html', '404.html']);
const escapeAttr = v => v.replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;');
const stripTags = v => v.replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim();

function pageUrl(file) {
  return file === 'index.html' ? `${siteOrigin}/` : `${siteOrigin}/${file}`;
}

function englishOnly(html) {
  // Pages carry English and Bengali copies of each string; descriptions use English.
  return html.replace(/<span class="translation-bn"[^>]*>[\s\S]*?<\/span>/g, '');
}

export function socialMeta(file, html) {
  const title = stripTags((html.match(/<title>([\s\S]*?)<\/title>/) || [, 'SafeNest'])[1]);
  let description = (html.match(/<meta name="description" content="([^"]*)"/) || [])[1];
  if (!description && file === 'account.html') description = 'Sign in to My SafeNest to manage your account, protection period and support requests.';
  if (!description) {
    const firstParagraph = (englishOnly(html).match(/<main[\s\S]*?<p[^>]*>([\s\S]*?)<\/p>/) || [])[1] || '';
    description = stripTags(firstParagraph).slice(0, 155).replace(/\s\S*$/, '') || 'SafeNest: gambling and adult-site blocking for Android, built for Bangladesh.';
  }
  const url = pageUrl(file);
  const image = `${siteOrigin}/assets/brand/social-preview.jpg`;
  const tags = [
    file === '404.html' ? '<base href="/">' : `<link rel="canonical" href="${url}">`,
    html.includes('name="description"') ? '' : `<meta name="description" content="${escapeAttr(description)}">`,
    privatePages.has(file) && !html.includes('name="robots"') ? '<meta name="robots" content="noindex, nofollow">' : '',
    '<meta property="og:type" content="website">',
    '<meta property="og:site_name" content="SafeNest">',
    `<meta property="og:title" content="${escapeAttr(title)}">`,
    `<meta property="og:description" content="${escapeAttr(description)}">`,
    `<meta property="og:url" content="${url}">`,
    `<meta property="og:image" content="${image}">`,
    '<meta property="og:image:width" content="1200">',
    '<meta property="og:image:height" content="630">',
    '<meta property="og:image:alt" content="SafeNest: keep unwanted sites out of your day">',
    '<meta name="twitter:card" content="summary_large_image">',
    `<meta name="twitter:title" content="${escapeAttr(title)}">`,
    `<meta name="twitter:description" content="${escapeAttr(description)}">`,
    `<meta name="twitter:image" content="${image}">`,
    '<link rel="manifest" href="site.webmanifest">',
    // Fonts load from Google; open the connections before the CSS asks for them.
    html.includes('rel="preconnect"') ? '' : '<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>'
  ].filter(Boolean).join('');
  return tags;
}

export function analyticsSnippet() {
  // Cloudflare Web Analytics is cookieless and needs no consent banner.
  // It is only injected when a beacon token is configured for the build.
  const token = process.env.CF_ANALYTICS_TOKEN || '';
  if (!/^[0-9a-f]{32}$/.test(token)) return '';
  return `<script defer src="https://static.cloudflareinsights.com/beacon.min.js" data-cf-beacon='{"token": "${token}"}'></script>`;
}

export async function checkInternalLinks(dir) {
  const files = (await readdir(dir)).filter(f => f.endsWith('.html'));
  const ids = new Map();
  const problems = [];
  for (const file of files) {
    const html = await readFile(`${dir}/${file}`, 'utf8');
    ids.set(file, new Set([...html.matchAll(/\sid="([^"]+)"/g)].map(m => m[1])));
  }
  for (const file of files) {
    const html = await readFile(`${dir}/${file}`, 'utf8');
    for (const [, attr, raw] of html.matchAll(/\s(href|src|poster)="([^"]*)"/g)) {
      const value = raw.trim();
      if (!value || /^(https?:|mailto:|tel:|data:|javascript:|#$)/.test(value)) continue;
      if (value.startsWith('#')) {
        if (!ids.get(file).has(value.slice(1))) problems.push(`${file}: missing anchor ${value}`);
        continue;
      }
      const [pathPart, hash] = value.split('#');
      let target = pathPart.replace(/^\.\//, '').replace(/\?.*$/, '');
      if (target === '' || target === '/' || target === '.') target = 'index.html';
      if (target.startsWith('/')) target = target.slice(1);
      try { await stat(`${dir}/${target}`); } catch { problems.push(`${file}: broken ${attr} ${value}`); continue; }
      if (hash && target.endsWith('.html') && ids.has(target) && !ids.get(target).has(hash)) problems.push(`${file}: missing anchor ${value}`);
    }
  }
  return problems;
}

export async function finalizeSite(dir = 'dist') {
  const files = (await readdir(dir)).filter(f => f.endsWith('.html'));
  const analytics = analyticsSnippet();
  if (analytics) {
    const privacy = await readFile(`${dir}/privacy.html`, 'utf8');
    if (!privacy.includes('Cloudflare Web Analytics')) throw new Error('CF_ANALYTICS_TOKEN is set but privacy.html does not describe Cloudflare Web Analytics.');
  }
  for (const file of files) {
    let html = await readFile(`${dir}/${file}`, 'utf8');
    if (!html.includes('rel="canonical"')) html = html.replace('</head>', `${socialMeta(file, html)}</head>`);
    if (analytics && !privatePages.has(file) && !html.includes('cloudflareinsights')) html = html.replace('</body>', `${analytics}</body>`);
    await writeFile(`${dir}/${file}`, html);
  }
  const indexable = files.filter(f => !privatePages.has(f)).sort((a, b) => (a === 'index.html' ? -1 : b === 'index.html' ? 1 : a.localeCompare(b)));
  const today = new Date().toISOString().slice(0, 10);
  const urls = indexable.map(f => `  <url><loc>${pageUrl(f)}</loc><lastmod>${today}</lastmod></url>`).join('\n');
  await writeFile(`${dir}/sitemap.xml`, `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls}\n</urlset>\n`);
  // Private pages carry noindex; a Disallow here would stop crawlers reading it.
  await writeFile(`${dir}/robots.txt`, `User-agent: *\nAllow: /\n\nSitemap: ${siteOrigin}/sitemap.xml\n`);
  await writeFile(`${dir}/site.webmanifest`, JSON.stringify({
    name: 'SafeNest', short_name: 'SafeNest', start_url: '/', display: 'browser',
    background_color: '#fffdfd', theme_color: '#631332',
    icons: [{ src: 'assets/brand/apple-touch-icon.png', sizes: '180x180', type: 'image/png' }]
  }));
  const problems = await checkInternalLinks(dir);
  if (problems.length) {
    throw new Error(`Broken internal links in ${dir}:\n  ${problems.join('\n  ')}`);
  }
  console.log(`Site: ${indexable.length} pages in sitemap, ${files.length} pages with social meta, links verified${analytics ? ', analytics on' : ''}.`);
}
