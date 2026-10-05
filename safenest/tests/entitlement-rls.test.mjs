import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { PGlite } from '@electric-sql/pglite';

test('customers cannot issue, change or read another user’s paid entitlement; duplicate receipts fail', async () => {
  const db = new PGlite();
  const owner = '11111111-1111-4111-8111-111111111111';
  const other = '22222222-2222-4222-8222-222222222222';
  try {
    await db.exec(`create role anon nologin; create role authenticated nologin;
      create role service_role nologin bypassrls; create schema auth;
      create table auth.users(id uuid primary key);
      create function auth.uid() returns uuid language sql stable as $$
        select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
      grant usage on schema auth,public to anon,authenticated,service_role;
      grant execute on function auth.uid() to anon,authenticated,service_role;
      insert into auth.users values ('${owner}'),('${other}');`);
    await db.exec(await readFile(new URL('../supabase/migrations/20261003095341_protection_entitlements.sql', import.meta.url), 'utf8'));
    const insert = `insert into public.protection_entitlements
      (user_id,plan_code,status,starts_at,ends_at,provider,provider_reference)
      values ($1,'monthly','active','2026-10-01','2026-11-01','local-test',$2)`;
    // These fixture receipts exist only inside embedded PostgreSQL, never production.
    await db.query(insert, [owner, 'receipt-a']);
    await db.query(insert, [other, 'receipt-b']);
    await assert.rejects(db.query(insert, [owner, 'receipt-a']), { code: '23505' });
    await db.exec('set role anon');
    await assert.rejects(db.query('select * from public.protection_entitlements'), { code: '42501' });
    await db.exec(`reset role; set role authenticated; select set_config('request.jwt.claim.sub','${owner}',false)`);
    assert.deepEqual((await db.query('select user_id from public.protection_entitlements')).rows, [{ user_id: owner }]);
    await assert.rejects(db.query(insert, [owner, 'fake-payment']), { code: '42501' });
    await assert.rejects(db.query("update public.protection_entitlements set ends_at='2099-01-01'"), { code: '42501' });
    await assert.rejects(db.query('delete from public.protection_entitlements'), { code: '42501' });
    await db.exec(`reset role; delete from auth.users where id='${owner}'`);
    assert.equal((await db.query('select count(*)::integer as total from public.protection_entitlements')).rows[0].total, 1);
  } finally { await db.close(); }
});
