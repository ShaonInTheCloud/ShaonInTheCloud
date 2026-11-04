import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readdirSync, readFileSync, statSync} from 'node:fs';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';

// SafeNest Bangla copy rule: the website never uses জুয়া; it says বেটিং in every context.
const root = fileURLToPath(new URL('../website/', import.meta.url));
const textFile = /\.(html|js|mjs|json|css|txt|md|xml|svg)$/;
function* files(dir) {
  for (const name of readdirSync(dir)) {
    if (name === 'node_modules' || name.startsWith('.')) continue;
    const path = join(dir, name);
    if (statSync(path).isDirectory()) yield* files(path);
    else if (textFile.test(name)) yield path;
  }
}

test('website copy uses বেটিং, never জুয়া', () => {
  const hits = [...files(root)].filter(path => readFileSync(path, 'utf8').includes('জুয়া'));
  assert.deepEqual(hits.map(p => p.slice(root.length)), []);
});
