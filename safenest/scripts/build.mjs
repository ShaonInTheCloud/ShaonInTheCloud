import { build } from 'esbuild';
import { mkdir, copyFile, writeFile, cp, rm } from 'node:fs/promises';
import { securityHeaders } from './security-headers.mjs';

const projectUrl = process.env.SUPABASE_URL || 'https://kflenmeizngmafwnwhgv.supabase.co';
const key = process.env.SUPABASE_PUBLISHABLE_KEY || 'sb_publishable_jt2VeNCAATz3iEiebx2Kog_ZiZVL7Vl';
const url = new URL(projectUrl);
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
  define: { __SUPABASE_URL__: JSON.stringify(url.origin), __SUPABASE_KEY__: JSON.stringify(key) }
});
// Apply these headers through the website host. The HTML also carries its CSP.
await writeFile('dist/_headers', securityHeaders(url.origin));
console.log(key ? 'Built account page. Live Auth and database verification still required.' : 'Built setup preview. Account forms are disabled until a publishable key is configured.');
await import('./release-pages.mjs');
