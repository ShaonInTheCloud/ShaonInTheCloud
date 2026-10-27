import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';

test('domain_health is service-role only, validates names and exports the blocked states', async () => {
  const db = new PGlite();
  try {
    await db.exec(`create role anon; create role authenticated; create role service_role bypassrls;
      grant usage on schema public to anon, authenticated, service_role;`);
    await db.exec(await readFile(new URL('../supabase/migrations/20261018090000_domain_health.sql', import.meta.url), 'utf8'));
    await db.exec(`grant all on public.domain_health, public.domain_health_blocked to service_role;
      grant execute on function public.domain_health_summary() to service_role;
      insert into public.domain_health(domain, state) values
        ('a.bet','active'),('b.bet','dormant'),('c.bet','archived'),('d.bet','parked'),('e.bet','review'),('f.bet','unchecked');`);
    await assert.rejects(db.exec(`insert into public.domain_health(domain) values ('Bad Domain')`));
    await assert.rejects(db.exec(`insert into public.domain_health(domain, state) values ('g.bet','deleted')`));
    await db.exec('set role service_role');
    const blocked = (await db.query('select domain from public.domain_health_blocked order by domain')).rows.map(r => r.domain);
    assert.deepEqual(blocked, ['a.bet', 'b.bet', 'e.bet', 'f.bet'], 'archived and parked names are not blocked');
    const summary = (await db.query('select * from public.domain_health_summary()')).rows;
    assert.equal(summary.length, 6);
    await db.exec('reset role; set role anon');
    await assert.rejects(db.query('select * from public.domain_health'), {code: '42501'});
    await assert.rejects(db.query('select * from public.domain_health_blocked'), {code: '42501'});
    await db.exec('reset role; set role authenticated');
    await assert.rejects(db.query('select * from public.domain_health'), {code: '42501'});
  } finally { await db.close(); }
});
