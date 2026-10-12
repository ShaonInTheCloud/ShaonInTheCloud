-- Security hardening, part 1 (safe to deploy now; no client change needed):
--   * force row level security on every table, so table owners and helper
--     functions go through policies unless they are superuser/bypassrls;
--   * audit rows come only from the trigger, never from a direct client insert.
--
-- Part 2 (MFA for admins and support operators) is staged in
-- supabase/pending-migrations/ and must ship together with the MFA enrol and
-- step-up UI, or every admin is locked out silently.
--
-- Each step checks the object exists first, so the migration also applies
-- cleanly to the partial schemas the unit tests build.

do $$
declare t text;
begin
  foreach t in array array[
    'public.profiles', 'public.protection_entitlements',
    'public.support_tickets', 'public.support_messages', 'supportdesk.operators',
    'public.gambling_brands', 'public.gambling_domains', 'public.domain_sources',
    'public.brand_aliases', 'public.domain_observations', 'public.blocklist_versions',
    'public.intelligence_audit',
    'payments.configuration', 'payments.events', 'payments.orders', 'payments.products',
    'payments.providers', 'payments.receipts', 'payments.sandbox_entitlements',
    'payments.sessions', 'payments.trial_claims'
  ] loop
    if to_regclass(t) is not null then execute format('alter table %s force row level security', t); end if;
  end loop;

  -- The audit trigger runs as definer (table owner), so the client-facing
  -- insert policy and grant are no longer needed and are removed.
  if to_regclass('public.intelligence_audit') is not null then
    execute 'drop policy if exists admin_audit_insert on public.intelligence_audit';
    execute 'revoke insert on public.intelligence_audit from authenticated';
    execute 'alter function public.intelligence_audit_change() security definer';
  end if;
end $$;
