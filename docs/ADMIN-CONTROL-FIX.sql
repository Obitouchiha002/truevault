-- Fix: the admin panel can read installs but block / premium do nothing visible.
-- Run this in the Supabase SQL editor. Safe to run more than once.
--
-- WHY
--
-- admin_block_srv and admin_premium_srv returned void, so the website could not tell "changed a
-- row" from "changed nothing" — a wrong id, or a missing grant, both looked like success. These
-- versions return the number of rows they changed, so the panel can say "1 install updated" or
-- "no install matched", and this file re-grants EXECUTE to service_role in case that was the cause.
--
-- Changing a function's return type needs a DROP first; create-or-replace cannot do it.

-- ------------------------------------------------------------------------------------------------
-- Block / unblock — now returns the number of installs changed.
-- ------------------------------------------------------------------------------------------------
drop function if exists admin_block_srv(text, boolean, text, int, text);

create function admin_block_srv(p_id text, p_blocked boolean,
                                p_reason text, p_minutes int, p_code text)
returns integer
language plpgsql security definer set search_path = public as $$
declare n integer;
begin
  update devices set
    blocked = p_blocked,
    block_reason  = case when p_blocked then p_reason else null end,
    block_code    = case when p_blocked then p_code   else null end,
    blocked_until = case when p_blocked and p_minutes is not null
                         then now() + make_interval(mins => p_minutes) else null end
  where id = p_id;
  get diagnostics n = row_count;
  return n;
end $$;

-- ------------------------------------------------------------------------------------------------
-- Premium — same, returns rows changed. Also ensures the column exists.
-- ------------------------------------------------------------------------------------------------
alter table devices add column if not exists premium boolean not null default false;

drop function if exists admin_premium_srv(text, boolean);

create function admin_premium_srv(p_id text, p_premium boolean)
returns integer
language plpgsql security definer set search_path = public as $$
declare n integer;
begin
  update devices set premium = p_premium where id = p_id;
  get diagnostics n = row_count;
  return n;
end $$;

-- ------------------------------------------------------------------------------------------------
-- Grants. service_role must be able to run these; anon must not. Re-stated here in case a re-run
-- of ADMIN-WEB.sql stripped them.
-- ------------------------------------------------------------------------------------------------
revoke all on function admin_block_srv(text,boolean,text,int,text)  from anon, public;
revoke all on function admin_premium_srv(text,boolean)              from anon, public;
grant execute on function admin_block_srv(text,boolean,text,int,text)  to service_role;
grant execute on function admin_premium_srv(text,boolean)              to service_role;

-- Confirm both are runnable by service_role and not by anon.
select p.proname,
       has_function_privilege('service_role', p.oid, 'execute') as service_role,
       has_function_privilege('anon',         p.oid, 'execute') as anon
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and p.proname in ('admin_block_srv','admin_premium_srv')
order by p.proname;
