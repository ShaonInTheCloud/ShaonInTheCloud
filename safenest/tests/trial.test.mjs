import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';
import {handleTrial} from '../supabase/functions/start-trial/handler.mjs';
const owner='11111111-1111-4111-8111-111111111111',other='22222222-2222-4222-8222-222222222222';
test('trial is server-timed, one per account, retry-safe, private and cannot issue paid access',async()=>{
  const db=new PGlite();
  try {
    await db.exec(`create role anon;create role authenticated;create role service_role bypassrls;
      create schema auth;create table auth.users(id uuid primary key,email_confirmed_at timestamptz,is_anonymous boolean default false);
      create function auth.uid() returns uuid language sql as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
      grant usage on schema auth,public to anon,authenticated,service_role;
      grant select,update on auth.users to service_role;
      insert into auth.users values ('${owner}',now(),false),('${other}',null,false);`);
    for(const name of ['20261003095341_protection_entitlements.sql','20261006215529_payment_foundation.sql','20261006215803_payment_foundation_hardening.sql','20261007001933_trial_subscriptions.sql','20261007040839_trial_claim_entitlement_index.sql'])
      await db.exec(await readFile(new URL('../supabase/migrations/'+name,import.meta.url),'utf8'));
    await db.exec('set role service_role');
    const start=async(plan='monthly',user=owner)=>(await db.query('select public.start_protection_trial($1,$2) result',[user,plan])).rows[0].result;
    const first=await start('quarterly'),again=await start('annual');
    assert.equal(first.entitlement.id,again.entitlement.id);
    assert.equal(again.selected_plan_code,'quarterly');
    assert.equal(again.entitlement.ends_at,first.entitlement.ends_at);
    assert.equal(Date.parse(first.entitlement.ends_at)-Date.parse(first.entitlement.starts_at),72*3600000);
    assert.equal(first.entitlement.plan_code,'trial');assert.equal(first.automatic_charging,false);
    await assert.rejects(start('invalid'),/invalid_plan/);
    await assert.rejects(start('monthly',other),/confirmed_account_required/);
    await db.exec(`reset role;update public.protection_entitlements set ends_at=now()-interval '1 second',starts_at=now()-interval '73 hours' where id='${first.entitlement.id}';set role service_role`);
    assert.equal((await start()).entitlement.id,first.entitlement.id);
    await db.exec(`reset role;set role authenticated;select set_config('request.jwt.claim.sub','${other}',false)`);
    assert.equal((await db.query('select * from public.protection_entitlements')).rows.length,0);
    await assert.rejects(start(),{code:'42501'});
    await assert.rejects(db.query('select * from payments.trial_claims'),{code:'42501'});
    await db.exec('reset role');
    assert.equal((await db.query('select count(*)::int n from payments.products where enabled')).rows[0].n,0);
    assert.deepEqual((await db.query("select amount_minor::int n from payments.products where environment='live' order by amount_minor")).rows.map(x=>x.n),[37900,99900,379900]);
  }finally{await db.close();}
});
test('trial endpoint validates Auth identity and confirmed email; rejects client user/time/paid flags',async()=>{
  const env={SUPABASE_URL:'https://fixture.supabase.co',SUPABASE_ANON_KEY:'fixture'};
  const req=(body,token='user.jwt.value')=>new Request('https://fixture/start',{method:'POST',headers:token?{Authorization:'Bearer '+token}:{},body:JSON.stringify(body)});
  let args;
  const deps={fetcher:async()=>Response.json({id:owner,email_confirmed_at:'2026-10-01'}),rpc:async(name,value)=>{args=value;return {automatic_charging:false};}};
  assert.equal((await handleTrial(req({plan_code:'monthly'},''),env,deps)).status,401);
  assert.equal((await handleTrial(req({plan_code:'monthly',user_id:other,ends_at:'2099-01-01'}),env,deps)).status,400);
  assert.equal(args,undefined);
  assert.equal((await handleTrial(req({plan_code:'monthly'}),env,deps)).status,200);
  assert.deepEqual(args,{p_user_id:owner,p_plan_code:'monthly'});
  assert.equal((await handleTrial(req({plan_code:'annual'}),env,{...deps,fetcher:async()=>Response.json({id:owner})})).status,403);
});
