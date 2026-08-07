-- Optional: the two actions in the mock-up that the current schema cannot do.
-- Run in the Supabase SQL editor only if you want them.

-- ------------------------------------------------------------------------------------------------
-- Delete an install row.
--
-- Worth understanding before you use it: this removes the record, not the app, and the Android ID
-- that identified it survives a reinstall. So deleting a *blocked* install unblocks it — the device
-- checks in again, finds no row, and is registered fresh with a clean status. Delete is for tidying
-- up test rows, not for punishing anyone; blocking is what stops an install.
-- ------------------------------------------------------------------------------------------------

create or replace function admin_delete_srv(p_id text)
returns void
language plpgsql security definer set search_path = public as $$
begin
  delete from devices where id = p_id;
end $$;

revoke all on function admin_delete_srv(text) from anon, public;
grant execute on function admin_delete_srv(text) to service_role;

-- ------------------------------------------------------------------------------------------------
-- A message to one install.
--
-- The app has to be taught to read and show this before it does anything — adding the column does
-- not make a message appear on anyone's phone. TrueVault's checkin would need to return it and the
-- app to render it; StreamGarden and CopyEye the same. Until then this stores text nobody sees.
-- ------------------------------------------------------------------------------------------------

alter table devices add column if not exists admin_message text;
alter table devices add column if not exists admin_message_at timestamptz;

create or replace function admin_message_srv(p_id text, p_message text)
returns void
language plpgsql security definer set search_path = public as $$
begin
  update devices
     set admin_message = nullif(p_message, ''),
         admin_message_at = case when nullif(p_message, '') is null then null else now() end
   where id = p_id;
end $$;

revoke all on function admin_message_srv(text,text) from anon, public;
grant execute on function admin_message_srv(text,text) to service_role;
