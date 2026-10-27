import test from 'node:test';
import assert from 'node:assert/strict';
import {handleCatalogue} from '../supabase/functions/_shared/payment-endpoints.mjs';

const request=()=>new Request('https://fixture.invalid/payment-catalogue');
const provider={provider:'stripe',environment:'sandbox',checkoutOpen:true,checkoutOrigins:['https://checkout.stripe.com']};

test('operator-disabled catalogue remains a successful closed response without a database call',async()=>{
  let calls=0;
  for(const adapters of [[],[{...provider,checkoutOpen:false}]]) {
    const response=await handleCatalogue(request(),{},{adapters,rpc:async()=>{calls++;throw new Error('must not call');}});
    assert.equal(response.status,200);
    assert.deepEqual(await response.json(),{available:false,products:[],providers:[]});
  }
  assert.equal(calls,0);
});

test('empty and unpayable catalogues retain open-provider metadata',async()=>{
  for(const products of [[],[{code:'orphan',providers:['nowpayments']}]]) {
    const response=await handleCatalogue(request(),{},{adapters:[provider],rpc:async()=>products});
    assert.equal(response.status,200);
    assert.deepEqual(await response.json(),{available:false,environment:'sandbox',products:[],
      providers:[{provider:'stripe',checkout_origins:provider.checkoutOrigins}]});
  }
});

test('catalogue backend failures return retryable errors without leaking internal details',async()=>{
  for(const rpc of [async()=>{throw new Error('private database credentials');},async()=>null]) {
    const response=await handleCatalogue(request(),{},{adapters:[provider],rpc});
    assert.equal(response.status,503);
    assert.equal(response.headers.get('cache-control'),'no-store');
    assert.deepEqual(await response.json(),{error:'payment_catalogue_unavailable'});
  }
});
