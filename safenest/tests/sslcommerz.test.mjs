import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,readdir} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';
import {sslcommerzAdapter,moneyMinor,providerTime} from '../supabase/functions/_shared/sslcommerz.mjs';
import {handleCatalogue,handleStatus,handleReturn,handleReconcile} from '../supabase/functions/_shared/payment-endpoints.mjs';
const env={SSLCOMMERZ_ENABLED:'true',SSLCOMMERZ_ENVIRONMENT:'sandbox',SSLCOMMERZ_TIMESTAMP_ZONE:'Asia/Dhaka',
  SSLCOMMERZ_SANDBOX_STORE_ID:'fixture',SSLCOMMERZ_SANDBOX_STORE_PASSWORD:'local-only',
  SSLCOMMERZ_CHECKOUT_ENABLED:'true',SUPABASE_URL:'https://fixture.supabase.co',SUPABASE_ANON_KEY:'fixture'};
const user='11111111-1111-4111-8111-111111111111',other='22222222-2222-4222-8222-222222222222';
const order={id:user,provider_order_id:'SNfixture1',provider:'sslcommerz',environment:'sandbox',amount_minor:37900,currency:'BDT'};
const validation={APIConnect:'DONE',status:'VALID',val_id:'fixtureval',tran_id:order.provider_order_id,
  currency:'BDT',currency_type:'BDT',amount:'379.00',currency_amount:'379',risk_level:'0',
  bank_tran_id:'fixturebank',tran_date:'2026-10-06 12:00:00'};
const headers=new Headers({'content-type':'application/x-www-form-urlencoded'});
const notify='tran_id=SNfixture1&val_id=fixtureval&status=VALID&amount=1&verified=true';
const json=x=>new Response(JSON.stringify(x),{headers:{'Content-Type':'application/json'}});
test('strict monetary parsing and confirmed Dhaka timestamps reject ambiguous values',()=>{
  assert.equal(moneyMinor('379.1'),37910);assert.equal(providerTime(validation.tran_date),'2026-10-06T06:00:00.000Z');
  for(const v of ['3e2','379.001','-379','NaN',379,'0'])assert.throws(()=>moneyMinor(v));
  for(const v of ['2026-02-30 12:00:00','2026-10-06T12:00:00Z','invalid'])assert.throws(()=>providerTime(v));
});
test('merchant gates never infer live mode from sandbox credentials',()=>{
  for(const change of [{SSLCOMMERZ_ENABLED:''},{SSLCOMMERZ_ENVIRONMENT:'live'},
    {SSLCOMMERZ_TIMESTAMP_ZONE:''},{SSLCOMMERZ_SANDBOX_STORE_PASSWORD:''}])
    assert.throws(()=>sslcommerzAdapter({...env,...change},()=>{}));
});
test('notification ignores forged browser evidence and independently checks the fixed provider API',async()=>{
  let calls=0;
  const adapter=sslcommerzAdapter(env,async()=>order,async(url,options)=>{
    calls++;assert.equal(new URL(url).origin,'https://sandbox.sslcommerz.com');assert.equal(options.redirect,'error');return json(validation);
  });
  const a=await adapter.validateNotification(notify,headers);assert.equal(a.amountMinor,37900);assert.equal(calls,1);
  const b=await sslcommerzAdapter(env,async()=>order,async()=>json({...validation,status:'VALIDATED',card_no:'ignored'})).validateNotification(notify,headers);
  assert.equal(a.evidenceSha256,b.evidenceSha256);assert.equal(a.eventId,b.eventId);assert.equal('card_no' in a,false);
  await assert.rejects(adapter.validateNotification(notify+'&val_id=duplicate',headers),/invalid_notification/);
});
test('wrong amounts, currency, merchant, references and risk never validate',async()=>{
  for(const change of [{amount:'350.00'},{currency_amount:'350'},{currency:'USD'},{store_id:'another'},
    {tran_id:'another'},{val_id:'another'},{risk_level:'1'},{status:'FAILED'},{APIConnect:'FAIL'}]){
    const adapter=sslcommerzAdapter(env,async()=>order,async()=>json({...validation,...change}));
    await assert.rejects(adapter.validateNotification(notify,headers));
  }
});
test('saved session is reused and uncertain creation cannot create another payable session',async()=>{
  const customer={name:'Local fixture',email:'fixture@example.com',phone:'01000000000',address:'Local',city:'Dhaka'};
  let calls=0,review=false;
  const reused=sslcommerzAdapter(env,async()=>({state:'ready',checkout_url:'https://sandbox.sslcommerz.com/fixture'}),async()=>{calls++;});
  assert.equal((await reused.createHostedSession(order,customer)).url,'https://sandbox.sslcommerz.com/fixture');assert.equal(calls,0);
  const adapter=sslcommerzAdapter(env,async(name)=>{if(name==='payment_session_review')review=true;return {state:review?'review':'claimed'};},async()=>{calls++;throw new Error('timeout');});
  await assert.rejects(adapter.createHostedSession(order,customer),/pending_review/);
  await assert.rejects(adapter.createHostedSession(order,customer),/pending_review/);assert.equal(calls,1);
});
test('hosted session contains only server price and rejects external checkout origins',async()=>{
  const customer={name:'Local',email:'fixture@example.com',phone:'01000000000',address:'Local',city:'Dhaka'};
  const adapter=sslcommerzAdapter(env,async()=>({state:'claimed'}),async(url,options)=>{
    const body=new URLSearchParams(options.body);assert.equal(body.get('total_amount'),'379.00');
    assert.equal(body.get('tran_id'),order.provider_order_id);assert.equal(body.get('shipping_method'),'NO');
    assert.equal(body.get('ipn_url'),'https://fixture.supabase.co/functions/v1/payment-notification');
    return json({status:'SUCCESS',sessionkey:'fixture',GatewayPageURL:'https://attacker.example/'});
  });
  await assert.rejects(adapter.createHostedSession(order,customer),/pending_review/);
});
test('reconciliation independently validates a single transaction; ambiguous payments require review',async()=>{
  let calls=0;const adapter=sslcommerzAdapter(env,async()=>order,async(url)=>{
    calls++;return json(url.includes('merchantTransID')?{APIConnect:'DONE',element:[validation]}:validation);
  });
  assert.equal((await adapter.reconcile(order)).status,'paid');assert.equal(calls,2);
  await assert.rejects(sslcommerzAdapter(env,async()=>order,async()=>json({APIConnect:'DONE',element:[validation,validation]})).reconcile(order),/review/);
});
test('disabled catalogue, untrusted redirects and unauthorized reconciliation grant nothing',async()=>{
  let calls=0;const rpc=async()=>{calls++;};
  assert.deepEqual(await (await handleCatalogue(new Request('https://fixture'),{}, {rpc})).json(),{available:false,products:[]});
  const returned=await handleReturn(new Request('https://fixture?status=paid&redirect=https://attacker.example'),{});
  assert.equal(returned.status,303);assert.equal(returned.headers.get('location'),'https://mysafenestbd.com/account.html?payment=checking');
  assert.equal((await handleReconcile(new Request('https://fixture',{method:'POST'}),env,{rpc})).status,401);assert.equal(calls,0);
});
test('status derives ownership from live authentication, never a body user ID',async()=>{
  const req=body=>new Request('https://fixture',{method:'POST',headers:{Authorization:'Bearer fixture'},body:JSON.stringify(body)});
  const deps={fetcher:async()=>json({id:user,email_confirmed_at:'fixture'}),rpc:async(name,args)=>{assert.equal(args.p_user_id,user);return null;}};
  assert.equal((await handleStatus(req({order_id:other}),env,deps)).status,404);
  assert.equal((await handleStatus(req({order_id:other,user_id:other}),env,deps)).status,400);
});
test('actual gateway SQL keeps sessions private, claims once, scopes status and defaults off',async()=>{
  const db=new PGlite();try{
    await db.exec(`create role anon;create role authenticated;create role service_role bypassrls;
      create schema auth;create table auth.users(id uuid primary key);create function auth.uid() returns uuid language sql as $$select null::uuid$$;
      grant usage on schema auth,public to anon,authenticated,service_role;insert into auth.users values('${user}'),('${other}');`);
    const dir=new URL('../supabase/migrations/',import.meta.url);
    const names=(await readdir(dir)).filter(x=>/protection_entitlements|payment_foundation|sslcommerz_gateway/.test(x)).sort();
    for(const name of names)await db.exec(await readFile(new URL(name,dir),'utf8'));
    assert.equal((await db.query('select mode from payments.configuration')).rows[0].mode,'disabled');
    assert.equal((await db.query('select count(*)::int n from payments.sessions')).rows[0].n,0);
    await db.exec(`update payments.configuration set mode='sandbox';insert into payments.providers values('sslcommerz','sandbox',true);
      insert into payments.products values('fixture','sandbox','monthly',37900,'BDT',2592000,true);set role service_role;`);
    const o=(await db.query(`select public.create_payment_order($1,'fixture','sslcommerz','sandbox','fixture-key-local-0001') result`,[user])).rows[0].result;
    assert.equal(o.provider_order_id.length,30);
    assert.equal((await db.query('select public.payment_session_claim($1) result',[o.id])).rows[0].result.state,'claimed');
    assert.equal((await db.query('select public.payment_session_claim($1) result',[o.id])).rows[0].result.state,'initializing');
    await db.query("select public.payment_session_save($1,'fixture','https://sandbox.sslcommerz.com/fixture')",[o.id]);
    assert.equal((await db.query('select public.payment_session_claim($1) result',[o.id])).rows[0].result.state,'ready');
    assert.equal((await db.query('select public.payment_order_status($1,$2) result',[o.id,other])).rows[0].result,null);
    assert.equal((await db.query('select public.payment_order_lookup($1,$2) result',[o.provider_order_id,'live'])).rows[0].result,null);
    for(let i=1;i<10;i++)await db.query(`select public.create_payment_order($1,'fixture','sslcommerz','sandbox',$2)`,[user,'fixture-rate-key-'+String(i).padStart(4,'0')]);
    await assert.rejects(db.query(`select public.create_payment_order($1,'fixture','sslcommerz','sandbox','fixture-rate-key-over')`,[user]),/order_rate_limited/);
    assert.equal((await db.query(`select public.create_payment_order($1,'fixture','sslcommerz','sandbox','fixture-key-local-0001') result`,[user])).rows[0].result.id,o.id);
    await db.exec('reset role;set role authenticated');
    await assert.rejects(db.query('select public.payment_order_status($1,$2)',[o.id,user]),/permission denied/);
    await assert.rejects(db.query('select * from payments.sessions'),/permission denied/);
  }finally{await db.close();}
});
