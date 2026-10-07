import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { PGlite } from '@electric-sql/pglite';

const tables = ['brand_aliases', 'domain_observations', 'domain_sources', 'gambling_brands', 'gambling_domains'];
const updateColumns = {brand_aliases:'alias', domain_observations:'dns_status', domain_sources:'source_name', gambling_brands:'name', gambling_domains:'notes'};
const migrations = ['20260929092635_gambling_intelligence', '20261005035025_launch_rls_performance', '20261007061659_consolidate_intelligence_admin_policies'];

test('consolidation preserves one admin-only policy and row isolation on every affected table', async () => {
  const db = new PGlite();
  try {
    await db.exec(`
      create role anon;
      create role authenticated;
      create role service_role bypassrls;
      create schema auth;
      create function auth.uid() returns uuid language sql as
        $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
      create function auth.jwt() returns jsonb language sql as
        $$select coalesce(nullif(current_setting('request.jwt.claims',true),''),'{}')::jsonb$$;
      grant usage on schema auth to authenticated,anon;
    `);
    for (const migration of migrations)
      await db.exec(readFileSync(new URL(`../../supabase/migrations/${migration}.sql`, import.meta.url), 'utf8'));

    const isolation = (await db.query(`select relname, relrowsecurity from pg_class
      where relnamespace='public'::regnamespace and relname=any($1::text[]) order by relname`, [tables])).rows;
    assert.equal(isolation.length, tables.length);
    assert.ok(isolation.every(table => table.relrowsecurity));
    const readOnly = (await db.query(`select tablename, policyname, cmd from pg_policies
      where schemaname='public' and tablename in ('blocklist_versions','intelligence_audit') and cmd='SELECT'
      order by tablename`)).rows;
    assert.deepEqual(readOnly, [
      {tablename:'blocklist_versions', policyname:'admin_read', cmd:'SELECT'},
      {tablename:'intelligence_audit', policyname:'admin_read', cmd:'SELECT'},
    ]);

    const policies = (await db.query(`select tablename, policyname, cmd, roles, qual, with_check
      from pg_policies where schemaname='public' and tablename = any($1::text[]) order by tablename`, [tables])).rows;
    assert.equal(policies.length, tables.length);
    assert.deepEqual(policies.map(p => p.tablename), tables);
    for (const policy of policies) {
      assert.equal(policy.policyname, 'admin_write');
      assert.equal(policy.cmd, 'ALL');
      assert.deepEqual(policy.roles, ['authenticated']);
      assert.match(policy.qual, /app_metadata/);
      assert.match(policy.qual, /safenest_admin/);
      assert.doesNotMatch(policy.qual, /user_metadata/);
      assert.equal(policy.with_check, policy.qual);
    }
    // Seed only this disposable PostgreSQL instance, never the live project.
    await db.exec(`
      insert into gambling_brands(id,name,slug) values('00000000-0000-0000-0000-000000000010','Test','test');
      insert into gambling_domains(id,brand_id,domain,registrable_domain) values('00000000-0000-0000-0000-000000000020','00000000-0000-0000-0000-000000000010','test.example.com','example.com');
      insert into domain_sources(domain_id,source_name,source_url,source_type,evidence_summary) values('00000000-0000-0000-0000-000000000020','Fixture','https://example.com/evidence','discovery','Disposable fixture evidence only');
      insert into brand_aliases(brand_id,alias,normalized_alias) values('00000000-0000-0000-0000-000000000010','Test','test');
      insert into domain_observations(domain_id,hostname,dns_status) values('00000000-0000-0000-0000-000000000020','test.example.com','fixture');
      set role authenticated;
      select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
    `);
    for (const claims of [{}, {user_metadata:{safenest_admin:true}}, {app_metadata:{safenest_admin:false}}]) {
      await db.query(`select set_config('request.jwt.claims',$1,false)`, [JSON.stringify(claims)]);
      for (const table of tables) {
        assert.equal((await db.query(`select count(*)::int as n from ${table}`)).rows[0].n, 0);
        const column = updateColumns[table];
        assert.equal((await db.query(`update ${table} set ${column}=${column} returning id`)).rows.length, 0);
        assert.equal((await db.query(`delete from ${table} returning id`)).rows.length, 0);
      }
      await assert.rejects(db.exec(`insert into domain_observations(domain_id,hostname,dns_status) values('00000000-0000-0000-0000-000000000020','denied.example.com','fixture')`));
    }
    await db.query(`select set_config('request.jwt.claims',$1,false)`, [JSON.stringify({app_metadata:{safenest_admin:true}})]);
    for (const table of tables) {
      assert.equal((await db.query(`select count(*)::int as n from ${table}`)).rows[0].n, 1);
      const column = updateColumns[table];
      assert.equal((await db.query(`update ${table} set ${column}=${column} returning id`)).rows.length, 1);
    }
    for (const table of ['domain_observations','brand_aliases','domain_sources','gambling_domains','gambling_brands'])
      assert.equal((await db.query(`delete from ${table} returning id`)).rows.length, 1);
    await db.exec(`reset role; set role anon;`);
    for (const table of tables)
      await assert.rejects(db.query(`select * from ${table}`));
  } finally { await db.close(); }
});
