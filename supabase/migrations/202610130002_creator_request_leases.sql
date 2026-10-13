create index if not exists idx_creator_requests_running_lease
on public.creator_requests (started_at)
where status = 'running';

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

    -- A GitHub worker has a 15-minute job timeout. Any request that has remained
    -- running for more than 20 minutes has outlived that worker and can be safely
    -- recovered. Preserve the attempts counter so repeated crashes still honor
    -- the existing five-attempt ceiling.
    update public.creator_requests
    set status = 'failed',
        worker_id = null,
        completed_at = now(),
        error_message = 'Creator worker lease expired after maximum attempts',
        updated_at = now()
    where status = 'running'
      and attempts >= 5
      and started_at is not null
      and started_at < now() - interval '20 minutes';

    update public.creator_requests
    set status = 'queued',
        worker_id = null,
        started_at = null,
        completed_at = null,
        error_message = null,
        updated_at = now()
    where status = 'running'
      and attempts < 5
      and started_at is not null
      and started_at < now() - interval '20 minutes';

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
        completed_at = null,
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

comment on function public.claim_creator_request(text) is
'Atomically recovers stale Creator worker leases and claims the oldest queued request for the service role.';
