import { build } from 'esbuild';
import { mkdir, copyFile, writeFile } from 'node:fs/promises';

const projectUrl = process.env.SUPABASE_URL || 'https://kflenmeizngmafwnwhgv.supabase.co';
const key = process.env.SUPABASE_PUBLISHABLE_KEY || '';
const url = new URL(projectUrl);
if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash) {
  throw new Error('SUPABASE_URL must be an HTTPS project URL without credentials.');
}
if (key && !/^sb_publishable_[A-Za-z0-9_-]+$/.test(key)) {
  throw new Error('Use a Supabase publishable key starting sb_publishable_.');
}
await mkdir('dist', { recursive: true });
for (const file of ['account.html', 'account.css']) {
  await copyFile(`web/${file}`, `dist/${file}`);
}
await build({
  entryPoints: ['web/account.js'], outfile: 'dist/account.js',
  bundle: true, minify: true, format: 'esm', target: ['es2022'],
  define: { __SUPABASE_URL__: JSON.stringify(url.origin), __SUPABASE_KEY__: JSON.stringify(key) }
});
// Apply these headers through the website host. The HTML also carries its CSP.
await writeFile('dist/_headers', `/account.html\n  Cache-Control: no-store\n  Referrer-Policy: no-referrer\n  X-Content-Type-Options: nosniff\n  X-Frame-Options: DENY\n`);
console.log(key ? 'Built account page. Live Auth and database verification still required.' : 'Built setup preview. Account forms are disabled until a publishable key is configured.');
