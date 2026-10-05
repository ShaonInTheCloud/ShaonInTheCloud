-- Additive: preserves profiles and all existing authentication policies.
create table public.gambling_brands (
 id uuid primary key default gen_random_uuid(), name text not null check(length(name) between 1 and 100), slug text unique not null check(slug ~ '^[a-z0-9-]{1,100}$'), category text not null default 'gambling' check(category='gambling'), country_focus text[] not null default '{BD}', risk_level text not null default 'high', active boolean not null default true, created_at timestamptz not null default now(), updated_at timestamptz not null default now());
create table public.gambling_domains (
 id uuid primary key default gen_random_uuid(), brand_id uuid references public.gambling_brands(id), domain text unique not null check(length(domain)<=253 and domain=lower(domain) and domain ~ '^[a-z0-9-]+(\.[a-z0-9-]+)+$'), registrable_domain text not null, domain_type text not null default 'candidate' check(domain_type in ('primary','mirror','candidate','subdomain')), confidence text not null default 'REVIEW_REQUIRED' check(confidence in ('CONFIRMED','HIGH','SUSPECTED','MIRROR','DEAD','FALSE_POSITIVE','REVIEW_REQUIRED')), active boolean not null default false, first_seen timestamptz not null default now(), last_seen timestamptz not null default now(), last_verified timestamptz, notes text not null default '' check(length(notes)<=4000), created_at timestamptz not null default now(), updated_at timestamptz not null default now(), check(not active or (confidence in ('CONFIRMED','HIGH','MIRROR') and last_verified is not null)));
create index gambling_domains_brand_idx on public.gambling_domains(brand_id);
create index gambling_domains_review_idx on public.gambling_domains(confidence,updated_at desc,id);
create index gambling_domains_active_idx on public.gambling_domains(domain) where active;
create index gambling_domains_registrable_idx on public.gambling_domains(registrable_domain);
create table public.domain_sources (
 id uuid primary key default gen_random_uuid(),domain_id uuid not null references public.gambling_domains(id) on delete cascade,source_name text not null,source_url text not null check(source_url ~ '^https://[^[:space:]]+$'),source_type text not null check(source_type in ('regulator','government','reporting','operator','discovery','user')),verified boolean not null default false,discovered_at timestamptz not null default now(),last_checked timestamptz not null default now(),evidence_summary text not null check(length(evidence_summary) between 20 and 2000),unique(domain_id,source_url));
create table public.brand_aliases(id uuid primary key default gen_random_uuid(),brand_id uuid not null references public.gambling_brands(id) on delete cascade,alias text not null,normalized_alias text not null,unique(brand_id,normalized_alias));
create index brand_alias_lookup on public.brand_aliases(normalized_alias);
create table public.domain_observations(id bigint generated always as identity primary key,domain_id uuid not null references public.gambling_domains(id) on delete cascade,hostname text not null,dns_status text not null,resolved_ips inet[] not null default '{}',redirect_target text,http_status integer,classification jsonb not null default '{}',observed_at timestamptz not null default now());
create index domain_observations_domain_time on public.domain_observations(domain_id,observed_at desc);
create index domain_observations_time on public.domain_observations using brin(observed_at);
create table public.blocklist_versions(id uuid primary key default gen_random_uuid(),version bigint unique not null check(version>0),generated_at timestamptz not null,expires_at timestamptz not null,domain_count integer not null check(domain_count between 1 and 100000),checksum text not null check(checksum ~ '^[0-9a-f]{64}$'),domains jsonb not null check(jsonb_typeof(domains)='array'),envelope text not null check(length(envelope)<8389632),public_key text not null,check(expires_at>generated_at));
create table public.intelligence_audit(id bigint generated always as identity primary key,actor uuid,table_name text not null,record_id text not null,action text not null,created_at timestamptz not null default now());
create index intelligence_audit_time on public.intelligence_audit(created_at desc);
create function public.intelligence_touch() returns trigger language plpgsql set search_path='' as $$begin new.updated_at=now();return new;end$$;
revoke all on function public.intelligence_touch() from public,anon,authenticated;
create trigger touch_brands before update on public.gambling_brands for each row execute function public.intelligence_touch();
create trigger touch_domains before update on public.gambling_domains for each row execute function public.intelligence_touch();
-- Invoker: admins have audit insert permission; ordinary users never have data mutation permission.
create function public.intelligence_audit_change() returns trigger language plpgsql set search_path='' as $$begin insert into public.intelligence_audit(actor,table_name,record_id,action) values(auth.uid(),TG_TABLE_NAME,coalesce(new.id,old.id)::text,TG_OP);return coalesce(new,old);end$$;
revoke all on function public.intelligence_audit_change() from public,anon,authenticated;
do $$declare t text;begin foreach t in array array['gambling_brands','gambling_domains','domain_sources','brand_aliases','domain_observations','blocklist_versions','intelligence_audit'] loop
 execute format('alter table public.%I enable row level security',t);
 execute format('revoke all on public.%I from anon,authenticated',t);
 execute format('grant all on public.%I to service_role',t);
 execute format('grant select on public.%I to authenticated',t);
 execute format('create policy admin_read on public.%I for select to authenticated using ((select auth.jwt()->''app_metadata''->>''safenest_admin'') = ''true'')',t);
 if t not in ('blocklist_versions','intelligence_audit') then
 execute format('grant insert,update,delete on public.%I to authenticated',t);
 execute format('create policy admin_write on public.%I for all to authenticated using ((select auth.jwt()->''app_metadata''->>''safenest_admin'') = ''true'') with check ((select auth.jwt()->''app_metadata''->>''safenest_admin'') = ''true'')',t);
 end if;
 if t in ('gambling_brands','gambling_domains','domain_sources','brand_aliases') then execute format('create trigger audit_change after insert or update or delete on public.%I for each row execute function public.intelligence_audit_change()',t);end if;
 end loop;end$$;
grant insert on public.intelligence_audit to authenticated;
create policy admin_audit_insert on public.intelligence_audit for insert to authenticated with check((select auth.jwt()->'app_metadata'->>'safenest_admin')='true' and actor=auth.uid());
grant usage,select on sequence public.domain_observations_id_seq,public.intelligence_audit_id_seq to authenticated,service_role;
-- Public release retrieval is narrowly exposed through the Edge API only; evidence stays private.
