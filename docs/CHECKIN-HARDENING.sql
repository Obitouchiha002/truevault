-- Hardening for `checkin`, the one function the public anon key can still call.
-- Run in the Supabase SQL editor. Safe to run more than once.
--
-- IF YOU RAN AN EARLIER VERSION OF THIS FILE, RUN THIS ONE — it repairs the damage.
--
-- The earlier version's `checkin` returned seven columns and omitted `premium`. The live function
-- returns eight. `create or replace` cannot change a function's return type, so instead of
-- replacing the real one it left two `checkin` overloads in place, and PostgREST could no longer
-- tell them apart — every app's check-in started failing with PGRST203. The apps fail open, so
-- nothing crashed, but no check-in reached the database: the admin panel stopped updating and
-- blocks stopped propagating.
--
-- This version drops every `checkin` overload first, by introspection, then creates exactly one.
-- That is why it is safe to run repeatedly and safe regardless of how many overloads exist now.
--
-- THE VULNERABILITY IT ALSO CLOSES
--
-- The anon key ships in every APK. It can call `checkin`, and must — that is how an app registers.
-- But the original accepted anything:
--   1. Any p_id, unlimited times → a loop fills the devices table until the quota is gone, and the
--      admin panel is unusable long before that. Cheapest and most damaging attack here.
--   2. Any p_platform → becomes a group heading in the panel (escaped, so not XSS, but fake apps in
--      the screen you make decisions from).
-- What it never could do, and still cannot: write `blocked` or `premium`. A blocked install cannot
-- unblock itself and nobody can grant themselves premium. The exposure is abuse, not data theft.

-- ------------------------------------------------------------------------------------------------
-- 1. Drop every existing checkin overload, whatever their signatures are.
-- ------------------------------------------------------------------------------------------------
do $$
declare f record;
begin
  for f in
    select p.oid::regprocedure as sig
    from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = 'checkin'
  loop
    execute format('drop function %s', f.sig);
    raise notice 'dropped: %', f.sig;
  end loop;
end $$;

-- ------------------------------------------------------------------------------------------------
-- 2. The rate-limit ledger.
-- ------------------------------------------------------------------------------------------------
create table if not exists checkin_attempts (
  addr inet        not null,
  at   timestamptz not null default now()
);
create index if not exists checkin_attempts_addr_at on checkin_attempts (addr, at desc);
alter table checkin_attempts enable row level security;
-- No policies: the anon key must not read this either.

-- ------------------------------------------------------------------------------------------------
-- 3. One checkin, returning all EIGHT columns the apps expect — premium included.
-- ------------------------------------------------------------------------------------------------
create function checkin(p_id text, p_name text, p_version text, p_platform text)
returns table (blocked boolean, reason text, code text, until timestamptz,
               premium boolean, latest_version text, update_url text, update_note text)
language plpgsql security definer set search_path = public as $$
#variable_conflict use_column
declare
  cfg      app_config;
  dev      devices;
  v_addr   inet := coalesce(inet_client_addr(), '0.0.0.0'::inet);
  v_exists boolean;
begin
  -- Whitelist. An unrecognised platform is refused, so only real apps ever reach the panel.
  -- Add new apps here as they ship.
  if p_platform is null or p_platform not in
       ('android', 'truevault-android', 'copyeye', 'pc', 'web') then
    raise exception 'unknown platform';
  end if;

  select exists(select 1 from devices where id = p_id) into v_exists;

  -- Only the creation of NEW rows is limited. An install that already exists checks in as often as
  -- it likes — that is the common path and must stay cheap. One address may mint 20 new ids an
  -- hour; a real person never reaches that, a flooding script dies there.
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

  -- Expire a timed ban whose clock has run out.
  update devices set blocked = false, block_reason = null, block_code = null, blocked_until = null
    where id = p_id and blocked and blocked_until is not null and blocked_until < now();

  select * into dev from devices where id = p_id;

  return query select
    (cfg.kill or dev.blocked),
    case when cfg.kill then 'Service temporarily unavailable' else dev.block_reason end,
    case when cfg.kill then '503' else dev.block_code end,
    dev.blocked_until,
    dev.premium,
    cfg.latest_version, cfg.update_url, cfg.update_note;
end $$;

grant execute on function checkin(text,text,text,text) to anon;

-- ------------------------------------------------------------------------------------------------
-- 4. Optional cleanup of rows the old unlimited version let through.
--    Check first, then delete — never run the delete blind.
-- ------------------------------------------------------------------------------------------------
-- select platform, count(*) from devices group by platform order by count(*) desc;
-- delete from devices where platform not in ('android','truevault-android','copyeye','pc','web');
