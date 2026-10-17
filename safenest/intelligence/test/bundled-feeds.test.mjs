import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {normalize} from '../src/core.mjs';

const assets = new URL('../../data/blocklists/sources/', import.meta.url);
const files = [
  ['gambling_hosts_vn.txt', 3983],
  ['gambling_hosts_sinfonietta.txt', 2690],
  ['gambling_bangladesh_researched.txt', 10],
  ['gambling_brand_families.txt', 28526],
];

test('bundled gambling feeds have expected provenance snapshots and safe hostname syntax', () => {
  const union = new Set();
  for (const [file, expected] of files) {
    const entries = readFileSync(new URL(file, assets), 'utf8').trim().split(/\r?\n/);
    assert.equal(entries.length, expected, file);
    assert.equal(new Set(entries).size, expected, `${file} contains duplicates`);
    for (const host of entries) {
      assert.equal(normalize(host).hostname, host, `${file}: ${host}`);
      union.add(host);
    }
  }
  assert.equal(union.size, 35155);
  for (const host of ['google.com', 'facebook.com', 'youtube.com', 'mysafenestbd.com']) {
    assert.equal(union.has(host), false, `unexpected bundled entry ${host}`);
  }
});

test('Bangladesh additions and exact app packages have recorded evidence', () => {
  const evidence = JSON.parse(readFileSync(new URL('../../data/bangladesh-gambling-research.json', import.meta.url), 'utf8'));
  const domains = readFileSync(new URL('gambling_bangladesh_researched.txt', assets), 'utf8').trim().split(/\r?\n/);
  assert.deepEqual(domains, evidence.active_domain_evidence.map(row => row.domain).sort());
  for (const row of evidence.active_domain_evidence) assert.ok(row.source.startsWith('https://'));
  const packages = readFileSync(new URL('../../android/app/src/main/java/com/safenest/app/KnownGamblingPackages.kt', import.meta.url), 'utf8');
  for (const row of evidence.active_android_package_evidence) {
    assert.ok(packages.includes(`"${row.package_name}"`));
    assert.ok(row.source.startsWith('https://'));
  }
});

test('bulk catalogue, provenance, import export and package visibility agree', () => {
  const root = new URL('../../', import.meta.url);
  const metadata = JSON.parse(readFileSync(new URL('data/gambling-brand-families.json', root), 'utf8'));
  const entries = readFileSync(new URL('gambling_brand_families.txt', assets), 'utf8').trim().split(/\r?\n/);
  assert.deepEqual(metadata.domains.map(row => row.domain), entries);
  assert.equal(metadata.hostname_count, 28526);
  assert.equal(metadata.ownership_verified, false);
  assert.equal(metadata.individually_browsed, false);
  assert.equal(metadata.upstream_sha256, 'd583202e86eb80f8cb6311e65d10f36e472d921d7cac5ff18e98d7dd0b9cf439');
  const all = [...new Set(files.flatMap(([file]) => readFileSync(new URL(file, assets), 'utf8').trim().split(/\r?\n/)))].sort();
  const exported = JSON.parse(readFileSync(new URL('data/safenest-gambling-import.json', root), 'utf8'));
  assert.deepEqual(exported.domains.map(row => row.domain), all);
  const packages = JSON.parse(readFileSync(new URL('data/known-gambling-packages.json', root), 'utf8')).packages;
  assert.equal(packages.length, 10);
  assert.equal(new Set(packages.map(p => p.package_name)).size, 10);
  const kotlin = readFileSync(new URL('android/app/src/main/java/com/safenest/app/KnownGamblingPackages.kt', root), 'utf8');
  const manifest = readFileSync(new URL('android/app/src/main/AndroidManifest.xml', root), 'utf8');
  for (const row of packages) {
    assert.ok(kotlin.includes(`"${row.package_name}"`));
    assert.ok(manifest.includes(`<package android:name="${row.package_name}" />`));
    assert.ok(row.sources.every(url => url.startsWith('https://')));
    assert.ok(!['com.android.chrome','org.mozilla.firefox','com.android.settings'].includes(row.package_name));
  }
});
