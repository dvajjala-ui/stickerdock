begin;

-- Additive, owner-only admin data. Existing Factory outputs remain untouched.
create table public.admin_packs (
    id uuid primary key,
    name text not null check (char_length(name) between 1 and 60),
    creator text not null check (char_length(creator) between 1 and 60),
    rating text not null check (rating in ('GENERAL','MATURE')),
    status text not null default 'draft' check (status in ('draft','reviewed','published','rejected','archived')),
    private_path text not null,
    sha256 text not null check (sha256 ~ '^[0-9a-f]{64}$'),
    sticker_count integer not null check (sticker_count between 1 and 100),
    "references" jsonb not null default '[]'::jsonb,
    origin_id uuid,
    version integer not null default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create table public.factory_settings (
    id text primary key check (id = 'default'),
    enabled boolean not null default true,
    daily_target integer not null default 40 check (daily_target between 3 and 100),
    batch_size integer not null default 5 check (batch_size between 1 and 10),
    topics jsonb not null default '[]'::jsonb,
    discovery_enabled boolean not null default true,
    auto_migrate_completed boolean not null default true,
    rating text not null default 'GENERAL' check (rating in ('GENERAL','MATURE')),
    content_categories jsonb not null default '["TRENDING","CREATIVE","MOVIES","SERIES","INDIA","TECHNOLOGY","EVERYDAY"]'::jsonb,
    updated_at timestamptz not null default now()
);
create table public.content_briefs (
    day date primary key,
    status text not null check (status in ('ready','failed')),
    source_mode text not null,
    model text,
    rating text not null check (rating in ('GENERAL','MATURE')),
    settings_hash text not null check (settings_hash ~ '^[0-9a-f]{64}$'),
    categories jsonb not null default '[]'::jsonb,
    custom_topics jsonb not null default '[]'::jsonb,
    items jsonb not null default '[]'::jsonb,
    item_count integer not null default 0 check (item_count between 0 and 24),
    topic_plans jsonb not null default '[]'::jsonb,
    "references" jsonb not null default '[]'::jsonb,
    warnings jsonb not null default '[]'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create table public.admin_events (
    id uuid primary key default gen_random_uuid(),
    action text not null,
    entity_id text,
    details jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);
create table public.catalog_leases (
    id text primary key check (id = 'catalog'),
    owner uuid not null,
    expires_at timestamptz not null
);
alter table public.admin_packs enable row level security;
alter table public.factory_settings enable row level security;
alter table public.content_briefs enable row level security;
alter table public.admin_events enable row level security;
alter table public.catalog_leases enable row level security;
revoke all on public.admin_packs, public.factory_settings, public.content_briefs, public.admin_events, public.catalog_leases from anon, authenticated;
grant all on public.admin_packs, public.factory_settings, public.content_briefs, public.catalog_leases to service_role;
grant select, insert on public.admin_events to service_role;
insert into public.factory_settings (id) values ('default') on conflict do nothing;
create trigger admin_packs_updated_at before update on public.admin_packs
for each row execute function public.set_updated_at();
create trigger factory_settings_updated_at before update on public.factory_settings
for each row execute function public.set_updated_at();
create trigger content_briefs_updated_at before update on public.content_briefs
for each row execute function public.set_updated_at();

create function public.acquire_catalog_lease(p_owner uuid)
returns table (acquired boolean) language plpgsql security invoker set search_path = '' as $$
begin
    insert into public.catalog_leases (id, owner, expires_at)
    values ('catalog', p_owner, now() + interval '10 minutes')
    on conflict (id) do update set owner = excluded.owner, expires_at = excluded.expires_at
    where public.catalog_leases.expires_at < now();
    return query select exists(select 1 from public.catalog_leases where id = 'catalog' and owner = p_owner);
end;
$$;
create function public.release_catalog_lease(p_owner uuid)
returns table (released boolean) language plpgsql security invoker set search_path = '' as $$
begin
    delete from public.catalog_leases where id = 'catalog' and owner = p_owner;
    return query select true;
end;
$$;
revoke all on function public.acquire_catalog_lease(uuid), public.release_catalog_lease(uuid) from public, anon, authenticated;
grant execute on function public.acquire_catalog_lease(uuid), public.release_catalog_lease(uuid) to service_role;

commit;

-- Verification: five rows should return, all with rowsecurity = true.
select tablename, rowsecurity
from pg_tables
where schemaname = 'public'
  and tablename in ('admin_packs','factory_settings','content_briefs','admin_events','catalog_leases')
order by tablename;

-- Verification: two rows should return for the quoted JSON column.
select table_name, column_name
from information_schema.columns
where table_schema = 'public'
  and table_name in ('admin_packs','content_briefs')
  and column_name = 'references'
order by table_name;

-- Private cloud library 0.6.0
-- Additive: private user libraries. The native app never receives a service key.
begin;
create table if not exists public.cloud_packs (
  owner_id uuid not null references auth.users(id) on delete cascade,
  id uuid not null,
  name text not null check (length(name) between 1 and 60),
  category text not null default 'ALL',
  kind text not null check (kind in ('STATIC','ANIMATED')),
  manifest jsonb not null,
  fingerprint text not null check (fingerprint ~ '^[0-9a-f]{64}$'),
  revision integer not null default 1,
  updated_at timestamptz not null default now(),
  primary key (owner_id,id)
);
create table if not exists public.cloud_assets (
  owner_id uuid not null references auth.users(id) on delete cascade,
  sha256 text not null check (sha256 ~ '^[0-9a-f]{64}$'),
  bytes integer not null check (bytes between 12 and 500000),
  ready boolean not null default false,
  created_at timestamptz not null default now(),
  primary key(owner_id,sha256)
);
create table if not exists public.community_submissions (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  pack_id uuid not null,
  name text not null,
  manifest jsonb not null,
  fingerprint text not null,
  status text not null default 'pending' check (status in ('pending','draft','rejected','withdrawn')),
  admin_pack_id uuid references public.admin_packs(id),
  rights_statement text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(owner_id,pack_id)
);
create table if not exists public.community_reports (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  catalog_id text not null check(length(catalog_id) <= 100),
  reason text not null check(length(reason) between 1 and 1000),
  status text not null default 'open' check(status in ('open','resolved')),
  created_at timestamptz not null default now(),
  unique(owner_id,catalog_id)
);
create table if not exists public.cloud_request_budgets (
  owner_id uuid primary key references auth.users(id) on delete cascade,
  window_start timestamptz not null default now(),
  calls integer not null default 0
);
alter table public.cloud_packs enable row level security;
alter table public.cloud_assets enable row level security;
alter table public.community_submissions enable row level security;
alter table public.community_reports enable row level security;
alter table public.cloud_request_budgets enable row level security;
-- Server authorizes every operation against the verified Auth user. No direct
-- client grants or public policies, including for opted-in submissions.
revoke all on public.cloud_packs, public.cloud_assets, public.community_submissions,
  public.community_reports, public.cloud_request_budgets from anon, authenticated;
grant all on public.cloud_packs, public.cloud_assets, public.community_submissions,
  public.community_reports, public.cloud_request_budgets to service_role;

create or replace function public.cloud_budget(p_owner uuid)
returns table(allowed boolean) language plpgsql security invoker set search_path = '' as $$
declare n integer;
begin
  insert into public.cloud_request_budgets(owner_id) values(p_owner)
  on conflict(owner_id) do nothing;
  update public.cloud_request_budgets set
    calls = case when window_start < now()-interval '1 hour' then 1 else calls+1 end,
    window_start = case when window_start < now()-interval '1 hour' then now() else window_start end
  where owner_id=p_owner returning calls into n;
  return query select n <= 1200;
end $$;

create or replace function public.cloud_reserve_asset(p_owner uuid, p_sha text, p_bytes integer)
returns table(reserved boolean) language plpgsql security invoker set search_path = '' as $$
begin
  -- Per-owner advisory lock makes quota checks safe across concurrent uploads.
  perform pg_advisory_xact_lock(hashtextextended(p_owner::text, 0));
  if exists(select 1 from public.cloud_assets where owner_id=p_owner and sha256=p_sha) then
    return query select true; return;
  end if;
  if (select count(*) >= 20000 or coalesce(sum(bytes),0)+p_bytes > 536870912
      from public.cloud_assets where owner_id=p_owner) then
    return query select false; return;
  end if;
  insert into public.cloud_assets(owner_id,sha256,bytes) values(p_owner,p_sha,p_bytes);
  return query select true;
end $$;
revoke all on function public.cloud_budget(uuid), public.cloud_reserve_asset(uuid,text,integer)
  from public,anon,authenticated;
grant execute on function public.cloud_budget(uuid), public.cloud_reserve_asset(uuid,text,integer)
  to service_role;
commit;
