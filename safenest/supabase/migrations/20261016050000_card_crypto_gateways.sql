-- Card (Stripe) and cryptocurrency (NOWPayments) gateways beside SSLCOMMERZ, plus a
-- Bangladesh / Global product split. Nothing here enables a provider, opens checkout or
-- seeds a payable price: every new row is disabled and prices are proposals the owner
-- confirms in the dashboard before enabling.

-- 1. Providers describe where their hosted checkout may redirect, which currencies they
--    settle, and how long a buyer has to complete payment (crypto confirmations are slow).
alter table payments.providers add column checkout_url_pattern text;
alter table payments.providers add column currencies text[] not null default '{BDT}';
alter table payments.providers add column checkout_window_seconds integer not null default 1800
  check (checkout_window_seconds between 600 and 86400);
alter table payments.providers add constraint providers_currencies_valid
  check (cardinality(currencies) >= 1 and array_to_string(currencies,',') ~ '^[A-Z]{3}(,[A-Z]{3})*$');

-- 2. Products belong to one edition: Bangladesh (BDT) or Global (USD).
alter table payments.products add column region text not null default 'bd' check (region in ('bd','global'));
alter table payments.products add constraint products_region_currency
  check ((region='bd' and currency='BDT') or (region='global' and currency<>'BDT'));

-- 3. A hosted session may carry a provider-side payment identifier learned later
--    (NOWPayments assigns the payment id after the invoice is opened).
alter table payments.sessions add column provider_payment_id text
  check (provider_payment_id is null or length(provider_payment_id) between 1 and 200);

-- Checkout URL patterns for the providers this codebase can drive. Existing SSLCOMMERZ rows
-- keep their enabled flag; new provider rows start disabled in both environments.
insert into payments.providers(provider,environment,enabled,checkout_url_pattern,currencies,checkout_window_seconds)
values
  ('sslcommerz','sandbox',false,'^https://sandbox\.sslcommerz\.com/','{BDT}',1800),
  ('sslcommerz','live',false,'^https://securepay\.sslcommerz\.com/','{BDT}',1800),
  ('stripe','sandbox',false,'^https://checkout\.stripe\.com/','{USD,EUR,BDT}',1800),
  ('stripe','live',false,'^https://checkout\.stripe\.com/','{USD,EUR,BDT}',1800),
  ('nowpayments','sandbox',false,'^https://sandbox\.nowpayments\.io/payment/','{USD}',7200),
  ('nowpayments','live',false,'^https://nowpayments\.io/payment/','{USD}',7200)
on conflict (provider,environment) do update set
  checkout_url_pattern=excluded.checkout_url_pattern,
  currencies=excluded.currencies,
  checkout_window_seconds=excluded.checkout_window_seconds;

-- Proposed Global edition prices (USD). Disabled until the owner confirms them.
insert into payments.products(product_code,environment,plan_code,amount_minor,currency,duration_seconds,enabled,region)
select p.code,e.environment,p.plan,p.amount,'USD',p.seconds,false,'global'
from (values ('global_monthly_v1','monthly',499,2592000),
  ('global_quarterly_v1','quarterly',1299,7776000),('global_annual_v1','annual',3999,31536000)) p(code,plan,amount,seconds)
cross join (values ('sandbox'),('live')) e(environment)
on conflict (product_code,environment) do nothing;

-- 4. Orders respect the provider's payment window and currency support.
create or replace function public.create_payment_order(
  p_user_id uuid, p_product_code text, p_provider text,
  p_environment text, p_idempotency_key text
) returns jsonb language plpgsql security invoker set search_path = '' as $$
declare
  product payments.products%rowtype;
  prov payments.providers%rowtype;
  existing payments.orders%rowtype;
begin
  perform 1 from payments.configuration where singleton and mode=p_environment for share;
  if not found then raise exception 'payment_processing_disabled' using errcode='P0001'; end if;
  select * into prov from payments.providers where provider=p_provider and environment=p_environment and enabled for share;
  if not found then raise exception 'provider_disabled' using errcode='P0001'; end if;
  select * into existing from payments.orders where user_id=p_user_id and idempotency_key=p_idempotency_key;
  if found then
    if (existing.product_code,existing.provider,existing.environment) is distinct from
       (p_product_code,p_provider,p_environment) then
      raise exception 'idempotency_conflict' using errcode='P0001';
    end if;
    return to_jsonb(existing);
  end if;
  select * into product from payments.products where product_code=p_product_code
    and environment=p_environment and enabled for share;
  if not found then raise exception 'product_disabled' using errcode='P0001'; end if;
  if not (product.currency = any(prov.currencies)) then
    raise exception 'provider_currency_unsupported' using errcode='P0001';
  end if;
  insert into payments.orders(user_id,idempotency_key,product_code,provider,environment,plan_code,
    amount_minor,currency,duration_seconds,expires_at)
    values(p_user_id,p_idempotency_key,p_product_code,p_provider,p_environment,product.plan_code,
      product.amount_minor,product.currency,product.duration_seconds,
      now() + prov.checkout_window_seconds * interval '1 second')
    on conflict (user_id,idempotency_key) do nothing;
  select * into existing from payments.orders where user_id=p_user_id and idempotency_key=p_idempotency_key;
  if (existing.product_code,existing.provider,existing.environment) is distinct from
     (p_product_code,p_provider,p_environment) then
    raise exception 'idempotency_conflict' using errcode='P0001';
  end if;
  return to_jsonb(existing);
end;
$$;

-- 5. Provider-agnostic replacements for the SSLCOMMERZ-specific helpers.
drop function public.payment_order_lookup(text,text);
create function public.payment_order_lookup(p_reference text,p_provider text,p_environment text)
returns jsonb language sql security invoker set search_path='' as $$
  select to_jsonb(o) || jsonb_build_object('receipt_transaction_id',r.transaction_id,'receipt_paid_at',r.paid_at,
    'session_id',s.session_id,'provider_payment_id',s.provider_payment_id)
  from payments.orders o
    left join payments.receipts r on r.order_id=o.id
    left join payments.sessions s on s.order_id=o.id
  where (o.id::text=p_reference or o.provider_order_id=p_reference)
    and o.provider=p_provider and o.environment=p_environment;
$$;

create or replace function public.payment_session_save(p_order_id uuid,p_session_id text,p_url text)
returns void language plpgsql security invoker set search_path='' as $$
declare pattern text;
begin
  select p.checkout_url_pattern into pattern from payments.orders o
    join payments.providers p on p.provider=o.provider and p.environment=o.environment where o.id=p_order_id;
  if pattern is null or length(p_session_id) not between 1 and 200 or length(p_url) not between 1 and 2000
    or p_url !~ pattern or p_url !~ '^https://' then raise exception 'invalid_session'; end if;
  update payments.sessions set state='ready',session_id=p_session_id,checkout_url=p_url
    where order_id=p_order_id and state='initializing';
  if not found then raise exception 'session_not_claimed'; end if;
end;
$$;

-- Records the provider's own payment identifier once a notification names it, so a later
-- reconciliation can ask the provider about that payment directly. Never changes money state.
create function public.payment_session_note(p_order_id uuid,p_payment_id text)
returns void language plpgsql security invoker set search_path='' as $$
begin
  if length(p_payment_id) not between 1 and 200 then raise exception 'invalid_session'; end if;
  update payments.sessions set provider_payment_id=p_payment_id
    where order_id=p_order_id and (provider_payment_id is null or provider_payment_id=p_payment_id);
  if not found then raise exception 'session_payment_conflict'; end if;
end;
$$;

-- The catalogue lists every enabled product of the current mode together with the enabled
-- providers able to settle its currency. Products with no provider are omitted.
create or replace function public.payment_product_catalogue(p_environment text)
returns jsonb language sql security invoker set search_path='' as $$
  select coalesce(jsonb_agg(jsonb_build_object('code',p.product_code,'plan',p.plan_code,'region',p.region,
      'amount_minor',p.amount_minor,'currency',p.currency,'duration_seconds',p.duration_seconds,
      'providers',(select coalesce(jsonb_agg(v.provider order by v.provider),'[]'::jsonb) from payments.providers v
        where v.environment=p_environment and v.enabled and p.currency = any(v.currencies)))
      order by p.region,p.amount_minor),'[]'::jsonb)
  from payments.products p where p.environment=p_environment and p.enabled
    and exists(select 1 from payments.configuration where mode=p_environment)
    and exists(select 1 from payments.providers v where v.environment=p_environment and v.enabled
      and p.currency = any(v.currencies));
$$;

drop function public.payment_reconciliation_batch(text);
create function public.payment_reconciliation_batch(p_provider text,p_environment text)
returns setof jsonb language plpgsql security invoker set search_path='' as $$
declare o payments.orders%rowtype;
begin
  perform 1 from payments.configuration where mode=p_environment for share;
  if not found then raise exception 'payment_processing_disabled'; end if;
  for o in select * from payments.orders where provider=p_provider and environment=p_environment
    and state in ('pending','failed','cancelled') and created_at>now()-interval '7 days'
    and coalesce(reconciled_at,created_at)<now()-interval '5 minutes'
    order by coalesce(reconciled_at,created_at) limit 3 for update skip locked
  loop
    update payments.orders set reconciled_at=now() where id=o.id;
    return next to_jsonb(o) || coalesce((select jsonb_build_object('session_id',s.session_id,
      'provider_payment_id',s.provider_payment_id) from payments.sessions s where s.order_id=o.id),'{}'::jsonb);
  end loop;
end;
$$;

revoke all on function public.payment_order_lookup(text,text,text),public.payment_session_note(uuid,text),
  public.payment_reconciliation_batch(text,text) from public,anon,authenticated;
grant execute on function public.payment_order_lookup(text,text,text),public.payment_session_note(uuid,text),
  public.payment_reconciliation_batch(text,text) to service_role;
