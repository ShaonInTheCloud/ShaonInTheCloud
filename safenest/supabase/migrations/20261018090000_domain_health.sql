-- Smart blocklist health: liveness of the large bulk gambling list (≈541k names).
-- Written only by the checker (intelligence/src/health-runner.mjs, service role) running on a separate server.
-- Separate from gambling_domains, which holds hand-verified evidence for signed releases.
--
-- States:
--   unchecked  imported, not yet visited
--   active     resolves and still looks like gambling                       -> exported, rechecked daily
--   dormant    does not resolve / no response, dead for under 30 days         -> still exported, rechecked daily
--   archived   dead for 30+ days                                             -> NOT exported, rechecked weekly; revives if it comes back
--   parked     resolves but shows a parked / for-sale page                    -> not exported, rechecked weekly
--   review     alive but no longer looks like gambling (3 checks in a row),
--              or a new mirror found through a redirect                       -> exported until the team decides, rechecked every 3 days
create table public.domain_health (
  domain text primary key check (length(domain) <= 253 and domain = lower(domain) and domain ~ '^[a-z0-9-]+(\.[a-z0-9-]+)+$'),
  source text not null default 'bundled' check (length(source) <= 300),
  state text not null default 'unchecked' check (state in ('unchecked','active','dormant','archived','parked','review')),
  first_seen timestamptz not null default now(),
  last_checked timestamptz,
  next_check timestamptz not null default now(),
  last_alive timestamptz,
  dead_since timestamptz,
  fail_streak integer not null default 0 check (fail_streak >= 0),
  low_streak integer not null default 0 check (low_streak >= 0),
  http_status integer,
  redirect_to text check (redirect_to is null or length(redirect_to) <= 253),
  score smallint check (score is null or score between 0 and 100),
  signals text[] not null default '{}',
  last_error text check (last_error is null or length(last_error) <= 200),
  reviewed_by_team boolean not null default false
);
create index domain_health_due_idx on public.domain_health (next_check);
create index domain_health_state_idx on public.domain_health (state);

alter table public.domain_health enable row level security;
-- No policies: browsers and apps never read or write this table. Only the service role (the checker) does.
revoke all on public.domain_health from anon, authenticated;

-- Domains that should be blocked right now (exported to the apps and the iPhone DNS filter).
create or replace view public.domain_health_blocked with (security_invoker = true) as
  select domain from public.domain_health where state in ('unchecked','active','dormant','review');
revoke all on public.domain_health_blocked from anon, authenticated;

-- One-line status for the team.
create or replace function public.domain_health_summary()
returns table(state text, domains bigint, checked_last_24h bigint)
language sql stable security invoker set search_path = public as $$
  select state, count(*), count(*) filter (where last_checked > now() - interval '24 hours')
  from public.domain_health group by state order by state
$$;
revoke all on function public.domain_health_summary() from public, anon, authenticated;
