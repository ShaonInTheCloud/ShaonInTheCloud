import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { PGlite } from '@electric-sql/pglite';

// Runs the actual migration on embedded PostgreSQL with Supabase's role and
// auth.uid() contract simulated. No live accounts, emails or cloud data used.
test('database enforces private profiles and immutable ownership', async () => {
  const db = new PGlite();
  const userA = '11111111-1111-4111-8111-111111111111';
  const userB = '22222222-2222-4222-8222-222222222222';
  try {
    await db.exec(`
      create role anon nologin;
      create role authenticated nologin;
      create role service_role nologin bypassrls;
      create schema auth;
      create table auth.users (id uuid primary key);
      create function auth.uid() returns uuid language sql stable as $$
        select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
      $$;
      grant usage on schema auth, public to anon, authenticated, service_role;
      grant execute on function auth.uid() to anon, authenticated;
      insert into auth.users values ('${userA}'), ('${userB}');
    `);
    await db.exec(await readFile(new URL('../supabase/migrations/20260924212500_private_profiles.sql', import.meta.url), 'utf8'));

    await db.exec('set role anon');
    await assert.rejects(db.query('select * from public.profiles'), { code: '42501' });
    await db.exec(`reset role; set role authenticated; select set_config('request.jwt.claim.sub', '${userA}', false);`);
    await db.query('insert into public.profiles (id, display_name, language) values ($1,$2,$3)', [userA, 'A', 'en']);
    assert.equal((await db.query('select * from public.profiles')).rows.length, 1);
    await db.query('update public.profiles set display_name=$1, language=$2 where id=$3', ['A saved', 'bn', userA]);
    assert.equal((await db.query('select language from public.profiles')).rows[0].language, 'bn');
    await assert.rejects(db.query('update public.profiles set id=$1', [userB]), { code: '42501' });
    await assert.rejects(db.query('update public.profiles set created_at=now()'), { code: '42501' });

    await db.query("select set_config('request.jwt.claim.sub', $1, false)", [userB]);
    assert.deepEqual((await db.query('select * from public.profiles')).rows, []);
    assert.equal((await db.query('update public.profiles set display_name=$1 where id=$2', ['intruder', userA])).affectedRows, 0);
    await assert.rejects(db.query('insert into public.profiles (id) values ($1)', [userA]), { code: '42501' });
    await assert.rejects(db.query("insert into public.profiles (id,language) values ($1,'invalid')", [userB]), { code: '23514' });
    await assert.rejects(db.query('insert into public.profiles (id,display_name) values ($1,$2)', [userB, 'x'.repeat(81)]), { code: '23514' });
    await db.query('insert into public.profiles (id, display_name) values ($1,$2)', [userB, 'B']);
    assert.equal((await db.query('select id from public.profiles')).rows[0].id, userB);
    await assert.rejects(db.query('delete from public.profiles'), { code: '42501' });

    await db.query("select set_config('request.jwt.claim.sub', '', false)");
    assert.deepEqual((await db.query('select * from public.profiles')).rows, []);
    await assert.rejects(db.query('insert into public.profiles (id) values ($1)', [userA]), { code: '42501' });
    await db.exec('reset role');
    await db.query('delete from auth.users where id=$1', [userB]);
    assert.equal((await db.query('select count(*)::integer as count from public.profiles')).rows[0].count, 1);
    assert.equal((await db.query('select display_name from public.profiles')).rows[0].display_name, 'A saved');
  } finally { await db.close(); }
});
