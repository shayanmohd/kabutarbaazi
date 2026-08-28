-- Functions, triggers and RPCs.
--
-- Every helper below is SECURITY DEFINER and STABLE, and both matter:
--   SECURITY DEFINER  a plain `exists (select 1 from blocks ...)` inside a policy on listings
--                     would itself be RLS-filtered and recurse.
--   STABLE            lets Postgres evaluate once per statement instead of once per row.
-- Policies also call (select auth.uid()) rather than bare auth.uid(), which is the documented
-- Supabase pattern: the bare call is re-evaluated per row and visibly slows a 500-row feed.

-- ---------------------------------------------------------------------------------------------
-- helpers
-- ---------------------------------------------------------------------------------------------
create or replace function public.is_blocked_with(other uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from blocks
     where (blocker_id = (select auth.uid()) and blocked_id = other)
        or (blocker_id = other and blocked_id = (select auth.uid()))
  );
$$;

create or replace function public.is_admin() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from profiles where id = (select auth.uid()) and role = 'admin');
$$;

-- The database-level enforcement of Play's "accept terms before uploading" requirement.
-- A modified client cannot bypass this because it is in the WITH CHECK of every content insert.
create or replace function public.has_accepted_terms() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from profiles
     where id = (select auth.uid())
       and terms_accepted_at is not null
       and not is_suspended
  );
$$;

-- ---------------------------------------------------------------------------------------------
-- signup: profile and contact are created in the same transaction as the auth user
-- ---------------------------------------------------------------------------------------------
-- search_path includes `extensions` because Supabase installs pgcrypto there, not in public.
-- With search_path = public alone, hmac() below resolves at creation time but fails at signup,
-- which is the worst possible place to discover it.
create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = public, extensions as $$
declare
  v_username text := lower(trim(new.raw_user_meta_data->>'username'));
  v_display  text := coalesce(nullif(trim(new.raw_user_meta_data->>'display_name'), ''), v_username);
  v_phone    text := trim(new.raw_user_meta_data->>'phone_e164');
  v_region   text := nullif(trim(new.raw_user_meta_data->>'region_code'), '');
  v_locale   text := coalesce(nullif(trim(new.raw_user_meta_data->>'locale'), ''), 'hi');
begin
  -- Re-validate server-side. The client checks these too, but the client is not a boundary.
  if v_username is null or v_username !~ '^[a-z][a-z0-9._]{2,19}$' then
    raise exception 'invalid_username';
  end if;
  if v_phone is null or v_phone !~ '^\+[1-9][0-9]{7,14}$' then
    raise exception 'invalid_phone';
  end if;

  insert into profiles (id, username, display_name, region_code, locale)
  values (new.id, v_username, v_display, v_region, v_locale);

  insert into private_contacts (user_id, phone_e164, phone_hash)
  values (
    new.id,
    v_phone,
    hmac(v_phone, coalesce(current_setting('app.phone_pepper', true), 'dev-pepper'), 'sha256')
  );

  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------------------------
-- counter triggers. SECURITY DEFINER so they can write columns the caller cannot.
-- ---------------------------------------------------------------------------------------------
create or replace function public.bump_counter() returns trigger
language plpgsql security definer set search_path = public as $$
declare
  delta int := case when TG_OP = 'INSERT' then 1 else -1 end;
  rec   record := case when TG_OP = 'INSERT' then new else old end;
begin
  case TG_ARGV[0]
    when 'reel_like'    then update reels set like_count = like_count + delta where id = rec.reel_id;
    when 'reel_comment' then update reels set comment_count = comment_count + delta where id = rec.reel_id;
    when 'post_like'    then update community_posts set like_count = like_count + delta where id = rec.post_id;
    when 'post_comment' then update community_posts set comment_count = comment_count + delta where id = rec.post_id;
    when 'saved'        then update listings set saved_count = saved_count + delta where id = rec.listing_id;
    when 'member'       then update communities set member_count = member_count + delta where id = rec.community_id;
    when 'post'         then update communities set post_count = post_count + delta where id = rec.community_id;
    when 'follow'       then
      update profiles set following_count = following_count + delta where id = rec.follower_id;
      update profiles set follower_count  = follower_count  + delta where id = rec.followee_id;
  end case;
  return null;
end;
$$;

create trigger trg_reel_likes    after insert or delete on reel_likes            for each row execute function bump_counter('reel_like');
create trigger trg_reel_comments after insert or delete on reel_comments         for each row execute function bump_counter('reel_comment');
create trigger trg_post_likes    after insert or delete on community_post_likes  for each row execute function bump_counter('post_like');
create trigger trg_post_comments after insert or delete on community_post_comments for each row execute function bump_counter('post_comment');
create trigger trg_saved         after insert or delete on saved_listings        for each row execute function bump_counter('saved');
create trigger trg_members       after insert or delete on community_members     for each row execute function bump_counter('member');
create trigger trg_posts         after insert or delete on community_posts       for each row execute function bump_counter('post');
create trigger trg_follows       after insert or delete on follows               for each row execute function bump_counter('follow');

-- Message insert updates the conversation summary so the list needs no join.
create or replace function public.on_message_insert() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  update conversations
     set last_message_at = new.created_at,
         last_message_preview = left(new.body, 120),
         last_sender_id = new.sender_id
   where id = new.conversation_id;
  return null;
end;
$$;
create trigger trg_message_insert after insert on messages
  for each row execute function on_message_insert();

-- Listing media cap, enforced where it cannot be bypassed.
create or replace function public.enforce_listing_media_limit() returns trigger
language plpgsql security definer set search_path = public as $$
declare n_img int; n_vid int;
begin
  select count(*) filter (where kind='image'), count(*) filter (where kind='video')
    into n_img, n_vid from listing_media where listing_id = new.listing_id;
  if new.kind = 'image' and n_img >= 6 then raise exception 'too_many_images'; end if;
  if new.kind = 'video' and n_vid >= 1 then raise exception 'too_many_videos'; end if;
  return new;
end;
$$;
create trigger trg_listing_media_limit before insert on listing_media
  for each row execute function enforce_listing_media_limit();

-- ---------------------------------------------------------------------------------------------
-- RPCs
-- ---------------------------------------------------------------------------------------------

create or replace function public.username_available(u text) returns boolean
language sql stable security definer set search_path = public as $$
  select not exists (select 1 from profiles where username = lower(trim(u)));
$$;

create or replace function public.accept_terms() returns void
language sql security definer set search_path = public as $$
  update profiles set terms_accepted_at = now(), updated_at = now()
   where id = (select auth.uid()) and terms_accepted_at is null;
$$;

-- Idempotent. Two clients racing to open the same thread both get the same row back.
create or replace function public.start_conversation(other uuid, listing uuid default null)
returns uuid
language plpgsql security definer set search_path = public as $$
declare
  me uuid := (select auth.uid());
  a uuid; b uuid; conv uuid;
begin
  if me is null then raise exception 'not_authenticated'; end if;
  if me = other then raise exception 'cannot_message_self'; end if;
  if not has_accepted_terms() then raise exception 'terms_not_accepted'; end if;
  if is_blocked_with(other) then raise exception 'blocked'; end if;

  a := least(me, other);
  b := greatest(me, other);

  insert into conversations (user_a, user_b, listing_id)
  values (a, b, listing)
  on conflict (user_a, user_b)
    do update set listing_id = coalesce(conversations.listing_id, excluded.listing_id)
  returning id into conv;

  return conv;
end;
$$;

create or replace function public.mark_conversation_read(conv uuid) returns void
language plpgsql security definer set search_path = public as $$
declare me uuid := (select auth.uid());
begin
  update conversations
     set a_last_read_at = case when user_a = me then now() else a_last_read_at end,
         b_last_read_at = case when user_b = me then now() else b_last_read_at end
   where id = conv and me in (user_a, user_b);
end;
$$;

-- The ONLY path to a phone number. Every guard here is load-bearing: without them the
-- "hidden until the buyer taps WhatsApp" promise in the privacy policy is not true.
create or replace function public.get_seller_whatsapp(listing uuid) returns text
language plpgsql security definer set search_path = public as $$
declare
  me uuid := (select auth.uid());
  v_seller uuid;
  v_phone text;
  v_today int;
begin
  if me is null then raise exception 'not_authenticated'; end if;

  select seller_id into v_seller
    from listings
   where id = listing and is_hidden = false and status = 'active';
  if v_seller is null then raise exception 'listing_not_available'; end if;

  if is_blocked_with(v_seller) then raise exception 'blocked'; end if;

  select count(*) into v_today
    from contact_reveals
   where viewer_id = me and created_at > now() - interval '1 day';
  if v_today >= 30 then raise exception 'rate_limited'; end if;

  select phone_e164 into v_phone from private_contacts where user_id = v_seller;
  if v_phone is null then raise exception 'no_contact'; end if;

  insert into contact_reveals (listing_id, viewer_id, seller_id) values (listing, me, v_seller);
  return v_phone;
end;
$$;

-- Reports. Resolves the owner, snapshots their username, dedupes, and applies auto-hide inside
-- the same transaction so a third report makes content unselectable immediately rather than
-- after some batch job runs.
create or replace function public.submit_report(
  p_target_type report_target_type,
  p_target_id   uuid,
  p_reason      report_reason,
  p_note        text default null
) returns void
language plpgsql security definer set search_path = public as $$
declare
  me uuid := (select auth.uid());
  v_owner uuid;
  v_owner_username text;
  v_owner_is_admin boolean := false;
  v_distinct int;
  v_reporter_age interval;
  -- Mirrored in :domain ModerationRules.AUTO_HIDE_THRESHOLD. Change both in one commit.
  c_threshold constant int := 3;
begin
  if me is null then raise exception 'not_authenticated'; end if;

  case p_target_type
    when 'listing'      then select seller_id from listings              where id = p_target_id into v_owner;
    when 'reel'         then select author_id from reels                 where id = p_target_id into v_owner;
    when 'reel_comment' then select author_id from reel_comments         where id = p_target_id into v_owner;
    when 'post'         then select author_id from community_posts       where id = p_target_id into v_owner;
    when 'post_comment' then select author_id from community_post_comments where id = p_target_id into v_owner;
    when 'message'      then select sender_id from messages              where id = p_target_id into v_owner;
    when 'user'         then select id        from profiles              where id = p_target_id into v_owner;
  end case;

  if v_owner is null then raise exception 'target_not_found'; end if;
  if v_owner = me then raise exception 'cannot_report_self'; end if;

  select username, role = 'admin' into v_owner_username, v_owner_is_admin
    from profiles where id = v_owner;

  insert into reports (reporter_id, target_type, target_id, target_owner_id,
                       target_owner_username, reason, note)
  values (me, p_target_type, p_target_id, v_owner, v_owner_username, p_reason, p_note)
  on conflict (reporter_id, target_type, target_id) do nothing;

  -- Reports from accounts younger than a day are filed but do not count toward auto-hide.
  -- Combined with one-account-per-phone this makes brigading a competitor expensive.
  select count(distinct r.reporter_id) into v_distinct
    from reports r
    join profiles p on p.id = r.reporter_id
   where r.target_type = p_target_type
     and r.target_id = p_target_id
     and p.created_at < now() - interval '24 hours';

  if v_distinct >= c_threshold and not v_owner_is_admin then
    case p_target_type
      when 'listing'      then update listings                set is_hidden = true, hidden_reason = 'auto_reports' where id = p_target_id;
      when 'reel'         then update reels                   set is_hidden = true, hidden_reason = 'auto_reports' where id = p_target_id;
      when 'reel_comment' then update reel_comments           set is_hidden = true, hidden_reason = 'auto_reports' where id = p_target_id;
      when 'post'         then update community_posts         set is_hidden = true, hidden_reason = 'auto_reports' where id = p_target_id;
      when 'post_comment' then update community_post_comments set is_hidden = true, hidden_reason = 'auto_reports' where id = p_target_id;
      when 'message'      then update messages                set is_hidden = true where id = p_target_id;
      when 'user'         then null;
    end case;
  end if;

  -- Keep the denormalised count roughly in step for the admin queue ordering.
  case p_target_type
    when 'listing'      then update listings                set report_count = report_count + 1 where id = p_target_id;
    when 'reel'         then update reels                   set report_count = report_count + 1 where id = p_target_id;
    when 'reel_comment' then update reel_comments           set report_count = report_count + 1 where id = p_target_id;
    when 'post'         then update community_posts         set report_count = report_count + 1 where id = p_target_id;
    when 'post_comment' then update community_post_comments set report_count = report_count + 1 where id = p_target_id;
    else null;
  end case;
end;
$$;

create or replace function public.admin_set_hidden(
  p_target_type report_target_type, p_target_id uuid, p_hidden boolean, p_reason text default null
) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not is_admin() then raise exception 'not_admin'; end if;
  case p_target_type
    when 'listing'      then update listings                set is_hidden = p_hidden, hidden_reason = p_reason where id = p_target_id;
    when 'reel'         then update reels                   set is_hidden = p_hidden, hidden_reason = p_reason where id = p_target_id;
    when 'reel_comment' then update reel_comments           set is_hidden = p_hidden, hidden_reason = p_reason where id = p_target_id;
    when 'post'         then update community_posts         set is_hidden = p_hidden, hidden_reason = p_reason where id = p_target_id;
    when 'post_comment' then update community_post_comments set is_hidden = p_hidden, hidden_reason = p_reason where id = p_target_id;
    when 'message'      then update messages                set is_hidden = p_hidden where id = p_target_id;
    else raise exception 'unsupported_target';
  end case;
  insert into audit_actions (admin_id, action, target_type, target_id)
  values ((select auth.uid()), case when p_hidden then 'hide' else 'unhide' end, p_target_type, p_target_id);
end;
$$;

create or replace function public.admin_suspend_user(p_user uuid, p_suspended boolean) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not is_admin() then raise exception 'not_admin'; end if;
  update profiles set is_suspended = p_suspended where id = p_user;
  insert into audit_actions (admin_id, action, target_type, target_id)
  values ((select auth.uid()), case when p_suspended then 'suspend' else 'unsuspend' end, 'user', p_user);
end;
$$;

create or replace function public.resolve_report(p_report uuid, p_status report_status) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not is_admin() then raise exception 'not_admin'; end if;
  update reports set status = p_status, resolved_by = (select auth.uid()), resolved_at = now()
   where id = p_report;
end;
$$;

-- Reels from people you follow. Keyset paginated: OFFSET would duplicate rows as new reels land.
create or replace function public.feed_following(before timestamptz default null, lim int default 10)
returns setof reels
language sql stable security definer set search_path = public as $$
  select r.* from reels r
   where r.is_hidden = false
     and r.author_id in (select followee_id from follows where follower_id = (select auth.uid()))
     and not is_blocked_with(r.author_id)
     and (before is null or r.created_at < before)
   order by r.created_at desc
   limit least(coalesce(lim, 10), 30);
$$;
