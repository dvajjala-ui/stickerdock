create table if not exists public.creator_requests (
    id uuid primary key,
    source_path text not null check (
        source_path ~ '^inbox/[0-9a-f]{64}\.(png|jpg|jpeg|webp)$'
    ),
    prompt text not null check (char_length(prompt) between 1 and 500),
    status text not null default 'queued' check (
        status in ('queued', 'running', 'complete', 'failed', 'cancelled')
    ),
    attempts integer not null default 0 check (attempts between 0 and 5),
    result_path text,
    error_message text,
    worker_id text,
    created_at timestamptz not null default now(),
    started_at timestamptz,
    completed_at timestamptz,
    updated_at timestamptz not null default now(),
    check (result_path is null or result_path ~ '^inbox/[0-9a-f]{64}\.(png|jpg|jpeg|webp)$'),
    check (error_message is null or char_length(error_message) <= 1000),
    check (worker_id is null or char_length(worker_id) <= 120)
);

create index if not exists idx_creator_requests_queue
on public.creator_requests (status, created_at)
where status = 'queued';

alter table public.creator_requests enable row level security;

-- Creator Studio and the private Factory access this queue only with the
-- Supabase service role. Revoke Data API privileges from public client roles
-- explicitly instead of relying on RLS/project grant defaults alone.
revoke all on table public.creator_requests from anon, authenticated;
grant all on table public.creator_requests to service_role;

create or replace function public.claim_creator_request(p_worker_id text)
returns setof public.creator_requests
language plpgsql
security invoker
set search_path = ''
as $$
declare
    claimed_id uuid;
begin
    if p_worker_id is null or char_length(btrim(p_worker_id)) not between 1 and 120 then
        raise exception 'invalid worker id';
    end if;

    select id into claimed_id
    from public.creator_requests
    where status = 'queued' and attempts < 5
    order by created_at, id
    for update skip locked
    limit 1;

    if claimed_id is null then
        return;
    end if;

    return query
    update public.creator_requests
    set status = 'running',
        attempts = attempts + 1,
        worker_id = btrim(p_worker_id),
        started_at = now(),
        error_message = null,
        updated_at = now()
    where id = claimed_id
    returning *;
end;
$$;

revoke all on function public.claim_creator_request(text) from public;
revoke all on function public.claim_creator_request(text) from anon;
revoke all on function public.claim_creator_request(text) from authenticated;
grant execute on function public.claim_creator_request(text) to service_role;

drop trigger if exists creator_requests_updated_at on public.creator_requests;
create trigger creator_requests_updated_at before update on public.creator_requests
for each row execute function public.set_updated_at();

comment on table public.creator_requests is
'Private Creator Studio to Factory AI request/result queue. Service-role access only.';
