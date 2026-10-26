import test from 'node:test';
import assert from 'node:assert/strict';
import { checkCloudflare, verifyReconciliation } from '../scripts/hosting-preflight.mjs';
const accountId = 'a'.repeat(32), zoneId = 'b'.repeat(32);
const settings = { accountId, zoneId, token: 'test-token', target: 'production' };
const zone = { id: zoneId, name: 'mysafenestbd.com', account: { id: accountId }, status: 'active', paused: false };
function api(zoneResult = zone, status = 200) {
  return async (url, options) => {
    assert.equal(options.method, undefined);
    return new Response(JSON.stringify({ success: status === 200, result: url.includes('/zones/') ? zoneResult : { id: accountId } }), { status });
  };
}
test('production requires an active zone belonging to the configured account', async () => {
  assert.equal(await checkCloudflare(settings, api()), true);
  for (const changed of [{ account: { id: 'c'.repeat(32) } }, { status: 'pending' }, { paused: true }, { name: 'other.com' }, { id: 'd'.repeat(32) }]) {
    await assert.rejects(checkCloudflare(settings, api({ ...zone, ...changed })), /Production blocked/);
  }
  await assert.rejects(checkCloudflare({ ...settings, zoneId: undefined }, api()), /ZONE_ID/);
  await assert.rejects(checkCloudflare(settings, api(zone, 403)), /denied/);
  await assert.rejects(checkCloudflare(settings, async () => { throw new Error('secret'); }), /network/);
  await assert.rejects(checkCloudflare({ ...settings, token: '' }, api()), /token/);
});
test('preview requires account access but no zone ownership', async () => {
  assert.equal(await checkCloudflare({ ...settings, target: 'preview', zoneId: undefined }, api()), true);
});
test('unreconciled, stale assets and unapproved commits fail closed', () => {
  const digest = 'd'.repeat(64), sha = 'e'.repeat(40);
  const record = { status: 'reconciled', sitesCommit: '4081fe8ac308609a7cf8b4aa5ac79f2080f3847c', sitesVersion: 45, assetSha256: digest, evidence: 'Reviewed source comparison and browser acceptance report' };
  const approval = { sha, approvedSha: sha };
  assert.doesNotThrow(() => verifyReconciliation(record, digest, approval));
  for (const changed of [{ status: 'blocked' }, { sitesCommit: 'old' }, { assetSha256: 'f'.repeat(64) }, { evidence: '' }]) {
    assert.throws(() => verifyReconciliation({ ...record, ...changed }, digest, approval), /blocked/);
  }
  assert.throws(() => verifyReconciliation(record, digest, { sha }), /exact reviewed commit/);
});
