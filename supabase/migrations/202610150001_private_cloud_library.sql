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
