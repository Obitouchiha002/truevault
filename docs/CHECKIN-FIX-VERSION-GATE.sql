-- Fix: every TrueVault (and CopyEye) install shows "A new version is required" and is suspended,
-- even a fresh download. Run this in the Supabase SQL editor. Safe to run more than once.
--
-- THE CAUSE
--
-- The deployed checkin blocks any install whose version is not app_config.latest_version. That
-- value is "2.16" — StreamGarden's version. TrueVault sends "0.3.3", which is not "2.16", so the
-- server marks it "update required" (code 426) and returns blocked = true. It can never match,
-- because TrueVault and StreamGarden number their releases completely differently. One shared
-- version floor was being applied to three different apps.
--
-- THE FIX
--
-- Version-gating now applies ONLY to StreamGarden platforms, which is the app that wants it. Every
-- other app — TrueVault, CopyEye — is exempt: it is only ever blocked by an explicit admin block or
-- the global kill switch, never by StreamGarden's version number. The update fields are still
-- returned so an app can show its own "update available" note if it chooses, but the server no
-- longer forces it.
--
-- Drop-and-recreate because the return type is a table; create-or-replace cannot change it.

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

  -- The version floor is StreamGarden's, so it only gates StreamGarden. TrueVault and CopyEye have
  -- their own release numbers and no floor of their own; applying this one to them blocks every
  -- build forever, which is exactly the bug this fixes.
  v_streamgarden := p_platform in ('android', 'streamgarden-android', 'pc', 'streamgarden-pc');
  v_needs_update := v_streamgarden
                    and cfg.latest_version is not null
                    and p_version is distinct from cfg.latest_version;

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
