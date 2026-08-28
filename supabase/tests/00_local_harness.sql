-- Local-only stub of the pieces Supabase provides, so the schema and its RLS policies can be
-- exercised against a plain Postgres instance with no Docker and no cloud project.
--
-- This file is NEVER applied to the real database. It exists purely so `run_tests.sh` can prove
-- the policies behave before they are deployed.

create extension if not exists pgcrypto;
create extension if not exists citext;

create schema if not exists auth;

create table if not exists auth.users (
  id                 uuid primary key default gen_random_uuid(),
  email              text unique,
  raw_user_meta_data jsonb,
  created_at         timestamptz not null default now()
);

-- Matches Supabase's real implementation: read `sub` out of the request JWT claims.
create or replace function auth.uid() returns uuid
language sql stable as $$
  select coalesce(
    nullif(current_setting('request.jwt.claim.sub', true), ''),
    (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub')
  )::uuid;
$$;

do $$ begin
  if not exists (select 1 from pg_roles where rolname = 'authenticated') then create role authenticated; end if;
  if not exists (select 1 from pg_roles where rolname = 'anon') then create role anon; end if;
  if not exists (select 1 from pg_roles where rolname = 'service_role') then create role service_role; end if;
end $$;

-- Supabase ships this publication; 0004 adds tables to it.
do $$ begin
  if not exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    create publication supabase_realtime;
  end if;
end $$;
