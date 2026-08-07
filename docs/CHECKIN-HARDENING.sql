-- Hardening for `checkin`, the one function the public anon key can still call.
-- Run in the Supabase SQL editor.
--
-- THE VULNERABILITY
--
-- The anon key ships inside every APK and takes a minute to extract. It can call `checkin`, and it
-- has to — that is how an app registers. But `checkin` accepts whatever it is given:
--
--   1. ANY `p_id`. It inserts a row for it. Nothing limits how many times, so a loop can create
--      unbounded rows: the devices table grows until the Supabase quota is gone, and the admin
--      panel becomes unusable long before that because it lists every row. Cheapest attack here
--      and the most damaging — a few minutes of scripting against a free-tier project.
--   2. ANY `p_platform`. It becomes a group heading in the admin panel. It is HTML-escaped, so
--      this is not XSS, but an attacker can create fake apps in a panel you make decisions from.
--   3. Someone else's `p_id`, if they know it. The response tells them that install's block and
--      premium status, and the update overwrites its name and version.
--
-- What it CANNOT do, and this is the part that was built right: `checkin` never writes `blocked`,
-- so a blocked install cannot unblock itself; it never writes `premium`, so nobody can grant
-- themselves paid features; and RLS with no policies means the key cannot read the table at all.
-- The damage is abuse and noise, not privilege escalation or data theft.
--
-- WHAT THIS ADDS
--
--   - A platform whitelist. An unknown tag is refused, so the panel only ever shows real apps.
--   - Per-address rate limiting on *new* installs. An existing install may check in as often as it
--     likes — that is normal and must stay cheap — but one address may only bring twenty NEW ids
--     into existence per hour. A real person crosses that never; a flooding script dies at twenty.

create table if not exists checkin_attempts (
  addr inet        not null,
  at   timestamptz not null default now()
);

create index if not exists checkin_attempts_addr_at on checkin_attempts (addr, at desc);

alter table checkin_attempts enable row level security;
-- No policies: the anon key must not read this either.

create or replace function checkin(p_id text, p_name text, p_version text, p_platform text)
returns table (blocked boolean, reason text, code text, until timestamptz,
               latest_version text, update_url text, update_note text)
language plpgsql security definer set search_path = public as $$
declare
  cfg      app_config;
  dev      devices;
  v_addr   inet := coalesce(inet_client_addr(), '0.0.0.0'::inet);
  v_exists boolean;
begin
  -- An unrecognised platform is refused outright. Add new apps here as they ship; leaving it open
  -- is what let an arbitrary string become a heading in the admin panel.
  if p_platform is null or p_platform not in ('android', 'truevault-android', 'copyeye', 'pc', 'web') then
    raise exception 'unknown platform';
  end if;

  select exists(select 1 from devices where id = p_id) into v_exists;

  -- Only the creation of new rows is limited. An install that already exists checks in freely,
  -- which is the common path and has to stay fast.
  if not v_exists then
    delete from checkin_attempts where at < now() - interval '1 day';

    if (select count(*) from checkin_attempts
        where addr = v_addr and at > now() - interval '1 hour') >= 20 then
      raise exception 'rate limited';
    end if;

    insert into checkin_attempts (addr) values (v_addr);
  end if;

  select * into cfg from app_config where id = 1;

  insert into devices (id, name, version, platform)
  values (p_id, left(coalesce(p_name, ''), 40), left(coalesce(p_version, ''), 20), p_platform)
  on conflict (id) do update
    set name = excluded.name, version = excluded.version,
        platform = excluded.platform, last_seen = now();

  update devices set blocked = false, block_reason = null, block_code = null, blocked_until = null
    where id = p_id and blocked and blocked_until is not null and blocked_until < now();

  select * into dev from devices where id = p_id;

  return query select
    (cfg.kill or dev.blocked),
    case when cfg.kill then 'Service temporarily unavailable' else dev.block_reason end,
    case when cfg.kill then '503' else dev.block_code end,
    dev.blocked_until,
    cfg.latest_version, cfg.update_url, cfg.update_note;
end $$;

grant execute on function checkin(text,text,text,text) to anon;

-- ------------------------------------------------------------------------------------------------
-- Clean up anything the old, unlimited version let through.
-- Check first, then delete — do not run the delete blind.
-- ------------------------------------------------------------------------------------------------

-- select platform, count(*) from devices group by platform order by count(*) desc;
-- delete from devices where platform not in ('android','truevault-android','copyeye','pc','web');
