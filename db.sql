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
