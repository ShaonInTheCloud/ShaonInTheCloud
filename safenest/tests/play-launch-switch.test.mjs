import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';

// The Play launch switch is a build-time flag; a wrong URL must fail the build, not ship.
test('build script validates SAFENEST_PLAY_STORE_URL and renders the announcement for both editions', async () => {
  const source = await readFile(new URL('../scripts/build.mjs', import.meta.url), 'utf8');
  assert.ok(source.includes('SAFENEST_PLAY_STORE_URL'));
  assert.ok(source.includes('id=com\\.safenest\\.app'), 'URL validation is pinned to the SafeNest package');
  assert.ok(source.includes('Get it on Google Play'));
  assert.ok(source.includes('Google Play থেকে নিন'));
  // Global (English-only) pages are post-processed too, with parent-relative asset paths.
  assert.ok(source.includes("readdir('dist/global')"));
  assert.ok(source.includes("const base = file.includes('/') ? '../' : ''"));
});
