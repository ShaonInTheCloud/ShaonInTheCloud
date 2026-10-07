import { build } from 'esbuild';
import { mkdir, copyFile, writeFile, cp, rm, readdir, readFile } from 'node:fs/promises';
import { securityHeaders, contentSecurityPolicy } from './security-headers.mjs';

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
console.log(key ? 'Built account page. Live Auth and database verification still required.' : 'Built setup preview. Account forms are disabled until a publishable key is configured.');
await import('./release-pages.mjs');

// This static host currently ignores _headers; a meta CSP still protects HTML
// script/resource loading. It cannot replace HSTS, no-store or frame-ancestors.
for (const file of await readdir('dist')) {
  if (!file.endsWith('.html')) continue;
  const path = `dist/${file}`;
  let html = await readFile(path, 'utf8');
  // One final brand layer covers marketing, generated service pages and Auth.
  // Keep the original page layouts and load the shared motion on every route.
  if (!html.includes('href="liquid-pink.css"')) {
    html = html.replace('</head>', '<link rel="stylesheet" href="liquid-pink.css"></head>');
  }
  html = html.replace(/<link[^>]*href="raspberry-theme\.css"[^>]*>/g, '');
  html = html.replace('</head>', '<link rel="stylesheet" href="raspberry-theme.css"></head>');
  if (!html.includes('src="liquid-pink.js"')) {
    html = html.replace('</body>', '<script src="liquid-pink.js" defer></script></body>');
  }
  html = html.replace(/<meta name="theme-color"[^>]*>/g, '');
  html = html.replace('</head>', '<meta name="theme-color" content="#631332"></head>');
  const policy = contentSecurityPolicy(url.origin, { meta: true });
  if (!html.includes('http-equiv="Content-Security-Policy"')) {
    html = html.replace(/<head>/, `<head><meta http-equiv="Content-Security-Policy" content="${policy}">`);
  }
  await writeFile(path, html);
}
