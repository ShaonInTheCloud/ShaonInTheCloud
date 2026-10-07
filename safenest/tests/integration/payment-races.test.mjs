import test from 'node:test';
import assert from 'node:assert/strict';
import {Pool} from 'pg';
import {fixture,order,evidence,rpc,recover,paid} from '../helpers/payment-fixture.mjs';

// Real multi-session PostgreSQL tests, never a PGlite parallel-promise simulation.
// Explicit opt-in plus loopback/database guards prevent production fixture writes.
const enabled=process.env.SAFENEST_PAYMENT_RACE_TEST==='local-fixture-only';
const host=process.env.PGHOST??'127.0.0.1';
const database=process.env.PGDATABASE??'safenest_payment_test';

test('isolated PostgreSQL payment callback/transition load and scheduler races', {skip:!enabled,timeout:180000},async t=>{
  assert.ok(['127.0.0.1','localhost','::1'].includes(host),'Only loopback fixture servers allowed');
  assert.match(database,/^safenest_payment_test(?:_[a-z0-9_]+)?$/);
  const owner=new Pool({host,database,max:2});
  const setup=await owner.connect();
  let pool;
  try {
    assert.equal((await setup.query("select to_regnamespace('payments') as schema")).rows[0].schema,null,'Database must be fresh');
    await fixture({exec:sql=>setup.query(sql),query:(sql,args)=>setup.query(sql,args)});
    await setup.query('reset role');setup.release();
    pool=new Pool({host,database,max:32,options:'-c role=service_role -c statement_timeout=15000 -c lock_timeout=10000'});
    const db={query:(sql,args)=>pool.query(sql,args)};
    const count=async table=>(await pool.query(`select count(*)::int n from ${table}`)).rows[0].n;
    const clear=async()=>pool.query('delete from payments.orders');
    async function overlap(orderId,tasks) {
      const lock=await owner.connect();await lock.query('begin');
      await lock.query('select id from payments.orders where id=$1 for update',[orderId]);
      const start=performance.now();
      const pending=tasks.map(fn=>fn());
      // Wait for actual blocked sessions, proving concurrent execution before unlock.
      let waiters=0;
      for(let i=0;i<100;i++) {
        waiters=Number((await owner.query("select count(*) n from pg_stat_activity where datname=$1 and wait_event_type='Lock'",[database])).rows[0].n);
        if(waiters>=4)break;
        await new Promise(resolve=>setTimeout(resolve,10));
      }
      await lock.query('commit');lock.release();
      const results=await Promise.allSettled(pending);
      assert.ok(waiters>=4,`Expected concurrent blocked DB sessions, observed ${waiters}`);
      t.diagnostic(`${tasks.length} competing requests / ${waiters} blocked sessions / ${Math.round(performance.now()-start)} ms`);
      return results;
    }
    await t.test('128 duplicate callbacks grant exactly one immutable finite sandbox window',async()=>{
      const o=await order(db),p=await evidence(db,o);
      const results=await overlap(o.id,Array.from({length:128},()=>()=>paid(db,p)));
      assert.equal(results.filter(x=>x.status==='rejected').length,0);
      assert.equal(results.filter(x=>x.value.duplicate===false).length,1);
      for(const table of ['payments.receipts','payments.events','payments.sandbox_entitlements'])assert.equal(await count(table),1);
      assert.equal(await count('public.protection_entitlements'),0);
      const row=(await pool.query('select * from payments.sandbox_entitlements')).rows[0];
      assert.equal(row.ends_at-row.starts_at,3600000);await clear();
    });
    await t.test('reused event with conflicting proof commits one identity and rolls back all conflicts',async()=>{
      const o=await order(db),p=await evidence(db,o);
      const results=await overlap(o.id,Array.from({length:64},(_,i)=>()=>paid(db,{...p,evidenceSha256:(i%2?'a':'b').repeat(64)})));
      assert.equal(results.filter(x=>x.status==='fulfilled').length,32);
      for(const x of results.filter(x=>x.status==='rejected'))assert.match(x.reason.message,/event_replay_conflict/);
      assert.equal(await count('payments.events'),1);assert.equal(await count('payments.sandbox_entitlements'),1);await clear();
    });
    await t.test('128 competing paid, refund, chargeback and failure events converge to terminal chargeback',async()=>{
      const o=await order(db),p=await evidence(db,o);
      const transitions=[()=>paid(db,p),()=>recover(db,p),()=>recover(db,p,{kind:'chargeback',id:'dispute'}),
        ()=>paid(db,{...p,status:'failed',eventId:'failed',transactionId:null,paidAt:null})];
      const results=await overlap(o.id,Array.from({length:128},(_,i)=>transitions[i%4]));
      assert.equal(results.filter(x=>x.status==='rejected').length,0);
      assert.equal((await pool.query('select state from payments.orders')).rows[0].state,'chargeback');
      for(const row of (await pool.query('select status from payments.sandbox_entitlements')).rows)assert.equal(row.status,'revoked');
      assert.equal(await count('payments.events'),4);assert.equal(await count('payments.recoveries'),2);await clear();
    });
    await t.test('concurrent duplicate partial refunds sum unique recovery IDs exactly once',async()=>{
      const o=await order(db),p=await evidence(db,o);await paid(db,p);
      const results=await overlap(o.id,Array.from({length:128},(_,i)=>()=>recover(db,p,{id:i%2?'refund-a':'refund-b',amountMinor:600})));
      assert.equal(results.filter(x=>x.status==='rejected').length,0);
      assert.equal(await count('payments.recoveries'),2);
      assert.equal((await pool.query('select sum(amount_minor)::int total from payments.recoveries')).rows[0].total,1200);
      assert.equal((await pool.query('select state from payments.orders')).rows[0].state,'refunded');await clear();
    });
    await t.test('one receipt racing across two orders belongs to one order only',async()=>{
      const a=await order(db,'fixture-race-key-0001'),b=await order(db,'fixture-race-key-0002');
      const p=await evidence(db,a),q={...p,orderId:b.id,eventId:'paid-other-order'};
      const results=await Promise.allSettled(Array.from({length:64},(_,i)=>paid(db,i%2?p:q)));
      assert.equal(results.filter(x=>x.status==='fulfilled').length,32);
      for(const x of results.filter(x=>x.status==='rejected'))assert.equal(x.reason.code,'23505');
      assert.equal(await count('payments.receipts'),1);assert.equal(await count('payments.sandbox_entitlements'),1);await clear();
    });
    await t.test('competing scheduler claims skip locked rows and fence an expired worker',async()=>{
      await pool.query('update payments.reconciliation_configuration set enabled=true');
      for(let i=0;i<10;i++)await order(db,'fixture-scheduler-key-'+String(i).padStart(4,'0'));
      const batches=await Promise.all(Array.from({length:8},()=>pool.query("select * from public.payment_reconciliation_batch('sandbox')")));
      const claims=batches.flatMap(b=>b.rows.map(x=>x.payment_reconciliation_batch));
      assert.equal(claims.length,10);assert.equal(new Set(claims.map(x=>x.id)).size,10);
      const a=claims[0];await pool.query("update payments.orders set reconciliation_lease_until=now()-interval '1 second' where id=$1",[a.id]);
      const b=(await pool.query("select * from public.payment_reconciliation_batch('sandbox')")).rows[0].payment_reconciliation_batch;
      assert.notEqual(a.reconciliation_lease,b.reconciliation_lease);
      assert.equal(await rpc(db)('payment_reconciliation_finish',{p_order_id:a.id,p_lease:a.reconciliation_lease,p_result:'processed'}),false);
      assert.equal(await rpc(db)('payment_reconciliation_finish',{p_order_id:b.id,p_lease:b.reconciliation_lease,p_result:'retry'}),true);
      assert.equal((await pool.query('select reconciled_at from payments.orders where id=$1',[b.id])).rows[0].reconciled_at,null);
      await clear();
    });
    assert.equal(await count('public.protection_entitlements'),0);
  } finally {
    if(!setup.released) {try{setup.release();}catch{}}
    await pool?.end();await owner.end();
  }
});
