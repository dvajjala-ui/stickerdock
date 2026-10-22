-- Private, opt-in AI caption jobs. All access uses a verified Auth identity on
-- the API server; clients never receive the service role or direct table grants.
begin;

create table if not exists public.personal_ai_jobs (
  id uuid primary key,
  owner_id uuid not null references auth.users(id) on delete cascade,
  source_sha text not null check (source_sha ~ '^[0-9a-f]{64}$'),
  source_path text not null,
  prompt text not null check (char_length(prompt) between 1 and 200),
  status text not null default 'uploading' check (status in ('uploading','queued','running','complete','failed','cancelled')),
  caption text check (caption is null or char_length(caption) between 1 and 64),
  error_code text check (error_code is null or error_code in ('provider','invalid_source','limit','worker_lost')),
  worker_id text check (worker_id is null or char_length(worker_id) <= 120),
  created_at timestamptz not null default now(),
  started_at timestamptz,
  completed_at timestamptz,
  source_purged_at timestamptz,
  updated_at timestamptz not null default now(),
  check (source_path = 'users/' || owner_id::text || '/ai/sources/' || id::text || '-' || source_sha || '.webp'),
  check ((status = 'complete') = (caption is not null))
);
create index if not exists personal_ai_owner_recent on public.personal_ai_jobs(owner_id, created_at desc);
create index if not exists personal_ai_queue on public.personal_ai_jobs(status, created_at) where status = 'queued';
create table if not exists public.personal_ai_provider_reservations (
  job_id uuid primary key references public.personal_ai_jobs(id) on delete cascade,
  reserved_on date not null default (now() at time zone 'utc')::date,
  reserved_at timestamptz not null default now()
);
alter table public.personal_ai_jobs enable row level security;
alter table public.personal_ai_provider_reservations enable row level security;
revoke all on public.personal_ai_jobs from public, anon, authenticated;
revoke all on public.personal_ai_provider_reservations from public, anon, authenticated;
grant all on public.personal_ai_jobs to service_role;
grant all on public.personal_ai_provider_reservations to service_role;

-- Admission is atomic across concurrent taps and devices. An existing ID can
-- only replay the exact same owner's source and prompt. Upload happens only
-- after this reservation succeeds, so rejected calls cannot fill storage.
create or replace function public.queue_personal_ai_caption(
  p_owner uuid, p_id uuid, p_sha text, p_prompt text)
returns table(accepted boolean, reason text, job_status text)
language plpgsql security invoker set search_path = '' as $$
declare existing public.personal_ai_jobs%rowtype;
begin
  if p_owner is null or p_id is null or p_sha is null or p_sha !~ '^[0-9a-f]{64}$'
     or p_prompt is null or char_length(p_prompt) not between 1 and 200 then
    raise exception 'invalid personal AI request';
  end if;
  perform pg_advisory_xact_lock(pg_catalog.hashtextextended('personal-ai:' || p_owner::text, 0));
  select * into existing from public.personal_ai_jobs where id = p_id;
  if found then
    if existing.owner_id = p_owner and existing.source_sha = p_sha and existing.prompt = p_prompt then
      return query select true, 'replay'::text, existing.status;
    else
      return query select false, 'identity_conflict'::text, null::text;
    end if;
    return;
  end if;
  if (select count(*) from public.personal_ai_jobs
      where owner_id = p_owner and created_at >= now() - interval '24 hours') >= 3 then
    return query select false, 'daily_limit'::text, null::text; return;
  end if;
  if (select count(*) from public.personal_ai_jobs
      where owner_id = p_owner and status in ('uploading','queued','running')) >= 2 then
    return query select false, 'pending_limit'::text, null::text; return;
  end if;
  insert into public.personal_ai_jobs(id, owner_id, source_sha, source_path, prompt)
  values(p_id, p_owner, p_sha, 'users/' || p_owner::text || '/ai/sources/' || p_id::text || '-' || p_sha || '.webp', p_prompt);
  return query select true, 'created'::text, 'uploading'::text;
end $$;

-- Reserve before any Groq call. This is a conservative four-call global daily
-- cap for the personal lane, independent of per-account admission. It prevents
-- unbounded free-tier consumption from many accounts and cannot be replayed.
create or replace function public.reserve_personal_ai_provider_call(p_id uuid, p_worker_id text)
returns table(allowed boolean)
language plpgsql security invoker set search_path = '' as $$
begin
  if not exists(select 1 from public.personal_ai_jobs
      where id = p_id and status = 'running' and worker_id = p_worker_id) then
    return query select false; return;
  end if;
  perform pg_advisory_xact_lock(pg_catalog.hashtextextended('personal-ai-provider-budget', 0));
  if exists(select 1 from public.personal_ai_provider_reservations where job_id = p_id)
      or (select count(*) from public.personal_ai_provider_reservations
          where reserved_on = (now() at time zone 'utc')::date) >= 4 then
    return query select false; return;
  end if;
  insert into public.personal_ai_provider_reservations(job_id) values(p_id);
  return query select true;
end $$;

create or replace function public.activate_personal_ai_caption(p_owner uuid, p_id uuid)
returns table(job_status text)
language plpgsql security invoker set search_path = '' as $$
begin
  update public.personal_ai_jobs set status = 'queued', updated_at = now()
  where owner_id = p_owner and id = p_id and status = 'uploading';
  return query select j.status from public.personal_ai_jobs j where j.owner_id = p_owner and j.id = p_id;
end $$;

-- Leases expire to a terminal state. We deliberately do not automatically
-- replay an ambiguous provider call: it may already have spent its quota.
create or replace function public.claim_personal_ai_caption(p_worker_id text)
returns setof public.personal_ai_jobs
language plpgsql security invoker set search_path = '' as $$
declare claimed_id uuid;
begin
  if p_worker_id is null or char_length(btrim(p_worker_id)) not between 1 and 120 then
    raise exception 'invalid worker id';
  end if;
  update public.personal_ai_jobs set status = 'failed', error_code = 'invalid_source',
      completed_at = now(), updated_at = now()
  where status = 'uploading' and created_at < now() - interval '20 minutes';
  update public.personal_ai_jobs set status = 'failed', error_code = 'worker_lost',
      completed_at = now(), updated_at = now()
  where status = 'running' and started_at < now() - interval '20 minutes';
  select id into claimed_id from public.personal_ai_jobs
  where status = 'queued' order by created_at, id for update skip locked limit 1;
  if claimed_id is null then return; end if;
  return query update public.personal_ai_jobs
    set status = 'running', worker_id = btrim(p_worker_id), started_at = now(), updated_at = now()
    where id = claimed_id returning *;
end $$;

create or replace function public.finish_personal_ai_caption(
  p_id uuid, p_worker_id text, p_caption text, p_error_code text)
returns table(updated boolean)
language plpgsql security invoker set search_path = '' as $$
declare n integer;
begin
  if (p_caption is null) = (p_error_code is null)
      or (p_caption is not null and (char_length(p_caption) not between 1 and 64
           or p_caption !~ '^[ -~]+$'))
      or (p_error_code is not null and p_error_code not in ('provider','invalid_source','limit')) then
    raise exception 'invalid AI completion';
  end if;
  update public.personal_ai_jobs
  set status = case when p_caption is null then 'failed' else 'complete' end,
      caption = p_caption, error_code = p_error_code,
      completed_at = now(), updated_at = now()
  where id = p_id and status = 'running' and worker_id = p_worker_id;
  get diagnostics n = row_count;
  return query select n = 1;
end $$;

-- Cancellation wins over an in-flight completion; it does not undo a provider
-- request already made by a worker.
create or replace function public.cancel_personal_ai_caption(p_owner uuid, p_id uuid)
returns table(job_status text)
language plpgsql security invoker set search_path = '' as $$
begin
  update public.personal_ai_jobs set status = 'cancelled', caption = null,
    completed_at = now(), updated_at = now()
  where owner_id = p_owner and id = p_id and status in ('uploading','queued','running');
  return query select j.status from public.personal_ai_jobs j where j.owner_id = p_owner and j.id = p_id;
end $$;

revoke all on function public.queue_personal_ai_caption(uuid,uuid,text,text),
  public.activate_personal_ai_caption(uuid,uuid),
  public.claim_personal_ai_caption(text),
  public.reserve_personal_ai_provider_call(uuid,text),
  public.finish_personal_ai_caption(uuid,text,text,text),
  public.cancel_personal_ai_caption(uuid,uuid) from public, anon, authenticated;
grant execute on function public.queue_personal_ai_caption(uuid,uuid,text,text),
  public.activate_personal_ai_caption(uuid,uuid),
  public.claim_personal_ai_caption(text),
  public.reserve_personal_ai_provider_call(uuid,text),
  public.finish_personal_ai_caption(uuid,text,text,text),
  public.cancel_personal_ai_caption(uuid,uuid) to service_role;
commit;
