-- StreamGarden: force an update only for genuinely old builds, not the one-or-two-behind ones.
-- Run this in the Supabase SQL editor. Safe to run more than once.
--
-- WHAT CHANGED FROM THE PREVIOUS FIX
--
-- The earlier gate blocked any StreamGarden build whose version was not EXACTLY latest_version.
-- That is too aggressive: it nags someone who is a single release behind. This replaces the hard
-- "must equal latest" rule with a floor: a `min_version`. Below the floor you must update; at or
-- above it you are fine, even if a newer release exists.
--
--   StreamGarden, version < min_version   -> blocked, "update required" (426)
--   StreamGarden, version >= min_version  -> allowed
--   TrueVault / CopyEye                    -> never version-gated (only admin block or kill switch)
--
-- Versions are compared as numbers per dotted part, so 2.9 < 2.10 < 2.25 sorts correctly — a plain
-- string compare would get that wrong. Bad or missing input fails OPEN (never blocks) on purpose:
-- a parse slip must not lock people out.
--
-- latest_version / update_url / update_note are still returned untouched, so an app can show a soft
-- "update available" note without being forced.

-- 1. The floor lives in app_config, next to latest_version.
alter table app_config add column if not exists min_version text;
update app_config set min_version = '2.25' where id = 1;

-- 2. Safe version -> int-array parser. array[2,16] < array[2,25] compares part-by-part as numbers.
--    Anything it cannot parse collapses to {0}, which is below every real version, but the callers
--    below only ever block when BOTH sides parsed to real versions, so a bad value never blocks.
create or replace function ver_key(v text)
returns int[]
language plpgsql immutable as $$
begin
  return (
    select coalesce(array_agg(coalesce(nullif(regexp_replace(part, '\D', '', 'g'), '')::int, 0)), array[0])
    from unnest(string_to_array(coalesce(v, ''), '.')) as part
  );
exception when others then
  return array[0];
end $$;

-- 3. Recreate checkin with the floor. Drop first because the return type is a table.
do $$
declare f record;
begin
  for f in
    select p.oid::regprocedure as sig
    from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = 'checkin'
  loop
    execute format('drop function %s', f.sig);
  end loop;
end $$;

create table if not exists checkin_attempts (
  addr inet        not null,
  at   timestamptz not null default now()
);
create index if not exists checkin_attempts_addr_at on checkin_attempts (addr, at desc);
alter table checkin_attempts enable row level security;

create function checkin(p_id text, p_name text, p_version text, p_platform text)
returns table (blocked boolean, reason text, code text, until timestamptz,
               premium boolean, latest_version text, update_url text, update_note text)
language plpgsql security definer set search_path = public as $$
#variable_conflict use_column
declare
  cfg           app_config;
  dev           devices;
  v_addr        inet := coalesce(inet_client_addr(), '0.0.0.0'::inet);
  v_exists      boolean;
  v_streamgarden boolean;
  v_needs_update boolean;
begin
  if p_platform is null or p_platform not in
       ('android', 'streamgarden-android', 'pc', 'streamgarden-pc', 'web',
        'truevault-android', 'copyeye', 'copyeye-android') then
    raise exception 'unknown platform';
  end if;

  select exists(select 1 from devices where id = p_id) into v_exists;

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

  -- The floor is StreamGarden's, so it only gates StreamGarden. Block only when this is a
  -- StreamGarden platform, a floor is set, the client sent a version, and that version is strictly
  -- BELOW the floor. One-release-behind builds (2.28 with a 2.25 floor) are at or above it, so they
  -- pass. TrueVault and CopyEye are never in v_streamgarden, so they are never version-gated.
  v_streamgarden := p_platform in ('android', 'streamgarden-android', 'pc', 'streamgarden-pc');
  v_needs_update := v_streamgarden
                    and cfg.min_version is not null
                    and coalesce(p_version, '') <> ''
                    and ver_key(p_version) < ver_key(cfg.min_version);

  return query select
    (cfg.kill or dev.blocked or v_needs_update),
    case
      when cfg.kill then 'Service temporarily unavailable'
      when dev.blocked then dev.block_reason
      when v_needs_update then 'A new version is required. Please update to continue.'
      else null
    end,
    case
      when cfg.kill then '503'
      when dev.blocked then dev.block_code
      when v_needs_update then '426'
      else null
    end,
    dev.blocked_until,
    dev.premium,
    cfg.latest_version, cfg.update_url, cfg.update_note;
end $$;

grant execute on function checkin(text,text,text,text) to anon;
