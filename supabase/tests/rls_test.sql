-- RLS assertions.
--
-- The Android client reaches Postgres directly with the anon key, so these policies are the
-- security boundary. Every assertion below is a leak that would otherwise reach production.

\set ON_ERROR_STOP on
\timing off

create table if not exists _test_results (name text, passed boolean);
truncate _test_results;

create or replace function t(name text, cond boolean) returns void
language plpgsql as $$
begin
  insert into _test_results values (name, cond);
  if cond then raise notice '  ok    %', name;
  else raise warning '  FAIL  %', name;
  end if;
end $$;

-- Runs an expression that is expected to raise, and records whether it did.
create or replace function t_raises(name text, stmt text) returns void
language plpgsql as $$
begin
  begin
    execute stmt;
    insert into _test_results values (name, false);
    raise warning '  FAIL  % (expected an error, none raised)', name;
  exception when others then
    insert into _test_results values (name, true);
    raise notice '  ok    % (blocked: %)', name, sqlerrm;
  end;
end $$;

create or replace function act_as(u uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claim.sub', u::text, false);
  execute 'set role authenticated';
end $$;

create or replace function act_as_anon() returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claim.sub', '', false);
  execute 'set role anon';
end $$;

create or replace function act_as_superuser() returns void language plpgsql as $$
begin
  execute 'reset role';
  perform set_config('request.jwt.claim.sub', '', false);
end $$;

-- The scaffolding itself is not under test, so every role may use it.
grant all on _test_results to authenticated, anon;
grant execute on function t(text, boolean), t_raises(text, text),
                          act_as(uuid), act_as_anon(), act_as_superuser() to authenticated, anon;

-- ---------------------------------------------------------------------------------------------
-- fixtures
-- ---------------------------------------------------------------------------------------------
\set alice  '''11111111-1111-4111-8111-111111111111'''
\set bob    '''22222222-2222-4222-8222-222222222222'''
\set carol  '''33333333-3333-4333-8333-333333333333'''
\set dave   '''44444444-4444-4444-8444-444444444444'''
\set admin  '''55555555-5555-4555-8555-555555555555'''
\set fresh  '''66666666-6666-4666-8666-666666666666'''

insert into auth.users (id, email, raw_user_meta_data) values
 (:alice, 'alice@users.kamapathy.app', '{"username":"alice","display_name":"Alice","phone_e164":"+919876500001","region_code":"delhi"}'),
 (:bob,   'bob@users.kamapathy.app',   '{"username":"bob","display_name":"Bob","phone_e164":"+919876500002","region_code":"delhi"}'),
 (:carol, 'carol@users.kamapathy.app', '{"username":"carol","display_name":"Carol","phone_e164":"+919876500003","region_code":"bihar"}'),
 (:dave,  'dave@users.kamapathy.app',  '{"username":"dave","display_name":"Dave","phone_e164":"+919876500004","region_code":"punjab"}'),
 (:admin, 'admin1@users.kamapathy.app','{"username":"adminuser","display_name":"Admin","phone_e164":"+919876500005","region_code":"delhi"}'),
 (:fresh, 'fresh@users.kamapathy.app', '{"username":"freshuser","display_name":"Fresh","phone_e164":"+919876500006","region_code":"delhi"}');

update profiles set role = 'admin' where id = :admin;
-- Everyone except `fresh` is an established account.
update profiles set created_at = now() - interval '30 days' where id <> :fresh;
update profiles set created_at = now() - interval '2 hours'  where id = :fresh;
update profiles set terms_accepted_at = now() where id <> :dave;  -- dave has NOT accepted terms

insert into listings (id, seller_id, title, price_minor, region_code, status)
values ('aaaa1111-0000-4000-8000-000000000001', :alice, 'Teddy jodi, 6 months', 450000, 'delhi', 'active'),
       ('aaaa1111-0000-4000-8000-000000000002', :alice, 'Hidden one',           450000, 'delhi', 'active'),
       ('bbbb2222-0000-4000-8000-000000000001', :bob,   'Sialkoti pair',        900000, 'delhi', 'active');
update listings set is_hidden = true where id = 'aaaa1111-0000-4000-8000-000000000002';

insert into reels (id, author_id, video_url, thumb_url, width, height, duration_ms)
values ('cccc3333-0000-4000-8000-000000000001', :alice, 'https://m/x.mp4', 'https://m/x.jpg', 720, 1280, 20000);

\echo ''
\echo 'PROFILES AND PRIVATE CONTACT'

select act_as(:bob);
select t('a user can read their own phone number',
         (select count(*) from private_contacts where user_id = :bob) = 1);
select t('a user CANNOT read someone else''s phone number',
         (select count(*) from private_contacts where user_id = :alice) = 0);
select t('profiles of other users are readable',
         (select count(*) from profiles where id = :alice) = 1);

select act_as_superuser();
select t_raises('a user cannot promote themselves to admin',
  $$ select act_as('22222222-2222-4222-8222-222222222222'::uuid);
     update profiles set role = 'admin' where id = '22222222-2222-4222-8222-222222222222'; $$);

\echo ''
\echo 'TERMS GATE (Play UGC requirement, enforced in the database)'

select act_as_superuser();
select t_raises('a user who has not accepted terms cannot post a listing',
  $$ select act_as('44444444-4444-4444-8444-444444444444'::uuid);
     insert into listings (seller_id, title, price_minor, region_code)
     values ('44444444-4444-4444-8444-444444444444', 'Should fail', 1000, 'delhi'); $$);

select act_as_superuser();
select act_as(:carol);
insert into listings (seller_id, title, price_minor, region_code)
values (:carol, 'Carol legit listing', 120000, 'bihar');
select t('a user who HAS accepted terms can post a listing',
         (select count(*) from listings where seller_id = :carol) = 1);

\echo ''
\echo 'HIDDEN CONTENT'

select act_as_superuser(); select act_as(:bob);
select t('hidden listings are invisible to other users',
         (select count(*) from listings where id = 'aaaa1111-0000-4000-8000-000000000002') = 0);
select act_as_superuser(); select act_as(:alice);
select t('the owner can still see their own hidden listing',
         (select count(*) from listings where id = 'aaaa1111-0000-4000-8000-000000000002') = 1);
select act_as_superuser(); select act_as(:admin);
select t('an admin can see hidden listings',
         (select count(*) from listings where id = 'aaaa1111-0000-4000-8000-000000000002') = 1);

\echo ''
\echo 'BLOCKING (must be symmetric, and must hide content in both directions)'

select act_as_superuser(); select act_as(:bob);
select t('before blocking, bob can see alice''s listing',
         (select count(*) from listings where id = 'aaaa1111-0000-4000-8000-000000000001') = 1);
insert into blocks (blocker_id, blocked_id) values (:bob, :alice);
select t('after blocking, the blocker cannot see the blocked user''s listing',
         (select count(*) from listings where id = 'aaaa1111-0000-4000-8000-000000000001') = 0);
select t('after blocking, the blocked user''s reel is also gone',
         (select count(*) from reels where author_id = '11111111-1111-4111-8111-111111111111') = 0);

select act_as_superuser(); select act_as(:alice);
select t('blocking is symmetric: the blocked user cannot see the blocker''s listing either',
         (select count(*) from listings where id = 'bbbb2222-0000-4000-8000-000000000001') = 0);
select t('a blocked user cannot see the blocker''s profile',
         (select count(*) from profiles where id = '22222222-2222-4222-8222-222222222222') = 0);

\echo ''
\echo 'PHONE REVEAL (the only path to a phone number)'

select act_as_superuser(); select act_as(:carol);
select t('a buyer can reveal a seller''s whatsapp number',
         get_seller_whatsapp('aaaa1111-0000-4000-8000-000000000001') = '+919876500001');
select act_as_superuser();
select t('the reveal is logged for the abuse trail',
         (select count(*) from contact_reveals where viewer_id = '33333333-3333-4333-8333-333333333333') = 1);
select t_raises('a blocked user cannot reveal the number',
  $$ select act_as('22222222-2222-4222-8222-222222222222'::uuid);
     select get_seller_whatsapp('aaaa1111-0000-4000-8000-000000000001'); $$);
select act_as_superuser();
select t_raises('a hidden listing''s number cannot be revealed',
  $$ select act_as('33333333-3333-4333-8333-333333333333'::uuid);
     select get_seller_whatsapp('aaaa1111-0000-4000-8000-000000000002'); $$);

\echo ''
\echo 'COMMUNITIES (membership required to post)'

select act_as_superuser(); select act_as(:carol);
select t_raises('a non-member cannot post to a community',
  $$ insert into community_posts (community_id, author_id, body)
     values ((select id from communities where region_code='bihar'),
             '33333333-3333-4333-8333-333333333333', 'hello'); $$);

insert into community_members (community_id, user_id)
values ((select id from communities where region_code='bihar'), :carol);
insert into community_posts (community_id, author_id, body)
values ((select id from communities where region_code='bihar'), :carol, 'Bihar walon salaam');
select t('a member CAN post to their community',
         (select count(*) from community_posts where author_id = :carol) = 1);
select act_as_superuser();
select t('the member counter trigger fired',
         (select member_count from communities where region_code='bihar') = 1);

\echo ''
\echo 'CHAT'

select act_as_superuser(); select act_as(:carol);
select t('start_conversation returns an id',
         start_conversation(:dave) is not null);
select t('calling start_conversation twice is idempotent',
         start_conversation(:dave) = (select id from conversations
                                       where user_a = least(:carol::uuid, :dave::uuid)
                                         and user_b = greatest(:carol::uuid, :dave::uuid)));
select act_as_superuser();
select t('only one conversation row exists for the pair',
         (select count(*) from conversations) = 1);
select t('the pair is stored in canonical order',
         (select user_a < user_b from conversations limit 1));

select act_as(:carol);
insert into messages (conversation_id, sender_id, body, client_id)
values ((select id from conversations limit 1), :carol, 'Kabutar available hai?', gen_random_uuid());
select t('a participant can read the thread',
         (select count(*) from messages) = 1);
select t('the conversation summary was updated by the trigger',
         (select last_message_preview from conversations limit 1) = 'Kabutar available hai?');

select act_as_superuser(); select act_as(:alice);
select t('a non-participant cannot read the conversation',
         (select count(*) from conversations) = 0);
select t('a non-participant cannot read its messages',
         (select count(*) from messages) = 0);

\echo ''
\echo 'REPORTING AND AUTO-HIDE'

-- Dave's listing is the target. Give him terms so he can post one.
select act_as_superuser();
update profiles set terms_accepted_at = now() where id = :dave;
insert into listings (id, seller_id, title, price_minor, region_code)
values ('dddd4444-0000-4000-8000-000000000001', :dave, 'Reportable listing', 100000, 'punjab');

select act_as(:alice);
select submit_report('listing', 'dddd4444-0000-4000-8000-000000000001', 'scam_or_fraud', 'advance payment');
select act_as_superuser(); select act_as(:bob);
select submit_report('listing', 'dddd4444-0000-4000-8000-000000000001', 'scam_or_fraud');
select act_as_superuser();
select t('two reports is below the threshold, content stays visible',
         (select is_hidden from listings where id = 'dddd4444-0000-4000-8000-000000000001') = false);

-- A brand new account reports it. This must NOT tip it over.
select act_as(:fresh);
select submit_report('listing', 'dddd4444-0000-4000-8000-000000000001', 'spam');
select act_as_superuser();
select t('a report from an account under 24h old does not count toward auto-hide',
         (select is_hidden from listings where id = 'dddd4444-0000-4000-8000-000000000001') = false);
select t('but the fresh account''s report is still filed for review',
         (select count(*) from reports where reporter_id = :fresh) = 1);

-- A third established account tips it.
select act_as(:carol);
select submit_report('listing', 'dddd4444-0000-4000-8000-000000000001', 'animal_cruelty');
select act_as_superuser();
select t('three established reporters auto-hides the listing',
         (select is_hidden from listings where id = 'dddd4444-0000-4000-8000-000000000001') = true);

select act_as(:alice);
select t('the auto-hidden listing is now invisible to everyone else',
         (select count(*) from listings where id = 'dddd4444-0000-4000-8000-000000000001') = 0);

select act_as_superuser(); select act_as(:alice);
select submit_report('listing', 'dddd4444-0000-4000-8000-000000000001', 'spam');
select act_as_superuser();
select t('reporting the same item twice is idempotent, so one user cannot brigade',
         (select count(*) from reports
           where reporter_id = :alice
             and target_id = 'dddd4444-0000-4000-8000-000000000001') = 1);
select act_as_superuser();
select t_raises('a user cannot report themselves to game the queue',
  $$ select act_as('44444444-4444-4444-8444-444444444444'::uuid);
     select submit_report('listing','dddd4444-0000-4000-8000-000000000001','spam'); $$);

select act_as(:alice);
select t('a normal user cannot read the moderation queue',
         (select count(*) from reports where reporter_id <> :alice) = 0);
select act_as_superuser(); select act_as(:admin);
select t('an admin CAN read the whole moderation queue',
         (select count(*) from reports) >= 4);

\echo ''
\echo 'EVIDENCE SURVIVES ACCOUNT DELETION'

select act_as_superuser();
delete from auth.users where id = :dave;
select t('deleting the reported account removes their profile',
         (select count(*) from profiles where id = :dave) = 0);
select t('but the reports against them survive',
         (select count(*) from reports where target_owner_username = 'dave') = 4);
select t('with the username snapshotted, so the trail is still readable',
         (select target_owner_username from reports
           where target_id = 'dddd4444-0000-4000-8000-000000000001' limit 1) = 'dave');

\echo ''
\echo 'ADMIN ACTIONS'

select act_as(:admin);
select admin_set_hidden('listing', 'aaaa1111-0000-4000-8000-000000000002', false, null);
select act_as_superuser();
select t('an admin can unhide content',
         (select is_hidden from listings where id = 'aaaa1111-0000-4000-8000-000000000002') = false);
select t('the admin action is written to the audit log',
         (select count(*) from audit_actions where action = 'unhide') = 1);

select t_raises('a normal user cannot call the admin hide RPC',
  $$ select act_as('11111111-1111-4111-8111-111111111111'::uuid);
     select admin_set_hidden('listing','bbbb2222-0000-4000-8000-000000000001', true, 'nope'); $$);

\echo ''
\echo 'ANONYMOUS ACCESS (the anon key ships inside the APK, so it is effectively public)'

-- Regression guard. Policies created without a TO clause apply to PUBLIC, which includes anon.
-- Because is_blocked_with() returns false when auth.uid() is NULL, the listings and profiles
-- policies evaluated TRUE for unauthenticated callers and the whole feed was world-readable.
-- Caught against the live project, fixed in 0006_restrict_anon.sql.
select act_as_superuser(); select act_as_anon();
select t('anon CANNOT read profiles',        (select count(*) from profiles) = 0);
select t('anon CANNOT read listings',        (select count(*) from listings) = 0);
select t('anon CANNOT read reels',           (select count(*) from reels) = 0);
select t('anon CANNOT read community posts', (select count(*) from community_posts) = 0);
select t('anon CANNOT read messages',        (select count(*) from messages) = 0);
select t('anon CANNOT read private contacts',(select count(*) from private_contacts) = 0);
select t('anon CAN still read reference regions', (select count(*) from regions) > 0);
select t('anon CAN still read reference breeds',  (select count(*) from breeds) > 0);
select t('anon CAN still check username availability', username_available('somebodynew') = true);

\echo ''
\echo '================ SUMMARY ================'
select act_as_superuser();
select count(*) filter (where passed) as passed,
       count(*) filter (where not passed) as failed,
       count(*) as total
  from _test_results;

select string_agg(name, E'\n  ') as failures from _test_results where not passed;

do $$
declare n int;
begin
  select count(*) into n from _test_results where not passed;
  if n > 0 then raise exception '% RLS assertion(s) FAILED', n; end if;
  raise notice 'All RLS assertions passed.';
end $$;
