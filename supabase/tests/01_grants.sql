-- Supabase grants these automatically to the API roles. Applied here so RLS, rather than a
-- missing GRANT, is what the tests are actually measuring.
grant usage on schema public to authenticated, anon;
grant all on all tables in schema public to authenticated;
-- Supabase's "automatically expose new tables" grants anon the same blanket access.
-- Reproducing it here is the whole point: without this line the local suite is more
-- restrictive than production and cannot catch an anon leak.
grant all on all tables in schema public to anon;
grant select, insert on deletion_requests to anon;
grant select on regions, breeds to anon;
grant usage on all sequences in schema public to authenticated, anon;
grant execute on all functions in schema public to authenticated, anon;

-- Re-apply the column whitelist, which the blanket GRANT ALL above undoes.
-- The revoke is the load-bearing half: table-level UPDATE overrides any column-level grant,
-- so it must be dropped before the permitted columns are granted back.
revoke update on profiles from authenticated;
grant update (display_name, avatar_url, bio, region_code, city, locale)
  on profiles to authenticated;
