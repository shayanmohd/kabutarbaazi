-- KabutarBaazi schema.
--
-- Design notes that are load-bearing:
--   * Phone numbers live in their own table, not on profiles. RLS is row-level, not
--     column-level, so there is no way to hide one column of profiles from other users.
--     A separate table is the only way "hidden until the buyer taps WhatsApp" is actually true.
--   * Communities are admin-seeded, one per region. Users cannot create groups in v1, which
--     removes an entire class of moderation problem.
--   * Comments on reels and on posts are two near-identical tables rather than one polymorphic
--     table. Polymorphic loses the foreign key and turns every RLS policy into a CASE. The
--     duplication is deliberate and accepted.

create extension if not exists pgcrypto;
create extension if not exists citext;

-- ---------------------------------------------------------------------------------------------
-- enums
-- ---------------------------------------------------------------------------------------------
create type listing_status     as enum ('active','sold','removed');
create type report_status      as enum ('open','actioned','dismissed');
create type report_target_type as enum ('listing','reel','reel_comment','post','post_comment','message','user');

-- animal_cruelty and illegal_sale are not decoration. A live-animal marketplace needs both, and
-- their presence in the reporting UI is the most persuasive thing a Play reviewer can see.
create type report_reason      as enum (
  'spam','scam_or_fraud','animal_cruelty','nudity_or_sexual',
  'violence','hate_or_harassment','impersonation','illegal_sale','other'
);

-- ---------------------------------------------------------------------------------------------
-- reference data
-- ---------------------------------------------------------------------------------------------
create table regions (
  code       text primary key,
  name_en    text not null,
  name_hi    text not null,
  name_ur    text not null,
  sort_order int  not null
);

create table breeds (
  id         smallserial primary key,
  slug       text not null unique,
  name_en    text not null,
  name_hi    text not null,
  name_ur    text not null,
  sort_order int  not null,
  is_active  boolean not null default true
);

-- ---------------------------------------------------------------------------------------------
-- identity
-- ---------------------------------------------------------------------------------------------
create table profiles (
  id                uuid primary key references auth.users(id) on delete cascade,
  username          citext not null unique check (username ~ '^[a-z][a-z0-9._]{2,19}$'),
  display_name      text not null check (char_length(display_name) between 1 and 40),
  avatar_url        text,
  bio               text check (char_length(bio) <= 200),
  region_code       text references regions(code),
  city              text check (char_length(city) <= 60),
  locale            text not null default 'hi' check (locale in ('en','hi','ur')),
  role              text not null default 'user' check (role in ('user','admin','demo')),
  -- Play's UGC policy requires terms acceptance before a user can upload. This column is what
  -- makes that enforceable in the database rather than merely in the UI.
  terms_accepted_at timestamptz,
  follower_count    int  not null default 0,
  following_count   int  not null default 0,
  is_suspended      boolean not null default false,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);
create index idx_profiles_region on profiles(region_code);

create table private_contacts (
  user_id    uuid primary key references profiles(id) on delete cascade,
  phone_e164 text not null check (phone_e164 ~ '^\+[1-9][0-9]{7,14}$'),
  -- HMAC of the number with a server-side pepper. A future SMS recovery flow looks a user up by
  -- this hash, so the client never has to query by raw phone number.
  phone_hash bytea not null,
  created_at timestamptz not null default now()
);
-- One account per phone number. The strongest anti-sockpuppet lever available for free.
-- Real consequence: a family sharing one handset gets one account. Accepted deliberately.
create unique index idx_private_contacts_hash on private_contacts(phone_hash);

-- ---------------------------------------------------------------------------------------------
-- marketplace
-- ---------------------------------------------------------------------------------------------
create table listings (
  id            uuid primary key default gen_random_uuid(),
  seller_id     uuid not null references profiles(id) on delete cascade,
  title         text not null check (char_length(title) between 3 and 80),
  description   text check (char_length(description) <= 2000),
  breed_id      smallint references breeds(id),
  breed_other   text check (char_length(breed_other) <= 40),
  price_minor   bigint not null check (price_minor >= 0 and price_minor <= 100000000),
  currency      char(3) not null default 'INR' check (currency in ('INR','PKR')),
  is_negotiable boolean not null default true,
  quantity      int not null default 1 check (quantity between 1 and 500),
  region_code   text not null references regions(code),
  city          text check (char_length(city) <= 60),
  status        listing_status not null default 'active',
  is_hidden     boolean not null default false,
  hidden_reason text,
  report_count  int not null default 0,
  saved_count   int not null default 0,
  view_count    int not null default 0,
  -- 'simple', not 'english'. The corpus is Hindi, Urdu and Hinglish transliteration, where
  -- English stemming would actively hurt recall.
  search_tsv    tsvector generated always as
                  (to_tsvector('simple', coalesce(title,'') || ' ' || coalesce(description,''))) stored,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  bumped_at     timestamptz not null default now()
);
create index idx_listings_feed   on listings(bumped_at desc)              where status='active' and is_hidden=false;
create index idx_listings_region on listings(region_code, bumped_at desc) where status='active' and is_hidden=false;
create index idx_listings_breed  on listings(breed_id, bumped_at desc)    where status='active' and is_hidden=false;
create index idx_listings_seller on listings(seller_id, created_at desc);
create index idx_listings_search on listings using gin(search_tsv);

create table listing_media (
  id          uuid primary key default gen_random_uuid(),
  listing_id  uuid not null references listings(id) on delete cascade,
  kind        text not null check (kind in ('image','video')),
  url         text not null,
  thumb_url   text,
  width       int, height int,
  duration_ms int,
  bytes       bigint,
  position    smallint not null default 0,
  created_at  timestamptz not null default now(),
  unique (listing_id, position),
  check (kind <> 'video' or duration_ms is not null)
);
create index idx_listing_media on listing_media(listing_id, position);

create table saved_listings (
  user_id    uuid references profiles(id) on delete cascade,
  listing_id uuid references listings(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, listing_id)
);
create index idx_saved_user on saved_listings(user_id, created_at desc);

-- Audit trail for phone reveals. Without this, "we log these reveals" in the privacy policy
-- would be a claim rather than a fact.
create table contact_reveals (
  id         bigserial primary key,
  listing_id uuid references listings(id) on delete cascade,
  viewer_id  uuid references profiles(id) on delete cascade,
  seller_id  uuid references profiles(id) on delete cascade,
  created_at timestamptz not null default now()
);
create index idx_reveals_viewer on contact_reveals(viewer_id, created_at desc);

-- ---------------------------------------------------------------------------------------------
-- reels
-- ---------------------------------------------------------------------------------------------
create table reels (
  id            uuid primary key default gen_random_uuid(),
  author_id     uuid not null references profiles(id) on delete cascade,
  caption       text check (char_length(caption) <= 300),
  video_url     text not null,
  thumb_url     text not null,
  width         int not null,
  height        int not null,
  duration_ms   int not null check (duration_ms between 1000 and 90000),
  bytes         bigint,
  like_count    int not null default 0,
  comment_count int not null default 0,
  view_count    int not null default 0,
  region_code   text references regions(code),
  is_hidden     boolean not null default false,
  hidden_reason text,
  report_count  int not null default 0,
  created_at    timestamptz not null default now()
);
create index idx_reels_feed   on reels(created_at desc) where is_hidden=false;
create index idx_reels_author on reels(author_id, created_at desc);

create table reel_likes (
  reel_id    uuid references reels(id) on delete cascade,
  user_id    uuid references profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (reel_id, user_id)
);
create index idx_reel_likes_user on reel_likes(user_id, created_at desc);

create table reel_comments (
  id            uuid primary key default gen_random_uuid(),
  reel_id       uuid not null references reels(id) on delete cascade,
  author_id     uuid not null references profiles(id) on delete cascade,
  body          text not null check (char_length(body) between 1 and 500),
  is_hidden     boolean not null default false,
  hidden_reason text,
  report_count  int not null default 0,
  created_at    timestamptz not null default now()
);
create index idx_reel_comments on reel_comments(reel_id, created_at desc) where is_hidden=false;

-- ---------------------------------------------------------------------------------------------
-- communities: one per region, admin-seeded. No user-created groups in v1.
-- ---------------------------------------------------------------------------------------------
create table communities (
  id             uuid primary key default gen_random_uuid(),
  region_code    text not null unique references regions(code),
  slug           text not null unique,
  name_en text not null, name_hi text not null, name_ur text not null,
  description_en text, description_hi text, description_ur text,
  cover_url      text,
  member_count   int not null default 0,
  post_count     int not null default 0,
  is_active      boolean not null default true,
  created_at     timestamptz not null default now()
);

create table community_members (
  community_id uuid references communities(id) on delete cascade,
  user_id      uuid references profiles(id) on delete cascade,
  role         text not null default 'member' check (role in ('member','moderator')),
  joined_at    timestamptz not null default now(),
  primary key (community_id, user_id)
);
create index idx_members_user on community_members(user_id, joined_at desc);

create table community_posts (
  id            uuid primary key default gen_random_uuid(),
  community_id  uuid not null references communities(id) on delete cascade,
  author_id     uuid not null references profiles(id) on delete cascade,
  body          text not null check (char_length(body) between 1 and 3000),
  like_count    int not null default 0,
  comment_count int not null default 0,
  is_hidden     boolean not null default false,
  hidden_reason text,
  report_count  int not null default 0,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);
create index idx_posts_feed   on community_posts(community_id, created_at desc) where is_hidden=false;
create index idx_posts_author on community_posts(author_id, created_at desc);

create table community_post_media (
  id         uuid primary key default gen_random_uuid(),
  post_id    uuid not null references community_posts(id) on delete cascade,
  kind       text not null check (kind in ('image','video')),
  url        text not null,
  thumb_url  text,
  width int, height int, duration_ms int, bytes bigint,
  position   smallint not null default 0,
  created_at timestamptz not null default now(),
  unique (post_id, position)
);

create table community_post_likes (
  post_id    uuid references community_posts(id) on delete cascade,
  user_id    uuid references profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (post_id, user_id)
);

create table community_post_comments (
  id            uuid primary key default gen_random_uuid(),
  post_id       uuid not null references community_posts(id) on delete cascade,
  author_id     uuid not null references profiles(id) on delete cascade,
  body          text not null check (char_length(body) between 1 and 500),
  is_hidden     boolean not null default false,
  hidden_reason text,
  report_count  int not null default 0,
  created_at    timestamptz not null default now()
);
create index idx_post_comments on community_post_comments(post_id, created_at desc) where is_hidden=false;

-- ---------------------------------------------------------------------------------------------
-- social
-- ---------------------------------------------------------------------------------------------
create table follows (
  follower_id uuid references profiles(id) on delete cascade,
  followee_id uuid references profiles(id) on delete cascade,
  created_at  timestamptz not null default now(),
  primary key (follower_id, followee_id),
  check (follower_id <> followee_id)
);
create index idx_follows_followee on follows(followee_id, created_at desc);

-- ---------------------------------------------------------------------------------------------
-- chat: one conversation per pair of users, never one per listing
-- ---------------------------------------------------------------------------------------------
create table conversations (
  id                   uuid primary key default gen_random_uuid(),
  user_a               uuid not null references profiles(id) on delete cascade,
  user_b               uuid not null references profiles(id) on delete cascade,
  listing_id           uuid references listings(id) on delete set null,
  last_message_at      timestamptz not null default now(),
  last_message_preview text,
  last_sender_id       uuid references profiles(id) on delete set null,
  a_last_read_at       timestamptz not null default 'epoch',
  b_last_read_at       timestamptz not null default 'epoch',
  created_at           timestamptz not null default now(),
  -- Canonical ordering enforced by the database, so a duplicate pair cannot exist even if two
  -- clients race to open the same thread.
  check (user_a < user_b),
  unique (user_a, user_b)
);
create index idx_conv_a on conversations(user_a, last_message_at desc);
create index idx_conv_b on conversations(user_b, last_message_at desc);

create table messages (
  id              uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references conversations(id) on delete cascade,
  sender_id       uuid not null references profiles(id) on delete cascade,
  body            text not null check (char_length(body) between 1 and 2000),
  -- Client-minted. Makes optimistic send plus retry idempotent, and lets the client dedupe the
  -- Realtime echo of its own insert.
  client_id       uuid not null,
  is_hidden       boolean not null default false,
  created_at      timestamptz not null default now(),
  unique (conversation_id, client_id)
);
create index idx_messages_thread on messages(conversation_id, created_at desc);
-- Required for Realtime to evaluate RLS against UPDATE and DELETE payloads.
alter table messages replica identity full;

-- ---------------------------------------------------------------------------------------------
-- safety
-- ---------------------------------------------------------------------------------------------
create table blocks (
  blocker_id uuid references profiles(id) on delete cascade,
  blocked_id uuid references profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  check (blocker_id <> blocked_id)
);
create index idx_blocks_blocked on blocks(blocked_id);

create table reports (
  id                    uuid primary key default gen_random_uuid(),
  reporter_id           uuid not null references profiles(id) on delete cascade,
  target_type           report_target_type not null,
  target_id             uuid not null,
  -- SET NULL, not CASCADE, plus a username snapshot. Cascading would let an abusive user erase
  -- the evidence against them by deleting their account, which is the opposite of moderation.
  target_owner_id       uuid references profiles(id) on delete set null,
  target_owner_username text,
  reason                report_reason not null,
  note                  text check (char_length(note) <= 500),
  status                report_status not null default 'open',
  resolved_by           uuid references profiles(id) on delete set null,
  resolved_at           timestamptz,
  created_at            timestamptz not null default now(),
  -- One report per user per item, so three taps from one angry buyer is still one report.
  unique (reporter_id, target_type, target_id)
);
create index idx_reports_queue  on reports(status, created_at desc);
create index idx_reports_target on reports(target_type, target_id);

create table audit_actions (
  id          bigserial primary key,
  admin_id    uuid references profiles(id) on delete set null,
  action      text not null,
  target_type report_target_type not null,
  target_id   uuid not null,
  report_id   uuid references reports(id) on delete set null,
  created_at  timestamptz not null default now()
);

create table device_tokens (
  token      text primary key,
  user_id    uuid not null references profiles(id) on delete cascade,
  platform   text not null default 'android',
  locale     text not null default 'hi',
  updated_at timestamptz not null default now()
);
create index idx_device_tokens_user on device_tokens(user_id);

-- Presign rate-limit ledger. The only thing between the free tier and someone burning
-- 500k Edge Function invocations.
create table upload_grants (
  id         bigserial primary key,
  user_id    uuid not null references profiles(id) on delete cascade,
  object_key text not null,
  created_at timestamptz not null default now()
);
create index idx_grants_user on upload_grants(user_id, created_at desc);

-- Backs the publicly reachable account-deletion URL that Play requires.
create table deletion_requests (
  id         uuid primary key default gen_random_uuid(),
  username   citext not null,
  phone_e164 text,
  note       text check (char_length(note) <= 500),
  status     text not null default 'open' check (status in ('open','done','rejected')),
  created_at timestamptz not null default now()
);
