-- Preserve old issued periods/orders; add trials and a new quarterly catalogue.
alter table public.protection_entitlements drop constraint protection_entitlements_plan_code_check;
alter table public.protection_entitlements add constraint protection_entitlements_plan_code_check
  check (plan_code in ('weekly','monthly','quarterly','annual','trial'));
alter table payments.products drop constraint products_plan_code_check;
alter table payments.products add constraint products_plan_code_check check (plan_code in ('weekly','monthly','quarterly','annual'));
alter table payments.orders drop constraint orders_plan_code_check;
alter table payments.orders add constraint orders_plan_code_check check (plan_code in ('weekly','monthly','quarterly','annual'));

-- New offers remain unavailable until merchant setup and payment QA are complete.
insert into payments.products(product_code,environment,plan_code,amount_minor,currency,duration_seconds,enabled)
select p.code,e.environment,p.plan,p.amount,'BDT',p.seconds,false
from (values ('monthly_v2','monthly',37900,2592000),
  ('quarterly_v1','quarterly',99900,7776000),('annual_v2','annual',379900,31536000)) p(code,plan,amount,seconds)
cross join (values ('sandbox'),('live')) e(environment);

create table payments.trial_claims (
  user_id uuid primary key references auth.users(id) on delete cascade,
  entitlement_id uuid references public.protection_entitlements(id) on delete set null,
  selected_plan_code text not null check (selected_plan_code in ('monthly','quarterly','annual')),
  claimed_at timestamptz not null default now()
);
alter table payments.trial_claims enable row level security;
revoke all on payments.trial_claims from public,anon,authenticated;
grant select,insert,update,delete on payments.trial_claims to service_role;
create policy deny_clients on payments.trial_claims as restrictive for all to anon,authenticated using (false) with check (false);

-- Service-only RPC: the Edge Function passes the verified, confirmed Auth identity.
-- Row lock + primary key make retries/concurrent starts return one unchanged window.
-- Kept in the unexposed schema because the confirmed-account row lock crosses
-- Auth permissions. The public entry point is INVOKER and service-only.
create function payments.start_protection_trial(p_user_id uuid,p_plan_code text) returns jsonb
language plpgsql security definer set search_path='' as $$
declare claim payments.trial_claims%rowtype; period public.protection_entitlements%rowtype; stamp timestamptz;
begin
  if p_plan_code is null or p_plan_code not in ('monthly','quarterly','annual') then raise exception 'invalid_plan'; end if;
  perform 1 from auth.users where id=p_user_id and email_confirmed_at is not null and not coalesce(is_anonymous,false) for update;
  if not found then raise exception 'confirmed_account_required'; end if;
  select * into claim from payments.trial_claims where user_id=p_user_id;
  if found then
    select * into period from public.protection_entitlements where id=claim.entitlement_id;
    return jsonb_build_object('entitlement',case when period.id is null then null else to_jsonb(period) end,
      'selected_plan_code',claim.selected_plan_code,'already_claimed',true,'payment_setup_available',false,'automatic_charging',false);
  end if;
  stamp:=clock_timestamp();
  if exists(select 1 from public.protection_entitlements where user_id=p_user_id and status='active'
    and starts_at<=stamp and ends_at>stamp) then raise exception 'active_access_exists'; end if;
  insert into public.protection_entitlements(user_id,plan_code,status,starts_at,ends_at,provider,provider_reference)
    values(p_user_id,'trial','active',stamp,stamp+interval '72 hours','safenest-trial',p_user_id::text) returning * into period;
  insert into payments.trial_claims(user_id,entitlement_id,selected_plan_code) values(p_user_id,period.id,p_plan_code);
  return jsonb_build_object('entitlement',to_jsonb(period),'selected_plan_code',p_plan_code,'already_claimed',false,
    'payment_setup_available',false,'automatic_charging',false);
end;
$$;
revoke all on function payments.start_protection_trial(uuid,text) from public,anon,authenticated;
grant execute on function payments.start_protection_trial(uuid,text) to service_role;
create function public.start_protection_trial(p_user_id uuid,p_plan_code text) returns jsonb
language sql security invoker set search_path='' as $$
  select payments.start_protection_trial(p_user_id,p_plan_code);
$$;
revoke all on function public.start_protection_trial(uuid,text) from public,anon,authenticated;
grant execute on function public.start_protection_trial(uuid,text) to service_role;
comment on table payments.trial_claims is 'One 72-hour trial per confirmed account. No payment mandate or automatic debit is created. Account deletion cascades the claim; this is not a one-person anti-abuse guarantee.';
