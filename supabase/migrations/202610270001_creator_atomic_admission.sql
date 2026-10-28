-- Apply before deploying the API callers. No table data or worker contracts change.
create or replace function public.admit_creator_request(
    p_id uuid, p_source_path text default null, p_prompt text default null,
    p_retry boolean default false)
returns table (accepted boolean, reason text, job_status text)
language plpgsql security invoker set search_path = '' as $$
declare
    existing public.creator_requests%rowtype;
begin
    if p_id is null or p_retry is null then
        raise exception 'invalid request identity';
    end if;
    if not p_retry and (p_source_path is null or
        p_source_path !~ '^inbox/[0-9a-f]{64}\.(png|jpg|jpeg|webp)$' or
        p_prompt is null or char_length(p_prompt) not between 1 and 500) then
        raise exception 'invalid Creator request';
    end if;

    -- New jobs and retries share a transaction lock. Worker claiming can only
    -- move an already-counted queued row to running, so it needs no new lock.
    perform pg_catalog.pg_advisory_xact_lock(734612305::bigint);
    select * into existing from public.creator_requests where id = p_id for update;
    if found then
        if not p_retry then
            return query select existing.source_path = p_source_path and existing.prompt = p_prompt,
                case when existing.source_path = p_source_path and existing.prompt = p_prompt
                    then 'replay'::text else 'identity_conflict'::text end, existing.status;
            return;
        end if;
        if existing.status not in ('failed', 'cancelled') then
            return query select false, 'changed'::text, existing.status;
            return;
        end if;
    elsif p_retry then
        return query select false, 'not_found'::text, null::text;
        return;
    end if;

    if (select count(*) from public.creator_requests where status in ('queued', 'running')) >= 25 then
        return query select false, 'full'::text, null::text;
        return;
    end if;
    if p_retry then
        update public.creator_requests set status = 'queued', attempts = 0,
            error_message = null, started_at = null, completed_at = null,
            worker_id = null, result_path = null, updated_at = now() where id = p_id;
    else
        insert into public.creator_requests(id, source_path, prompt, status)
            values (p_id, p_source_path, p_prompt, 'queued');
    end if;
    return query select true, case when p_retry then 'retried'::text else 'created'::text end, 'queued'::text;
end;
$$;

revoke all on function public.admit_creator_request(uuid,text,text,boolean) from public, anon, authenticated;
grant execute on function public.admit_creator_request(uuid,text,text,boolean) to service_role;
comment on function public.admit_creator_request(uuid,text,text,boolean) is
'Service-role atomic Creator admission/retry with payload-checked replay and a shared 25-outstanding cap.';
