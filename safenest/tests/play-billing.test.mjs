import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile, readdir} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';
import {normalizeSubscription, rpcArguments, defaultPlanMap, PURCHASE_TOKEN} from '../supabase/functions/_shared/google-play.mjs';
import {handleVerify} from '../supabase/functions/play-billing-verify/handler.mjs';
import {handleRtdn} from '../supabase/functions/play-billing-rtdn/handler.mjs';

// Disposable local fixtures only: no Google credentials, no project keys, no network.
const buyer = '11111111-1111-4111-8111-111111111111';
const other = '22222222-2222-4222-8222-222222222222';
const token = 'fixture-purchase-token-' + 'a'.repeat(40);
const migrations = new URL('../supabase/migrations/', import.meta.url);

async function fixture() {
  const db = new PGlite();
  await db.exec(`create role anon nologin; create role authenticated nologin;
    create role service_role nologin bypassrls; create schema auth;
    create table auth.users(id uuid primary key);
    create function auth.uid() returns uuid language sql stable as $$
      select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
    grant usage on schema auth,public to anon,authenticated,service_role;
    grant execute on function auth.uid() to anon,authenticated,service_role;
    insert into auth.users values ('${buyer}'),('${other}');`);
  for (const file of (await readdir(migrations)).filter(name =>
      /_protection_entitlements|_payment_foundation(?:_hardening)?|_trial_subscriptions|_google_play_billing/.test(name)).sort())
    await db.exec(await readFile(new URL(file, migrations), 'utf8'));
  await db.exec('set role service_role');
  return db;
}

function google(overrides = {}) {
  const start = new Date(Date.now() - 60_000).toISOString();
  const expiry = new Date(Date.now() + 30 * 86_400_000).toISOString();
  return {
    kind: 'androidpublisher#subscriptionPurchaseV2', startTime: start, subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
    latestOrderId: 'GPA.1234-5678-9012-34567', acknowledgementState: 'ACKNOWLEDGEMENT_STATE_PENDING',
    externalAccountIdentifiers: {obfuscatedExternalAccountId: buyer},
    lineItems: [{productId: 'safenest_protection', expiryTime: expiry, offerDetails: {basePlanId: 'monthly'}}],
    ...overrides,
  };
}

async function apply(db, userId, sub, {purchase = token, hash = 'b'.repeat(64)} = {}) {
  const a = rpcArguments(userId, purchase, sub, hash);
  const row = (await db.query(`select public.process_play_subscription($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11) as result`,
    [a.p_user_id, a.p_purchase_token, a.p_product_id, a.p_base_plan_id, a.p_plan_code, a.p_state, a.p_starts_at, a.p_expires_at,
      a.p_order_id, a.p_linked_purchase_token, a.p_evidence_sha256])).rows[0];
  return row.result;
}

test('normalizeSubscription maps Google state and plan; rejects unknown products', () => {
  const sub = normalizeSubscription(google());
  assert.equal(sub.state, 'ACTIVE'); assert.equal(sub.planCode, 'monthly'); assert.equal(sub.externalAccountId, buyer);
  assert.equal(sub.acknowledged, false);
  assert.equal(normalizeSubscription(google({subscriptionState: 'SUBSCRIPTION_STATE_CANCELED'})).state, 'CANCELED');
  assert.equal(normalizeSubscription(google({subscriptionState: 'SUBSCRIPTION_STATE_EXPIRED',
    canceledStateContext: {developerInitiatedCancellation: {}}})).state, 'REVOKED');
  assert.throws(() => normalizeSubscription(google({lineItems: [{productId: 'other', expiryTime: google().lineItems[0].expiryTime, offerDetails: {basePlanId: 'monthly'}}]})), /unknown_play_plan/);
  assert.throws(() => normalizeSubscription(google({lineItems: []})), /purchase_not_found/);
  assert.equal(defaultPlanMap('safenest_protection', 'annual'), 'annual');
  assert.equal(defaultPlanMap('safenest_protection', 'lifetime'), '');
  assert.ok(PURCHASE_TOKEN.test(token)); assert.ok(!PURCHASE_TOKEN.test('short'));
});

test('ledger binds a token to one account, issues/updates one entitlement, and revokes', async () => {
  const db = await fixture();
  try {
    const sub = normalizeSubscription(google());
    const first = await apply(db, buyer, sub);
    assert.equal(first.status, 'active'); assert.equal(first.user_id, buyer);
    const rows = (await db.query(`select * from public.protection_entitlements`)).rows;
    assert.equal(rows.length, 1); assert.equal(rows[0].provider, 'google_play'); assert.equal(rows[0].plan_code, 'monthly');
    // Renewal: same token, later expiry, still one entitlement row.
    const renewed = normalizeSubscription(google({lineItems: [{productId: 'safenest_protection',
      expiryTime: new Date(Date.now() + 60 * 86_400_000).toISOString(), offerDetails: {basePlanId: 'monthly'}}]}));
    const second = await apply(db, buyer, renewed, {hash: 'c'.repeat(64)});
    assert.equal(second.entitlement_id, first.entitlement_id);
    assert.equal((await db.query(`select count(*)::int as n from public.protection_entitlements`)).rows[0].n, 1);
    // Another account cannot claim this token; RTDN path (null user) works for the bound one.
    await assert.rejects(apply(db, other, renewed), /purchase_bound_to_other_account/);
    const viaRtdn = await apply(db, null, renewed);
    assert.equal(viaRtdn.user_id, buyer);
    await assert.rejects(apply(db, null, renewed, {purchase: 'unbound-token-' + 'z'.repeat(30)}), /purchase_not_bound/);
    // Refund/revocation ends access immediately.
    const revoked = normalizeSubscription(google({subscriptionState: 'SUBSCRIPTION_STATE_EXPIRED', canceledStateContext: {developerInitiatedCancellation: {}}}));
    assert.equal((await apply(db, buyer, revoked)).status, 'revoked');
    assert.equal((await db.query(`select status from public.protection_entitlements`)).rows[0].status, 'revoked');
    // Expired state never grants.
    const expired = normalizeSubscription(google({subscriptionState: 'SUBSCRIPTION_STATE_EXPIRED'}));
    assert.equal((await apply(db, buyer, expired)).status, 'expired');
    // Customers can only read their own row; nobody but service_role can write.
    await db.exec(`set role authenticated; select set_config('request.jwt.claim.sub','${other}',false)`);
    assert.equal((await db.query(`select count(*)::int as n from public.protection_entitlements`)).rows[0].n, 0);
    await assert.rejects(db.query(`select public.process_play_subscription($1,$2,'p','b','monthly','ACTIVE',now(),now()+interval '1 day',null,null,$3)`,
      [other, token, 'd'.repeat(64)]), /permission denied/);
  } finally { await db.close(); }
});

function fakeFetch(googleBody, {userId = buyer, rpcResult, rpcStatus = 200} = {}) {
  const calls = [];
  const fetcher = async (url, init = {}) => {
    calls.push({url: String(url), method: init.method ?? 'GET'});
    const ok = body => new Response(JSON.stringify(body), {status: 200, headers: {'Content-Type': 'application/json'}});
    if (String(url).endsWith('/auth/v1/user')) return ok({id: userId});
    if (String(url).includes('oauth2.googleapis.com/token')) return ok({access_token: 'ya29.fixture'});
    if (String(url).includes('/purchases/subscriptionsv2/tokens/')) return googleBody ? ok(googleBody) : new Response('{}', {status: 404});
    if (String(url).endsWith(':acknowledge')) return new Response('{}', {status: 200});
    if (String(url).includes('/rest/v1/rpc/process_play_subscription')) return new Response(JSON.stringify(rpcResult), {status: rpcStatus});
    throw new Error('unexpected fetch ' + url);
  };
  return {fetcher, calls};
}

// A throwaway 2048-bit test key so the JWT signing path runs for real.
const testKey = `-----BEGIN PRIVATE KEY-----
MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDLTx81TDF175Fm
0cecvM/ETcPviNAQRN74/dTo+3ZpEPUDL2Rjg/dvtESkI4KwdSd6zDEcX9QxXHwB
jhaKXcC26ukq+SeQJ4nSYbIp5LFZ0J+v35zxxrEW5mTEW0PBmSIM+EUo0pJiQV4H
lINyhOWIhxeEr/ZG894UZaQ458NF3ee+T3gRPCczoBcD42I5ny/nMAUT2CNFhSqb
HApWMGm/69YA8MWTd7AhXXoq138fLgpLxragU36A/0SaFHXQvJY7hGMei3e87Nl+
u4lkAsJ5Ic2yPFH+tEW29xn30BhXNa0LXIwV6VrVof6XISD5zbo+UNql9WPk55wK
jR1xtbvDAgMBAAECggEAUXBkQ+k9dsx8Jf+qQfKwFyY/clgPYDT2TC8BdkIkcHnG
a3w0bliSaUCArm87DowfQGVC1ubcDNnLIqRUCyD67Uq0E+6yFmCAKLHRuE5PbHbR
QJYGPCMnFNE9VBpqzcYVibR5n5yZ3pnonIi1Ds6FU2cp2JDTyiAtuWXtno3X/sfq
HeCEQWmfrzW1L8+0lsqDDH46UUveNLFPO96D40NEzTalUH/qk2ls4TAMWU4xb6JG
IDQGlqgyc3FZbcZ05d7FA/AVytsm1UkcHaw3H2daoYsCpTFEUx3T1XZL9SPTU8WO
n1dLY7psX2cQJ109XKRQHU+SAFVf5AOjvEXjHirpYQKBgQDngKoHuE90bdsXBMs+
1C8DgTsn0iDSUN7vl/S8TIgIt1DglIEnf7RMh+WMNa/nrIjhf60X/NxyN75edfQ/
Pnlc0GSI+xxM/adeyt0aPy85WRRmvIaTUVW4R8UMOjXJbkNi49oc7qI7IU2B95Xf
+M75wr7Yv7ViD/8VuA099kQO3QKBgQDg0rQfn73/LaFiFUfC6KBAJTFz+4+cx2gO
Brme64OOZL6CDO+bZG3ET2zMNCoZrEK6imReNmogQtHCxU1RU58OfB7cLVUsEbFb
llZzFlSqn4J/fjHZUKE5k8NM29W/6nWF+0vgXCctLtDsCpB5RaFc4hUndrKHOHUl
UFacKAc7HwKBgQDl38j2aXncLsmCjewTszYL0OMgo3Kb5Fm6p7UI1SWvIfhZSJ0G
z7QE2lyBYT4OWjCBWfpS+28aq2eI/K9PrYVtODDcJBzyBl2Prvjl+x+1XlL3wPQQ
jI8FsZ5kidim76pSI9aNLBhDhsoj5+kWwUUklNBVAefmO+QbeOsNP+BMsQKBgEtM
huOonvwejlTJh9VyEGoritqrwLfj2agI/JaLju5cM6pfJslG47TCXVAvkII0vcy4
mVEsrHLNrm+roW/BsB4r2DIqRMKT0nE4cw/sxMK41f5Zh9yfMsCUCu9tGbRDlMUh
G5KzirOJYJQOHcFgXuE7H5auHLFJaYLr23ZFudZFAoGBAOOud4kWRt5ryqz/HAPA
2x62gWSyyEUeYcnZPIRQGf15Tlitbwuh/5aw3HKVOf+bKNY/OXdD7US7KP/rrBsy
rvdcl/2TzdJda08doWzuxebDzvUQ2QlNoe3+DbK/s91837Juoz+7USFy3hCA8G3u
BOnoU05cN6cUdJiJthpvLmb6
-----END PRIVATE KEY-----`;

const env = {SUPABASE_URL: 'https://fixture.supabase.co', SUPABASE_ANON_KEY: 'sb_publishable_fixture',
  SUPABASE_SERVICE_ROLE_KEY: 'service-fixture', PLAY_PACKAGE_NAME: 'com.safenest.app',
  PLAY_SERVICE_ACCOUNT_JSON: JSON.stringify({client_email: 'play@fixture.iam.gserviceaccount.com', private_key: testKey}),
  PLAY_RTDN_SECRET: 'r'.repeat(48)};

const rpcOk = {user_id: buyer, entitlement_id: '33333333-3333-4333-8333-333333333333', status: 'active', plan_code: 'monthly',
  starts_at: new Date(Date.now() - 60_000).toISOString(), ends_at: new Date(Date.now() + 30 * 86_400_000).toISOString(), state: 'ACTIVE'};

test('verify endpoint: auth, token validation, Google re-fetch, acknowledge and entitlement reply', async () => {
  const request = (body, auth = 'Bearer fixture.jwt.token') => new Request('https://fixture/functions/v1/play-billing-verify',
    {method: 'POST', headers: {Authorization: auth, 'Content-Type': 'application/json'}, body: JSON.stringify(body)});
  assert.equal((await handleVerify(request({purchase_token: token}, ''), env, {fetcher: async () => { throw new Error('no'); }})).status, 401);
  assert.equal((await handleVerify(request({purchase_token: 'bad'}), env, fakeFetch(google()))).status, 400);
  const {fetcher, calls} = fakeFetch(google(), {rpcResult: rpcOk});
  const reply = await handleVerify(request({purchase_token: token}), env, {fetcher});
  assert.equal(reply.status, 200);
  const body = await reply.json();
  assert.equal(body.active, true); assert.equal(body.entitlement.plan_code, 'monthly'); assert.equal(body.user_id, buyer);
  assert.ok(calls.some(c => c.url.endsWith(':acknowledge') && c.method === 'POST'), 'acknowledges a pending purchase');
  assert.ok(calls.some(c => c.url.includes('/purchases/subscriptionsv2/tokens/')), 'fetches from Google');
  // Token bought under a different account id is refused.
  const foreign = fakeFetch(google({externalAccountIdentifiers: {obfuscatedExternalAccountId: other}}), {rpcResult: rpcOk});
  assert.equal((await handleVerify(request({purchase_token: token}), env, {fetcher: foreign.fetcher})).status, 409);
  // Pending purchases do not grant yet.
  const pending = fakeFetch(google({subscriptionState: 'SUBSCRIPTION_STATE_PENDING'}), {rpcResult: rpcOk});
  const pendingReply = await (await handleVerify(request({purchase_token: token}), env, {fetcher: pending.fetcher})).json();
  assert.equal(pendingReply.active, false); assert.equal(pendingReply.pending, true);
  // Unknown token at Google.
  assert.equal((await handleVerify(request({purchase_token: token}), env, {fetcher: fakeFetch(null).fetcher})).status, 404);
  // Test purchases are rejected unless the operator allows them.
  const testPurchase = fakeFetch(google({testPurchase: {}}), {rpcResult: rpcOk});
  assert.equal((await handleVerify(request({purchase_token: token}), env, {fetcher: testPurchase.fetcher})).status, 403);
  assert.equal((await handleVerify(request({purchase_token: token}), {...env, PLAY_ALLOW_TEST_PURCHASES: 'true'}, {fetcher: testPurchase.fetcher})).status, 200);
  // Missing Play configuration is a 503, never a grant.
  assert.equal((await handleVerify(request({purchase_token: token}), {...env, PLAY_SERVICE_ACCOUNT_JSON: ''}, {fetcher})).status, 503);
});

test('RTDN endpoint: secret gate, decodes Pub/Sub envelope, re-fetches and updates the bound account', async () => {
  const push = (notification, key = env.PLAY_RTDN_SECRET) => new Request(`https://fixture/functions/v1/play-billing-rtdn?key=${key}`,
    {method: 'POST', headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({message: {data: Buffer.from(JSON.stringify(notification)).toString('base64'), messageId: '1'}, subscription: 'x'})});
  const notification = {version: '1.0', packageName: 'com.safenest.app', eventTimeMillis: String(Date.now()),
    subscriptionNotification: {version: '1.0', notificationType: 2, purchaseToken: token, subscriptionId: 'safenest_protection'}};
  assert.equal((await handleRtdn(push(notification, 'wrong'), env, fakeFetch(google()))).status, 403);
  assert.equal((await handleRtdn(push(notification, ''), env, fakeFetch(google()))).status, 403);
  const {fetcher, calls} = fakeFetch(google({acknowledgementState: 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED'}), {rpcResult: rpcOk});
  const reply = await handleRtdn(push(notification), env, {fetcher});
  assert.equal(reply.status, 200); assert.equal((await reply.json()).status, 'active');
  assert.ok(!calls.some(c => c.url.endsWith(':acknowledge')), 'already acknowledged: no second acknowledge');
  const rpcCall = calls.find(c => c.url.includes('process_play_subscription'));
  assert.ok(rpcCall);
  // Test notifications and other packages are acknowledged without action.
  assert.equal((await (await handleRtdn(push({packageName: 'com.safenest.app', testNotification: {version: '1.0'}}), env, {fetcher})).json()).ignored, 'not_subscription');
  assert.equal((await (await handleRtdn(push({...notification, packageName: 'com.other'}), env, {fetcher})).json()).ignored, 'other_package');
  // A token the ledger has never seen returns 2xx so Pub/Sub stops retrying.
  const unbound = fakeFetch(google(), {rpcResult: {message: 'purchase_not_bound'}, rpcStatus: 400});
  assert.equal((await (await handleRtdn(push(notification), env, {fetcher: unbound.fetcher})).json()).ignored, 'purchase_not_bound');
});
