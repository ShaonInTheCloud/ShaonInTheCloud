import { readdir, readFile, writeFile } from 'node:fs/promises';

const origin = 'https://mysafenestbd.com';
const descriptions = {
  index: 'SafeNest is an Android app in development with local website filtering, optional app limits, and English and Bangla support.',
  'how-it-works': 'Learn how SafeNest uses local DNS filtering and optional Android app limits, including permissions, setup and coverage limits.',
  pricing: 'View planned SafeNest prices: ৳379 monthly, ৳999 quarterly and ৳3,799 annually. Checkout and automatic charging are not open.',
  'our-story': 'Learn about SafeNest and its work on practical digital boundaries for people in Bangladesh.',
  campaign: 'Explore practical steps for creating digital boundaries with SafeNest. The Android app is still in development.',
  contact: 'Contact SafeNest through a private account support request for app, billing, privacy or business questions.',
  privacy: 'Read how the SafeNest development website and Android app handle account information, local data and support messages.',
  terms: 'Read the current SafeNest development terms, permissions, service limits and subscription status.',
  support: 'Find SafeNest Android setup guidance, permission explanations, troubleshooting and release information.'
};
const escape = text => text.replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;');
const urls = [];
for (const file of await readdir('dist')) {
  if (!file.endsWith('.html')) continue;
  const slug = file.slice(0, -5), path = `dist/${file}`;
  let html = await readFile(path, 'utf8');
  html = html.replace(/<link\b[^>]*rel=["']canonical["'][^>]*>/gi, '')
    .replace(/<meta\b[^>]*name=["']description["'][^>]*>/gi, '');
  const url = `${origin}/${slug === 'index' ? '' : slug}`;
  let meta = `<link rel="canonical" href="${url}">`;
  if (descriptions[slug]) {
    meta += `<meta name="description" content="${escape(descriptions[slug])}">`;
    urls.push(url);
  } else if (!/name=["']robots["']/i.test(html)) meta += '<meta name="robots" content="noindex">';
  await writeFile(path, html.replace('</head>', `${meta}</head>`));
}
await writeFile('dist/robots.txt', `User-agent: *\nAllow: /\nSitemap: ${origin}/sitemap.xml\n`);
await writeFile('dist/sitemap.xml', `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">${urls.sort().map(url => `<url><loc>${url}</loc></url>`).join('')}</urlset>\n`);
