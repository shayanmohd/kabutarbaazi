-- Chat push delivery.
--
-- The notify-message Edge Function verifies a shared secret header, so this trigger has to send
-- it. The real value lives in supabase/.env.local (gitignored) and in the project's Edge Function
-- secrets as WEBHOOK_SECRET. Applying this file means substituting it:
--
--   sed "s/__WEBHOOK_SECRET__/$WEBHOOK_SECRET/" supabase/migrations/0007_push_webhook.sql | psql ...
--
-- Supabase's dashboard "Database Webhooks" feature writes an equivalent trigger; this is the
-- same thing kept in version control, and it also enables pg_net, which the feature does for you.
create extension if not exists pg_net;

create or replace function public.notify_message_push() returns trigger
language plpgsql security definer set search_path = public, net, extensions as $$
begin
  perform net.http_post(
    url     := 'https://lhlrhgocjuwmxwbjsbch.supabase.co/functions/v1/notify-message',
    body    := jsonb_build_object('record', to_jsonb(new)),
    headers := jsonb_build_object(
                 'Content-Type', 'application/json',
                 'x-webhook-secret', '__WEBHOOK_SECRET__'
               )
  );
  return null;
end;
$$;

-- Deliberately a second trigger rather than an addition to on_message_insert(): a failing or slow
-- HTTP call must never roll back or hold up the message write itself.
drop trigger if exists trg_message_push on public.messages;
create trigger trg_message_push after insert on public.messages
  for each row execute function public.notify_message_push();
