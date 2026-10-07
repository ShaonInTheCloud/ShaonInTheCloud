-- The admin_write policies use FOR ALL and the same admin predicate as the
-- admin_read policies, so they already authorize SELECT without broadening
-- access. Remove the redundant SELECT policies flagged by the database advisor.
drop policy if exists admin_read on public.gambling_brands;
drop policy if exists admin_read on public.gambling_domains;
drop policy if exists admin_read on public.domain_sources;
drop policy if exists admin_read on public.brand_aliases;
drop policy if exists admin_read on public.domain_observations;
