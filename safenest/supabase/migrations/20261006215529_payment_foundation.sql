-- No products, provider credentials, orders or entitlements are seeded.
-- Only verified server adapters may call the service_role-only RPCs below.
create schema payments;
revoke all on schema payments from public, anon, authenticated;
grant usage on schema payments to service_role;

create table payments.configuration (
  singleton boolean primary key default true check (singleton),
  mode text not null default 'disabled' check (mode in ('disabled','sandbox','live'))
);
insert into payments.configuration default values;

create table payments.providers (
  provider text not null check (provider ~ '^[a-z][a-z0-9_-]{0,39}$'),
  environment text not null check (environment in ('sandbox','live')),
  enabled boolean not null default false,
  primary key (provider,environment)
);
create table payments.products (
  product_code text not null check (product_code ~ '^[a-z][a-z0-9_-]{0,79}$'),
  environment text not null check (environment in ('sandbox','live')),
  plan_code text not null check (plan_code in ('weekly','monthly','annual')),
  amount_minor bigint not null check (amount_minor > 0 and amount_minor <= 9007199254740991),
  currency text not null check (currency ~ '^[A-Z]{3}$'),
  duration_seconds integer not null check (duration_seconds > 0),
  enabled boolean not null default false,
  primary key (product_code,environment)
);
create table payments.orders (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  idempotency_key text not null check (idempotency_key ~ '^[A-Za-z0-9_-]{16,128}$'),
  product_code text not null,
  provider text not null,
  environment text not null,
  plan_code text not null check (plan_code in ('weekly','monthly','annual')),
  amount_minor bigint not null check (amount_minor > 0 and amount_minor <= 9007199254740991),
  currency text not null check (currency ~ '^[A-Z]{3}$'),
  duration_seconds integer not null check (duration_seconds > 0),
  state text not null default 'pending' check (state in ('pending','paid','failed','cancelled','refunded','chargeback')),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '30 minutes',
  updated_at timestamptz not null default now(),
  unique (user_id,idempotency_key),
  foreign key (provider,environment) references payments.providers(provider,environment),
  foreign key (product_code,environment) references payments.products(product_code,environment),
  check (isfinite(created_at) and isfinite(expires_at) and expires_at > created_at)
);
create index payment_orders_reconciliation on payments.orders(environment,state,updated_at);

-- One immutable economic identity per order and one order per provider transaction.
create table payments.receipts (
  order_id uuid primary key references payments.orders(id) on delete cascade,
  provider text not null,
  environment text not null,
  transaction_id text not null check (length(transaction_id) between 1 and 200),
  paid_at timestamptz not null check (isfinite(paid_at)),
  state text not null check (state in ('paid','refunded','chargeback')),
  unique (provider,environment,transaction_id)
);
create table payments.events (
  provider text not null,
  environment text not null,
  event_id text not null check (length(event_id) between 1 and 200),
  order_id uuid not null references payments.orders(id) on delete cascade,
  normalized jsonb not null,
  result jsonb not null,
  processed_at timestamptz not null default now(),
  primary key (provider,environment,event_id)
);
create index payment_events_order on payments.events(order_id);

-- Sandbox grants cannot be read by protection-access or activate the Android app.
create table payments.sandbox_entitlements (
  order_id uuid primary key references payments.orders(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  plan_code text not null,
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  status text not null check (status in ('active','revoked')),
  check (isfinite(starts_at) and isfinite(ends_at) and ends_at > starts_at)
);
create index sandbox_entitlements_owner on payments.sandbox_entitlements(user_id);
alter table public.protection_entitlements add column payment_order_id uuid unique
  references payments.orders(id) on delete cascade;
alter table public.protection_entitlements add constraint entitlement_finite_window
  check (isfinite(starts_at) and isfinite(ends_at));

alter table payments.configuration enable row level security;
alter table payments.providers enable row level security;
alter table payments.products enable row level security;
alter table payments.orders enable row level security;
alter table payments.receipts enable row level security;
alter table payments.events enable row level security;
alter table payments.sandbox_entitlements enable row level security;
revoke all on all tables in schema payments from public, anon, authenticated;
grant select,insert,update,delete on all tables in schema payments to service_role;

-- p_user_id is derived from a live Auth lookup by the server, never request JSON.
-- Auth confirmation/anonymous checks happen in the authenticated Edge handler.
-- The FK rejects deleted identities without expanding service_role access to auth.
create function public.create_payment_order(
  p_user_id uuid, p_product_code text, p_provider text,
  p_environment text, p_idempotency_key text
) returns jsonb language plpgsql security invoker set search_path = '' as $$
declare
  product payments.products%rowtype;
  existing payments.orders%rowtype;
begin
  perform 1 from payments.configuration where singleton and mode=p_environment for share;
  if not found then raise exception 'payment_processing_disabled' using errcode='P0001'; end if;
  perform 1 from payments.providers where provider=p_provider and environment=p_environment and enabled for share;
  if not found then raise exception 'provider_disabled' using errcode='P0001'; end if;
  -- A retry preserves the original price/window even when catalogue prices change.
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
  insert into payments.orders(user_id,idempotency_key,product_code,provider,environment,plan_code,
    amount_minor,currency,duration_seconds)
    values(p_user_id,p_idempotency_key,p_product_code,p_provider,p_environment,product.plan_code,
      product.amount_minor,product.currency,product.duration_seconds)
    on conflict (user_id,idempotency_key) do nothing;
  select * into existing from payments.orders where user_id=p_user_id and idempotency_key=p_idempotency_key;
  if (existing.product_code,existing.provider,existing.environment) is distinct from
     (p_product_code,p_provider,p_environment) then
    raise exception 'idempotency_conflict' using errcode='P0001';
  end if;
  return to_jsonb(existing);
end;
$$;

-- Trusted adapter output only: this function cannot verify gateway signatures/credentials.
-- No HTTP notification or browser flag is allowed to call it directly.
create function public.process_validated_payment(
  p_order_id uuid, p_provider text, p_environment text, p_event_id text,
  p_transaction_id text, p_status text, p_amount_minor bigint, p_currency text,
  p_paid_at timestamptz, p_validation_reference text, p_evidence_sha256 text
) returns jsonb language plpgsql security invoker set search_path = '' as $$
declare
  ord payments.orders%rowtype;
  receipt payments.receipts%rowtype;
  previous payments.events%rowtype;
  normalized jsonb;
  outcome jsonb;
  entitlement_id uuid;
  grant_end timestamptz;
begin
  perform 1 from payments.configuration where singleton and mode=p_environment for share;
  if not found then raise exception 'payment_processing_disabled' using errcode='P0001'; end if;
  perform 1 from payments.providers where provider=p_provider and environment=p_environment and enabled for share;
  if not found then raise exception 'provider_disabled' using errcode='P0001'; end if;
  if p_status is null or p_status not in ('pending','paid','failed','cancelled','refunded','chargeback')
    or p_event_id is null or length(p_event_id) not between 1 and 200
    or p_validation_reference is null or length(p_validation_reference) not between 1 and 200
    or p_evidence_sha256 is null or p_evidence_sha256 !~ '^[0-9a-f]{64}$' then
    raise exception 'invalid_validated_evidence' using errcode='P0001';
  end if;
  -- Serialize event reuse across orders, then serialize the complete order transaction.
  -- A hash collision only causes extra waiting; the primary key checks the actual identity.
  perform pg_advisory_xact_lock(hashtextextended(p_provider || ':' || p_environment || ':' || p_event_id,0));
  select * into ord from payments.orders where id=p_order_id for update;
  if not found then raise exception 'order_not_found' using errcode='P0001'; end if;
  if (ord.provider,ord.environment,ord.amount_minor,ord.currency) is distinct from
     (p_provider,p_environment,p_amount_minor,p_currency) then
    raise exception 'payment_order_mismatch' using errcode='P0001';
  end if;
  normalized := jsonb_build_object('order_id',p_order_id,'transaction_id',p_transaction_id,
    'status',p_status,'amount_minor',p_amount_minor,'currency',p_currency,'paid_at',p_paid_at,
    'validation_reference',p_validation_reference,'evidence_sha256',p_evidence_sha256);
  select * into previous from payments.events where provider=p_provider and environment=p_environment and event_id=p_event_id;
  if found then
    if previous.normalized is distinct from normalized then raise exception 'event_replay_conflict' using errcode='P0001'; end if;
    return previous.result || jsonb_build_object('duplicate',true);
  end if;
  if p_status in ('paid','refunded','chargeback') then
    if p_transaction_id is null or length(p_transaction_id) not between 1 and 200 or p_paid_at is null
      or not isfinite(p_paid_at) or p_paid_at < ord.created_at or p_paid_at > ord.expires_at or p_paid_at > clock_timestamp() then
      raise exception 'invalid_payment_time_or_reference' using errcode='P0001';
    end if;
    -- One receipt per order. Reused receipts across orders fail the unique constraint.
    insert into payments.receipts(order_id,provider,environment,transaction_id,paid_at,state)
      values(ord.id,p_provider,p_environment,p_transaction_id,p_paid_at,p_status)
      on conflict (order_id) do nothing;
    select * into receipt from payments.receipts where order_id=ord.id;
    if (receipt.provider,receipt.environment,receipt.transaction_id,receipt.paid_at) is distinct from
       (p_provider,p_environment,p_transaction_id,p_paid_at) then
      raise exception 'receipt_conflict' using errcode='P0001';
    end if;
    if p_status in ('refunded','chargeback') then
      update payments.receipts set state=p_status where order_id=ord.id;
      update payments.orders set state=p_status,updated_at=now() where id=ord.id;
      update public.protection_entitlements set status='revoked',updated_at=now() where payment_order_id=ord.id;
      update payments.sandbox_entitlements set status='revoked' where order_id=ord.id;
      outcome := jsonb_build_object('order_id',ord.id,'action','revoked','environment',p_environment);
    elsif receipt.state in ('refunded','chargeback') then
      -- Reversal tombstone wins even if its delivery preceded the paid notification.
      outcome := jsonb_build_object('order_id',ord.id,'action','reversal_prevents_grant','environment',p_environment);
    else
      grant_end := p_paid_at + ord.duration_seconds * interval '1 second';
      if p_environment='live' then
        insert into public.protection_entitlements(user_id,plan_code,status,starts_at,ends_at,
          provider,provider_reference,payment_order_id)
          values(ord.user_id,ord.plan_code,'active',p_paid_at,grant_end,
            p_provider || ':live',p_transaction_id,ord.id)
          on conflict (payment_order_id) do nothing;
        select id into entitlement_id from public.protection_entitlements where payment_order_id=ord.id;
      else
        insert into payments.sandbox_entitlements(order_id,user_id,plan_code,starts_at,ends_at,status)
          values(ord.id,ord.user_id,ord.plan_code,p_paid_at,grant_end,'active')
          on conflict (order_id) do nothing;
      end if;
      update payments.orders set state='paid',updated_at=now() where id=ord.id;
      outcome := jsonb_build_object('order_id',ord.id,'action','paid','environment',p_environment,'entitlement_id',entitlement_id);
    end if;
  else
    -- Non-success events never grant, extend or undo a paid/reversed order.
    if ord.state in ('pending','failed','cancelled') and p_status <> 'pending' then
      update payments.orders set state=p_status,updated_at=now() where id=ord.id;
    end if;
    outcome := jsonb_build_object('order_id',ord.id,'action','no_grant','environment',p_environment);
  end if;
  insert into payments.events(provider,environment,event_id,order_id,normalized,result)
    values(p_provider,p_environment,p_event_id,ord.id,normalized,outcome);
  return outcome || jsonb_build_object('duplicate',false);
end;
$$;
revoke all on function public.create_payment_order(uuid,text,text,text,text) from public,anon,authenticated;
revoke all on function public.process_validated_payment(uuid,text,text,text,text,text,bigint,text,timestamptz,text,text) from public,anon,authenticated;
grant execute on function public.create_payment_order(uuid,text,text,text,text) to service_role;
grant execute on function public.process_validated_payment(uuid,text,text,text,text,text,bigint,text,timestamptz,text,text) to service_role;
