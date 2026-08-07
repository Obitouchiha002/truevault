# Admin backend — install list, block, premium, kill switch

This is the same design StreamGarden uses, ported to TrueVault, with a `premium` flag added.

**Read this section before setting it up.** It changes what the app is.

Without a backend, TrueVault declares no internet permission, contains no network code, and collects
nothing — and the website, the Play Data Safety form and the privacy policy all say so. Turning this
on makes every one of those statements false until they are rewritten, and they have to be rewritten
before the build ships. A privacy app whose privacy policy is out of date is worse than one that
never claimed anything.

It also means you can lock a person out of their own encrypted files. The vault is on their device
and the data is never destroyed by a block, but the app that opens it will refuse to run. Decide
deliberately that you want that power before you enable this.

The build is unaffected until you create `supabase.properties`. With no such file the network code
is compiled out, `INTERNET` is stripped from the merged manifest, and the app behaves exactly as it
does today. That is the default.

> **This controls the app, not the device.** It can stop *this app* from running and show a message.
> It cannot read files, see the vault contents, track location, or use the camera or microphone —
> there are no such permissions in the manifest, with or without a backend.

## What it does

- **Check-in.** On launch and on returning to the foreground, the app calls `checkin` with its
  Android ID, the name the user typed on first launch, and its version. That registers the install
  and returns its status.
- **Fails open.** If the backend is unreachable the app keeps working. A vault whose owner is on a
  plane must still open. The last *known* status is cached, so a device you blocked stays blocked
  when it goes offline — but a device that has never been blocked is never held hostage by a network
  error.
- **Block / unblock**, timed or permanent, with a reason and code shown on the block screen.
- **Premium**, a per-install flag the app can read.
- **Update notice**: set a latest version and link; older installs see a banner.
- **Kill switch**: one flag suspends every install at once.

Admin actions are authorised by a **PIN that lives only in your Supabase database** and is checked
inside each SQL function. The app ships with the *public* anon key only — never the PIN.

## Reaching the admin panel

No visible button. Tap the **top-left corner of the notes screen 5 times**, then enter the PIN. A
wrong PIN does nothing at all — no error, no shake, no hint that a panel exists.

## One-time setup

1. Create a free project at [supabase.com](https://supabase.com).
2. **Project Settings → API**: copy the **Project URL** and the **anon public** key into
   `supabase.properties` at the repository root (it is gitignored — see
   `supabase.properties.example`):

   ```properties
   supabaseUrl=https://YOUR-PROJECT.supabase.co
   supabaseAnonKey=eyJhbGciOi...
   ```

3. **SQL Editor → New query**, paste the script below, **change the PIN**, and run it.

```sql
-- One row per install.
create table if not exists installs (
  id            text primary key,          -- Android ID; survives reinstall
  name          text,                      -- whatever the user typed on first launch
  version       text,
  blocked       boolean not null default false,
  block_reason  text,
  block_code    text,
  blocked_until timestamptz,               -- null = permanent while blocked
  premium       boolean not null default false,
  first_seen    timestamptz not null default now(),
  last_seen     timestamptz not null default now()
);

-- Global config: the admin PIN, kill switch, and update notice.
create table if not exists app_config (
  id             int primary key default 1,
  admin_pin      text not null,
  kill           boolean not null default false,
  latest_version text,
  update_url     text,
  update_note    text
);

-- ⚠️ CHANGE THIS PIN before running. Six digits is guessable; use something long and random.
insert into app_config (id, admin_pin) values (1, 'CHANGE_ME')
  on conflict (id) do nothing;

alter table installs   enable row level security;
alter table app_config enable row level security;
-- No policies, deliberately. With RLS on and no policy, the anon key cannot read or write
-- either table directly. The only way in is the SECURITY DEFINER functions below, and three
-- of the four demand the PIN. This is the whole security model — do not add a policy.

-- Register or refresh an install and return its status. No PIN: anyone may check in.
create or replace function checkin(p_id text, p_name text, p_version text)
returns table (blocked boolean, reason text, code text, until timestamptz,
               premium boolean, latest_version text, update_url text, update_note text)
language plpgsql security definer set search_path = public as $$
declare cfg app_config; ins installs;
begin
  select * into cfg from app_config where id = 1;

  insert into installs (id, name, version)
  values (p_id, p_name, p_version)
  on conflict (id) do update
    set name = excluded.name, version = excluded.version, last_seen = now();

  -- Expire a timed ban.
  update installs set blocked = false, block_reason = null, block_code = null, blocked_until = null
    where id = p_id and blocked and blocked_until is not null and blocked_until < now();

  select * into ins from installs where id = p_id;

  return query select
    (cfg.kill or ins.blocked),
    case when cfg.kill then 'Service temporarily unavailable' else ins.block_reason end,
    case when cfg.kill then '503' else ins.block_code end,
    ins.blocked_until,
    ins.premium,
    cfg.latest_version, cfg.update_url, cfg.update_note;
end $$;

-- Admin: list installs (PIN required).
create or replace function admin_installs(p_pin text)
returns setof installs
language plpgsql security definer set search_path = public as $$
begin
  if p_pin <> (select admin_pin from app_config where id = 1) then
    raise exception 'unauthorized';
  end if;
  return query select * from installs order by last_seen desc;
end $$;

-- Admin: block or unblock, optionally timed, with a reason and code (PIN required).
create or replace function admin_block(p_pin text, p_id text, p_blocked boolean,
                                       p_reason text, p_minutes int, p_code text)
returns void
language plpgsql security definer set search_path = public as $$
begin
  if p_pin <> (select admin_pin from app_config where id = 1) then
    raise exception 'unauthorized';
  end if;
  update installs set
    blocked = p_blocked,
    block_reason  = case when p_blocked then p_reason else null end,
    block_code    = case when p_blocked then p_code   else null end,
    blocked_until = case when p_blocked and p_minutes is not null
                         then now() + make_interval(mins => p_minutes) else null end
  where id = p_id;
end $$;

-- Admin: grant or revoke premium (PIN required).
create or replace function admin_premium(p_pin text, p_id text, p_premium boolean)
returns void
language plpgsql security definer set search_path = public as $$
begin
  if p_pin <> (select admin_pin from app_config where id = 1) then
    raise exception 'unauthorized';
  end if;
  update installs set premium = p_premium where id = p_id;
end $$;

-- Admin: kill switch and update notice (PIN required).
create or replace function admin_config(p_pin text, p_kill boolean,
                                        p_latest text, p_url text, p_note text)
returns void
language plpgsql security definer set search_path = public as $$
begin
  if p_pin <> (select admin_pin from app_config where id = 1) then
    raise exception 'unauthorized';
  end if;
  update app_config set kill = p_kill, latest_version = p_latest,
                        update_url = p_url, update_note = p_note where id = 1;
end $$;

-- The anon key may call these five functions and nothing else.
grant execute on function checkin(text,text,text)                       to anon;
grant execute on function admin_installs(text)                          to anon;
grant execute on function admin_block(text,text,boolean,text,int,text)  to anon;
grant execute on function admin_premium(text,text,boolean)              to anon;
grant execute on function admin_config(text,boolean,text,text,text)     to anon;
```

## Security notes

- The **anon key is public by design** — it is embedded in every copy of the app and anyone can
  extract it. That is safe *only* because RLS is on with no policies, so the key cannot read or
  write the tables. It can call the five functions above, and four of them demand the PIN. If you
  ever add an RLS policy to `installs`, the anon key immediately becomes a full read of every
  install record. Do not.
- **The PIN is the only admin secret.** Because the anon key is public, anyone can script guesses
  against `admin_installs`. Six digits falls in minutes. Use a long random string, and rotate it by
  updating the `app_config` row.
- Blocking uses the **Android ID**, which survives reinstall, so a blocked user cannot escape by
  reinstalling; only a factory reset changes it. It is an opaque identifier and nothing else about
  the device is collected — no model, no manufacturer, no location, no contacts.
- **What is now collected**, and what the privacy policy must therefore say: an install identifier,
  the name the user typed, the app version, and a first-seen and last-seen timestamp. Nothing about
  the vault, its contents, its size, or what the user does in the app is ever sent.

## Before you ship a build with this enabled

- [ ] `legal/privacy-policy.md` rewritten: it currently states no data is collected
- [ ] `legal/play-data-safety-map.md` rewritten: currently declares "no data collected"
- [ ] `web/index.html` — the "0 data collected", "1 permission" and "no internet permission" claims
- [ ] `web/privacy-policy.html` regenerated from the markdown
- [ ] In-app copy that repeats the no-network claim
- [ ] `README.md` permissions table

`scripts/check-no-network-claims.sh` fails the build if `supabase.properties` exists while any of
those still claim otherwise.
