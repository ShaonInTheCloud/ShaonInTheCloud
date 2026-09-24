-- Passwords and email identities stay in Supabase Auth.
-- A profile is created on the user's first explicit Save profile action.
-- No auth.users trigger: profile failures cannot block account creation.
create table public.profiles (
  id uuid primary key default auth.uid() references auth.users(id) on delete cascade,
  display_name text not null default '' check (char_length(display_name) <= 80),
  language text not null default 'en' check (language in ('en', 'bn')),
  created_at timestamptz not null default now()
);

alter table public.profiles enable row level security;
revoke all on public.profiles from public, anon, authenticated;
grant select on public.profiles to authenticated;
grant insert (id, display_name, language) on public.profiles to authenticated;
grant update (display_name, language) on public.profiles to authenticated;
grant all on public.profiles to service_role;

create policy "Read own profile" on public.profiles
  for select to authenticated using ((select auth.uid()) = id);
create policy "Create own profile" on public.profiles
  for insert to authenticated with check ((select auth.uid()) = id);
create policy "Edit own profile" on public.profiles
  for update to authenticated
  using ((select auth.uid()) = id) with check ((select auth.uid()) = id);

comment on table public.profiles is 'Private name and language preferences. No browsing history or protection entitlement.';
