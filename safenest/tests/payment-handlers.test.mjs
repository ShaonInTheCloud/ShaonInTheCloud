import test from 'node:test';
import assert from 'node:assert/strict';
import {handleOrder} from '../supabase/functions/payment-order/handler.mjs';
import {handleNotification} from '../supabase/functions/payment-notification/handler.mjs';
import {configuredAdapter, PaymentError, validatedArguments, processNotification,
  checkoutUrl, readLimitedBody, serviceRpc, reconcileOrder} from '../supabase/functions/_shared/payments.mjs';

const userId='11111111-1111-4111-8111-111111111111';
const orderId='22222222-2222-4222-8222-222222222222';
const env={SUPABASE_URL:'https://fixture.invalid',SUPABASE_ANON_KEY:'fixture-public',SUPABASE_SERVICE_ROLE_KEY:'fixture-secret'};
const body={product_code:'fixture_month',idempotency_key:'fixture-order-key-0001'};
const request=(data=body,headers={})=>new Request('https://fixture.invalid/payment-order',{
  method:'POST',headers:{authorization:'Bearer fixture.jwt.token',...headers},body:JSON.stringify(data)});
const auth=async()=>Response.json({id:userId,email_confirmed_at:'2026-01-01T00:00:00Z',is_anonymous:false});
const valid={orderId,provider:'fixture',environment:'sandbox',eventId:'fixture-event',
  transactionId:'fixture-receipt',status:'paid',amountMinor:1200,currency:'BDT',
  paidAt:'2026-10-06T10:00:00Z',validationReference:'fixture-validation',evidenceSha256:'a'.repeat(64)};
const adapter={provider:'fixture',environment:'sandbox',checkoutOrigins:['https://checkout.fixture.invalid'],
  validateNotification:async()=>valid,createHostedSession:async()=>({url:'https://checkout.fixture.invalid/session'})};

test('production adapter stays unavailable regardless of fake flags or credentials',async()=>{
  assert.throws(()=>configuredAdapter({PAYMENTS_ENABLED:'true',mode:'live'}),/payment_provider_not_configured/);
  let writes=0;
  const response=await handleOrder(request(),{...env,PAYMENTS_ENABLED:'true'},
    {fetcher:auth,rpc:async()=>{writes++;}});
  assert.equal(response.status,503); assert.equal(writes,0);
  assert.deepEqual(await response.json(),{error:'payment_provider_not_configured'});
  const callback=await handleNotification(new Request('https://fixture.invalid/notification',{
    method:'POST',body:JSON.stringify({...valid,verified:true,mode:'live'})}),env,{rpc:async()=>{writes++;}});
  assert.equal(callback.status,503); assert.equal(writes,0);
});

test('order checks live authentication, confirmation, method and allowed origin before writes',async()=>{
  let writes=0; const deps={fetcher:auth,adapterFor:()=>adapter,rpc:async()=>{writes++;}};
  assert.equal((await handleOrder(request(body,{authorization:''}),env,deps)).status,401);
  assert.equal((await handleOrder(request(),env,{...deps,fetcher:async()=>Response.json({}, {status:401})})).status,401);
  for (const user of [{id:userId,is_anonymous:true,email_confirmed_at:'yes'}, {id:userId}, {id:'invalid',email_confirmed_at:'yes'}])
    assert.equal((await handleOrder(request(),env,{...deps,fetcher:async()=>Response.json(user)})).status,403);
  assert.equal((await handleOrder(request(body,{origin:'https://evil.invalid'}),env,deps)).status,403);
  assert.equal((await handleOrder(new Request('https://fixture.invalid',{method:'GET'}),env,deps)).status,405);
  assert.equal(writes,0);
});

test('order rejects buyer/price/duration/provider overrides and derives identity server-side',async()=>{
  let calls=[];
  const deps={fetcher:auth,adapterFor:()=>adapter,rpc:async(name,args)=>{
    calls.push({name,args}); return {id:orderId,state:'pending',expires_at:new Date(Date.now()+60000).toISOString()};
  }};
  for (const change of [{user_id:userId},{amount_minor:1},{duration_seconds:1},{provider:'evil'},
    {environment:'live'},{paid:true},{product_code:'bad product'},{product_code:['fixture_month']},{idempotency_key:'short'}])
    assert.equal((await handleOrder(request({...body,...change}),env,deps)).status,400);
  assert.equal(calls.length,0);
  const response=await handleOrder(request(),env,deps);
  assert.equal(response.status,200);
  assert.deepEqual(calls,[{name:'create_payment_order',args:{p_user_id:userId,
    p_product_code:'fixture_month',p_provider:'fixture',p_environment:'sandbox',p_idempotency_key:body.idempotency_key}}]);
  assert.deepEqual(await response.json(),{order_id:orderId,checkout_url:'https://checkout.fixture.invalid/session'});
  assert.equal(response.headers.get('cache-control'),'no-store');
});

test('session outage and nonpayable orders do not attempt payment processing',async()=>{
  const calls=[];
  const deps={fetcher:auth,adapterFor:()=>({...adapter,createHostedSession:async()=>{throw new Error('fixture outage');}}),
    rpc:async(name)=>{calls.push(name);return {state:'pending',expires_at:new Date(Date.now()+60000).toISOString()};}};
  assert.equal((await handleOrder(request(),env,deps)).status,503);
  assert.deepEqual(calls,['create_payment_order']);
  for (const order of [{state:'paid',expires_at:'2099-01-01'}, {state:'pending',expires_at:'2000-01-01'}]) {
    let sessions=0;
    const response=await handleOrder(request(),env,{fetcher:auth,rpc:async()=>order,
      adapterFor:()=>({...adapter,createHostedSession:async()=>{sessions++;}})});
    assert.equal(response.status,409);assert.equal(sessions,0);
  }
});

test('checkout redirect accepts only explicit HTTPS provider origins',()=>{
  assert.equal(checkoutUrl(adapter,'https://checkout.fixture.invalid/session'), 'https://checkout.fixture.invalid/session');
  for(const url of ['http://checkout.fixture.invalid','https://checkout.fixture.invalid.evil.invalid',
    'https://user:password@checkout.fixture.invalid','javascript:alert(1)','https://evil.invalid','invalid'])
    assert.throws(()=>checkoutUrl(adapter,url),/invalid_provider_checkout/);
});

test('unverified raw callback never reaches ledger, even with a forged verified flag',async()=>{
  let writes=0;
  const rejecting={...adapter,validateNotification:async()=>{throw new PaymentError('notification_not_verified',400);}};
  await assert.rejects(processNotification(rejecting,JSON.stringify({...valid,verified:true}),new Headers(),async()=>{writes++;}),/notification_not_verified/);
  const response=await handleNotification(new Request('https://fixture.invalid',{method:'POST',body:'forged'}),env,
    {adapterFor:()=>rejecting,rpc:async()=>{writes++;}});
  assert.equal(response.status,400); assert.equal(writes,0);
});

test('independent validation completes before processing; persisted evidence is strictly whitelisted',async()=>{
  const calls=[];
  const verifying={...adapter,validateNotification:async(raw,headers)=>{
    calls.push('validate');assert.equal(raw,'raw-callback');assert.equal(headers.get('fixture-signature'),'example');
    return {...valid,card_number:'DO-NOT-PERSIST',cvv:'DO-NOT-PERSIST',email:'DO-NOT-PERSIST'};
  }};
  const output=await processNotification(verifying,'raw-callback',new Headers({'fixture-signature':'example'}),async(name,args)=>{
    calls.push('rpc');assert.equal(name,'process_validated_payment');
    assert.deepEqual(args,validatedArguments(adapter,valid));return {duplicate:true};
  });
  assert.deepEqual(calls,['validate','rpc']);assert.deepEqual(output,{duplicate:true});
  const response=await handleNotification(new Request('https://fixture.invalid',{method:'POST',body:'raw'}),env,
    {adapterFor:()=>adapter,rpc:async()=>({entitlement_id:'private'})});
  assert.deepEqual(await response.json(),{received:true});
});

test('adapter output rejects wrong environment/provider and unsafe monetary/time/reference values',()=>{
  for(const change of [{environment:'live'},{provider:'unknown'},{orderId:'invalid'},{amountMinor:1.2},
    {amountMinor:9007199254740992},{amountMinor:'1200'},{currency:'bdt'},{eventId:''},
    {validationReference:null},{status:'unknown'},{transactionId:null},{paidAt:'invalid'}, {evidenceSha256:'invalid'}])
    assert.throws(()=>validatedArguments(adapter,{...valid,...change}),/invalid_provider_evidence/);
  const pending={...valid,status:'pending',transactionId:null,paidAt:null};
  assert.equal(validatedArguments(adapter,pending).p_paid_at,null);
  assert.throws(()=>validatedArguments(adapter,{...pending,transactionId:'receipt'}),/invalid_provider_evidence/);
});

test('bounded streaming reads reject oversized content even without Content-Length',async()=>{
  await assert.rejects(readLimitedBody(new Request('https://fixture.invalid',{method:'POST',body:'x'.repeat(16385)})),/invalid_request/);
  const response=await handleOrder(request({...body,product_code:'x'.repeat(20000)}),env,{fetcher:auth});
  assert.equal(response.status,413);
});

test('provider outage returns retryable non-success with no ledger writes or leaked error detail',async()=>{
  let calls=0;
  const response=await handleNotification(new Request('https://fixture.invalid',{method:'POST',body:'example'}),env,{
    adapterFor:()=>({...adapter,validateNotification:async()=>{throw new Error('fixture merchant secret');}}),
    rpc:async()=>{calls++;}});
  assert.equal(response.status,503); assert.equal(calls,0);
  assert.deepEqual(await response.json(),{error:'payment_verification_unavailable'});
});

test('service RPC uses server key, refuses redirects and exposes no upstream errors',async()=>{
  const rpc=serviceRpc(env,async(url,init)=>{
    assert.equal(url,'https://fixture.invalid/rest/v1/rpc/process_validated_payment');
    assert.equal(init.headers.apikey,'fixture-secret');assert.equal(init.redirect,'error');
    return Response.json({message:'fixture secret',code:'P0001'},{status:400});
  });
  await assert.rejects(rpc('process_validated_payment',{}),/payment_processing_unavailable/);
});

test('reconciliation shares the validated processor and rejects evidence for another order',async()=>{
  const order={id:orderId,provider:'fixture',environment:'sandbox'};
  let writes=0;
  const provider={...adapter,reconcile:async()=>valid};
  await reconcileOrder(provider,order,async(name,args)=>{
    writes++;assert.equal(name,'process_validated_payment');assert.equal(args.p_order_id,orderId);
  });
  await assert.rejects(reconcileOrder(provider,{...order,id:userId},async()=>{writes++;}),/reconciliation_order_mismatch/);
  assert.equal(writes,1);
});
