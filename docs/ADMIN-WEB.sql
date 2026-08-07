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
