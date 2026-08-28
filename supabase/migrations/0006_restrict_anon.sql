-- Scope every content policy to the `authenticated` role.
--
-- WHY THIS EXISTS
-- Policies created without a TO clause apply to PUBLIC, which includes Supabase's `anon` role.
-- The anon key ships inside the APK and is therefore effectively public, so every policy whose
-- USING expression happened to evaluate true for an unauthenticated caller was readable by
-- anyone who pulled the key out of the app.
--
-- Concretely, `not is_blocked_with(seller_id)` returns true when auth.uid() is NULL, because no
-- block row can match a NULL user. That single fact made the whole listings feed, and the entire
-- profiles table, world-readable. Verified against the live project before this migration:
-- a plain `curl` with only the anon key returned both.
--
-- KabutarBaazi requires a login for every screen, so nothing except the signup-time helpers
-- needs anonymous access at all.
--
-- Deliberately left readable by `anon`:
--   regions, breeds        pure reference data, no user information
--   deletion_requests      INSERT only, backing the public account-deletion page Play requires
--   username_available()   called on the signup form before a session exists

do $$
declare
  p record;
  keep_public constant text[] := array['regions_read', 'breeds_read', 'deletion_insert_public'];
begin
  for p in
    select policyname, tablename
      from pg_policies
     where schemaname = 'public'
       and not (policyname = any (keep_public))
  loop
    execute format('alter policy %I on public.%I to authenticated', p.policyname, p.tablename);
  end loop;
end $$;

-- Revoke the blanket table grants Supabase hands `anon` when "automatically expose new tables"
-- is on. Policies are the primary control, but a role with no privilege cannot reach the table
-- at all, which is a second independent line of defence.
revoke all on all tables in schema public from anon;
grant select on public.regions, public.breeds to anon;
grant insert on public.deletion_requests to anon;
grant execute on function public.username_available(text) to anon;
