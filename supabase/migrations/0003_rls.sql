-- Row-level security.
--
-- The Android client talks straight to Postgres through PostgREST using the anon key, so these
-- policies ARE the security boundary. A wrong policy here is a data leak, not a 403.
--
-- Two rules keep it tractable:
--   1. Only the PARENT table carries the visibility predicate (is_hidden + block check). Every
--      child gates on `exists (select 1 from <parent> where id = <fk>)`, and that subquery is
--      itself RLS-filtered. So the rule exists once per entity and cannot drift: hide a reel and
--      its likes, comments and media all become unselectable in the same statement.
--   2. Deny by default. Every table has RLS enabled and no permissive fallback.

alter table profiles                enable row level security;
alter table private_contacts        enable row level security;
alter table regions                 enable row level security;
alter table breeds                  enable row level security;
alter table listings                enable row level security;
alter table listing_media           enable row level security;
alter table saved_listings          enable row level security;
alter table contact_reveals         enable row level security;
alter table reels                   enable row level security;
alter table reel_likes              enable row level security;
alter table reel_comments           enable row level security;
alter table communities             enable row level security;
alter table community_members       enable row level security;
alter table community_posts         enable row level security;
alter table community_post_media    enable row level security;
alter table community_post_likes    enable row level security;
alter table community_post_comments enable row level security;
alter table follows                 enable row level security;
alter table conversations           enable row level security;
alter table messages                enable row level security;
alter table blocks                  enable row level security;
alter table reports                 enable row level security;
alter table audit_actions           enable row level security;
alter table device_tokens           enable row level security;
alter table upload_grants           enable row level security;
alter table deletion_requests       enable row level security;

-- reference data is public
create policy regions_read on regions for select using (true);
create policy breeds_read  on breeds  for select using (true);

-- profiles -------------------------------------------------------------------------------------
create policy profiles_read on profiles for select
  using (id = (select auth.uid()) or is_admin() or (not is_suspended and not is_blocked_with(id)));
create policy profiles_update_own on profiles for update
  using (id = (select auth.uid())) with check (id = (select auth.uid()));
-- No insert policy: profiles are created only by the handle_new_user trigger.

-- Whitelist the columns a user may change on their own profile.
--
-- A column-level REVOKE is NOT sufficient on its own: holding table-level UPDATE overrides any
-- column-level revoke, so the table-level privilege must be dropped first and the permitted
-- columns granted back explicitly. Getting this wrong lets any user run
--   update profiles set role = 'admin' where id = auth.uid()
-- which then satisfies every is_admin() branch in every policy in this file.
revoke update on profiles from authenticated;
grant update (display_name, avatar_url, bio, region_code, city, locale)
  on profiles to authenticated;

-- private_contacts: the phone number. Owner and admin only, no exceptions. Everyone else must
-- go through get_seller_whatsapp(), which rate-limits and logs.
create policy contacts_read_own   on private_contacts for select using (user_id = (select auth.uid()) or is_admin());
create policy contacts_write_own  on private_contacts for insert with check (user_id = (select auth.uid()));
create policy contacts_update_own on private_contacts for update using (user_id = (select auth.uid()));

-- listings -------------------------------------------------------------------------------------
create policy listings_read on listings for select using (
  is_admin()
  or seller_id = (select auth.uid())
  or (not is_hidden and status <> 'removed' and not is_blocked_with(seller_id))
);
create policy listings_insert on listings for insert
  with check (seller_id = (select auth.uid()) and has_accepted_terms());
create policy listings_update_own on listings for update
  using (seller_id = (select auth.uid())) with check (seller_id = (select auth.uid()));
create policy listings_delete_own on listings for delete
  using (seller_id = (select auth.uid()) or is_admin());

create policy listing_media_read on listing_media for select
  using (exists (select 1 from listings l where l.id = listing_id));
create policy listing_media_insert on listing_media for insert
  with check (exists (select 1 from listings l where l.id = listing_id and l.seller_id = (select auth.uid())));
create policy listing_media_delete on listing_media for delete
  using (exists (select 1 from listings l where l.id = listing_id and l.seller_id = (select auth.uid())));

create policy saved_read   on saved_listings for select using (user_id = (select auth.uid()));
create policy saved_insert on saved_listings for insert
  with check (user_id = (select auth.uid()) and exists (select 1 from listings l where l.id = listing_id));
create policy saved_delete on saved_listings for delete using (user_id = (select auth.uid()));

create policy reveals_admin on contact_reveals for select using (is_admin());

-- reels ----------------------------------------------------------------------------------------
create policy reels_read on reels for select using (
  is_admin() or author_id = (select auth.uid())
  or (not is_hidden and not is_blocked_with(author_id))
);
create policy reels_insert on reels for insert
  with check (author_id = (select auth.uid()) and has_accepted_terms());
create policy reels_update_own on reels for update using (author_id = (select auth.uid()));
create policy reels_delete_own on reels for delete using (author_id = (select auth.uid()) or is_admin());

create policy reel_likes_read   on reel_likes for select using (user_id = (select auth.uid()));
create policy reel_likes_insert on reel_likes for insert
  with check (user_id = (select auth.uid()) and exists (select 1 from reels r where r.id = reel_id));
create policy reel_likes_delete on reel_likes for delete using (user_id = (select auth.uid()));

create policy reel_comments_read on reel_comments for select using (
  is_admin() or author_id = (select auth.uid())
  or (not is_hidden and not is_blocked_with(author_id)
      and exists (select 1 from reels r where r.id = reel_id))
);
create policy reel_comments_insert on reel_comments for insert with check (
  author_id = (select auth.uid()) and has_accepted_terms()
  and exists (select 1 from reels r where r.id = reel_id)
);
-- The reel's author can delete comments on their own reel, which is the minimum self-moderation
-- a creator needs.
create policy reel_comments_delete on reel_comments for delete using (
  author_id = (select auth.uid()) or is_admin()
  or exists (select 1 from reels r where r.id = reel_id and r.author_id = (select auth.uid()))
);

-- communities ----------------------------------------------------------------------------------
create policy communities_read on communities for select using (is_active or is_admin());

create policy members_read   on community_members for select
  using (exists (select 1 from communities c where c.id = community_id));
create policy members_join   on community_members for insert with check (user_id = (select auth.uid()));
create policy members_leave  on community_members for delete using (user_id = (select auth.uid()));

create policy posts_read on community_posts for select using (
  is_admin() or author_id = (select auth.uid())
  or (not is_hidden and not is_blocked_with(author_id)
      and exists (select 1 from communities c where c.id = community_id))
);
-- Posting requires membership, enforced here rather than in the UI.
create policy posts_insert on community_posts for insert with check (
  author_id = (select auth.uid()) and has_accepted_terms()
  and exists (select 1 from community_members m
               where m.community_id = community_id and m.user_id = (select auth.uid()))
);
create policy posts_update_own on community_posts for update using (author_id = (select auth.uid()));
create policy posts_delete_own on community_posts for delete using (author_id = (select auth.uid()) or is_admin());

create policy post_media_read on community_post_media for select
  using (exists (select 1 from community_posts p where p.id = post_id));
create policy post_media_insert on community_post_media for insert
  with check (exists (select 1 from community_posts p where p.id = post_id and p.author_id = (select auth.uid())));

create policy post_likes_read   on community_post_likes for select using (user_id = (select auth.uid()));
create policy post_likes_insert on community_post_likes for insert
  with check (user_id = (select auth.uid()) and exists (select 1 from community_posts p where p.id = post_id));
create policy post_likes_delete on community_post_likes for delete using (user_id = (select auth.uid()));

create policy post_comments_read on community_post_comments for select using (
  is_admin() or author_id = (select auth.uid())
  or (not is_hidden and not is_blocked_with(author_id)
      and exists (select 1 from community_posts p where p.id = post_id))
);
create policy post_comments_insert on community_post_comments for insert with check (
  author_id = (select auth.uid()) and has_accepted_terms()
  and exists (select 1 from community_posts p where p.id = post_id)
);
create policy post_comments_delete on community_post_comments for delete using (
  author_id = (select auth.uid()) or is_admin()
  or exists (select 1 from community_posts p where p.id = post_id and p.author_id = (select auth.uid()))
);

-- follows --------------------------------------------------------------------------------------
create policy follows_read on follows for select
  using (not is_blocked_with(follower_id) and not is_blocked_with(followee_id));
create policy follows_insert on follows for insert
  with check (follower_id = (select auth.uid()) and not is_blocked_with(followee_id));
create policy follows_delete on follows for delete using (follower_id = (select auth.uid()));

-- chat -----------------------------------------------------------------------------------------
-- Blocking hides the whole conversation in both directions.
create policy conversations_read on conversations for select using (
  (select auth.uid()) in (user_a, user_b)
  and not is_blocked_with(case when user_a = (select auth.uid()) then user_b else user_a end)
);
-- No insert or update policy: conversations are created only by start_conversation(), and read
-- state is moved only by mark_conversation_read().

create policy messages_read on messages for select using (
  not is_hidden and exists (select 1 from conversations c where c.id = conversation_id)
);
create policy messages_insert on messages for insert with check (
  sender_id = (select auth.uid()) and has_accepted_terms()
  and exists (select 1 from conversations c where c.id = conversation_id)
);
-- Messages are immutable. No update, no delete.

-- safety ---------------------------------------------------------------------------------------
create policy blocks_read   on blocks for select using (blocker_id = (select auth.uid()));
create policy blocks_insert on blocks for insert with check (blocker_id = (select auth.uid()));
create policy blocks_delete on blocks for delete using (blocker_id = (select auth.uid()));

create policy reports_read   on reports for select using (reporter_id = (select auth.uid()) or is_admin());
create policy reports_update on reports for update using (is_admin());
-- No insert policy: reports go through submit_report(), which validates the target and applies
-- the auto-hide threshold.

create policy audit_read on audit_actions for select using (is_admin());

create policy tokens_all on device_tokens for all
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy grants_read on upload_grants for select using (user_id = (select auth.uid()));

-- The public web deletion form posts here as anon. Play requires that URL to exist and work
-- without an app install.
create policy deletion_insert_public on deletion_requests for insert with check (true);
create policy deletion_read_admin    on deletion_requests for select using (is_admin());
create policy deletion_update_admin  on deletion_requests for update using (is_admin());
