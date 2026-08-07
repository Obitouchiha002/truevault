-- Rate-limits the admin PIN. Run this in the Supabase SQL editor.
--
-- WHY THIS IS THE MOST IMPORTANT FILE IN THE PROJECT'S BACKEND
--
-- The anon key is public. It ships inside every APK and anyone can extract it in about a minute.
-- That is by design and it is safe, because row-level security is on with no policies, so the key
-- cannot read or write a single table directly.
--
-- But it CAN call `admin_devices(p_pin)`, and that function's only defence is a string comparison.
-- Nothing anywhere limits how often it may be called. So an attacker with the extracted key can
-- script guesses at whatever rate the network allows — a few hundred a second — against a PIN with
-- no lockout. A six-digit PIN falls in minutes. Once it is guessed they can list every install,
-- block anyone, and flip the kill switch.
--
-- The app cannot fix this. Throttling the PIN box in the app stops a person holding the phone and
-- nothing else: the attacker is not using the app, they are POSTing to the endpoint directly. The
-- limit has to live where the check lives, which is here.
--
-- What this adds: five wrong PINs from one address in fifteen minutes, then that address is refused
-- for fifteen minutes regardless of what it sends. Correct PINs are not counted and never lock out.

create table if not exists admin_attempts (
  addr       inet         not null,
  at         timestamptz  not null default now(),
  ok         boolean      not null
);

create index if not exists admin_attempts_addr_at on admin_attempts (addr, at desc);

-- Returns true when this address should be refused outright.
create or replace function admin_locked_out(p_addr inet)
returns boolean
language sql security definer set search_path = public as $$
  select count(*) >= 5
  from admin_attempts
  where addr = p_addr
    and not ok
    and at > now() - interval '15 minutes';
$$;

-- The guard every admin function now calls first.
--
-- It records the attempt before deciding, so a failure cannot be rolled back by the caller, and it
-- raises the same 'unauthorized' for a locked-out address as for a wrong PIN — telling an attacker
-- "you are rate limited" tells them the PIN was wrong AND that guessing is worth continuing later.
create or replace function admin_check_pin(p_pin text)
returns void
language plpgsql security definer set search_path = public as $$
declare
  v_addr inet := coalesce(inet_client_addr(), '0.0.0.0'::inet);
  v_ok   boolean;
begin
  if admin_locked_out(v_addr) then
    insert into admin_attempts (addr, ok) values (v_addr, false);
    raise exception 'unauthorized';
  end if;

  v_ok := (p_pin = (select admin_pin from app_config where id = 1));
  insert into admin_attempts (addr, ok) values (v_addr, v_ok);

  if not v_ok then
    raise exception 'unauthorized';
  end if;

  -- Keep the table from growing without bound. Older rows are past every window that reads them.
  delete from admin_attempts where at < now() - interval '1 day';
end $$;

alter table admin_attempts enable row level security;
-- No policies: the anon key must not be able to read this table. Being able to see which addresses
-- have failed, and how recently, would hand an attacker the lockout schedule.

-- ------------------------------------------------------------------------------------------------
-- Re-create each admin function to use the guard. The bodies are otherwise unchanged.
-- ------------------------------------------------------------------------------------------------

create or replace function admin_devices(p_pin text)
returns setof devices
language plpgsql security definer set search_path = public as $$
begin
  perform admin_check_pin(p_pin);
  return query select * from devices order by last_seen desc;
end $$;

create or replace function admin_block(p_pin text, p_id text, p_blocked boolean,
                                       p_reason text, p_minutes int, p_code text)
returns void
language plpgsql security definer set search_path = public as $$
begin
  perform admin_check_pin(p_pin);
  update devices set
    blocked = p_blocked,
    block_reason  = case when p_blocked then p_reason else null end,
    block_code    = case when p_blocked then p_code   else null end,
    blocked_until = case when p_blocked and p_minutes is not null
                         then now() + make_interval(mins => p_minutes) else null end
  where id = p_id;
end $$;

create or replace function admin_config(p_pin text, p_kill boolean,
                                        p_latest text, p_url text, p_note text)
returns void
language plpgsql security definer set search_path = public as $$
begin
  perform admin_check_pin(p_pin);
  update app_config set kill = p_kill, latest_version = p_latest,
                        update_url = p_url, update_note = p_note where id = 1;
end $$;

-- admin_check_pin and admin_locked_out are NOT granted to anon. They are called from inside the
-- SECURITY DEFINER functions above, which run as the owner, so the caller never needs them —
-- and granting admin_locked_out would let anyone probe whether an address is currently blocked.
revoke all on function admin_check_pin(text)   from anon, public;
revoke all on function admin_locked_out(inet)  from anon, public;

-- ------------------------------------------------------------------------------------------------
-- After running this, also do the one thing SQL cannot do for you:
--
--   update app_config set admin_pin = '<long random string>' where id = 1;
--
-- Rate limiting buys time against guessing; it does not make a six-digit PIN strong. Use something
-- long enough that five guesses per fifteen minutes is hopeless — a passphrase, or 24 random
-- characters. Note this changes the PIN for StreamGarden too, since both apps read the same row.
-- ------------------------------------------------------------------------------------------------
