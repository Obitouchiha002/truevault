-- Move admin off the phone and onto the website. Run this in the Supabase SQL editor.
--
-- WHAT THIS ACHIEVES
--
-- Today the app calls `admin_devices`, `admin_block` and `admin_config`, so those are granted to
-- `anon` — and `anon` ships inside every APK. That is the whole reason the admin PIN could be
-- brute-forced: a public key that can call an admin function, with only a string comparison in
-- between.
--
-- After this, `anon` can call exactly one function: `checkin`. The admin work happens in a Vercel
-- serverless function using the service_role key, which never leaves the server. There is no
-- privileged function left for a public key to reach, so the guessing attack does not get harder —
-- it stops existing. `docs/PIN-RATE-LIMIT.sql` becomes unnecessary.
--
-- Run this only after the website admin is deployed and you have confirmed it works. Between the
-- two, you have no working admin at all.

-- ------------------------------------------------------------------------------------------------
-- 1. Server-side twins, with no PIN argument.
--
-- They take no PIN because they are unreachable without the service_role key, and a PIN checked by
-- the caller that holds the master credential proves nothing. Authentication moved to the website:
-- ADMIN_PASSWORD, compared in constant time, rate limited, over HTTPS.
-- ------------------------------------------------------------------------------------------------

create or replace function admin_devices_srv()
returns setof devices
language sql security definer set search_path = public as $$
  select * from devices order by last_seen desc;
$$;

create or replace function admin_block_srv(p_id text, p_blocked boolean,
                                           p_reason text, p_minutes int, p_code text)
returns void
language plpgsql security definer set search_path = public as $$
begin
  update devices set
    blocked = p_blocked,
    block_reason  = case when p_blocked then p_reason else null end,
    block_code    = case when p_blocked then p_code   else null end,
    blocked_until = case when p_blocked and p_minutes is not null
                         then now() + make_interval(mins => p_minutes) else null end
  where id = p_id;
end $$;

-- Optional: only needed if you added the premium column.
create or replace function admin_premium_srv(p_id text, p_premium boolean)
returns void
language plpgsql security definer set search_path = public as $$
begin
  update devices set premium = p_premium where id = p_id;
end $$;

-- ------------------------------------------------------------------------------------------------
-- 2. These are for service_role only. Granting them to anon would undo the entire point.
-- ------------------------------------------------------------------------------------------------

revoke all on function admin_devices_srv()                              from anon, public;
revoke all on function admin_block_srv(text,boolean,text,int,text)      from anon, public;
revoke all on function admin_premium_srv(text,boolean)                  from anon, public;

-- ------------------------------------------------------------------------------------------------
-- 3. Take the old admin functions away from the public key.
--
-- This is the line that closes the hole. Everything above is plumbing; this is the fix.
--
-- StreamGarden still calls these with its own in-app panel, so run these three ONLY once you have
-- moved StreamGarden's admin to the website as well — otherwise its panel stops working. If you are
-- not ready for that yet, skip this step: TrueVault's website admin works regardless, and
-- docs/PIN-RATE-LIMIT.sql keeps the old path defensible in the meantime.
-- ------------------------------------------------------------------------------------------------

-- revoke execute on function admin_devices(text)                          from anon;
-- revoke execute on function admin_block(text,text,boolean,text,int,text) from anon;
-- revoke execute on function admin_config(text,boolean,text,text,text)    from anon;

-- ------------------------------------------------------------------------------------------------
-- 4. Confirm what `anon` can still reach. `checkin` should be the only row.
-- ------------------------------------------------------------------------------------------------

select p.proname
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and has_function_privilege('anon', p.oid, 'execute')
order by p.proname;

-- ------------------------------------------------------------------------------------------------
-- 5. Web admin password, set from the browser instead of the command line.
--
-- Stored as a scrypt hash, never as the password. The salt and parameters travel with it in one
-- string, so verification needs nothing else.
--
-- First-run claim is the interesting problem here: /admin is a public URL, so "the first visitor
-- sets the password" would hand the panel to whoever found it first. Instead the first setup is
-- authorised by the admin PIN you already use for StreamGarden — you know it, a stranger does not,
-- and it needs no command line. After that the PIN is irrelevant to this panel.
-- ------------------------------------------------------------------------------------------------

alter table app_config add column if not exists admin_web_hash text;

-- Is a web password set yet? No PIN needed: the answer is a single boolean and reveals nothing.
create or replace function admin_web_configured_srv()
returns boolean
language sql security definer set search_path = public as $$
  select coalesce(admin_web_hash, '') <> '' from app_config where id = 1;
$$;

create or replace function admin_web_hash_srv()
returns text
language sql security definer set search_path = public as $$
  select admin_web_hash from app_config where id = 1;
$$;

-- Setting or changing it requires the existing admin PIN, checked here rather than by the caller.
create or replace function admin_web_set_srv(p_pin text, p_hash text)
returns void
language plpgsql security definer set search_path = public as $$
begin
  if p_pin <> (select admin_pin from app_config where id = 1) then
    raise exception 'unauthorized';
  end if;
  update app_config set admin_web_hash = p_hash where id = 1;
end $$;

revoke all on function admin_web_configured_srv() from anon, public;
revoke all on function admin_web_hash_srv()       from anon, public;
revoke all on function admin_web_set_srv(text,text) from anon, public;
