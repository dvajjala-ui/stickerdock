-- Additive intake queue. Downloaded candidates do not enter generation automatically.
create table if not exists public.media_intake_items (
  id uuid primary key,
  collection_id uuid not null,
  platform text not null check (platform ~ '^[a-z][a-z0-9_-]{1,39}$'),
  topic text not null check (char_length(topic) between 1 and 240),
  source_url text not null check (source_url like 'https://%'),
  download_url text not null check (download_url like 'https://%'),
  original_sha256 text not null unique check (original_sha256 ~ '^[0-9a-f]{64}$'),
  stored_sha256 text not null check (stored_sha256 ~ '^[0-9a-f]{64}$'),
  storage_path text not null check (storage_path ~ '^handnavigate/candidates/[0-9a-f]{64}\.webp$'),
  rights_status text not null default 'UNREVIEWED' check (rights_status in ('UNREVIEWED','APPROVED','REJECTED')),
  generation_eligible boolean not null default false,
  created_at timestamptz not null default now(),
  constraint media_intake_review_required check (not generation_eligible or rights_status = 'APPROVED')
);
alter table public.media_intake_items enable row level security;
revoke all on table public.media_intake_items from public, anon, authenticated;
grant select, insert, update on table public.media_intake_items to service_role;
create index if not exists media_intake_collection on public.media_intake_items (collection_id, created_at);
create index if not exists media_intake_review on public.media_intake_items (rights_status, created_at);
comment on table public.media_intake_items is 'HandNavigate source candidates and provenance; private, no automatic publication or Factory admission.';
