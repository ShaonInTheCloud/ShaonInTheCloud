import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,readdir} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';

// All commercial values/accounts/evidence here are disposable local fixtures.
// This runner has no network, project keys or access to production.
const buyer = '11111111-1111-4111-8111-111111111111';
const other = '22222222-2222-4222-8222-222222222222';
const migrations = new URL('../supabase/migrations/', import.meta.url);
async function fixture(mode = 'sandbox') {
  const db = new PGlite();
  await db.exec(`create role anon nologin; create role authenticated nologin;
    create role service_role nologin bypassrls; create schema auth;
    create table auth.users(id uuid primary key);
    create function auth.uid() returns uuid language sql stable as $$
      select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
    grant usage on schema auth,public to anon,authenticated,service_role;
    grant execute on function auth.uid() to anon,authenticated,service_role;
    insert into auth.users values ('${buyer}'),('${other}');`);
  await db.exec(await readFile(new URL('../supabase/migrations/20261003095341_protection_entitlements.sql', import.meta.url),'utf8'));
  for (const file of (await readdir(migrations)).filter(name=>/_payment_foundation(?:_hardening)?\.sql$/.test(name)).sort())
    await db.exec(await readFile(new URL(file,migrations),'utf8'));
  if (mode !== 'disabled') {
    await db.query('update payments.configuration set mode=$1',[mode]);
    await db.query(`insert into payments.providers values ('fixture',$1,true);
      `,[mode]);
    await db.query(`insert into payments.products values ('fixture_month',$1,'monthly',1200,'BDT',3600,true)`,[mode]);
  }
  await db.exec('set role service_role');
  return db;
}
async function order(db, {user = buyer, key = 'fixture-order-key-0001', mode = 'sandbox'} = {}) {
  return (await db.query(`select public.create_payment_order($1,'fixture_month','fixture',$2,$3) as result`,[user,mode,key])).rows[0].result;
}
async function evidence(db, ord, changes = {}) {
  return {order:ord.id, provider:'fixture', mode:ord.environment, event:'fixture-event-1',
    transaction:'fixture-receipt-1', status:'paid', amount:ord.amount_minor, currency:ord.currency,
    paidAt:(await db.query('select clock_timestamp() as t')).rows[0].t.toISOString(),
    validation:'fixture-validation', hash:'a'.repeat(64), ...changes};
}
async function process(db, e) {
  const args = [e.order,e.provider,e.mode,e.event,e.transaction,e.status,e.amount,e.currency,e.paidAt,e.validation,e.hash];
  return (await db.query(`select public.process_validated_payment($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11) as result`,args)).rows[0].result;
}
async function count(db, table) {return (await db.query(`select count(*)::int as n from ${table}`)).rows[0].n;}

test('migration defaults disabled with no products, providers, orders or grants', async () => {
  const db = await fixture('disabled');
  try {
    assert.equal((await db.query('select mode from payments.configuration')).rows[0].mode,'disabled');
    for (const table of ['payments.providers','payments.products','payments.orders','payments.events',
      'payments.receipts','payments.sandbox_entitlements','public.protection_entitlements']) assert.equal(await count(db,table),0);
    await assert.rejects(order(db), /payment_processing_disabled/);
    await assert.rejects(process(db,{mode:'live'}), /payment_processing_disabled/);
  } finally {await db.close();}
});

test('order retry preserves server price/window and account-scoped idempotency', async () => {
  const db = await fixture();
  try {
    const a = await order(db);
    await db.exec("update payments.products set amount_minor=2400,duration_seconds=7200");
    const retry = await order(db);
    assert.deepEqual(retry,a);
    const b = await order(db,{user:other});
    assert.notEqual(b.id,a.id); assert.equal(b.amount_minor,2400);
    assert.equal(a.amount_minor,1200); assert.equal(a.duration_seconds,3600);
    await db.exec("insert into payments.products values ('fixture_year','sandbox','annual',9999,'BDT',9000,true)");
    await assert.rejects(db.query(`select public.create_payment_order($1,'fixture_year','fixture','sandbox','fixture-order-key-0001')`,[buyer]), /idempotency_conflict/);
    await assert.rejects(order(db,{user:'33333333-3333-4333-8333-333333333333'}),{code:'23503'});
  } finally {await db.close();}
});

test('provider/product switches and environment gate reject order creation and grants', async () => {
  const db = await fixture();
  try {
    await assert.rejects(order(db,{mode:'live'}),/payment_processing_disabled/);
    await db.exec('update payments.providers set enabled=false');
    await assert.rejects(order(db), /provider_disabled/);
    await db.exec('update payments.providers set enabled=true; update payments.products set enabled=false');
    await assert.rejects(order(db), /product_disabled/);
    await db.exec('update payments.products set enabled=true');
    const a=await order(db); const e=await evidence(db,a);
    await db.exec("update payments.configuration set mode='disabled'");
    await assert.rejects(process(db,e),/payment_processing_disabled/);
    assert.equal(await count(db,'payments.events'),0);
  } finally {await db.close();}
});

test('sandbox paid receipt grants one finite local window; repeated events never extend it', async () => {
  const db = await fixture();
  try {
    const a = await order(db); const e=await evidence(db,a);
    const first=await process(db,e); assert.equal(first.duplicate,false);
    const before=(await db.query('select * from payments.sandbox_entitlements')).rows;
    assert.equal(before.length,1); assert.equal(before[0].ends_at - before[0].starts_at,3600000);
    assert.equal((await process(db,e)).duplicate,true);
    await process(db,{...e,event:'fixture-event-2'});
    assert.deepEqual((await db.query('select * from payments.sandbox_entitlements')).rows,before);
    assert.equal(await count(db,'public.protection_entitlements'),0);
    assert.equal(await count(db,'payments.receipts'),1);
    assert.equal(await count(db,'payments.events'),2);
  } finally {await db.close();}
});

test('live processing is verified ONLY in isolated PostgreSQL, with source-order uniqueness', async () => {
  const db=await fixture('live');
  try {
    const a=await order(db,{mode:'live'}); const e=await evidence(db,a);
    const r=await process(db,e); assert.ok(r.entitlement_id);
    await process(db,e); await process(db,{...e,event:'fixture-event-2'});
    assert.equal(await count(db,'public.protection_entitlements'),1);
    assert.equal(await count(db,'payments.sandbox_entitlements'),0);
    const row=(await db.query('select * from public.protection_entitlements')).rows[0];
    assert.equal(row.payment_order_id,a.id); assert.equal(row.user_id,buyer);
    assert.equal(row.provider,'fixture:live'); assert.equal(row.ends_at-row.starts_at,3600000);
    await process(db,{...e,event:'fixture-chargeback',status:'chargeback'});
    await process(db,e);
    await process(db,{...e,event:'fixture-late-paid'});
    assert.equal((await db.query('select status from public.protection_entitlements')).rows[0].status,'revoked');
  } finally {await db.close();}
});

test('wrong amount/currency/provider/environment/order, malformed evidence and bad dates make no writes', async () => {
  const db=await fixture();
  try {
    const a=await order(db); const e=await evidence(db,a);
    const invalid=[{amount:1199},{amount:0},{amount:null},{currency:'EUR'},{currency:'bdt'},
      {provider:'unknown'},{mode:'live'},{order:'33333333-3333-4333-8333-333333333333'},
      {status:'unknown'},{status:null},{event:''},{event:null},{validation:null},{hash:'bad'},
      {transaction:''},{transaction:null},{paidAt:null},{paidAt:'infinity'},
      {paidAt:'2020-01-01T00:00:00Z'},{paidAt:'2099-01-01T00:00:00Z'}];
    for (const change of invalid) await assert.rejects(process(db,{...e,...change}));
    assert.equal(await count(db,'payments.events'),0);
    assert.equal(await count(db,'payments.receipts'),0);
    assert.equal(await count(db,'payments.sandbox_entitlements'),0);
    assert.equal((await db.query('select state from payments.orders')).rows[0].state,'pending');
  } finally {await db.close();}
});

test('same event with changed contents and one receipt reused on another order/account fail atomically', async () => {
  const db=await fixture();
  try {
    const a=await order(db); const e=await evidence(db,a); await process(db,e);
    await assert.rejects(process(db,{...e,validation:'different'}),/event_replay_conflict/);
    await assert.rejects(process(db,{...e,event:'new-event',transaction:'another-receipt'}),/receipt_conflict/);
    const b=await order(db,{user:other}); const eb=await evidence(db,b,{event:'new-event'});
    await assert.rejects(process(db,eb),{code:'23505'});
    await assert.rejects(process(db,{...eb,event:e.event}),/event_replay_conflict/);
    assert.equal(await count(db,'payments.receipts'),1); assert.equal(await count(db,'payments.events'),1);
    assert.equal(await count(db,'payments.sandbox_entitlements'),1);
  } finally {await db.close();}
});

test('pending, failed and cancelled events grant nothing; late failures cannot undo paid access', async () => {
  const db=await fixture();
  try {
    const a=await order(db); const e=await evidence(db,a);
    for (const status of ['pending','failed','cancelled']) {
      await process(db,{...e,event:status,status,transaction:null,paidAt:null});
      assert.equal(await count(db,'payments.sandbox_entitlements'),0);
    }
    // An independently validated payment inside the order window is authoritative.
    await process(db,e);
    await process(db,{...e,event:'late-failure',status:'failed',transaction:null,paidAt:null});
    assert.equal((await db.query('select state from payments.orders')).rows[0].state,'paid');
    assert.equal((await db.query('select status from payments.sandbox_entitlements')).rows[0].status,'active');
  } finally {await db.close();}
});

test('reversal before paid is a tombstone; late paid events never grant', async () => {
  const db=await fixture();
  try {
    const a=await order(db); const e=await evidence(db,a);
    await process(db,{...e,event:'refund-first',status:'refunded'});
    assert.equal((await process(db,e)).action,'reversal_prevents_grant');
    assert.equal(await count(db,'payments.sandbox_entitlements'),0);
    assert.equal((await db.query('select state from payments.orders')).rows[0].state,'refunded');
  } finally {await db.close();}
});

test('expired orders reject late payment, but delayed delivery uses verified paid-at, never delivery time', async () => {
  const db=await fixture();
  try {
    const a=await order(db);
    await db.query("update payments.orders set created_at=now()-interval '2 hours',expires_at=now()-interval '1 hour' where id=$1",[a.id]);
    const e=await evidence(db,a);
    await assert.rejects(process(db,e),/invalid_payment_time_or_reference/);
    const paidAt=(await db.query("select (now()-interval '90 minutes') as t")).rows[0].t.toISOString();
    await process(db,{...e,paidAt});
    const row=(await db.query('select * from payments.sandbox_entitlements')).rows[0];
    assert.equal(row.starts_at.toISOString(),paidAt);
    assert.ok(row.ends_at < new Date());
  } finally {await db.close();}
});

test('failure after entitlement insertion rolls back receipt, grant, order transition and event together', async () => {
  const db=await fixture('live');
  try {
    const a=await order(db,{mode:'live'}); const e=await evidence(db,a);
    await db.exec(`reset role;
      create function payments.fixture_fail() returns trigger language plpgsql as $$
        begin raise exception 'fixture-injected-failure'; end $$;
      create trigger fixture_fail before insert on payments.events for each row execute function payments.fixture_fail();
      set role service_role;`);
    await assert.rejects(process(db,e),/fixture-injected-failure/);
    assert.equal(await count(db,'payments.receipts'),0); assert.equal(await count(db,'payments.events'),0);
    assert.equal(await count(db,'public.protection_entitlements'),0);
    assert.equal((await db.query('select state from payments.orders')).rows[0].state,'pending');
    await db.exec('reset role; drop trigger fixture_fail on payments.events; set role service_role');
    await process(db,e); assert.equal(await count(db,'public.protection_entitlements'),1);
  } finally {await db.close();}
});

test('anonymous/authenticated clients cannot call RPCs or access ledger; entitlements remain owner-read-only', async () => {
  const db=await fixture('live');
  try {
    const a=await order(db,{mode:'live'}); const e=await evidence(db,a); await process(db,e);
    for (const role of ['anon','authenticated']) {
      await db.exec(`reset role; set role ${role}`);
      await assert.rejects(order(db,{mode:'live'}),{code:'42501'});
      await assert.rejects(process(db,e),{code:'42501'});
      for (const table of ['orders','products','providers','receipts','events','configuration','sandbox_entitlements'])
        await assert.rejects(db.query(`select * from payments.${table}`),{code:'42501'});
    }
    await db.exec(`select set_config('request.jwt.claim.sub','${other}',false)`);
    assert.equal(await count(db,'public.protection_entitlements'),0);
    await db.exec(`select set_config('request.jwt.claim.sub','${buyer}',false)`);
    assert.equal(await count(db,'public.protection_entitlements'),1);
    await assert.rejects(db.query("update public.protection_entitlements set status='active'"),{code:'42501'});
    await db.exec(`reset role; delete from auth.users where id='${buyer}'`);
    assert.equal(await count(db,'payments.orders'),0); assert.equal(await count(db,'payments.events'),0);
    assert.equal(await count(db,'payments.receipts'),0); assert.equal(await count(db,'public.protection_entitlements'),0);
  } finally {await db.close();}
});
