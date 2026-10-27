import { build } from 'esbuild';
import { mkdir, copyFile, writeFile, cp, rm, readdir, readFile } from 'node:fs/promises';
import { securityHeaders, contentSecurityPolicy } from './security-headers.mjs';
import { workerSource } from './cloudflare-worker.mjs';

const projectUrl = process.env.SUPABASE_URL || 'https://kflenmeizngmafwnwhgv.supabase.co';
const key = process.env.SUPABASE_PUBLISHABLE_KEY || 'sb_publishable_jt2VeNCAATz3iEiebx2Kog_ZiZVL7Vl';
const url = new URL(projectUrl);
const captchaEnabled = process.env.SAFENEST_AUTH_CAPTCHA_ENABLED === 'true';
const captchaSitekey = process.env.TURNSTILE_SITEKEY || '0x4AAAAAAFPyYH0-GfSghhqT';
if (captchaEnabled && !/^0x[0-9A-Za-z_-]{10,64}$/.test(captchaSitekey)) throw new Error('A production Turnstile sitekey is required.');
if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash) {
  throw new Error('SUPABASE_URL must be an HTTPS project URL without credentials.');
}
if (key && !/^sb_publishable_[A-Za-z0-9_-]+$/.test(key)) {
  throw new Error('Use a Supabase publishable key starting sb_publishable_.');
}
// A clean build cannot accidentally retain obsolete checkout or account assets.
await rm('dist', { recursive: true, force: true });
await mkdir('dist', { recursive: true });
await cp('website', 'dist', { recursive: true });
for (const file of ['account.html', 'account.css']) {
  await copyFile(`web/${file}`, `dist/${file}`);
}
await build({
  entryPoints: ['web/account.js'], outfile: 'dist/account.js',
  bundle: true, minify: true, format: 'esm', target: ['es2022'],
  define: { __SUPABASE_URL__: JSON.stringify(url.origin), __SUPABASE_KEY__: JSON.stringify(key), __AUTH_CAPTCHA_ENABLED__: JSON.stringify(captchaEnabled), __TURNSTILE_SITEKEY__: JSON.stringify(captchaSitekey) }
});
// Apply these headers through the website host. The HTML also carries its CSP.
await writeFile('dist/_headers', securityHeaders(url.origin));
// Deployable to Cloudflare in front of the host that ignores _headers; see docs/security-headers-cloudflare.md.
await writeFile('dist/cloudflare-security-headers-worker.js', workerSource(securityHeaders(url.origin)));
await writeFile('dist/cloudflare-static-assets-worker.js', workerSource(securityHeaders(url.origin), { assets: true }));
await writeFile('dist/.assetsignore', 'cloudflare-security-headers-worker.js\ncloudflare-static-assets-worker.js\n');
console.log(key ? 'Built account page. Live Auth and database verification still required.' : 'Built setup preview. Account forms are disabled until a publishable key is configured.');
await import('./release-pages.mjs');

const playStoreUrl = process.env.SAFENEST_PLAY_STORE_URL || '';
if (playStoreUrl && !/^https:\/\/play\.google\.com\/store\/apps\/details\?id=com\.safenest\.app(&[\w=.-]*)?$/.test(playStoreUrl)) {
  throw new Error('SAFENEST_PLAY_STORE_URL must be the Play listing for com.safenest.app.');
}
export function playAnnouncement(bilingual) {
  const pair = (en, bn) => bilingual
    ? `<span data-en="${en}" data-bn="${bn}"><span class="translation-en" lang="en">${en}</span><span class="translation-bn" lang="bn">${bn}</span></span>`
    : `<span>${en}</span>`;
  return `<aside class="announcement">${pair('SafeNest for Android is on Google Play', 'SafeNest এখন Google Play-তে')}` +
    `<a href="${playStoreUrl}" rel="noopener">${pair('Get it on Google Play', 'Google Play থেকে নিন')}</a></aside>`;
}
// This static host currently ignores _headers; a meta CSP still protects HTML
// script/resource loading. It cannot replace HSTS, no-store or frame-ancestors.
const pages = [...(await readdir('dist')).filter(f => f.endsWith('.html')),
  ...(await readdir('dist/global')).filter(f => f.endsWith('.html')).map(f => `global/${f}`)];
for (const file of pages) {
  const path = `dist/${file}`;
  const base = file.includes('/') ? '../' : '';
  let html = await readFile(path, 'utf8');
  // One final brand layer covers marketing, generated service pages and Auth.
  // Keep the original page layouts; the hot-pink layer (Oct 2026) replaces the
  // liquid-pink artwork and raspberry theme with the neon S grid and rabbit guide.
  // Global pages live one folder down, so every path is written relative to `base`.
  html = html.replace(/<link[^>]*href="(?:\.\.\/)?(?:liquid-pink|raspberry-theme|hotpink-theme)\.css"[^>]*>/g, '');
  html = html.replace('</head>', `<link rel="stylesheet" href="${base}hotpink-theme.css"></head>`);
  html = html.replace(/<script[^>]*src="(?:\.\.\/)?(?:liquid-pink|s-background|rabbit-guide)\.js"[^>]*><\/script>/g, '');
  html = html.replace('</body>', `<script src="${base}s-background.js" defer></script><script src="${base}rabbit-guide.js" defer></script></body>`);
  // Launch switch: once the Play listing is live, set SAFENEST_PLAY_STORE_URL and every
  // page's announcement bar becomes the store link. Nothing is shown before that.
  if (playStoreUrl) html = html.replace(/<aside class="announcement">[\s\S]*?<\/aside>/, playAnnouncement(html.includes('lang="bn"')));
  html = html.replace(/<meta name="theme-color"[^>]*>/g, '');
  html = html.replace('</head>', '<meta name="theme-color" content="#d80f7f"></head>');
  const policy = contentSecurityPolicy(url.origin, { meta: true });
  if (html.includes('http-equiv="Content-Security-Policy"')) {
    // Auth's older inline policy must use the same resource permissions as
    // the host policy, including the shared theme's Google Fonts.
    html = html.replace(/<meta http-equiv="Content-Security-Policy"[^>]*>/g,
      `<meta http-equiv="Content-Security-Policy" content="${policy}">`);
  } else {
    html = html.replace(/<head>/, `<head><meta http-equiv="Content-Security-Policy" content="${policy}">`);
  }
  await writeFile(path, html);
}
