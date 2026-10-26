import test from 'node:test';
import assert from 'node:assert/strict';
import {handleReconcile, handleStatus} from '../supabase/functions/_shared/payment-endpoints.mjs';

const key = 'fixture-reconcile-secret-'.padEnd(48, 'x');
const env = {SSLCOMMERZ_ENABLED:'true', SSLCOMMERZ_ENVIRONMENT:'sandbox',
  SSLCOMMERZ_TIMESTAMP_ZONE:'Asia/Dhaka', SSLCOMMERZ_SANDBOX_STORE_ID:'fixture',
  SSLCOMMERZ_SANDBOX_STORE_PASSWORD:'local-only', SUPABASE_URL:'https://fixture.invalid',
  SUPABASE_ANON_KEY:'fixture', SSLCOMMERZ_RECONCILE_KEY:key};
const req = (supplied, body, method='POST') => new Request('https://fixture.invalid', {
  method, ...(supplied === undefined ? {} : {headers:{'x-reconcile-key':supplied}}),
  ...(body === undefined ? {} : {body})});

test('reconcile rejects absent, short, wrong-length and equal-length wrong keys before any backend access', async () => {
  let calls=0;
  const deps={fetcher:async()=>{calls++;throw Error('unexpected network');},rpc:async()=>{calls++;}};
  for (const expected of [undefined, '', 'x'.repeat(31), key]) {
    for (const actual of [undefined, '', 'x'.repeat(31), 'x'.repeat(key.length), key+'x'])
      assert.equal((await handleReconcile(req(actual), {...env,SSLCOMMERZ_RECONCILE_KEY:expected}, deps)).status,401);
  }
  assert.equal(calls,0);
});

test('reconcile requires POST, a strong matching key and an empty body before reading orders', async () => {
  let calls=0;
  const deps={fetcher:async()=>{throw Error('unexpected network');},rpc:async(name,args)=>{
    calls++;assert.equal(name,'payment_reconciliation_batch');assert.deepEqual(args,{p_environment:'sandbox'});return [];
  }};
  assert.equal((await handleReconcile(req(key,undefined,'GET'),env,deps)).status,405);
  assert.equal((await handleReconcile(req(key,'{}'),env,deps)).status,400);
  assert.equal(calls,0);
  const result=await handleReconcile(req(key),env,deps);
  assert.equal(result.status,200);
  assert.deepEqual(await result.json(),{processed:0,review:0});assert.equal(calls,1);
});

test('status rejects absent, invalid, anonymous and unconfirmed customer authentication before RPC', async () => {
  let calls=0;
  const request=authorization=>new Request('https://fixture.invalid',{method:'POST',headers:{authorization},
    body:JSON.stringify({order_id:'11111111-1111-4111-8111-111111111111'})});
  const rpc=async()=>{calls++;};
  for (const authorization of ['', 'Basic fixture']) {
    assert.equal((await handleStatus(request(authorization),env,{rpc,fetcher:async()=>{calls++;}})).status,401);
  }
  assert.equal((await handleStatus(request('Bearer forged'),env,{rpc,fetcher:async()=>new Response(null,{status:401})})).status,401);
  for (const user of [{id:'11111111-1111-4111-8111-111111111111'},
    {id:'11111111-1111-4111-8111-111111111111',is_anonymous:true,email_confirmed_at:'fixture'}]) {
    assert.equal((await handleStatus(request('Bearer fixture'),env,{rpc,fetcher:async()=>Response.json(user)})).status,403);
  }
  assert.equal(calls,0);
});
