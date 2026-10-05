-- Payment webhooks or a trusted administrator issue entitlements. The client only reads its own row.
-- A future billing integration must verify payment server-side before INSERT/UPDATE.
create table public.protection_entitlements (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  plan_code text not null check (plan_code in ('weekly', 'monthly', 'annual')),
  status text not null check (status in ('active', 'expired', 'revoked')),
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  provider text not null,
  provider_reference text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint entitlement_valid_window check (ends_at > starts_at),
  constraint entitlement_provider_reference_unique unique (provider, provider_reference)
);
create index protection_entitlements_owner_window
  on public.protection_entitlements (user_id, ends_at desc);
alter table public.protection_entitlements enable row level security;
revoke all on public.protection_entitlements from anon, authenticated;
grant select on public.protection_entitlements to authenticated;
grant all on public.protection_entitlements to service_role;
create policy "read own entitlement"
  on public.protection_entitlements
  for select to authenticated
  using ((select auth.uid()) = user_id);
comment on table public.protection_entitlements is
  'Server-issued paid protection windows. A status of active is valid only between starts_at and ends_at; the client cannot write these rows.';
