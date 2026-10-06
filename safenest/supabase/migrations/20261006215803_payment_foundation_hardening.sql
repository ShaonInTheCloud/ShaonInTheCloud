-- Cover FK lookups for operator catalogue/provider changes and parent deletes.
create index payment_orders_product on payments.orders(product_code,environment);
create index payment_orders_provider on payments.orders(provider,environment);

-- Explicit restrictive denials preserve the intended no-client-access boundary
-- even if a future permissive policy is accidentally added. Schema/table grants
-- remain revoked; service_role deliberately uses its existing BYPASSRLS role.
create policy deny_clients on payments.configuration as restrictive for all to anon,authenticated using (false) with check (false);
create policy deny_clients on payments.providers as restrictive for all to anon,authenticated using (false) with check (false);
create policy deny_clients on payments.products as restrictive for all to anon,authenticated using (false) with check (false);
create policy deny_clients on payments.orders as restrictive for all to anon,authenticated using (false) with check (false);
create policy deny_clients on payments.receipts as restrictive for all to anon,authenticated using (false) with check (false);
create policy deny_clients on payments.events as restrictive for all to anon,authenticated using (false) with check (false);
create policy deny_clients on payments.sandbox_entitlements as restrictive for all to anon,authenticated using (false) with check (false);
