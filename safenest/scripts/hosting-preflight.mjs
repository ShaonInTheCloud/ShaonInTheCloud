import { readFile, readdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { pathToFileURL } from 'node:url';

export async function assetDigest(root = 'dist') {
  const hash = createHash('sha256');
  async function walk(dir, prefix = '') {
    for (const entry of (await readdir(dir, { withFileTypes: true })).sort((a, b) => a.name < b.name ? -1 : a.name > b.name ? 1 : 0)) {
      const relative = prefix + entry.name;
      if (entry.isSymbolicLink()) throw new Error('Asset symlinks are not permitted');
      if (entry.isDirectory()) await walk(`${dir}/${entry.name}`, `${relative}/`);
      else {
        const body = await readFile(`${dir}/${entry.name}`);
        hash.update(`${relative}\0${body.length}\0`); hash.update(body);
      }
    }
  }
  await walk(root);
  return hash.digest('hex');
}

export function verifyReconciliation(record, digest, { sha, approvedSha }) {
  if (record.status !== 'reconciled' || record.sitesCommit !== '4081fe8ac308609a7cf8b4aa5ac79f2080f3847c' || record.sitesVersion !== 45) {
    throw new Error('Deployment blocked: reconcile the live Sites v45 source before uploading GitHub assets');
  }
  if (!/^[a-f0-9]{64}$/.test(record.assetSha256 ?? '') || record.assetSha256 !== digest || !record.evidence?.trim()) {
    throw new Error('Deployment blocked: asset digest and reconciliation evidence are required');
  }
  if (!/^[a-f0-9]{40}$/.test(sha ?? '') || approvedSha !== sha) {
    throw new Error('Deployment blocked: environment HOSTING_APPROVED_SHA must match the exact reviewed commit');
  }
}

export async function checkCloudflare({ token, accountId, zoneId, target }, fetcher = fetch) {
  if (!['preview', 'production'].includes(target)) throw new Error('Unknown hosting target');
  if (!token || !/^[a-f0-9]{32}$/.test(accountId ?? '')) throw new Error('Cloudflare token and valid account ID required');
  async function get(path) {
    let response, data;
    try {
      response = await fetcher(`https://api.cloudflare.com/client/v4${path}`, {
        headers: { Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(15000), redirect: 'error'
      });
      data = await response.json();
    } catch { throw new Error('Cloudflare read-only preflight failed: network or invalid response'); }
    if (!response.ok || data.success !== true || !data.result) throw new Error('Cloudflare preflight denied: check token scope and resource access');
    return data.result;
  }
  const account = await get(`/accounts/${accountId}`);
  if (account.id !== accountId) throw new Error('Cloudflare account mismatch');
  if (target === 'production') {
    if (!/^[a-f0-9]{32}$/.test(zoneId ?? '')) throw new Error('Production requires CLOUDFLARE_ZONE_ID; edge IPs do not establish zone ownership');
    const zone = await get(`/zones/${zoneId}`);
    if (zone.id !== zoneId || zone.name !== 'mysafenestbd.com' || zone.account?.id !== accountId || zone.status !== 'active' || zone.paused !== false) {
      throw new Error('Production blocked: mysafenestbd.com must be active, unpaused and owned by the configured account');
    }
  }
  return true;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    if (process.argv[2] === 'digest') console.log(await assetDigest());
    else {
      const target = process.argv[2];
      if (process.argv.includes('--deployment')) {
        const record = JSON.parse(await readFile('cloudflare/source-reconciliation.json', 'utf8'));
        verifyReconciliation(record, await assetDigest(), { sha: process.env.GITHUB_SHA, approvedSha: process.env.HOSTING_APPROVED_SHA });
      }
      await checkCloudflare({ token: process.env.CLOUDFLARE_API_TOKEN, accountId: process.env.CLOUDFLARE_ACCOUNT_ID, zoneId: process.env.CLOUDFLARE_ZONE_ID, target });
      console.log('Read-only account/zone preflight passed; DNS, mail, TLS and browser acceptance still require cutover review.');
    }
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
