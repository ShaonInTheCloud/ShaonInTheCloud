import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';

const buyer = '11111111-1111-4111-8111-111111111111';
const reviewer = '22222222-2222-4222-8222-222222222222';
const files = ['20261003095341_protection_entitlements', '20261006215529_payment_foundation',
  '20261006215803_payment_foundation_hardening', '20261006225937_sslcommerz_gateway',
  '20261007001224_trial_subscriptions', '20261012120000_operator_controls'];

async function fixture() {
  const db = new PGlite();
  await db.exec(`create role anon nologin; create role authenticated nologin;
    create role service_role nologin bypassrls; create schema auth;
    create table auth.users(id uuid primary key);
    create function auth.uid() returns uuid language sql stable as $$
      select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
    grant usage on schema auth,public to anon,authenticated,service_role;
    grant execute on function auth.uid() to anon,authenticated,service_role;
    insert into auth.users values ('${buyer}'),('${reviewer}');`);
  for (const f of files) await db.exec(await readFile(new URL(`../supabase/migrations/${f}.sql`, import.meta.url), 'utf8'));
  await db.query(`update payments.configuration set mode='live'`);
  await db.query(`insert into payments.providers values ('fixture','live',true)`);
  await db.query(`insert into payments.products values ('fixture_month','live','monthly',1200,'BDT',3600,true)`);
  await db.exec('set role service_role');
  return db;
}
const rpc = (db, sql, args) => db.query(`select ${sql} as r`, args).then(x => x.rows[0].r);

async function paidOrder(db) {
  const o = await rpc(db, `public.create_payment_order($1,'fixture_month','fixture','live','operator-test-key-001')`, [buyer]);
  const paidAt = (await db.query('select clock_timestamp() as t')).rows[0].t.toISOString();
  await rpc(db, `public.process_validated_payment($1,'fixture','live','evt-1','txn-1','paid',$2,'BDT',$3,'val-1',$4)`,
    [o.id, o.amount_minor, paidAt, 'a'.repeat(64)]);
  return o;
}

test('reviewer access is finite, audited and not a payment', async () => {
  const db = await fixture();
  try {
    const g = await rpc(db, `public.operator_grant_reviewer_access($1,14,'Play Console reviewer account')`, [reviewer]);
    const row = (await db.query('select * from public.protection_entitlements where id=$1', [g.entitlement_id])).rows[0];
    assert.equal(row.status, 'active'); assert.equal(row.provider, 'operator'); assert.equal(row.plan_code, 'trial');
    assert.equal(Math.round((row.ends_at - row.starts_at) / 86400000), 14);
    assert.equal((await db.query('select count(*)::int n from payments.orders')).rows[0].n, 0);
    const log = (await db.query('select * from payments.operator_actions')).rows;
    assert.equal(log.length, 1); assert.equal(log[0].action, 'grant_reviewer_access'); assert.equal(log[0].user_id, reviewer);
    await assert.rejects(rpc(db, `public.operator_grant_reviewer_access($1,91,'too long a period')`, [reviewer]), /invalid_duration/);
    await assert.rejects(rpc(db, `public.operator_grant_reviewer_access($1,0,'zero day period')`, [reviewer]), /invalid_duration/);
    await assert.rejects(rpc(db, `public.operator_grant_reviewer_access($1,7,'')`, [reviewer]), /reason_required/);
    await assert.rejects(rpc(db, `public.operator_grant_reviewer_access('33333333-3333-4333-8333-333333333333',7,'unknown user')`, []), /violates foreign key/);
    assert.equal((await db.query('select count(*)::int n from payments.operator_actions')).rows[0].n, 1);
  } finally { await db.close(); }
});

test('revoking an entitlement is immediate, audited, and cannot repeat', async () => {
  const db = await fixture();
  try {
    const g = await rpc(db, `public.operator_grant_reviewer_access($1,7,'reviewer account to remove')`, [reviewer]);
    await rpc(db, `public.operator_revoke_entitlement($1,'review finished')`, [g.entitlement_id]);
    assert.equal((await db.query('select status from public.protection_entitlements where id=$1', [g.entitlement_id])).rows[0].status, 'revoked');
    await assert.rejects(rpc(db, `public.operator_revoke_entitlement($1,'second attempt')`, [g.entitlement_id]), /entitlement_not_revocable/);
    assert.equal((await db.query(`select count(*)::int n from payments.operator_actions where action='revoke_entitlement'`)).rows[0].n, 1);
  } finally { await db.close(); }
});

test('refund revokes the paid entitlement and a late paid notification cannot restore it', async () => {
  const db = await fixture();
  try {
    const o = await paidOrder(db);
    assert.equal((await db.query(`select status from public.protection_entitlements where payment_order_id=$1`, [o.id])).rows[0].status, 'active');
    const r = await rpc(db, `public.operator_refund_order($1,'Customer refunded at gateway')`, [o.id]);
    assert.equal(r.state, 'refunded'); assert.equal(r.entitlements_revoked, 1);
    assert.equal((await db.query(`select status from public.protection_entitlements where payment_order_id=$1`, [o.id])).rows[0].status, 'revoked');
    assert.equal((await db.query(`select state from payments.orders where id=$1`, [o.id])).rows[0].state, 'refunded');
    assert.equal((await db.query(`select state from payments.receipts where order_id=$1`, [o.id])).rows[0].state, 'refunded');
    // A replayed or reconciled "paid" proof must stay revoked.
    const paidAt = (await db.query('select clock_timestamp() as t')).rows[0].t.toISOString();
    const again = await rpc(db, `public.process_validated_payment($1,'fixture','live','evt-2','txn-1','paid',$2,'BDT',$3,'val-2',$4)`,
      [o.id, o.amount_minor, paidAt, 'b'.repeat(64)]).catch(e => e);
    const status = (await db.query(`select status from public.protection_entitlements where payment_order_id=$1`, [o.id])).rows[0].status;
    assert.equal(status, 'revoked', 'refund must not be undone by a later paid proof: ' + (again.message ?? JSON.stringify(again)));
    await assert.rejects(rpc(db, `public.operator_refund_order($1,'refund twice please')`, [o.id]), /order_not_refundable/);
  } finally { await db.close(); }
});

test('customers and anonymous callers cannot call operator functions or read the audit log; log is append-only', async () => {
  const db = await fixture();
  try {
    const g = await rpc(db, `public.operator_grant_reviewer_access($1,7,'reviewer for permissions test')`, [reviewer]);
    for (const role of ['anon', 'authenticated']) {
      await db.exec(`set role ${role}`);
      await assert.rejects(rpc(db, `public.operator_grant_reviewer_access($1,7,'self service grant')`, [buyer]), /permission denied/);
      await assert.rejects(rpc(db, `public.operator_revoke_entitlement($1,'self service revoke')`, [g.entitlement_id]), /permission denied/);
      await assert.rejects(rpc(db, `public.operator_refund_order($1,'self service refund')`, [g.entitlement_id]), /permission denied/);
      await assert.rejects(db.query('select * from payments.operator_actions'), /permission denied/);
      await db.exec('set role service_role');
    }
    await assert.rejects(db.query(`update payments.operator_actions set reason='edited reason text'`), /permission denied/);
    await assert.rejects(db.query('delete from payments.operator_actions'), /permission denied/);
    // Even the table owner (SQL editor) cannot rewrite history.
    await db.exec('reset role');
    await assert.rejects(db.query(`update payments.operator_actions set reason='edited reason text'`), /append_only/);
    await assert.rejects(db.query('delete from payments.operator_actions'), /append_only/);
  } finally { await db.close(); }
});
