-- Extends the deployed private ledger; no products, prices or providers are seeded.
alter table payments.orders add column provider_order_id text not null
  default ('SN' || substr(replace(gen_random_uuid()::text,'-',''),1,28));
alter table payments.orders add constraint payment_order_provider_id_unique unique(provider_order_id);
alter table payments.orders add column reconciled_at timestamptz;
create table payments.sessions (
  order_id uuid primary key references payments.orders(id) on delete cascade,
  state text not null check(state in ('initializing','ready','review')),
  session_id text, checkout_url text,
  created_at timestamptz not null default now(),
  check ((state='ready' and session_id is not null and checkout_url is not null)
      or (state<>'ready' and session_id is null and checkout_url is null))
);
alter table payments.sessions enable row level security;
revoke all on payments.sessions from public,anon,authenticated;
grant select,insert,update,delete on payments.sessions to service_role;
create policy deny_clients on payments.sessions as restrictive for all
  to anon,authenticated using(false) with check(false);

create function public.payment_order_lookup(p_reference text,p_environment text)
returns jsonb language sql security invoker set search_path='' as $$
  select to_jsonb(o) || jsonb_build_object('receipt_transaction_id',r.transaction_id,'receipt_paid_at',r.paid_at)
  from payments.orders o left join payments.receipts r on r.order_id=o.id
  where (o.id::text=p_reference or o.provider_order_id=p_reference)
    and o.provider='sslcommerz' and o.environment=p_environment;
$$;
create function public.payment_session_claim(p_order_id uuid)
returns jsonb language plpgsql security invoker set search_path='' as $$
declare o payments.orders%rowtype; s payments.sessions%rowtype;
begin
  select * into o from payments.orders where id=p_order_id for update;
  if not found or o.state<>'pending' or o.expires_at<=clock_timestamp() then
    raise exception 'order_not_payable';
  end if;
  perform 1 from payments.configuration where mode=o.environment for share;
  if not found then raise exception 'payment_processing_disabled'; end if;
  perform 1 from payments.providers where provider=o.provider and environment=o.environment and enabled for share;
  if not found then raise exception 'provider_disabled'; end if;
  insert into payments.sessions(order_id,state) values(o.id,'initializing') on conflict do nothing;
  if found then return jsonb_build_object('state','claimed'); end if;
  select * into s from payments.sessions where order_id=o.id;
  return to_jsonb(s);
end;
$$;
create function public.payment_session_save(p_order_id uuid,p_session_id text,p_url text)
returns void language plpgsql security invoker set search_path='' as $$
begin
  if length(p_session_id) not between 1 and 200 or length(p_url) not between 1 and 2000
    or p_url !~ '^https://(sandbox|securepay)\.sslcommerz\.com/' then raise exception 'invalid_session'; end if;
  update payments.sessions set state='ready',session_id=p_session_id,checkout_url=p_url
    where order_id=p_order_id and state='initializing';
  if not found then raise exception 'session_not_claimed'; end if;
end;
$$;
create function public.payment_session_review(p_order_id uuid)
returns void language sql security invoker set search_path='' as $$
  update payments.sessions set state='review' where order_id=p_order_id and state='initializing';
$$;
create function public.payment_product_catalogue(p_environment text)
returns jsonb language sql security invoker set search_path='' as $$
  select coalesce(jsonb_agg(jsonb_build_object('code',p.product_code,'plan',p.plan_code,
      'amount_minor',p.amount_minor,'currency',p.currency,'duration_seconds',p.duration_seconds)
      order by p.amount_minor),'[]'::jsonb)
  from payments.products p where p.environment=p_environment and p.enabled
    and exists(select 1 from payments.configuration where mode=p_environment)
    and exists(select 1 from payments.providers where provider='sslcommerz' and environment=p_environment and enabled);
$$;
create function public.payment_order_status(p_order_id uuid,p_user_id uuid)
returns jsonb language sql security invoker set search_path='' as $$
  select jsonb_build_object('order_id',id,'state',state,'environment',environment,
    'amount_minor',amount_minor,'currency',currency,'expires_at',expires_at)
  from payments.orders where id=p_order_id and user_id=p_user_id;
$$;
create function public.payment_reconciliation_batch(p_environment text)
returns setof jsonb language plpgsql security invoker set search_path='' as $$
declare o payments.orders%rowtype;
begin
  perform 1 from payments.configuration where mode=p_environment for share;
  if not found then raise exception 'payment_processing_disabled'; end if;
  for o in select * from payments.orders where provider='sslcommerz' and environment=p_environment
    and state in ('pending','failed','cancelled') and created_at>now()-interval '7 days'
    and coalesce(reconciled_at,created_at)<now()-interval '5 minutes'
    order by coalesce(reconciled_at,created_at) limit 3 for update skip locked
  loop
    update payments.orders set reconciled_at=now() where id=o.id;
    return next to_jsonb(o);
  end loop;
end;
$$;
revoke all on function public.payment_order_lookup(text,text),public.payment_session_claim(uuid),
  public.payment_session_save(uuid,text,text),public.payment_session_review(uuid),
  public.payment_product_catalogue(text),public.payment_order_status(uuid,uuid),
  public.payment_reconciliation_batch(text) from public,anon,authenticated;
grant execute on function public.payment_order_lookup(text,text),public.payment_session_claim(uuid),
  public.payment_session_save(uuid,text,text),public.payment_session_review(uuid),
  public.payment_product_catalogue(text),public.payment_order_status(uuid,uuid),
  public.payment_reconciliation_batch(text) to service_role;

-- Bound authenticated order abuse while preserving account-scoped retry keys.
create function payments.bound_order_creation() returns trigger
language plpgsql security invoker set search_path='' as $$
begin
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(new.user_id::text,0));
  if exists(select 1 from payments.orders where user_id=new.user_id and idempotency_key=new.idempotency_key)
    then return new; end if;
  if (select count(*) from payments.orders where user_id=new.user_id and created_at>now()-interval '1 hour')>=10
    then raise exception 'order_rate_limited'; end if;
  return new;
end;
$$;
revoke all on function payments.bound_order_creation() from public,anon,authenticated;
grant execute on function payments.bound_order_creation() to service_role;
create trigger bound_order_creation before insert on payments.orders
  for each row execute function payments.bound_order_creation();
