-- Keep the existing admin authorization; cache JWT/UID once per statement.
alter policy admin_read on public.gambling_brands using (((select auth.jwt()) -> 'app_metadata' ->> 'safenest_admin') = 'true');
alter policy admin_write on public.gambling_brands using (((select auth.jwt()) -> 'app_metadata' ->> 'safenest_admin') = 'true') with check (((select auth.jwt()) -> 'app_metadata' ->> 'safenest_admin') = 'true');
alter policy admin_read on public.gambling_domains using (((select auth.jwt()) -> 'app_metadata' ->> 'safenest_admin') = 'true');
alter policy admin_write on public.gambling_domains using (((select auth.jwt()) -> 'app_metadata' ->> 'safenest_admin') = 'true') with check (((select auth.jwt()) -> 'app_metadata' ->> 'safenest_admin') = 'true');
alter policy admin_read on public.domain_sources using (((select auth.jwt()) -> 'app_metadata' ->> 'safenest_admin') = 'true');
alter policy admin_write on public.domain_sources using (((select auth.jwt()) -> 'app_metadata' ->> 'safenest_admin') = 'true') with check (((select auth.jwt()) -> 'app_metadata' ->> 'safenest_admin') = 'true');
alter policy admin_read on public.brand_aliases using (((select auth.jwt()) -> 'app_metadata' ->> 'safenest_admin') = 'true');
alter policy admin_write on public.brand_aliases using (((select auth.jwt()) -> 'app_metadata' ->> 'safenest_admin') = 'true') with check (((select auth.jwt()) -> 'app_metadata' ->> 'safenest_admin') = 'true');
alter policy admin_read on public.domain_observations using (((select auth.jwt()) -> 'app_metadata' ->> 'safenest_admin') = 'true');
alter policy admin_write on public.domain_observations using (((select auth.jwt()) -> 'app_metadata' ->> 'safenest_admin') = 'true') with check (((select auth.jwt()) -> 'app_metadata' ->> 'safenest_admin') = 'true');
alter policy admin_read on public.blocklist_versions using (((select auth.jwt()) -> 'app_metadata' ->> 'safenest_admin') = 'true');
alter policy admin_read on public.intelligence_audit using (((select auth.jwt()) -> 'app_metadata' ->> 'safenest_admin') = 'true');
alter policy admin_audit_insert on public.intelligence_audit with check ((((select auth.jwt()) -> 'app_metadata' ->> 'safenest_admin') = 'true') and actor = (select auth.uid()));
