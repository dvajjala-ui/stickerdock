-- Advertise reviewed source capabilities without uploading browser recipes or cookies.
alter table public.collection_workers add column if not exists supported_sources jsonb not null default '["pinterest","reddit"]';
create table if not exists public.collection_source_catalog (
 id text primary key check(id ~ '^[a-z][a-z0-9_-]{1,39}$'),
 name text not null check(char_length(name) between 1 and 100),
 revision text check(revision is null or revision ~ '^[0-9a-f]{64}$'),
 created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
alter table public.collection_source_catalog enable row level security;
revoke all on public.collection_source_catalog from public,anon,authenticated;
grant select,insert,update on public.collection_source_catalog to service_role;

create or replace function public.claim_compatible_collection_job(p_worker_id uuid,p_sources jsonb)
returns setof public.collection_jobs language plpgsql security definer set search_path=public,pg_temp as $$
declare chosen uuid; ids jsonb;
begin
 if jsonb_typeof(p_sources) is distinct from 'array' then raise exception 'Invalid source capabilities';end if;
 if jsonb_array_length(p_sources) not between 1 and 20 then raise exception 'Invalid source capabilities';end if;
 if exists(select 1 from jsonb_array_elements(p_sources) s where jsonb_typeof(s)<>'object' or
   coalesce(s->>'id','') !~ '^[a-z][a-z0-9_-]{1,39}$' or char_length(coalesce(s->>'name','')) not between 1 and 100 or
   (s->>'revision' is not null and s->>'revision' !~ '^[0-9a-f]{64}$')) then raise exception 'Invalid source capability';end if;
 if (select count(distinct s->>'id') from jsonb_array_elements(p_sources) s)<>jsonb_array_length(p_sources) then raise exception 'Duplicate source capability';end if;
 select jsonb_agg(s->>'id') into ids from jsonb_array_elements(p_sources) s;
 insert into collection_workers(id,supported_sources) values(p_worker_id,ids)
 on conflict(id) do update set last_seen=now(),supported_sources=excluded.supported_sources;
 insert into collection_source_catalog(id,name,revision)
 select s->>'id',s->>'name',s->>'revision' from jsonb_array_elements(p_sources) s
 on conflict(id) do update set name=excluded.name,revision=excluded.revision,updated_at=now();
 update collection_jobs set status='CANCELLED',lease_until=null,finished_at=now(),updated_at=now()
 where cancel_requested and status in ('RUNNING','WAITING_FOR_LOGIN') and lease_until<now();
 select id into chosen from collection_jobs where not cancel_requested and sources <@ ids and
 (status='QUEUED' or (status in ('RUNNING','WAITING_FOR_LOGIN') and lease_until<now()))
 order by created_at for update skip locked limit 1;
 if chosen is null then return;end if;
 return query update collection_jobs set status='RUNNING',worker_id=p_worker_id,
 lease_until=now()+interval '2 minutes',attempts=attempts+1,updated_at=now(),finished_at=null
 where id=chosen returning *;
end $$;
revoke all on function public.claim_compatible_collection_job(uuid,jsonb) from public,anon,authenticated;
grant execute on function public.claim_compatible_collection_job(uuid,jsonb) to service_role;

-- Existing worker versions can still run the original platforms. They cannot
-- consume jobs that require a newly installed recipe.
create or replace function public.claim_collection_job(p_worker_id uuid) returns setof public.collection_jobs
language sql security definer set search_path=public,pg_temp as $$
 select * from claim_compatible_collection_job(p_worker_id,'[{"id":"pinterest","name":"Pinterest"},{"id":"reddit","name":"Reddit"}]'::jsonb);
$$;
revoke all on function public.claim_collection_job(uuid) from public,anon,authenticated;
grant execute on function public.claim_collection_job(uuid) to service_role;
notify pgrst,'reload schema';
