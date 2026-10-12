-- Operator-only controls: finite reviewer/test access and refund/chargeback revocation.
-- Callable only by service_role (Supabase SQL editor or a trusted server), never by customers or anon.
-- Every call is recorded in an append-only audit table.
create table payments.operator_actions (
  id uuid primary key default gen_random_uuid(),
  action text not null check (action in ('grant_reviewer_access','revoke_entitlement','refund_order')),
  user_id uuid,
  order_id uuid,
  entitlement_id uuid,
  reason text not null check (length(btrim(reason)) between 5 and 500),
  performed_by text not null default current_user,
  created_at timestamptz not null default now()
);
create index operator_actions_created on payments.operator_actions(created_at desc);
alter table payments.operator_actions enable row level security;
revoke all on payments.operator_actions from public, anon, authenticated;
grant select, insert on payments.operator_actions to service_role;
create policy deny_clients on payments.operator_actions as restrictive for all
  to anon, authenticated using (false) with check (false);

create function payments.forbid_audit_change() returns trigger language plpgsql
set search_path = '' as $$ begin raise exception 'operator_actions_append_only'; end; $$;
create trigger operator_actions_append_only before update or delete on payments.operator_actions
  for each row execute function payments.forbid_audit_change();

-- Finite access for Play reviewers or internal testing. No payment is created or implied.
create function public.operator_grant_reviewer_access(p_user_id uuid, p_days integer, p_reason text)
returns jsonb language plpgsql security invoker set search_path = '' as $$
declare e public.protection_entitlements%rowtype;
begin
  if p_days is null or p_days not between 1 and 90 then raise exception 'invalid_duration' using errcode = 'P0001'; end if;
  if p_reason is null or length(btrim(p_reason)) < 5 then raise exception 'reason_required' using errcode = 'P0001'; end if;
  insert into public.protection_entitlements(user_id, plan_code, status, starts_at, ends_at, provider, provider_reference)
    values (p_user_id, 'trial', 'active', clock_timestamp(), clock_timestamp() + p_days * interval '1 day',
            'operator', 'reviewer:' || gen_random_uuid()::text)
    returning * into e;
  insert into payments.operator_actions(action, user_id, entitlement_id, reason)
    values ('grant_reviewer_access', p_user_id, e.id, p_reason);
  return jsonb_build_object('entitlement_id', e.id, 'ends_at', e.ends_at);
end;
$$;

-- Immediately ends one entitlement (support error, abuse, expired reviewer account).
create function public.operator_revoke_entitlement(p_entitlement_id uuid, p_reason text)
returns jsonb language plpgsql security invoker set search_path = '' as $$
declare e public.protection_entitlements%rowtype;
begin
  if p_reason is null or length(btrim(p_reason)) < 5 then raise exception 'reason_required' using errcode = 'P0001'; end if;
  update public.protection_entitlements set status = 'revoked', updated_at = now()
    where id = p_entitlement_id and status <> 'revoked' returning * into e;
  if not found then raise exception 'entitlement_not_revocable' using errcode = 'P0001'; end if;
  insert into payments.operator_actions(action, user_id, order_id, entitlement_id, reason)
    values ('revoke_entitlement', e.user_id, e.payment_order_id, e.id, p_reason);
  return jsonb_build_object('entitlement_id', e.id, 'status', 'revoked');
end;
$$;

-- Record a refund that was paid out at the gateway: marks the order and receipt refunded and
-- revokes the entitlement it issued. The gateway adapter only reports successful payments, so
-- refunds are applied here. A later refund notification for the same order cannot grant access again.
create function public.operator_refund_order(p_order_id uuid, p_reason text)
returns jsonb language plpgsql security invoker set search_path = '' as $$
declare o payments.orders%rowtype; n integer;
begin
  if p_reason is null or length(btrim(p_reason)) < 5 then raise exception 'reason_required' using errcode = 'P0001'; end if;
  select * into o from payments.orders where id = p_order_id for update;
  if not found then raise exception 'order_not_found' using errcode = 'P0001'; end if;
  if o.state <> 'paid' then raise exception 'order_not_refundable' using errcode = 'P0001'; end if;
  update payments.orders set state = 'refunded', updated_at = now() where id = o.id;
  update payments.receipts set state = 'refunded' where order_id = o.id;
  update public.protection_entitlements set status = 'revoked', updated_at = now()
    where payment_order_id = o.id and status <> 'revoked';
  get diagnostics n = row_count;
  update payments.sandbox_entitlements set status = 'revoked' where order_id = o.id;
  insert into payments.operator_actions(action, user_id, order_id, reason)
    values ('refund_order', o.user_id, o.id, p_reason);
  return jsonb_build_object('order_id', o.id, 'state', 'refunded', 'entitlements_revoked', n);
end;
$$;

revoke all on function public.operator_grant_reviewer_access(uuid, integer, text),
  public.operator_revoke_entitlement(uuid, text), public.operator_refund_order(uuid, text)
  from public, anon, authenticated;
grant execute on function public.operator_grant_reviewer_access(uuid, integer, text),
  public.operator_revoke_entitlement(uuid, text), public.operator_refund_order(uuid, text) to service_role;
revoke all on function payments.forbid_audit_change() from public, anon, authenticated;
