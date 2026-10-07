import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';
import {handleTrial} from '../supabase/functions/start-trial/handler.mjs';
import {handleAccess} from '../supabase/functions/protection-access/index.ts';
import {installTrial} from '../web/trial.js';

test('disabled website rollout cannot enroll even a confirmed consenting account', async () => {
  const original = globalThis.document;
  const button={disabled:true,textContent:''},note={textContent:''};
  let submit,calls=0;
  const form={elements:{consent:{checked:true},plan_code:{value:'quarterly'}},
    addEventListener:(event,fn)=>{assert.equal(event,'submit');submit=fn;}};
  globalThis.document={getElementById:id=>({'trial-form':form,'start-trial':button,'trial-status':note})[id]};
  try {
    installTrial({functions:{invoke:async()=>{calls++;throw new Error('Unexpected trial call');}}},
      {identity:()=> 'confirmed-fixture',onStarted:()=>assert.fail('Unexpected enrollment')});
    await submit({preventDefault(){}});
    assert.equal(calls,0);assert.equal(button.disabled,true);
  } finally { globalThis.document=original; }
});

// Integrated real migrations/handlers with fixture Auth/REST transport. This
// exercises exact boundary semantics, not real email login or Android consent.
test('trial endpoint → ledger → access: exact expiry, unchanged retries and deletion cleanup', async () => {
  const db = new PGlite();
  const owner = '33333333-3333-4333-8333-333333333333';
  const env = {SUPABASE_URL:'https://fixture.supabase.co', SUPABASE_ANON_KEY:'fixture'};
  const request = (path, body) => new Request(env.SUPABASE_URL+path, {
    method:'POST', headers:{Authorization:'Bearer fixture.jwt.value'}, body:JSON.stringify(body),
  });
  try {
    await db.exec(`create role anon; create role authenticated; create role service_role bypassrls;
      create schema auth; create table auth.users(id uuid primary key,email_confirmed_at timestamptz,is_anonymous boolean default false);
      create function auth.uid() returns uuid language sql as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
      grant usage on schema auth,public to anon,authenticated,service_role;
      grant select,update on auth.users to service_role;
      insert into auth.users values ('${owner}',now(),false);`);
    for (const file of ['20261003095341_protection_entitlements.sql','20261006215529_payment_foundation.sql',
      '20261006215803_payment_foundation_hardening.sql','20261007001224_trial_subscriptions.sql']) {
      await db.exec(await readFile(new URL('../supabase/migrations/'+file,import.meta.url),'utf8'));
    }
    const rpc = async (name,args) => {
      assert.equal(name,'start_protection_trial');
      await db.exec('set role service_role');
      try { return (await db.query('select public.start_protection_trial($1,$2) result',
        [args.p_user_id,args.p_plan_code])).rows[0].result; }
      finally { await db.exec('reset role'); }
    };
    const fetcher = async input => {
      const url = new URL(input);
      if (url.pathname === '/auth/v1/user') return Response.json({id:owner,email_confirmed_at:'2026-10-07'});
      assert.equal(url.pathname,'/rest/v1/protection_entitlements');
      const q = url.searchParams;
      assert.equal(q.get('user_id'),'eq.'+owner);
      assert.equal(q.get('status'),'eq.active');
      const id = q.get('id')?.slice(3) ?? null;
      const rows = (await db.query(`select id,plan_code,starts_at,ends_at from public.protection_entitlements
        where user_id=$1 and status='active' and starts_at<=$2 and ends_at>$3
        and ($4::uuid is null or id=$4::uuid) order by ends_at desc limit 1`,
      [owner,q.get('starts_at').slice(4),q.get('ends_at').slice(3),id])).rows;
      return Response.json(rows);
    };
    const access = async stamp => (await handleAccess(request('/access',{}),env,fetcher,new Date(stamp))).json();
    // A confirmed identity / read-only access check cannot start the trial.
    assert.equal((await access(Date.now())).active,false);
    assert.equal((await db.query('select count(*)::int n from payments.trial_claims')).rows[0].n,0);
    const firstResponse = await handleTrial(request('/trial',{plan_code:'quarterly'}),env,{fetcher,rpc});
    assert.equal(firstResponse.status,200);
    const first = await firstResponse.json();
    const start = Date.parse(first.entitlement.starts_at), end = Date.parse(first.entitlement.ends_at);
    assert.equal(end-start,72*60*60*1000);
    assert.equal(first.selected_plan_code,'quarterly');
    assert.equal(first.automatic_charging,false);
    assert.equal(first.entitlement.plan_code,'trial');
    for (const plan of ['annual','monthly']) {
      const retry = await (await handleTrial(request('/trial',{plan_code:plan}),env,{fetcher,rpc})).json();
      assert.equal(retry.already_claimed,true);
      assert.equal(retry.entitlement.id,first.entitlement.id);
      assert.equal(retry.entitlement.ends_at,first.entitlement.ends_at);
      assert.equal(retry.selected_plan_code,'quarterly');
    }
    assert.equal((await access(start)).active,true);
    assert.equal((await access(end-1)).active,true);
    assert.equal((await access(end)).active,false);
    assert.equal((await access(end+1)).active,false);
    await db.query(`update public.protection_entitlements set starts_at=clock_timestamp()-interval '73 hours',
      ends_at=clock_timestamp()-interval '1 hour' where id=$1`,[first.entitlement.id]);
    const expired = await rpc('start_protection_trial',{p_user_id:owner,p_plan_code:'annual'});
    assert.equal(expired.already_claimed,true);
    assert.equal(expired.entitlement.id,first.entitlement.id);
    assert.equal((await access(Date.now())).active,false);
    await db.query(`update public.protection_entitlements set status='revoked' where id=$1`,[first.entitlement.id]);
    assert.equal((await rpc('start_protection_trial',{p_user_id:owner,p_plan_code:'monthly'})).entitlement.status,'revoked');
    // Losing the entitlement row must not erase the once-per-account claim.
    await db.query('delete from public.protection_entitlements where id=$1',[first.entitlement.id]);
    assert.equal((await rpc('start_protection_trial',{p_user_id:owner,p_plan_code:'monthly'})).entitlement,null);
    assert.equal((await db.query('select count(*)::int n from payments.trial_claims')).rows[0].n,1);
    assert.equal((await db.query('select count(*)::int n from payments.orders')).rows[0].n,0);
    await db.query('delete from auth.users where id=$1',[owner]);
    assert.equal((await db.query('select count(*)::int n from payments.trial_claims')).rows[0].n,0);
    assert.equal((await db.query('select count(*)::int n from public.protection_entitlements')).rows[0].n,0);
  } finally { await db.close(); }
});
