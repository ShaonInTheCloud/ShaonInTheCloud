import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,readdir} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';
import {stripeAdapter,verifyStripeSignature} from '../supabase/functions/_shared/stripe.mjs';
import {nowpaymentsAdapter,ipnSignature,fiatMinor,sortedJson} from '../supabase/functions/_shared/nowpayments.mjs';
import {configuredAdapter,configuredAdapters,notificationProvider,processNotification,reconcileOrder} from '../supabase/functions/_shared/payments.mjs';
import {handleCatalogue,handleReconcile} from '../supabase/functions/_shared/payment-endpoints.mjs';
import {handleOrder} from '../supabase/functions/payment-order/handler.mjs';
import {handleNotification} from '../supabase/functions/payment-notification/handler.mjs';

// Disposable local fixtures only: no real keys, accounts, prices or network.
const user='11111111-1111-4111-8111-111111111111',other='22222222-2222-4222-8222-222222222222';
const json=(body,status=200)=>Response.json(body,{status});
const stripeEnv={SUPABASE_URL:'https://fixture.supabase.invalid',STRIPE_ENABLED:'true',STRIPE_ENVIRONMENT:'sandbox',
  STRIPE_SECRET_KEY:'sk_test_fixturefixturefixture',STRIPE_WEBHOOK_SECRET:'whsec_fixturefixturefixture',STRIPE_CHECKOUT_ENABLED:'true'};
const nowEnv={SUPABASE_URL:'https://fixture.supabase.invalid',NOWPAYMENTS_ENABLED:'true',NOWPAYMENTS_ENVIRONMENT:'sandbox',
  NOWPAYMENTS_API_KEY:'FIXTURE-API-KEY-0001',NOWPAYMENTS_IPN_SECRET:'fixture-ipn-secret-0001',NOWPAYMENTS_CHECKOUT_ENABLED:'true'};
const order={id:user,provider_order_id:'SNfixture1',provider:'stripe',environment:'sandbox',amount_minor:499,currency:'USD',
  plan_code:'monthly',expires_at:new Date(Date.now()+1800000).toISOString()};
const cryptoOrder={...order,provider:'nowpayments',expires_at:new Date(Date.now()+7200000).toISOString()};
const charge={object:'charge',id:'ch_fixture1',status:'succeeded',paid:true,refunded:false,disputed:false,amount:499,currency:'usd',created:Math.floor(Date.now()/1000)-30};
const session={object:'checkout.session',id:'cs_test_fixture1',livemode:false,mode:'payment',status:'complete',payment_status:'paid',
  client_reference_id:'SNfixture1',amount_total:499,currency:'usd',payment_intent:{object:'payment_intent',id:'pi_fixture1',latest_charge:charge}};
async function signedStripe(body,secret=stripeEnv.STRIPE_WEBHOOK_SECRET,at=Math.floor(Date.now()/1000)) {
  const key=await crypto.subtle.importKey('raw',new TextEncoder().encode(secret),{name:'HMAC',hash:'SHA-256'},false,['sign']);
  const mac=Array.from(new Uint8Array(await crypto.subtle.sign('HMAC',key,new TextEncoder().encode(`${at}.${body}`))),b=>b.toString(16).padStart(2,'0')).join('');
  return new Headers({'stripe-signature':`t=${at},v1=${mac}`,'content-type':'application/json'});
}
const stripeEvent=(type='checkout.session.completed',data=session)=>JSON.stringify({object:'event',id:'evt_1',type,livemode:false,data:{object:data}});

test('every gateway stays disabled until its own complete server configuration exists',()=>{
  assert.deepEqual(configuredAdapters({}),[]);
  for (const broken of [{STRIPE_ENABLED:'true'},{...stripeEnv,STRIPE_SECRET_KEY:'sk_live_fixturefixturefixture'},
    {...stripeEnv,STRIPE_ENVIRONMENT:'live'},{...stripeEnv,STRIPE_WEBHOOK_SECRET:'nope'},
    {...nowEnv,NOWPAYMENTS_IPN_SECRET:'short'},{...nowEnv,NOWPAYMENTS_ENVIRONMENT:'live'}])
    assert.throws(()=>configuredAdapter(broken),/payment_provider_not_configured/);
  const both=configuredAdapters({...stripeEnv,...nowEnv});
  assert.deepEqual(both.map(a=>a.provider),['stripe','nowpayments']);
  assert.throws(()=>configuredAdapter({...stripeEnv,...nowEnv}),/payment_provider_not_configured/); // two configured, none named
  assert.equal(configuredAdapter({...stripeEnv,...nowEnv},undefined,fetch,'nowpayments').provider,'nowpayments');
  assert.throws(()=>configuredAdapter({...stripeEnv,...nowEnv},undefined,fetch,'sslcommerz'),/payment_provider_not_configured/);
});

test('notifications route to a configured provider by signature header or explicit name only',()=>{
  const req=(headers,url='https://fixture/notify')=>new Request(url,{method:'POST',headers});
  assert.equal(notificationProvider(req({'stripe-signature':'t=1,v1=a'})),'stripe');
  assert.equal(notificationProvider(req({'x-nowpayments-sig':'ab'})),'nowpayments');
  assert.equal(notificationProvider(req({'content-type':'application/x-www-form-urlencoded'})),'sslcommerz');
  assert.equal(notificationProvider(req({},'https://fixture/notify?provider=nowpayments')),'nowpayments');
  assert.equal(notificationProvider(req({'stripe-signature':'x'},'https://fixture/notify?provider=evil')),undefined);
  assert.equal(notificationProvider(req({})),undefined);
});

test('stripe: signature window, independent session re-fetch and exact amount/currency/reference checks',async()=>{
  const raw=stripeEvent();
  await assert.rejects(verifyStripeSignature('t=1,v1=00',raw,stripeEnv.STRIPE_WEBHOOK_SECRET),/invalid_notification/);
  const stale=await signedStripe(raw,stripeEnv.STRIPE_WEBHOOK_SECRET,Math.floor(Date.now()/1000)-900);
  await assert.rejects(verifyStripeSignature(stale.get('stripe-signature'),raw,stripeEnv.STRIPE_WEBHOOK_SECRET),/invalid_notification/);
  const wrongSecret=await signedStripe(raw,'whsec_otherotherotherother');
  const calls=[];const rpc=async(name,args)=>{calls.push(name);return name==='payment_order_lookup'?{...order,session_id:'cs_test_fixture1'}:{};};
  const fetched=[];const fetcher=async(url,init)=>{fetched.push({url,auth:init.headers.Authorization});return json(session);};
  const adapter=stripeAdapter(stripeEnv,rpc,fetcher);
  await assert.rejects(adapter.validateNotification(raw,wrongSecret),/invalid_notification/);
  assert.equal(fetched.length,0);
  const evidence=await adapter.validateNotification(raw,await signedStripe(raw));
  assert.equal(evidence.status,'paid');assert.equal(evidence.transactionId,'ch_fixture1');assert.equal(evidence.amountMinor,499);
  assert.equal(evidence.currency,'USD');assert.equal(evidence.eventId,'paid:ch_fixture1');assert.equal(evidence.validationReference,'cs_test_fixture1');
  assert.match(fetched[0].url,/^https:\/\/api\.stripe\.com\/v1\/checkout\/sessions\/cs_test_fixture1\?/);
  assert.equal(fetched[0].auth,'Bearer '+stripeEnv.STRIPE_SECRET_KEY);
  // The webhook body itself is never trusted: a tampered body with a valid signature still re-fetches and compares.
  for (const variant of [{...session,amount_total:1},{...session,currency:'eur'},{...session,client_reference_id:'SNother'},
    {...session,livemode:true},{...session,payment_intent:{...session.payment_intent,latest_charge:{...charge,amount:1}}}])
    await assert.rejects(stripeAdapter(stripeEnv,rpc,async()=>json(variant)).validateNotification(raw,await signedStripe(raw)),/payment_order_mismatch/);
  await assert.rejects(stripeAdapter(stripeEnv,rpc,async()=>json({...session,payment_status:'unpaid'})).validateNotification(raw,await signedStripe(raw)),/payment_not_validated/);
  await assert.rejects(stripeAdapter(stripeEnv,rpc,async()=>json({...session,payment_intent:{...session.payment_intent,latest_charge:{...charge,refunded:true}}})).validateNotification(raw,await signedStripe(raw)),/payment_requires_review/);
  // Unrelated event types are acknowledged (200) without any lookup so Stripe stops retrying.
  const ignored=stripeEvent('payment_intent.created',{object:'payment_intent',id:'pi_x'});
  await assert.rejects(adapter.validateNotification(ignored,await signedStripe(ignored)),e=>e.code==='event_ignored'&&e.status===200);
  // An expired session cancels the order without a receipt.
  const expired=await stripeAdapter(stripeEnv,rpc,async()=>json({...session,status:'expired',payment_status:'unpaid'})).validateNotification(raw,await signedStripe(raw));
  assert.equal(expired.status,'cancelled');assert.equal(expired.transactionId,undefined);
});

test('stripe: hosted session is created once with the ledger price, card only, and reconciliation uses the saved session',async()=>{
  const calls=[];const rpc=async(name,args)=>{calls.push({name,args});
    if(name==='payment_session_claim')return {state:'claimed'};return {};};
  const posted=[];
  const adapter=stripeAdapter(stripeEnv,rpc,async(url,init)=>{posted.push({url,init});
    return json({object:'checkout.session',id:'cs_test_new',url:'https://checkout.stripe.com/c/pay/cs_test_new'});});
  const result=await adapter.createHostedSession(order,{email:'buyer@example.com'});
  assert.equal(result.url,'https://checkout.stripe.com/c/pay/cs_test_new');
  const body=new URLSearchParams(posted[0].init.body);
  assert.equal(posted[0].url,'https://api.stripe.com/v1/checkout/sessions');
  assert.equal(body.get('line_items[0][price_data][unit_amount]'),'499');
  assert.equal(body.get('line_items[0][price_data][currency]'),'usd');
  assert.equal(body.get('payment_method_types[0]'),'card');
  assert.equal(body.get('client_reference_id'),'SNfixture1');
  assert.equal(posted[0].init.headers['Idempotency-Key'],'safenest-order-'+order.id);
  assert.deepEqual(calls.at(-1),{name:'payment_session_save',args:{p_order_id:order.id,p_session_id:'cs_test_new',p_url:result.url}});
  await assert.rejects(stripeAdapter(stripeEnv,rpc,async()=>json({object:'checkout.session',id:'cs_x',url:'https://attacker.example/'})).createHostedSession(order,{email:'buyer@example.com'}),/pending_review/);
  await assert.rejects(adapter.createHostedSession({...order,currency:'BDT',amount_minor:10},{email:'buyer@example.com'}),/invalid_provider_order/);
  await assert.rejects(adapter.createHostedSession(order,{}),/billing_details_required/);
  await assert.rejects(stripeAdapter({...stripeEnv,STRIPE_CHECKOUT_ENABLED:'false'},rpc).createHostedSession(order,{email:'b@e.com'}),/checkout_not_open/);
  const reconciled=await stripeAdapter(stripeEnv,rpc,async()=>json(session)).reconcile({...order,session_id:'cs_test_fixture1'});
  assert.equal(reconciled.status,'paid');
  await assert.rejects(stripeAdapter(stripeEnv,rpc,async()=>json(session)).reconcile(order),/payment_requires_review/);
  await assert.rejects(stripeAdapter(stripeEnv,rpc,async()=>json(session)).reconcile({...order,provider:'nowpayments'}),/reconciliation_order_mismatch/);
});

test('nowpayments: IPN signature over sorted JSON, independent payment re-fetch, final states only',async()=>{
  assert.equal(sortedJson({b:1,a:{d:[2,{z:1,y:2}],c:'x'}}),'{"a":{"c":"x","d":[2,{"y":2,"z":1}]},"b":1}');
  assert.equal(fiatMinor('4.99'),499);assert.equal(fiatMinor(4.99),499);assert.equal(fiatMinor('5'),500);assert.equal(fiatMinor('4.9900000'),499);
  assert.throws(()=>fiatMinor('4.991'),/invalid_provider_amount/);assert.throws(()=>fiatMinor('-1'),/invalid_provider_amount/);
  const payload={payment_id:7001,order_id:'SNfixture1',payment_status:'finished',price_amount:4.99,price_currency:'usd',pay_currency:'usdttrc20'};
  const raw=JSON.stringify(payload);
  const sig=await ipnSignature(nowEnv.NOWPAYMENTS_IPN_SECRET,payload);
  const headers=(s=sig)=>new Headers({'x-nowpayments-sig':s,'content-type':'application/json'});
  const finished={payment_id:7001,order_id:'SNfixture1',payment_status:'finished',price_amount:'4.99',price_currency:'usd',updated_at:new Date().toISOString()};
  const calls=[];const rpc=async(name,args)=>{calls.push({name,args});return name==='payment_order_lookup'?cryptoOrder:{};};
  const fetched=[];const adapter=nowpaymentsAdapter(nowEnv,rpc,async(url,init)=>{fetched.push({url,key:init.headers['x-api-key']});return json(finished);});
  await assert.rejects(adapter.validateNotification(raw,headers('0'.repeat(128))),/invalid_notification/);
  await assert.rejects(adapter.validateNotification(raw,headers(await ipnSignature('wrong-secret-wrong-secret',payload))),/invalid_notification/);
  assert.equal(fetched.length,0);
  const evidence=await adapter.validateNotification(raw,headers());
  assert.equal(evidence.status,'paid');assert.equal(evidence.transactionId,'7001');assert.equal(evidence.currency,'USD');assert.equal(evidence.amountMinor,499);
  assert.equal(fetched[0].url,'https://api-sandbox.nowpayments.io/v1/payment/7001');assert.equal(fetched[0].key,nowEnv.NOWPAYMENTS_API_KEY);
  assert.ok(calls.some(c=>c.name==='payment_session_note'&&c.args.p_payment_id==='7001'));
  for (const variant of [{...finished,order_id:'SNother'},{...finished,price_amount:'5.00'},{...finished,price_currency:'eur'},{...finished,payment_id:7002}])
    await assert.rejects(nowpaymentsAdapter(nowEnv,rpc,async()=>json(variant)).validateNotification(raw,headers()),/payment_order_mismatch/);
  await assert.rejects(nowpaymentsAdapter(nowEnv,rpc,async()=>json({...finished,payment_status:'confirming'})).validateNotification(raw,headers()),e=>e.code==='event_ignored'&&e.status===200);
  await assert.rejects(nowpaymentsAdapter(nowEnv,rpc,async()=>json({...finished,payment_status:'partially_paid'})).validateNotification(raw,headers()),/payment_requires_review/);
  const expired=await nowpaymentsAdapter(nowEnv,rpc,async()=>json({...finished,payment_status:'expired'})).validateNotification(raw,headers());
  assert.equal(expired.status,'cancelled');
  // A payment id the IPN names but the API attributes to a different order never gets noted as ours.
  const noted=[];await assert.rejects(nowpaymentsAdapter(nowEnv,async(n,a)=>{if(n==='payment_session_note'){noted.push(a);throw new Error('conflict');}return cryptoOrder;},
    async()=>json(finished)).validateNotification(raw,headers()),/payment_order_mismatch/);
  assert.equal(noted.length,1);
});

test('nowpayments: invoice is created with the ledger price and redirects only to the hosted invoice page',async()=>{
  const calls=[];const rpc=async(name,args)=>{calls.push({name,args});if(name==='payment_session_claim')return {state:'claimed'};return {};};
  const posted=[];
  const adapter=nowpaymentsAdapter(nowEnv,rpc,async(url,init)=>{posted.push({url,body:JSON.parse(init.body)});
    return json({id:'5551112223',invoice_url:'https://sandbox.nowpayments.io/payment/?iid=5551112223'});});
  const result=await adapter.createHostedSession(cryptoOrder,{email:'buyer@example.com'});
  assert.equal(result.url,'https://sandbox.nowpayments.io/payment/?iid=5551112223');
  assert.equal(posted[0].url,'https://api-sandbox.nowpayments.io/v1/invoice');
  assert.equal(posted[0].body.price_amount,4.99);assert.equal(posted[0].body.price_currency,'usd');assert.equal(posted[0].body.order_id,'SNfixture1');
  assert.match(posted[0].body.ipn_callback_url,/^https:\/\/fixture\.supabase\.invalid\/functions\/v1\/payment-notification\?provider=nowpayments$/);
  assert.deepEqual(calls.at(-1),{name:'payment_session_save',args:{p_order_id:cryptoOrder.id,p_session_id:'5551112223',p_url:result.url}});
  await assert.rejects(nowpaymentsAdapter(nowEnv,rpc,async()=>json({id:'1',invoice_url:'https://nowpayments.io/payment/?iid=1'})).createHostedSession(cryptoOrder,{email:'b@e.com'}),/pending_review/);
  await assert.rejects(adapter.createHostedSession(order,{email:'b@e.com'}),/invalid_provider_order/);
  await assert.rejects(adapter.reconcile(cryptoOrder),/payment_requires_review/);
  const reconciled=await nowpaymentsAdapter(nowEnv,rpc,async()=>json({payment_id:7001,order_id:'SNfixture1',payment_status:'finished',
    price_amount:4.99,price_currency:'usd',updated_at:new Date().toISOString()})).reconcile({...cryptoOrder,provider_payment_id:'7001'});
  assert.equal(reconciled.status,'paid');
});

test('order endpoint lets the buyer pick a configured method but nothing else; callbacks route by provider',async()=>{
  const env={...stripeEnv,...nowEnv,SUPABASE_ANON_KEY:'fixture-public',SUPABASE_SERVICE_ROLE_KEY:'fixture-secret'};
  const auth=async(url)=>url.endsWith('/auth/v1/user')?json({id:user,email:'b@e.com',email_confirmed_at:'2026-01-01T00:00:00Z',is_anonymous:false}):json({});
  const seen=[];
  const deps={fetcher:auth,rpc:async(name,args)=>{seen.push({name,args});return {id:order.id,state:'pending',expires_at:order.expires_at};},
    adapterFor:provider=>({provider,environment:'sandbox',checkoutOrigins:['https://checkout.fixture.invalid'],
      createHostedSession:async()=>({url:'https://checkout.fixture.invalid/'+provider})})};
  const request=(body)=>new Request('https://fixture/payment-order',{method:'POST',headers:{authorization:'Bearer fixture.jwt.token'},body:JSON.stringify(body)});
  const base={product_code:'global_monthly_v1',idempotency_key:'fixture-order-key-0001'};
  const crypto1=await handleOrder(request({...base,provider:'nowpayments'}),env,deps);
  assert.equal(crypto1.status,200);assert.equal((await crypto1.json()).checkout_url,'https://checkout.fixture.invalid/nowpayments');
  assert.equal(seen[0].args.p_provider,'nowpayments');
  assert.equal((await handleOrder(request({...base,provider:'paypal'}),env,deps)).status,400);
  assert.equal((await handleOrder(request({...base,provider:'stripe',environment:'live'}),env,deps)).status,400);
  // Real resolver: an unconfigured provider name is refused before any write.
  let writes=0;
  const refused=await handleOrder(request({...base,provider:'sslcommerz'}),env,{fetcher:auth,rpc:async()=>{writes++;}});
  assert.equal(refused.status,503);assert.equal(writes,0);
  // Notification routing picks the adapter from the request, never from the body.
  const raw=stripeEvent();
  const routed=await handleNotification(new Request('https://fixture/notify',{method:'POST',headers:await signedStripe(raw),body:raw}),env,
    {fetcher:async()=>json(session),rpc:async(name)=>name==='payment_order_lookup'?{...order,session_id:'cs_test_fixture1'}:{action:'paid'}});
  assert.equal(routed.status,200);
  const unknown=await handleNotification(new Request('https://fixture/notify?provider=evil',{method:'POST',body:raw}),env,{rpc:async()=>{writes++;}});
  assert.equal(unknown.status,503);assert.equal(writes,0);
});

test('catalogue exposes only providers with open checkout and prices they can settle; reconcile walks every provider',async()=>{
  const products=[{code:'monthly_v2',plan:'monthly',region:'bd',amount_minor:37900,currency:'BDT',duration_seconds:2592000,providers:['sslcommerz','stripe']},
    {code:'global_monthly_v1',plan:'monthly',region:'global',amount_minor:499,currency:'USD',duration_seconds:2592000,providers:['nowpayments','stripe']},
    {code:'orphan',plan:'monthly',region:'global',amount_minor:1,currency:'EUR',duration_seconds:1,providers:['sslcommerz']}];
  const adapters=[{provider:'stripe',environment:'sandbox',checkoutOpen:true,checkoutOrigins:['https://checkout.stripe.com']},
    {provider:'nowpayments',environment:'sandbox',checkoutOpen:false,checkoutOrigins:['https://sandbox.nowpayments.io']}];
  const body=await (await handleCatalogue(new Request('https://fixture'),{},{rpc:async()=>products,adapters})).json();
  assert.equal(body.available,true);assert.equal(body.environment,'sandbox');
  assert.deepEqual(body.providers,[{provider:'stripe',checkout_origins:['https://checkout.stripe.com']}]);
  assert.deepEqual(body.products.map(p=>[p.code,p.providers]),[['monthly_v2',['stripe']],['global_monthly_v1',['stripe']]]);
  const closed=await (await handleCatalogue(new Request('https://fixture'),{},{rpc:async()=>products,adapters:adapters.map(a=>({...a,checkoutOpen:false}))})).json();
  assert.deepEqual(closed,{available:false,products:[],providers:[]});
  const mixed=await handleCatalogue(new Request('https://fixture'),{},{rpc:async()=>products,adapters:[adapters[0],{...adapters[1],checkoutOpen:true,environment:'live'}]});
  assert.equal(mixed.status,503);
  assert.deepEqual(await mixed.json(),{error:'payment_catalogue_unavailable'});
  const batches=[];const key='k'.repeat(40);
  const reconcile=await handleReconcile(new Request('https://fixture',{method:'POST',headers:{'x-reconcile-key':key}}),{PAYMENT_RECONCILE_KEY:key},
    {rpc:async(name,args)=>{batches.push(args.p_provider);return [];},adapters});
  assert.deepEqual(await reconcile.json(),{processed:0,review:0,failed:0});assert.deepEqual(batches,['stripe','nowpayments']);
});

test('gateway SQL: regions, provider currencies, payment windows, checkout patterns and provider-scoped lookups',async()=>{
  const db=new PGlite();try{
    await db.exec(`create role anon;create role authenticated;create role service_role bypassrls;
      create schema auth;create table auth.users(id uuid primary key,email_confirmed_at timestamptz default now(),is_anonymous boolean default false);
      create function auth.uid() returns uuid language sql as $$select null::uuid$$;
      grant usage on schema auth,public to anon,authenticated,service_role;grant select,update on auth.users to service_role;
      insert into auth.users values('${user}'),('${other}');`);
    const dir=new URL('../supabase/migrations/',import.meta.url);
    const names=(await readdir(dir)).filter(x=>/protection_entitlements|payment_foundation|sslcommerz_gateway|trial_subscriptions|card_crypto_gateways/.test(x)).sort();
    for(const name of names)await db.exec(await readFile(new URL(name,dir),'utf8'));
    // Nothing is payable after migration: providers off, global prices off, mode disabled.
    assert.equal((await db.query(`select count(*)::int n from payments.providers where enabled`)).rows[0].n,0);
    assert.equal((await db.query(`select count(*)::int n from payments.products where enabled`)).rows[0].n,0);
    assert.equal((await db.query(`select count(*)::int n from payments.products where region='global' and currency='USD'`)).rows[0].n,6);
    await assert.rejects(db.query(`insert into payments.products values('bad','sandbox','monthly',100,'USD',60,false,'bd')`),/products_region_currency/);
    await db.exec(`update payments.configuration set mode='sandbox';
      update payments.providers set enabled=true where environment='sandbox' and provider in ('stripe','nowpayments');
      update payments.products set enabled=true where environment='sandbox';set role service_role;`);
    const catalogue=(await db.query(`select public.payment_product_catalogue('sandbox') c`)).rows[0].c;
    const byCode=Object.fromEntries(catalogue.map(p=>[p.code,p]));
    assert.deepEqual(byCode.monthly_v2.providers,['stripe']);assert.equal(byCode.monthly_v2.region,'bd');
    assert.deepEqual(byCode.global_monthly_v1.providers,['nowpayments','stripe']);assert.equal(byCode.global_monthly_v1.region,'global');
    const card=(await db.query(`select public.create_payment_order($1,'global_monthly_v1','stripe','sandbox','fixture-key-card-0001') o`,[user])).rows[0].o;
    const coin=(await db.query(`select public.create_payment_order($1,'global_annual_v1','nowpayments','sandbox','fixture-key-coin-0001') o`,[user])).rows[0].o;
    assert.equal(Math.round((Date.parse(card.expires_at)-Date.parse(card.created_at))/60000),30);
    assert.equal(Math.round((Date.parse(coin.expires_at)-Date.parse(coin.created_at))/60000),120);
    await assert.rejects(db.query(`select public.create_payment_order($1,'monthly_v2','nowpayments','sandbox','fixture-key-coin-0002')`,[user]),/provider_currency_unsupported/);
    await assert.rejects(db.query(`select public.create_payment_order($1,'monthly_v2','sslcommerz','sandbox','fixture-key-ssl-0001')`,[user]),/provider_disabled/);
    const coin2=(await db.query(`select public.create_payment_order($1,'global_annual_v1','nowpayments','sandbox','fixture-key-coin-0003') o`,[user])).rows[0].o;
    const card2=(await db.query(`select public.create_payment_order($1,'global_annual_v1','stripe','sandbox','fixture-key-card-0003') o`,[user])).rows[0].o;
    for (const [id,url,ok] of [[card.id,'https://checkout.stripe.com/c/pay/cs_1',true],[coin.id,'https://sandbox.nowpayments.io/payment/?iid=1',true],
      [coin2.id,'https://nowpayments.io/payment/?iid=1',false],[card2.id,'https://sandbox.nowpayments.io/payment/?iid=1',false]]) {
      await db.query('select public.payment_session_claim($1)',[id]);
      const attempt=db.query("select public.payment_session_save($1,'s1',$2)",[id,url]);
      if(ok) await attempt; else await assert.rejects(attempt,/invalid_session/);
    }
    await db.query(`select public.payment_session_note($1,'pay-1')`,[coin.id]);
    await assert.rejects(db.query(`select public.payment_session_note($1,'pay-2')`,[coin.id]),/session_payment_conflict/);
    assert.equal((await db.query(`select public.payment_order_lookup($1,'stripe','sandbox') r`,[coin.provider_order_id])).rows[0].r,null);
    const found=(await db.query(`select public.payment_order_lookup($1,'nowpayments','sandbox') r`,[coin.provider_order_id])).rows[0].r;
    assert.equal(found.provider_payment_id,'pay-1');assert.equal(found.session_id,'s1');
    await db.exec(`reset role;update payments.orders set created_at=created_at-interval '10 minutes';set role service_role`);
    const batch=(await db.query(`select public.payment_reconciliation_batch('nowpayments','sandbox') r`)).rows.map(r=>r.r);
    assert.equal(batch.length,2);assert.equal(batch.find(o=>o.id===coin.id).provider_payment_id,'pay-1');assert.equal(batch.find(o=>o.id===coin2.id).provider_payment_id,null);
    assert.equal((await db.query(`select public.payment_reconciliation_batch('stripe','sandbox') r`)).rows.find(r=>r.r.id===card.id).r.session_id,'s1');
    await db.exec('reset role;set role authenticated');
    await assert.rejects(db.query(`select public.payment_order_lookup('x','stripe','sandbox')`),/permission denied/);
    await assert.rejects(db.query(`select public.payment_session_note($1,'x')`,[coin.id]),/permission denied/);
    await assert.rejects(db.query('select * from payments.providers'),/permission denied/);
  }finally{await db.close();}
});
