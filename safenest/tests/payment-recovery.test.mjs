import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,readdir} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';
import {validatedRecoveryArguments,processNotification} from '../supabase/functions/_shared/payments.mjs';
import {normalizeVerifiedRecovery,sslcommerzAdapter} from '../supabase/functions/_shared/sslcommerz.mjs';
import {handleReconcile} from '../supabase/functions/_shared/payment-endpoints.mjs';
import {fixture,order,evidence,rpc,recover,paid,buyer,lease,adapter,proof} from './helpers/payment-fixture.mjs';
const query=async(db,sql)=>(await db.query(sql)).rows;

test('full refund and chargeback are idempotent terminal states; late paid/processing never resurrect',async()=>{
  const db=await fixture();try{
    const o=await order(db),p=await evidence(db,o);await paid(db,p);
    assert.equal((await recover(db,p)).action,'revoked');assert.equal((await recover(db,p)).duplicate,true);
    await recover(db,p,{state:'pending'});await paid(db,{...p,eventId:'late-paid'});
    assert.equal((await query(db,'select status from payments.sandbox_entitlements'))[0].status,'revoked');
    await recover(db,p,{kind:'chargeback',id:'disputefixture'});await paid(db,{...p,status:'refunded',eventId:'late-legacy-refund'});
    assert.equal((await query(db,'select state from payments.orders'))[0].state,'chargeback');
    assert.equal((await query(db,'select state from payments.receipts'))[0].state,'chargeback');
    assert.equal((await query(db,"select state from payments.recoveries where recovery_id='refundfixture'"))[0].state,'completed');
  }finally{await db.close();}
});
test('partial/unknown refunds preserve existing window; reviewed cumulative full refund revokes once',async()=>{
  const db=await fixture();try{
    const o=await order(db),p=await evidence(db,o);await paid(db,p);
    const before=await query(db,'select * from payments.sandbox_entitlements');
    assert.equal((await recover(db,p,{amountMinor:300})).action,'recovery_requires_review');
    await recover(db,p,{id:'unknown',amountMinor:null});
    assert.deepEqual(await query(db,'select * from payments.sandbox_entitlements'),before);
    await assert.rejects(recover(db,p,{amountMinor:600}),/recovery_identity_conflict/);
    await recover(db,p,{id:'refund-rest',amountMinor:900});
    assert.equal((await query(db,'select state from payments.orders'))[0].state,'refunded');
    const events=(await query(db,'select count(*)::int n from payments.events'))[0].n;
    await assert.rejects(recover(db,p,{id:'over-refund',amountMinor:1}),/refund_total_exceeds_payment/);
    assert.equal((await query(db,'select count(*)::int n from payments.events'))[0].n,events);
    assert.equal((await query(db,'select count(*)::int n from public.protection_entitlements'))[0].n,0);
  }finally{await db.close();}
});
test('review preceding paid holds new grants; cancellation clears only its own pending review',async()=>{
  const db=await fixture();try{
    const o=await order(db),p=await evidence(db,o);
    await recover(db,p,{state:'pending',amountMinor:null});
    assert.equal((await paid(db,p)).action,'recovery_review_prevents_new_grant');
    assert.equal((await query(db,'select count(*)::int n from payments.sandbox_entitlements'))[0].n,0);
    await recover(db,p,{state:'cancelled',amountMinor:null});
    await paid(db,{...p,eventId:'fresh-paid'});
    assert.equal((await query(db,'select count(*)::int n from payments.sandbox_entitlements'))[0].n,1);
  }finally{await db.close();}
});
test('unknown completion can gain verified amount without double counting or changed identity',async()=>{
  const db=await fixture();try{
    const o=await order(db),p=await evidence(db,o);
    await recover(db,p,{amountMinor:null});await recover(db,p);
    assert.equal((await paid(db,p)).action,'reversal_prevents_grant');
    assert.equal((await query(db,'select count(*)::int n from payments.recoveries'))[0].n,1);
    const other=await order(db,'fixture-payment-order-0002'),p2=await evidence(db,other);
    await assert.rejects(recover(db,p2),/recovery_identity_conflict/);
    for(const role of ['anon','authenticated']) {
      await db.exec('reset role;set role '+role);
      await assert.rejects(recover(db,p),/permission denied/);
      await assert.rejects(db.query('select * from payments.recoveries'),/permission denied/);
      await assert.rejects(db.query('select payments.enqueue_reconciliation()'),/permission denied/);
    }
  }finally{await db.close();}
});
test('recovery boundary rejects missing/unsafe amounts and persists only normalized fields',async()=>{
  const e=await normalizeVerifiedRecovery(proof,{kind:'chargeback',id:'dispute',state:'completed',amountMinor:1200});
  for(const change of [{recoveryAmountMinor:undefined},{recoveryAmountMinor:0},{recoveryAmountMinor:1201},
    {recoveryAmountMinor:0.5},{recoveryState:'won'},{kind:'unknown'},{transactionId:null},{environment:'live'}])
    assert.throws(()=>validatedRecoveryArguments(adapter,{...e,...change}));
  const args=validatedRecoveryArguments(adapter,{...e,cvv:'private',raw:'private'});
  assert.equal('cvv' in args,false);assert.equal('raw' in args,false);
  let calls=0;
  await processNotification({...adapter,validateNotification:async()=>e},'fixture',new Headers(),async(name)=>{
    assert.equal(name,'process_validated_recovery');calls++;
  });assert.equal(calls,1);
});

test('SSLCOMMERZ refund query verifies receipt/reference and never trusts callback amount or dispute claim',async()=>{
  const o={id:buyer,provider_order_id:'SNfixture',provider:'sslcommerz',environment:'sandbox',amount_minor:1200,currency:'BDT'};
  const validation={APIConnect:'DONE',status:'VALID',val_id:'fixtureval',tran_id:o.provider_order_id,currency:'BDT',currency_type:'BDT',
    amount:'12.00',currency_amount:'12.00',risk_level:'0',bank_tran_id:'bankfixture',tran_date:'2026-10-07 07:00:00'};
  const env={SSLCOMMERZ_ENABLED:'true',SSLCOMMERZ_ENVIRONMENT:'sandbox',SSLCOMMERZ_TIMESTAMP_ZONE:'Asia/Dhaka',
    SSLCOMMERZ_SANDBOX_STORE_ID:'fixture',SSLCOMMERZ_SANDBOX_STORE_PASSWORD:'test-only',SUPABASE_URL:'https://fixture.supabase.co'};
  const verified={APIConnect:'DONE',status:'refunded',refund_ref_id:'refundfixture',tran_id:o.provider_order_id,bank_tran_id:'bankfixture'};
  const make=result=>sslcommerzAdapter(env,async()=>o,async(url,init)=>{
    assert.equal(init.redirect,'error');assert.equal(new URL(url).origin,'https://sandbox.sslcommerz.com');
    return Response.json(url.includes('validationserver')?validation:result);
  });
  const headers=new Headers({'content-type':'application/x-www-form-urlencoded'});
  const raw='tran_id=SNfixture&val_id=fixtureval&refund_ref_id=refundfixture&refund_amount=12.00';
  assert.equal((await make(verified).validateNotification(raw,headers)).recoveryAmountMinor,null);
  assert.equal((await make({...verified,refund_amount:'3.00'}).validateNotification(raw,headers)).recoveryAmountMinor,300);
  for(const change of [{bank_tran_id:'other'},{tran_id:'other'},{refund_ref_id:'other'},{status:'success'},{APIConnect:'FAIL'},{refund_amount:'13.00'}])
    await assert.rejects(make({...verified,...change}).validateNotification(raw,headers));
  await assert.rejects(make(verified).validateNotification('tran_id=SNfixture&val_id=fixtureval&status=CHARGEBACK',headers),/recovery_verification_unavailable/);
});

test('leases exclude competing runs, fence stale completion and recover crashes; paid orders remain eligible',async()=>{
  const db=await fixture();try{
    const o=await order(db);const run=rpc(db);
    await assert.rejects(run('payment_reconciliation_batch',{p_environment:'sandbox'}),/reconciliation_disabled/);
    await db.exec('update payments.reconciliation_configuration set enabled=true');
    const batch=async()=>(await db.query("select * from public.payment_reconciliation_batch('sandbox')")).rows.map(x=>x.payment_reconciliation_batch);
    const a=await batch();assert.equal(a.length,1);assert.equal((await batch()).length,0);
    assert.equal(await run('payment_reconciliation_finish',{p_order_id:o.id,p_lease:lease,p_result:'processed'}),false);
    await db.exec("update payments.orders set reconciliation_lease_until=now()-interval '1 second'");
    const b=await batch();assert.notEqual(a[0].reconciliation_lease,b[0].reconciliation_lease);
    assert.equal(await run('payment_reconciliation_finish',{p_order_id:o.id,p_lease:a[0].reconciliation_lease,p_result:'processed'}),false);
    assert.equal(await run('payment_reconciliation_finish',{p_order_id:o.id,p_lease:b[0].reconciliation_lease,p_result:'retry'}),true);
    let row=(await query(db,'select * from payments.orders'))[0];assert.equal(row.reconciled_at,null);assert.equal(row.reconciliation_failures,1);
    assert.ok(row.reconciliation_due_at-new Date()>290000);
    await paid(db,await evidence(db,o));await db.exec('update payments.orders set reconciliation_due_at=now()');
    assert.equal((await batch()).length,1);
    await db.exec('reset role;update payments.reconciliation_configuration set enabled=false');assert.equal((await query(db,'select payments.enqueue_reconciliation() result'))[0].result,null);
  }finally{await db.close();}
});

test('protected worker bounds concurrency, saves retry/review outcomes and accepts no caller options',async()=>{
  const env={SSLCOMMERZ_RECONCILE_KEY:'fixture-local-only-key-'.repeat(3)};
  const req=body=>new Request('https://fixture',{method:'POST',headers:{'x-reconcile-key':env.SSLCOMMERZ_RECONCILE_KEY},body});
  let active=0,max=0,finished=0;
  const deps={adapterFor:()=>({...adapter,reconcile:async(o)=>{
    active++;max=Math.max(max,active);await new Promise(resolve=>setTimeout(resolve,5));active--;
    if(o.id.endsWith('0001'))throw new Error('private provider failure');
    return {...proof,orderId:o.id};
  }}),rpc:async(name,args)=>{
    if(name==='payment_reconciliation_batch')return Array.from({length:12},(_,i)=>({id:`11111111-1111-4111-8111-${String(i).padStart(12,'0')}`,provider:'sslcommerz',environment:'sandbox',reconciliation_lease:lease}));
    if(name==='payment_reconciliation_finish'){finished++;return true;}
    return {action:args.p_order_id.endsWith('0002')?'recovery_requires_review':'paid'};
  }};
  const result=await handleReconcile(req('{}'),env,deps);assert.equal(result.status,503);
  assert.deepEqual(await result.json(),{processed:10,review:1,retry:1,unfinished:0});assert.equal(finished,12);assert.equal(max,3);
  assert.equal((await handleReconcile(req('{"order_id":"injected"}'),env,deps)).status,400);
  assert.equal((await handleReconcile(req('x'.repeat(1025)),env,deps)).status,413);
});

test('scheduler dispatch stays off by default and uses fixed Vault secret with bounded empty-body request',async()=>{
  const db=await fixture();try{
    await db.exec(`reset role;
      create schema vault;create table vault.decrypted_secrets(name text,decrypted_secret text);
      create schema net;create table net.fixture_calls(url text,headers jsonb,body jsonb,timeout_ms integer);
      create function net.http_post(url text,headers jsonb,body jsonb,timeout_milliseconds integer) returns bigint language plpgsql as $$
      begin insert into net.fixture_calls values(url,headers,body,timeout_milliseconds);return 42;end $$;`);
    assert.equal((await query(db,'select payments.enqueue_reconciliation() result'))[0].result,null);
    await db.exec("update payments.reconciliation_configuration set enabled=true,endpoint='https://kflenmeizngmafwnwhgv.supabase.co/functions/v1/payment-reconcile'");
    await assert.rejects(db.query('select payments.enqueue_reconciliation()'),/reconciliation_secret_missing/);
    await db.query('insert into vault.decrypted_secrets values($1,$2)',['safenest_payment_reconcile_key','fixture-secret-'.repeat(3)]);
    assert.equal((await query(db,'select payments.enqueue_reconciliation() result'))[0].result,42);
    const call=(await query(db,'select * from net.fixture_calls'))[0];
    assert.deepEqual(call.body,{});assert.equal(call.timeout_ms,60000);
    assert.equal(call.headers['x-reconcile-key'],'fixture-secret-'.repeat(3));
    await assert.rejects(db.query("update payments.reconciliation_configuration set endpoint='https://attacker.example/'"),/check constraint/);
    await db.exec("update payments.configuration set mode='disabled'");
    assert.equal((await query(db,'select payments.enqueue_reconciliation() result'))[0].result,null);
    assert.equal((await query(db,'select count(*)::int n from net.fixture_calls'))[0].n,1);
  }finally{await db.close();}
});

test('reconciliation verifies every event before writing and applies recovery before new payment',async()=>{
  const {reconcileOrder}=await import('../supabase/functions/_shared/payments.mjs');
  const recovery=await normalizeVerifiedRecovery(proof,{kind:'refund',id:'refundfixture',state:'pending',amountMinor:null});
  const o={id:buyer,...adapter};const calls=[];
  const verifier={...adapter,reconcile:async()=>[proof,recovery]};
  await reconcileOrder(verifier,o,async name=>{calls.push(name);return {action:name.includes('recovery')?'recovery_requires_review':'paid'};});
  assert.deepEqual(calls,['process_validated_recovery','process_validated_payment']);
  calls.length=0;
  await assert.rejects(reconcileOrder({...verifier,reconcile:async()=>[proof,{...recovery,recoveryAmountMinor:1.5}]},o,async n=>calls.push(n)));
  assert.equal(calls.length,0);
});
