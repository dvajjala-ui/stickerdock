-- Outbound worker queue. No browser profiles or provider credentials enter the database.
alter table public.media_intake_items add column if not exists title text not null default '' check (char_length(title)<=160);
alter table public.media_intake_items add column if not exists tags jsonb not null default '[]' check (jsonb_typeof(tags)='array' and jsonb_array_length(tags)<=20);
alter table public.media_intake_items add column if not exists archived boolean not null default false;
alter table public.media_intake_items add column if not exists version integer not null default 1;
alter table public.media_intake_items add column if not exists updated_at timestamptz not null default now();

create table if not exists public.collection_plans (
 id uuid primary key default gen_random_uuid(), name text not null check(char_length(name) between 1 and 100),
 topics jsonb not null check(jsonb_typeof(topics)='array' and jsonb_array_length(topics) between 1 and 10),
 sources jsonb not null check(jsonb_typeof(sources)='array' and jsonb_array_length(sources) between 1 and 10),
 image_limit integer not null default 20 check(image_limit between 1 and 100),
 daily boolean not null default false, archived boolean not null default false,
 version integer not null default 1, created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table if not exists public.collection_jobs (
 id uuid primary key default gen_random_uuid(), request_id text unique not null check(char_length(request_id) between 8 and 128),
 topics jsonb not null, sources jsonb not null, image_limit integer not null check(image_limit between 1 and 100),
 status text not null default 'QUEUED' check(status in ('QUEUED','RUNNING','WAITING_FOR_LOGIN','VERIFIED_STORAGE','PARTIAL','AUTH_REQUIRED','DELIVERY_FAILED','FAILED','CANCELLED','INTERRUPTED')),
 worker_id uuid, lease_until timestamptz, cancel_requested boolean not null default false, attempts integer not null default 0,
 local_id uuid, saved integer not null default 0, delivered integer not null default 0, error text,
 source_status jsonb not null default '{}', events jsonb not null default '[]',
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(), finished_at timestamptz
);
create index if not exists collection_jobs_queue on public.collection_jobs(status,created_at);
create table if not exists public.collection_workers (
 id uuid primary key, created_at timestamptz not null default now(), last_seen timestamptz not null default now()
);
alter table public.collection_workers enable row level security;
revoke all on public.collection_workers from public,anon,authenticated;
grant select,insert,update on public.collection_workers to service_role;
alter table public.collection_plans enable row level security;
alter table public.collection_jobs enable row level security;
revoke all on public.collection_plans,public.collection_jobs from public,anon,authenticated;
grant select,insert,update on public.collection_plans,public.collection_jobs to service_role;

create or replace function public.claim_collection_job(p_worker_id uuid) returns setof public.collection_jobs
language plpgsql security definer set search_path=public,pg_temp as $$
declare chosen uuid;
begin
 insert into collection_workers(id) values(p_worker_id) on conflict(id) do update set last_seen=now();
 update collection_jobs set status='CANCELLED',lease_until=null,finished_at=now(),updated_at=now()
 where cancel_requested and status in ('RUNNING','WAITING_FOR_LOGIN') and lease_until<now();
 -- Reclaim only an expired lease. Workers heartbeat while the browser is active.
 select id into chosen from collection_jobs where not cancel_requested and
 (status='QUEUED' or (status in ('RUNNING','WAITING_FOR_LOGIN') and lease_until < now()))
 order by created_at for update skip locked limit 1;
 if chosen is null then return; end if;
 return query update collection_jobs set status='RUNNING',worker_id=p_worker_id,
 lease_until=now()+interval '2 minutes',attempts=attempts+1,updated_at=now(),finished_at=null
 where id=chosen returning *;
end $$;
revoke all on function public.claim_collection_job(uuid) from public,anon,authenticated;
grant execute on function public.claim_collection_job(uuid) to service_role;

create or replace function public.report_collection_job(p_id uuid,p_worker_id uuid,p_report jsonb)
returns setof public.collection_jobs language plpgsql security definer set search_path=public,pg_temp as $$
declare next_status text;
begin
 insert into collection_workers(id) values(p_worker_id) on conflict(id) do update set last_seen=now();
 next_status=p_report->>'status';
 if next_status not in ('RUNNING','WAITING_FOR_LOGIN','VERIFIED_STORAGE','PARTIAL','AUTH_REQUIRED','DELIVERY_FAILED','FAILED','CANCELLED','INTERRUPTED') then raise exception 'Invalid collection status'; end if;
 return query update collection_jobs set status=next_status,
 local_id=coalesce((p_report->>'local_id')::uuid,local_id),
 saved=greatest(saved,least(image_limit,(p_report->>'saved')::integer)),
 delivered=greatest(delivered,least(image_limit,(p_report->>'delivered')::integer)),
 error=left(p_report->>'error',500),source_status=coalesce(p_report->'source_status','{}'),
 events=coalesce(p_report->'events','[]'),updated_at=now(),
 lease_until=case when next_status in ('RUNNING','WAITING_FOR_LOGIN') then now()+interval '2 minutes' else null end,
 finished_at=case when next_status in ('RUNNING','WAITING_FOR_LOGIN') then null else now() end
 where id=p_id and worker_id=p_worker_id and lease_until>now() and status in ('RUNNING','WAITING_FOR_LOGIN') returning *;
end $$;
revoke all on function public.report_collection_job(uuid,uuid,jsonb) from public,anon,authenticated;
grant execute on function public.report_collection_job(uuid,uuid,jsonb) to service_role;
notify pgrst,'reload schema';
