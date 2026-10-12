-- STAGED, NOT APPLIED. Move into supabase/migrations/ only when:
--   1. TOTP is enabled in the hosted dashboard (Authentication > MFA);
--   2. the admin console and support desk can enrol a factor and step the
--      session up (supabase.auth.mfa.enroll / challengeAndVerify), so a
--      signed-in admin can reach aal2;
--   3. every admin and operator has enrolled.
-- Deploying it earlier makes admin tables read as empty and turns operators
-- into ordinary users, with no error shown.
--
-- Covered by tests: intelligence/test/database.test.mjs and
-- tests/support-desk.test.mjs load this file and assert the aal1 negatives.

-- Admin access needs the server-set flag AND an MFA-verified session (aal2).
create or replace function public.is_safenest_admin() returns boolean
language sql stable set search_path='' as $$
  select coalesce((select auth.jwt()) -> 'app_metadata' ->> 'safenest_admin', '') = 'true'
     and coalesce((select auth.jwt()) ->> 'aal', '') = 'aal2';
$$;
revoke all on function public.is_safenest_admin() from public;
grant execute on function public.is_safenest_admin() to authenticated;

do $$
declare t text;
begin
  foreach t in array array['gambling_brands','gambling_domains','domain_sources','brand_aliases','domain_observations']
  loop
    if to_regclass('public.'||t) is null then continue; end if;
    execute format('alter policy admin_read on public.%I using (public.is_safenest_admin())', t);
    execute format('alter policy admin_write on public.%I using (public.is_safenest_admin()) with check (public.is_safenest_admin())', t);
  end loop;
  if to_regclass('public.blocklist_versions') is not null then
    execute 'alter policy admin_read on public.blocklist_versions using (public.is_safenest_admin())';
  end if;
  if to_regclass('public.intelligence_audit') is not null then
    execute 'alter policy admin_read on public.intelligence_audit using (public.is_safenest_admin())';
  end if;
end $$;

-- Support operators also need an MFA-verified session.
do $$
begin
  if to_regprocedure('supportdesk.is_operator()') is null then return; end if;
  create or replace function supportdesk.is_operator() returns boolean
  language sql stable security definer set search_path='' as $f$
   select coalesce((select auth.jwt()) ->> 'aal', '') = 'aal2'
     and exists(select 1 from auth.users u join supportdesk.operators o on o.email=lower(u.email)
     where u.id=supportdesk.actor());
  $f$;
end $$;
