-- Read-only post-deployment verification for StickerDock Supabase objects.
-- Run after both migrations. This script does not insert, update or delete data.

-- Expect all three rows with rls_enabled = true.
select
    c.relname as table_name,
    c.relrowsecurity as rls_enabled,
    c.relforcerowsecurity as rls_forced
from pg_catalog.pg_class c
join pg_catalog.pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relkind = 'r'
  and c.relname in ('factory_runs', 'sticker_packs', 'creator_requests')
order by c.relname;

-- Expected client-role grants:
--   factory_runs: none
--   creator_requests: none
--   sticker_packs: SELECT only
-- service_role should have the privileges needed by the private workers.
select
    table_name,
    grantee,
    privilege_type
from information_schema.role_table_grants
where table_schema = 'public'
  and table_name in ('factory_runs', 'sticker_packs', 'creator_requests')
  and grantee in ('anon', 'authenticated', 'service_role')
order by table_name, grantee, privilege_type;

-- Expect one StickerDock table policy: approved GENERAL pack metadata reads.
select
    schemaname,
    tablename,
    policyname,
    roles,
    cmd,
    qual,
    with_check
from pg_catalog.pg_policies
where schemaname = 'public'
  and tablename in ('factory_runs', 'sticker_packs', 'creator_requests')
order by tablename, policyname;

-- The old broad public Storage SELECT policy must not exist. Public object
-- downloads are provided by the public bucket endpoint instead.
select
    schemaname,
    tablename,
    policyname,
    roles,
    cmd,
    qual
from pg_catalog.pg_policies
where schemaname = 'storage'
  and tablename = 'objects'
  and policyname = 'read public factory artifacts';

-- Expect SECURITY INVOKER (prosecdef = false), an empty search_path, and the
-- service_role as the only StickerDock client role with EXECUTE.
select
    p.proname as function_name,
    pg_catalog.pg_get_function_identity_arguments(p.oid) as arguments,
    p.prosecdef as security_definer,
    p.proconfig as function_config
from pg_catalog.pg_proc p
join pg_catalog.pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname = 'claim_creator_request';

select
    routine_name,
    grantee,
    privilege_type
from information_schema.routine_privileges
where routine_schema = 'public'
  and routine_name = 'claim_creator_request'
  and grantee in ('PUBLIC', 'anon', 'authenticated', 'service_role')
order by grantee;

-- Expect both buckets, private=false/public=true as named below, 75 MB limits,
-- and only the configured MIME types.
select
    id,
    name,
    public,
    file_size_limit,
    allowed_mime_types
from storage.buckets
where id in ('sticker-factory-private', 'sticker-factory-public')
order by id;

-- Expect one BEFORE UPDATE trigger on each table.
select
    event_object_table as table_name,
    trigger_name,
    action_timing,
    event_manipulation
from information_schema.triggers
where trigger_schema = 'public'
  and trigger_name in (
      'factory_runs_updated_at',
      'sticker_packs_updated_at',
      'creator_requests_updated_at'
  )
order by table_name, trigger_name;
