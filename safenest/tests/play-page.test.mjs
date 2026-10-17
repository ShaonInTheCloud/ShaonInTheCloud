import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,access} from 'node:fs/promises';
// Nest Run must stay inside the site's CSP (script-src 'self', fonts from Google only) and link only to pages that exist.
const dir=new URL('../website/play/',import.meta.url);
test('Nest Run page has no inline or third-party scripts and every local reference resolves',async()=>{
  const html=await readFile(new URL('index.html',dir),'utf8');
  assert.equal((html.match(/<script\b[^>]*>(?!<\/script>)[\s\S]*?<\/script>/g)||[]).length,0,'inline script found');
  for (const [,src] of html.matchAll(/<script[^>]+src="([^"]+)"/g)) assert.ok(!/^https?:/.test(src),'third-party script '+src);
  for (const [,href] of html.matchAll(/<link[^>]+href="(https?:[^"]+)"/g)) assert.match(href,/^https:\/\/fonts\.(googleapis|gstatic)\.com(\/|$)/);
  assert.ok(!/\son\w+="/.test(html),'inline event handler found');
  const refs=[...html.matchAll(/(?:href|src)="([^"#?]+)"/g)].map(m=>m[1]).filter(r=>!/^(https?:|mailto:)/.test(r));
  const built=new Set(['../account.html','../contact.html','../privacy.html','../support.html','../delete-account.html','../checkout.html','../terms.html']);
  for (const ref of new Set(refs)) {
    if (built.has(ref)) continue; // generated into dist by scripts/build.mjs
    const target=ref.endsWith('/')?ref+'index.html':ref;
    await assert.doesNotReject(access(new URL(target,dir)),'missing '+ref);
  }
  const js=await readFile(new URL('play.js',dir),'utf8');
  assert.match(js,/Math\.max\(0, \(now - last\)/,'frame delta must be clamped at zero');
  assert.ok(!/fetch\(|XMLHttpRequest|navigator\.sendBeacon/.test(js),'the game must not make network requests');
});
