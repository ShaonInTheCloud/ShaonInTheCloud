create schema supportdesk;
revoke all on schema supportdesk from public,anon;
grant usage on schema supportdesk to authenticated,service_role;

-- Configure operators separately; do not commit the owner's private inbox.
create table supportdesk.operators (email text primary key check(email=lower(email)));
alter table supportdesk.operators enable row level security;
revoke all on supportdesk.operators from public,anon,authenticated;
grant all on supportdesk.operators to service_role;
create policy deny_clients on supportdesk.operators as restrictive for all to anon,authenticated using(false) with check(false);

create function supportdesk.actor() returns uuid
language sql stable security definer set search_path='' as $$
 select u.id from auth.users u join auth.sessions s on s.user_id=u.id
 where u.id=auth.uid() and u.email_confirmed_at is not null and not coalesce(u.is_anonymous,false)
 and s.id=(auth.jwt()->>'session_id')::uuid and (s.not_after is null or s.not_after>now())
 limit 1;
$$;
create function supportdesk.is_operator() returns boolean
language sql stable security definer set search_path='' as $$
 select exists(select 1 from auth.users u join supportdesk.operators o on o.email=lower(u.email)
 where u.id=supportdesk.actor());
$$;

create table public.support_tickets (
 id uuid primary key,
 user_id uuid not null references auth.users(id) on delete cascade,
 category text not null check(category in ('app','account','billing','privacy','business')),
 subject text not null check(char_length(btrim(subject)) between 3 and 120),
 status text not null default 'open' check(status in ('open','closed')),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now()
);
create index support_tickets_owner_created on public.support_tickets(user_id,created_at desc);
create index support_tickets_updated on public.support_tickets(updated_at desc);
create table public.support_messages (
 id uuid primary key,
 ticket_id uuid not null references public.support_tickets(id) on delete cascade,
 author_id uuid not null references auth.users(id) on delete cascade,
 is_support boolean not null,
 body text not null check(char_length(btrim(body)) between 10 and 4000),
 created_at timestamptz not null default now()
);
create index support_messages_ticket_created on public.support_messages(ticket_id,created_at);
create index support_messages_author_created on public.support_messages(author_id,created_at desc);
alter table public.support_tickets enable row level security;
alter table public.support_messages enable row level security;
revoke all on public.support_tickets,public.support_messages from public,anon,authenticated;
grant select on public.support_tickets,public.support_messages to authenticated;
grant all on public.support_tickets,public.support_messages to service_role;
create policy support_ticket_read on public.support_tickets for select to authenticated
 using(user_id=(select supportdesk.actor()) or (select supportdesk.is_operator()));
create policy support_message_read on public.support_messages for select to authenticated
 using(exists(select 1 from public.support_tickets t where t.id=ticket_id
  and (t.user_id=(select supportdesk.actor()) or (select supportdesk.is_operator()))));

-- Internal privileged writer: derives identity, ownership and staff status itself.
-- User row locking serializes per-account rate limits; IDs make retry idempotent.
create function supportdesk.write_message(p_id uuid,p_ticket uuid,p_body text,p_category text default null,p_subject text default null) returns uuid
language plpgsql security definer set search_path='' as $$
declare actor_id uuid:=supportdesk.actor(); staff boolean:=supportdesk.is_operator(); ticket public.support_tickets%rowtype;
begin
 if actor_id is null then raise exception 'confirmed_session_required'; end if;
 if p_id is null or p_ticket is null or p_body is null or char_length(btrim(p_body)) not between 10 and 4000 then raise exception 'invalid_message'; end if;
 perform 1 from auth.users where id=actor_id for update;
 select * into ticket from public.support_tickets where id=p_ticket for update;
 if found then
  if ticket.user_id<>actor_id and not staff then raise exception 'ticket_unavailable'; end if;
  if exists(select 1 from public.support_messages where id=p_id and ticket_id=p_ticket and author_id=actor_id) then return p_ticket; end if;
  if ticket.status<>'open' then raise exception 'ticket_closed'; end if;
 else
  if p_category is null or p_category not in ('app','account','billing','privacy','business') or p_subject is null
   or char_length(btrim(p_subject)) not between 3 and 120 then raise exception 'invalid_ticket'; end if;
  if (select count(*) from public.support_tickets where user_id=actor_id and created_at>now()-interval '24 hours')>=5 then raise exception 'request_limit'; end if;
  insert into public.support_tickets(id,user_id,category,subject) values(p_ticket,actor_id,p_category,btrim(p_subject));
 end if;
 if (select count(*) from public.support_messages where author_id=actor_id and created_at>now()-interval '24 hours')>=40 then raise exception 'request_limit'; end if;
 insert into public.support_messages(id,ticket_id,author_id,is_support,body) values(p_id,p_ticket,actor_id,staff,btrim(p_body));
 update public.support_tickets set updated_at=now() where id=p_ticket;
 return p_ticket;
end;
$$;
create function supportdesk.set_status(p_ticket uuid,p_status text) returns void
language plpgsql security definer set search_path='' as $$
begin
 if not supportdesk.is_operator() then raise exception 'operator_required'; end if;
 if p_status is null or p_status not in ('open','closed') then raise exception 'invalid_status'; end if;
 update public.support_tickets set status=p_status,updated_at=now() where id=p_ticket;
 if not found then raise exception 'ticket_unavailable'; end if;
end;
$$;
revoke all on all functions in schema supportdesk from public,anon,authenticated;
grant execute on function supportdesk.actor(),supportdesk.is_operator(),supportdesk.write_message(uuid,uuid,text,text,text),supportdesk.set_status(uuid,text) to authenticated;

create function public.support_is_operator() returns boolean language sql security invoker set search_path='' as $$select supportdesk.is_operator();$$;
create function public.support_send(p_id uuid,p_ticket uuid,p_body text,p_category text default null,p_subject text default null) returns uuid
 language sql security invoker set search_path='' as $$select supportdesk.write_message(p_id,p_ticket,p_body,p_category,p_subject);$$;
create function public.support_set_status(p_ticket uuid,p_status text) returns void
 language sql security invoker set search_path='' as $$select supportdesk.set_status(p_ticket,p_status);$$;
revoke all on function public.support_is_operator(),public.support_send(uuid,uuid,text,text,text),public.support_set_status(uuid,text) from public,anon,authenticated;
grant execute on function public.support_is_operator(),public.support_send(uuid,uuid,text,text,text),public.support_set_status(uuid,text) to authenticated;
comment on table public.support_tickets is 'Private account support. Replies appear in My SafeNest; no outbound email notifications. Account deletion cascades tickets and messages. Backup/log retention remains to be finalized.';
