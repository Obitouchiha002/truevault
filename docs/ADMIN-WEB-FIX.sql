-- Fix for ADMIN-WEB.sql: give service_role back the EXECUTE it needs.
-- Run this in the Supabase SQL editor. Safe to run more than once.
--
-- WHAT WENT WRONG
--
-- Postgres grants EXECUTE on every new function to PUBLIC by default. `service_role` had no grant
-- of its own — it was riding on that default. So `revoke all on function ... from anon, public`
-- did what it said and rather more: it locked out anon, which was the point, and service_role,
-- which was not. The server holds the master key and still could not call its own functions.
--
-- The symptom was a 502 from /api/admin on `status` and `list`. Not obviously a permissions error,
-- because the API refuses to return the upstream message to the browser — deliberately, since a
-- PostgREST error echoes the request. The detail was in the Vercel log the whole time.
--
-- The revokes stay. Removing PUBLIC's default grant is correct and is what stops the anon key
-- reaching these; the missing half was naming service_role explicitly.

grant execute on function admin_devices_srv()                          to service_role;
grant execute on function admin_block_srv(text,boolean,text,int,text)  to service_role;
grant execute on function admin_web_configured_srv()                   to service_role;
grant execute on function admin_web_hash_srv()                         to service_role;
grant execute on function admin_web_set_srv(text,text)                 to service_role;

-- Only if you added the premium column; harmless to skip.
do $$
begin
  execute 'grant execute on function admin_premium_srv(text,boolean) to service_role';
exception when undefined_function then
  raise notice 'admin_premium_srv does not exist — skipping, premium is optional';
end $$;

-- ------------------------------------------------------------------------------------------------
-- Check: service_role should be able to execute all of these, and anon none of them.
-- ------------------------------------------------------------------------------------------------

select p.proname,
       has_function_privilege('service_role', p.oid, 'execute') as service_role,
       has_function_privilege('anon',         p.oid, 'execute') as anon
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname like 'admin\_%\_srv'
order by p.proname;
