-- Credential-independent recovery only. No activation, prices, secrets or grants seeded.
alter table payments.orders add column recovery_review boolean not null default false;
create table payments.recoveries (
  provider text not null,
  environment text not null,
  recovery_id text not null check(length(recovery_id) between 1 and 160),
  order_id uuid not null references payments.orders(id) on delete cascade,
  kind text not null check(kind in ('refund','chargeback')),
  state text not null check(state in ('pending','completed','cancelled')),
  amount_minor bigint check(amount_minor > 0 and amount_minor <= 9007199254740991),
  updated_at timestamptz not null default now(),
  primary key(provider,environment,recovery_id)
);
create index payment_recoveries_order on payments.recoveries(order_id);
alter table payments.recoveries enable row level security;
revoke all on payments.recoveries from public,anon,authenticated;
grant select,insert,update,delete on payments.recoveries to service_role;
create policy deny_clients on payments.recoveries as restrictive for all to anon,authenticated using(false) with check(false);

-- Only independently verified, normalized server evidence reaches this boundary.
-- Original purchase amount/time/transaction must match; reversal amount is separate.
create function public.process_validated_recovery(
  p_order_id uuid,p_provider text,p_environment text,p_event_id text,
  p_transaction_id text,p_kind text,p_recovery_id text,p_recovery_state text,
  p_amount_minor bigint,p_currency text,p_paid_at timestamptz,
  p_recovery_amount_minor bigint,p_validation_reference text,p_evidence_sha256 text
) returns jsonb language plpgsql security invoker set search_path='' as $$
declare
  ord payments.orders%rowtype; receipt payments.receipts%rowtype;
  prior payments.recoveries%rowtype; previous payments.events%rowtype;
  normalized jsonb; outcome jsonb; total numeric; action text; needs_review boolean;
begin
  perform 1 from payments.configuration where singleton and mode=p_environment for share;
  if not found then raise exception 'payment_processing_disabled'; end if;
  perform 1 from payments.providers where provider=p_provider and environment=p_environment and enabled for share;
  if not found then raise exception 'provider_disabled'; end if;
  if p_kind is null or p_kind not in ('refund','chargeback') or p_recovery_state is null
    or p_recovery_state not in ('pending','completed','cancelled')
    or p_recovery_id is null or length(p_recovery_id) not between 1 and 160
    or p_event_id is null or length(p_event_id) not between 1 and 200
    or p_validation_reference is null or length(p_validation_reference) not between 1 and 200
    or p_evidence_sha256 is null or p_evidence_sha256 !~ '^[0-9a-f]{64}$'
    or (p_recovery_amount_minor is not null and (p_recovery_amount_minor<=0 or p_recovery_amount_minor>9007199254740991)) then
    raise exception 'invalid_recovery_evidence';
  end if;
  -- Same event lock as payments, then recovery identity, then order row. Never invert.
  perform pg_advisory_xact_lock(hashtextextended(p_provider||':'||p_environment||':'||p_event_id,0));
  perform pg_advisory_xact_lock(20261007,hashtext(p_provider||':'||p_environment||':'||p_recovery_id));
  select * into ord from payments.orders where id=p_order_id for update;
  if not found then raise exception 'order_not_found'; end if;
  if (ord.provider,ord.environment,ord.amount_minor,ord.currency) is distinct from
    (p_provider,p_environment,p_amount_minor,p_currency) or p_recovery_amount_minor>ord.amount_minor then
    raise exception 'payment_order_mismatch';
  end if;
  normalized := jsonb_build_object('order_id',p_order_id,'transaction_id',p_transaction_id,
    'kind',p_kind,'recovery_id',p_recovery_id,'state',p_recovery_state,
    'amount_minor',p_amount_minor,'currency',p_currency,'paid_at',p_paid_at,
    'recovery_amount_minor',p_recovery_amount_minor,'validation_reference',p_validation_reference,'evidence_sha256',p_evidence_sha256);
  select * into previous from payments.events where provider=p_provider and environment=p_environment and event_id=p_event_id;
  if found then
    if previous.normalized is distinct from normalized then raise exception 'event_replay_conflict'; end if;
    return previous.result||jsonb_build_object('duplicate',true);
  end if;
  if p_transaction_id is null or length(p_transaction_id) not between 1 and 200 or p_paid_at is null
    or not isfinite(p_paid_at) or p_paid_at<ord.created_at or p_paid_at>ord.expires_at or p_paid_at>clock_timestamp() then
    raise exception 'invalid_payment_time_or_reference';
  end if;
  -- A recovery preceding the purchase callback creates its economic tombstone, never a grant.
  insert into payments.receipts(order_id,provider,environment,transaction_id,paid_at,state)
    values(ord.id,p_provider,p_environment,p_transaction_id,p_paid_at,'paid') on conflict(order_id) do nothing;
  select * into receipt from payments.receipts where order_id=ord.id;
  if (receipt.provider,receipt.environment,receipt.transaction_id,receipt.paid_at) is distinct from
    (p_provider,p_environment,p_transaction_id,p_paid_at) then raise exception 'receipt_conflict'; end if;
  select * into prior from payments.recoveries where provider=p_provider and environment=p_environment and recovery_id=p_recovery_id;
  if found then
    if prior.order_id<>ord.id or prior.kind<>p_kind or
      (prior.amount_minor is not null and p_recovery_amount_minor is not null and prior.amount_minor<>p_recovery_amount_minor) then
      raise exception 'recovery_identity_conflict';
    end if;
    -- Completed never regresses. Authoritative completion may supersede cancellation.
    update payments.recoveries set state=case when prior.state='completed' or p_recovery_state='completed' then 'completed'
        when prior.state='cancelled' then 'cancelled' else p_recovery_state end,
      amount_minor=coalesce(prior.amount_minor,p_recovery_amount_minor),updated_at=now()
      where provider=p_provider and environment=p_environment and recovery_id=p_recovery_id;
  else
    insert into payments.recoveries(provider,environment,recovery_id,order_id,kind,state,amount_minor)
      values(p_provider,p_environment,p_recovery_id,ord.id,p_kind,p_recovery_state,p_recovery_amount_minor);
  end if;
  select coalesce(sum(amount_minor),0) into total from payments.recoveries
    where order_id=ord.id and kind='refund' and state='completed';
  if total>ord.amount_minor then raise exception 'refund_total_exceeds_payment'; end if;
  -- Partial/unknown reversals are an unresolved policy decision, NOT prorated access.
  select exists(select 1 from payments.recoveries where order_id=ord.id and state<>'cancelled'
    and (state='pending' or amount_minor is null or (kind='chargeback' and amount_minor<ord.amount_minor)))
    or (total>0 and total<ord.amount_minor) into needs_review;
  if receipt.state='chargeback' or exists(select 1 from payments.recoveries where order_id=ord.id
      and kind='chargeback' and state='completed' and amount_minor=ord.amount_minor) then
    action:='chargeback';
  elsif receipt.state='refunded' or total=ord.amount_minor then action:='refunded';
  end if;
  if action is not null then
    update payments.receipts set state=action where order_id=ord.id;
    update payments.orders set state=action,recovery_review=false,updated_at=now() where id=ord.id;
    update public.protection_entitlements set status='revoked',updated_at=now() where payment_order_id=ord.id;
    update payments.sandbox_entitlements set status='revoked' where order_id=ord.id;
    outcome:=jsonb_build_object('order_id',ord.id,'action','revoked','state',action,'environment',p_environment);
  else
    update payments.orders set recovery_review=needs_review,updated_at=now() where id=ord.id;
    outcome:=jsonb_build_object('order_id',ord.id,'action',case when needs_review then 'recovery_requires_review' else 'recovery_no_effect' end,'environment',p_environment);
  end if;
  insert into payments.events(provider,environment,event_id,order_id,normalized,result)
    values(p_provider,p_environment,p_event_id,ord.id,normalized,outcome);
  return outcome||jsonb_build_object('duplicate',false);
end $$;
revoke all on function public.process_validated_recovery(uuid,text,text,text,text,text,text,text,bigint,text,timestamptz,bigint,text,text) from public,anon,authenticated;
grant execute on function public.process_validated_recovery(uuid,text,text,text,text,text,text,text,bigint,text,timestamptz,bigint,text,text) to service_role;

create or replace function public.process_validated_payment(
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
  resumed_event boolean := false;
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
    if previous.result->>'action'='recovery_review_prevents_new_grant' and not ord.recovery_review then
      -- Re-evaluate the SAME independently verified event after its hold clears.
      -- The original paid-at/window stay immutable; no synthetic payment is needed.
      resumed_event:=true;
    else
      return previous.result || jsonb_build_object('duplicate',true);
    end if;
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
      -- Chargeback is terminal and cannot be downgraded by a late refund.
      p_status := case when receipt.state='chargeback' then 'chargeback' else p_status end;
      update payments.receipts set state=p_status where order_id=ord.id;
      update payments.orders set state=p_status,updated_at=now() where id=ord.id;
      update public.protection_entitlements set status='revoked',updated_at=now() where payment_order_id=ord.id;
      update payments.sandbox_entitlements set status='revoked' where order_id=ord.id;
      outcome := jsonb_build_object('order_id',ord.id,'action','revoked','environment',p_environment);
    elsif receipt.state in ('refunded','chargeback') then
      -- Reversal tombstone wins even if its delivery preceded the paid notification.
      outcome := jsonb_build_object('order_id',ord.id,'action','reversal_prevents_grant','environment',p_environment);
    elsif ord.recovery_review and not exists(select 1 from payments.sandbox_entitlements where order_id=ord.id)
      and not exists(select 1 from public.protection_entitlements where payment_order_id=ord.id) then
      -- Preserve existing access for policy-unknown partials; hold a NEW grant for review.
      outcome := jsonb_build_object('order_id',ord.id,'action','recovery_review_prevents_new_grant','environment',p_environment);
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
    values(p_provider,p_environment,p_event_id,ord.id,normalized,outcome)
    on conflict(provider,environment,event_id) do update set result=excluded.result;
  return outcome || jsonb_build_object('duplicate',resumed_event);
end;
$$;

-- Scheduler/worker gate is independent of checkout. Migration always leaves it off.
create table payments.reconciliation_configuration (
  singleton boolean primary key default true check(singleton),
  enabled boolean not null default false,
  environment text not null default 'sandbox' check(environment in ('sandbox','live')),
  endpoint text check(endpoint ~ '^https://[a-z0-9]{20}\.supabase\.co/functions/v1/payment-reconcile$'),
  batch_size integer not null default 12 check(batch_size between 1 and 12)
);
insert into payments.reconciliation_configuration default values;
alter table payments.reconciliation_configuration enable row level security;
revoke all on payments.reconciliation_configuration from public,anon,authenticated;
grant select,update on payments.reconciliation_configuration to service_role;
create policy deny_clients on payments.reconciliation_configuration as restrictive for all to anon,authenticated using(false) with check(false);
alter table payments.orders add column reconciliation_lease uuid;
alter table payments.orders add column reconciliation_lease_until timestamptz;
alter table payments.orders add column reconciliation_due_at timestamptz not null default now();
alter table payments.orders add column reconciliation_failures integer not null default 0;
alter table payments.orders add column reconciliation_result text check(reconciliation_result in ('processed','review','retry'));
create index payment_reconciliation_due on payments.orders(environment,reconciliation_due_at,id)
  where state not in ('refunded','chargeback');

create or replace function public.payment_reconciliation_batch(p_environment text)
returns setof jsonb language plpgsql security invoker set search_path='' as $$
declare o payments.orders%rowtype; batch integer;
begin
  perform 1 from payments.configuration where mode=p_environment for share;
  if not found then raise exception 'payment_processing_disabled'; end if;
  select batch_size into batch from payments.reconciliation_configuration where enabled and environment=p_environment for share;
  if not found then raise exception 'reconciliation_disabled'; end if;
  perform 1 from payments.providers where provider='sslcommerz' and environment=p_environment and enabled for share;
  if not found then raise exception 'provider_disabled'; end if;
  for o in select * from payments.orders where provider='sslcommerz' and environment=p_environment
    and state not in ('refunded','chargeback') and reconciliation_due_at<=clock_timestamp()
    and (reconciliation_lease_until is null or reconciliation_lease_until<=clock_timestamp())
    order by reconciliation_due_at,id limit batch for update skip locked
  loop
    -- A lease lasts beyond the bounded worker deadline; crashed claims become eligible again.
    update payments.orders set reconciliation_lease=gen_random_uuid(),
      reconciliation_lease_until=clock_timestamp()+interval '120 seconds'
      where id=o.id returning * into o;
    return next to_jsonb(o)||jsonb_build_object('receipt_transaction_id',(select transaction_id from payments.receipts where order_id=o.id),
      'receipt_paid_at',(select paid_at from payments.receipts where order_id=o.id),
      'recoveries',coalesce((select jsonb_agg(jsonb_build_object('recovery_id',recovery_id,'kind',kind,'state',state,'amount_minor',amount_minor))
        from payments.recoveries where order_id=o.id and (state='pending' or (state='completed' and amount_minor is null))),'[]'::jsonb));
  end loop;
end $$;

create function public.payment_reconciliation_finish(p_order_id uuid,p_lease uuid,p_result text)
returns boolean language plpgsql security invoker set search_path='' as $$
declare o payments.orders%rowtype; delay_seconds integer;
begin
  if p_result is null or p_result not in ('processed','review','retry') then raise exception 'invalid_reconciliation_result'; end if;
  select * into o from payments.orders where id=p_order_id for update;
  if not found or o.reconciliation_lease is distinct from p_lease or p_lease is null
    or o.reconciliation_lease_until<=clock_timestamp() then return false; end if;
  -- Bounded exponential retry (5m..6h); review/paid sweeps remain visible at 24h.
  delay_seconds:=case when p_result='retry' then least(21600,300*power(2,least(o.reconciliation_failures,7)))::integer
    when p_result='review' or o.state='paid' then 86400 else 300 end;
  update payments.orders set reconciled_at=case when p_result='processed' then clock_timestamp() else reconciled_at end,
    reconciliation_result=p_result,reconciliation_failures=case when p_result='retry' then least(reconciliation_failures+1,8) else 0 end,
    reconciliation_due_at=clock_timestamp()+delay_seconds*interval '1 second',
    reconciliation_lease=null,reconciliation_lease_until=null where id=o.id;
  return true;
end $$;
revoke all on function public.payment_reconciliation_finish(uuid,uuid,text) from public,anon,authenticated;
grant execute on function public.payment_reconciliation_finish(uuid,uuid,text) to service_role;

-- Optional pg_net/Vault entry point. No extensions enabled, cron job or secret created.
-- Only the database owner can enqueue. Fixed Vault names; no secret in cron text.
create function payments.enqueue_reconciliation()
returns bigint language plpgsql security invoker set search_path='' as $$
declare cfg payments.reconciliation_configuration%rowtype; secret text; request_id bigint;
begin
  select * into cfg from payments.reconciliation_configuration where enabled;
  if not found then return null; end if;
  if cfg.endpoint is null then raise exception 'reconciliation_endpoint_missing'; end if;
  if not exists(select 1 from payments.configuration where mode=cfg.environment)
    or not exists(select 1 from payments.providers where provider='sslcommerz' and environment=cfg.environment and enabled) then
    return null;
  end if;
  if to_regclass('vault.decrypted_secrets') is null or to_regnamespace('net') is null then
    raise exception 'reconciliation_extensions_missing';
  end if;
  execute 'select decrypted_secret from vault.decrypted_secrets where name=$1'
    into secret using 'safenest_payment_reconcile_key';
  if secret is null or length(secret)<32 then raise exception 'reconciliation_secret_missing'; end if;
  -- Empty request body matches the handler. NOT an RPC or arbitrary URL dispatcher.
  execute 'select net.http_post(url:=$1,headers:=$2,body:=''{}''::jsonb,timeout_milliseconds:=60000)'
    into request_id using cfg.endpoint,jsonb_build_object('x-reconcile-key',secret);
  return request_id;
end $$;
revoke all on function payments.enqueue_reconciliation() from public,anon,authenticated,service_role;
