import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';

// The Play launch switch is a build-time flag; a wrong URL must fail the build, not ship.
test('build script validates SAFENEST_PLAY_STORE_URL and renders the announcement for both editions', async () => {
  const source = await readFile(new URL('../scripts/build.mjs', import.meta.url), 'utf8');
  assert.match(source, /SAFENEST_PLAY_STORE_URL/);
  assert.match(source, /play\\.google\\.com\\\/store\\\/apps\\\/details\\?id=com\\.safenest\\.app/);
  assert.match(source, /Get it on Google Play/);
  assert.match(source, /Google Play থেকে নিন/);
  // Global (English-only) pages are post-processed too, with parent-relative asset paths.
  assert.match(source, /readdir\('dist\/global'\)/);
  assert.match(source, /const base = file\.includes\('\/'\) \? '\.\.\/' : ''/);
});
