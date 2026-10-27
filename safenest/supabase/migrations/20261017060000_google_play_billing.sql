-- Google Play Billing subscriptions for the Play-distributed Android app.
-- The server verifies every purchase token with the Google Play Developer API
-- (purchases.subscriptionsv2) before it writes here. The client never writes.
create table payments.play_subscriptions (
  purchase_token text primary key check (length(purchase_token) between 20 and 4096),
  user_id uuid not null references auth.users(id) on delete cascade,
  product_id text not null check (length(product_id) between 1 and 200),
  base_plan_id text not null check (length(base_plan_id) between 1 and 200),
  plan_code text not null check (plan_code in ('weekly','monthly','quarterly','annual')),
  order_id text check (order_id is null or length(order_id) between 1 and 200),
  state text not null check (state in ('ACTIVE','IN_GRACE_PERIOD','CANCELED','ON_HOLD','PAUSED','EXPIRED','PENDING','REVOKED','UNKNOWN')),
  starts_at timestamptz not null,
  expires_at timestamptz not null,
  linked_purchase_token text,
  evidence_sha256 text not null check (evidence_sha256 ~ '^[0-9a-f]{64}$'),
  verified_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  constraint play_subscription_window check (expires_at > starts_at)
);
create index play_subscriptions_user_idx on payments.play_subscriptions (user_id, expires_at desc);
alter table payments.play_subscriptions enable row level security;
comment on table payments.play_subscriptions is
  'Server-verified Google Play subscription state keyed by purchase token. Bound to one SafeNest account for the token''s lifetime.';

-- Idempotent: the verify endpoint and the real-time developer notification
-- (RTDN) handler both call this with freshly fetched Google state.
-- p_user_id null means "the account this token is already bound to" (RTDN path).
create function public.process_play_subscription(
  p_user_id uuid, p_purchase_token text, p_product_id text, p_base_plan_id text,
  p_plan_code text, p_state text, p_starts_at timestamptz, p_expires_at timestamptz,
  p_order_id text, p_linked_purchase_token text, p_evidence_sha256 text
) returns jsonb language plpgsql security invoker set search_path = '' as $$
declare
  existing payments.play_subscriptions%rowtype;
  owner uuid;
  entitled boolean;
  entitlement_status text;
  entitlement_id uuid;
begin
  if p_purchase_token is null or length(p_purchase_token) not between 20 and 4096
    or p_plan_code not in ('weekly','monthly','quarterly','annual')
    or p_state not in ('ACTIVE','IN_GRACE_PERIOD','CANCELED','ON_HOLD','PAUSED','EXPIRED','PENDING','REVOKED','UNKNOWN')
    or p_starts_at is null or p_expires_at is null or not isfinite(p_starts_at) or not isfinite(p_expires_at)
    or p_expires_at <= p_starts_at
    or p_evidence_sha256 is null or p_evidence_sha256 !~ '^[0-9a-f]{64}$' then
    raise exception 'invalid_play_evidence' using errcode='P0001';
  end if;
  perform pg_advisory_xact_lock(hashtextextended('google_play:' || p_purchase_token, 0));
  select * into existing from payments.play_subscriptions where purchase_token=p_purchase_token for update;
  if found then
    -- A purchase token belongs to exactly one SafeNest account forever.
    if p_user_id is not null and existing.user_id <> p_user_id then
      raise exception 'purchase_bound_to_other_account' using errcode='P0001';
    end if;
    owner := existing.user_id;
    update payments.play_subscriptions set product_id=p_product_id, base_plan_id=p_base_plan_id,
      plan_code=p_plan_code, order_id=coalesce(p_order_id, order_id), state=p_state,
      starts_at=p_starts_at, expires_at=p_expires_at, linked_purchase_token=p_linked_purchase_token,
      evidence_sha256=p_evidence_sha256, verified_at=now()
      where purchase_token=p_purchase_token;
  else
    if p_user_id is null then raise exception 'purchase_not_bound' using errcode='P0001'; end if;
    owner := p_user_id;
    insert into payments.play_subscriptions(purchase_token,user_id,product_id,base_plan_id,plan_code,
      order_id,state,starts_at,expires_at,linked_purchase_token,evidence_sha256)
      values(p_purchase_token,owner,p_product_id,p_base_plan_id,p_plan_code,p_order_id,p_state,
        p_starts_at,p_expires_at,p_linked_purchase_token,p_evidence_sha256);
  end if;
  -- Google keeps a cancelled subscription usable until expiry. Revoked/refunded ends now.
  entitled := p_state in ('ACTIVE','IN_GRACE_PERIOD','CANCELED') and p_expires_at > clock_timestamp();
  entitlement_status := case when p_state = 'REVOKED' then 'revoked'
    when entitled then 'active' else 'expired' end;
  insert into public.protection_entitlements(user_id,plan_code,status,starts_at,ends_at,provider,provider_reference)
    values(owner,p_plan_code,entitlement_status,p_starts_at,p_expires_at,'google_play',p_purchase_token)
    on conflict (provider,provider_reference) do update set plan_code=excluded.plan_code,
      status=excluded.status, starts_at=excluded.starts_at, ends_at=excluded.ends_at, updated_at=now();
  select id into entitlement_id from public.protection_entitlements
    where provider='google_play' and provider_reference=p_purchase_token;
  return jsonb_build_object('user_id',owner,'entitlement_id',entitlement_id,'status',entitlement_status,
    'plan_code',p_plan_code,'starts_at',p_starts_at,'ends_at',p_expires_at,'state',p_state);
end;
$$;
revoke all on function public.process_play_subscription(uuid,text,text,text,text,text,timestamptz,timestamptz,text,text,text) from public,anon,authenticated;
grant execute on function public.process_play_subscription(uuid,text,text,text,text,text,timestamptz,timestamptz,text,text,text) to service_role;
grant select, insert, update on payments.play_subscriptions to service_role;
grant usage on schema payments to service_role;
