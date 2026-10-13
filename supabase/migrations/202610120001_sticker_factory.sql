create extension if not exists pgcrypto;

create table if not exists public.factory_runs (
    day date primary key,
    status text not null check (status in ('running', 'complete', 'failed')),
    target integer not null check (target between 3 and 100),
    generated integer not null default 0 check (generated between 0 and 100),
    attempts integer not null default 0 check (attempts between 0 and 100),
    last_result text,
    groq_metrics jsonb not null default '{}'::jsonb,
    updated_at timestamptz not null default now()
);

create table if not exists public.sticker_packs (
    id uuid primary key default gen_random_uuid(),
    day date not null unique,
    name text not null check (char_length(name) between 1 and 60),
    creator text not null check (char_length(creator) between 1 and 60),
    rating text not null check (rating in ('GENERAL', 'MATURE')),
    status text not null check (status in ('pending', 'approved', 'rejected')),
    private_path text not null,
    public_path text,
    sha256 text not null check (sha256 ~ '^[0-9a-f]{64}$'),
    sticker_count integer not null check (sticker_count between 3 and 100),
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.factory_runs enable row level security;
alter table public.sticker_packs enable row level security;

drop policy if exists "public approved general packs" on public.sticker_packs;
create policy "public approved general packs" on public.sticker_packs
for select using (status = 'approved' and rating = 'GENERAL');

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
    ('sticker-factory-private', 'sticker-factory-private', false, 75000000,
     array['application/zip', 'application/json', 'image/png', 'image/jpeg', 'image/webp',
           'image/gif', 'video/mp4', 'video/webm', 'video/quicktime']),
    ('sticker-factory-public', 'sticker-factory-public', true, 75000000,
     array['application/zip', 'application/json'])
on conflict (id) do update set
    public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

-- Public Storage objects are served through /storage/v1/object/public/... without
-- a storage.objects SELECT policy. Keep listing/authenticated object metadata
-- closed to anon/authenticated clients; trusted Factory workers use service_role.
drop policy if exists "read public factory artifacts" on storage.objects;

create or replace function public.set_updated_at()
returns trigger language plpgsql as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists factory_runs_updated_at on public.factory_runs;
create trigger factory_runs_updated_at before update on public.factory_runs
for each row execute function public.set_updated_at();

drop trigger if exists sticker_packs_updated_at on public.sticker_packs;
create trigger sticker_packs_updated_at before update on public.sticker_packs
for each row execute function public.set_updated_at();

comment on table public.factory_runs is
'Service-role-only operational state. No public read policy.';
comment on table public.sticker_packs is
'Factory output metadata. Public reads see approved GENERAL packs only.';
