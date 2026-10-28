-- One immutable scheduled collection per plan/India day, with an atomic shared budget.
alter table public.collection_plans add column if not exists use_planner boolean not null default false;
alter table public.collection_jobs add column if not exists plan_id uuid references public.collection_plans(id);
alter table public.collection_jobs add column if not exists scheduled_day date;
alter table public.collection_jobs add column if not exists topic_origin text not null default 'saved' check(topic_origin in ('saved','planner','fallback'));
create unique index if not exists collection_plan_day on public.collection_jobs(plan_id,scheduled_day)
 where plan_id is not null and scheduled_day is not null;

create or replace function public.enqueue_daily_collection(
 p_plan_id uuid,p_day date,p_topics jsonb,p_topic_origin text,p_budget integer default 20
) returns table(job jsonb,reused boolean)
language plpgsql security definer set search_path=public,pg_temp as $$
declare plan public.collection_plans%rowtype; found_job public.collection_jobs%rowtype;
 request_key text; day_start timestamptz; stored integer; reserved integer; remaining integer;
begin
 if p_day is null or p_budget is null or p_budget<1 or p_budget>10000 then raise exception 'Invalid daily budget'; end if;
 if p_topics is null or jsonb_typeof(p_topics)<>'array' or jsonb_array_length(p_topics) not between 1 and 10
 or exists(select 1 from jsonb_array_elements(p_topics) t where jsonb_typeof(t)<>'string' or char_length(t#>>'{}') not between 1 and 240)
 or p_topic_origin is null or p_topic_origin not in ('saved','planner','fallback') then raise exception 'Invalid daily topics'; end if;
 perform pg_advisory_xact_lock(hashtext('handnavigate-daily:'||p_day::text));
 select * into plan from public.collection_plans p where p.id=p_plan_id and p.daily and not p.archived for share;
 if not found then return; end if;
 request_key='daily:'||p_day::text||':'||plan.id::text;
 select * into found_job from public.collection_jobs j where j.request_id=request_key or j.request_id like request_key||':%' limit 1;
 if found then return query select to_jsonb(found_job),true; return; end if;
 day_start=p_day::timestamp at time zone 'Asia/Kolkata';
 select count(*) into stored from public.media_intake_items i where i.created_at>=day_start and i.created_at<day_start+interval '1 day';
 -- Reserve every unfinished active request, including an older queued request.
 select coalesce(sum(greatest(0,j.image_limit-j.delivered)),0) into reserved from public.collection_jobs j
 where j.status in ('QUEUED','RUNNING','WAITING_FOR_LOGIN');
 remaining=least(plan.image_limit,p_budget-stored-reserved);
 if remaining<=0 then return; end if;
 insert into public.collection_jobs(request_id,topics,sources,image_limit,plan_id,scheduled_day,topic_origin)
 values(request_key,p_topics,plan.sources,remaining,plan.id,p_day,p_topic_origin) returning * into found_job;
 return query select to_jsonb(found_job),false;
end $$;
revoke all on function public.enqueue_daily_collection(uuid,date,jsonb,text,integer) from public,anon,authenticated;
grant execute on function public.enqueue_daily_collection(uuid,date,jsonb,text,integer) to service_role;
notify pgrst,'reload schema';
