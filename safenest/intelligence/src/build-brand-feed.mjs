// Reproducible extraction from an already classified third-party gambling feed.
// Brand-name text is a research tag, never a new domain or proof of ownership.
import {readFileSync, writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {normalize, aliasKey} from './core.mjs';

const [input] = process.argv.slice(2);
if (!input) throw Error('Usage: node src/build-brand-feed.mjs /path/to/gambling.txt');
const source = readFileSync(input);
const sha256 = createHash('sha256').update(source).digest('hex');
if (sha256 !== 'd583202e86eb80f8cb6311e65d10f36e472d921d7cac5ff18e98d7dd0b9cf439') {
  throw Error('Upstream snapshot changed; review provenance and license before importing');
}
const root = new URL('../../', import.meta.url);
const seed = JSON.parse(readFileSync(new URL('intelligence/seed.json', root), 'utf8'));
const brands = seed.brands.filter(b => aliasKey(b.name).length >= 4 || b.name === 'baji');
const rejected = [];
const selected = new Map();
for (const line of source.toString('utf8').split(/\r?\n/)) {
  if (!line.startsWith('0.0.0.0 ')) continue;
  const raw = line.trim().split(/\s+/)[1];
  try {
    const domain = normalize(raw).domain;
    // Normalize the www prefix only; never collapse to a registrable parent domain.
    const key = aliasKey(domain);
    const tags = brands.filter(b => [b.name, ...(b.aliases || [])].some(a => key.includes(aliasKey(a)))).map(b => b.slug);
    if (tags.length) selected.set(domain, tags);
  } catch { rejected.push(raw); }
}
const domains = [...selected.keys()].sort();
if (domains.length < 1000 || domains.length > 100000) throw Error('Unexpected feed size; review before import');
const forbidden = ['google.com','facebook.com','youtube.com','microsoft.com','bkash.com','nagad.com.bd','mysafenestbd.com','gov.bd'];
if (domains.some(d => forbidden.includes(d))) throw Error('Shared service/root domain found');
const metadata = {
  schema: 'safenest-third-party-brand-feed/1', retrieved_at: '2026-10-03',
  source: 'https://github.com/blocklistproject/Lists/blob/main/gambling.txt',
  upstream_git_blob: '701eb4384ae3d65cfc25d9334f7eb0ee34fb4ef9', upstream_sha256: sha256,
  license: 'Unlicense', source_classification: 'gambling',
  geographic_scope: 'Global domains with textual matches to the Bangladesh-focused brand seed; Bangladesh availability not measured',
  ownership_verified: false, individually_browsed: false,
  hostname_count: domains.length, rejected_syntax_count: rejected.length,
  brands: brands.map(b => ({name:b.name,slug:b.slug,matching_hostnames:domains.filter(d => selected.get(d).includes(b.slug)).length})),
  domains: domains.map(domain => ({domain, category:'gambling', source:'blocklistproject', brand_text_matches:selected.get(domain), confidence:'THIRD_PARTY_FEED'}))
};
writeFileSync(new URL('android/app/src/main/assets/gambling_brand_families.txt', root), domains.join('\n')+'\n');
writeFileSync(new URL('data/gambling-brand-families.json', root), JSON.stringify(metadata,null,2)+'\n');
console.log(JSON.stringify({domains:domains.length,brands:metadata.brands.filter(b=>b.matching_hostnames),rejected:rejected.length}));
