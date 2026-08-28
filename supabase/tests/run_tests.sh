#!/usr/bin/env bash
# Spins up a throwaway local Postgres, applies the migrations, and runs the RLS assertions.
# Nothing here touches the real Supabase project or any existing local cluster.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PGBIN="${PGBIN:-/opt/homebrew/opt/postgresql@17/bin}"
WORK="${WORK:-/private/tmp/claude-501/kabutarbaazi-pgtest}"
PORT="${PORT:-55432}"
DB=kabutarbaazi_test

export PATH="$PGBIN:$PATH"
# macOS: without an explicit locale the postmaster goes multithreaded during startup and
# refuses to boot ("postmaster became multithreaded during startup").
export LC_ALL=C
export LANG=C

cleanup() {
  if [ -d "$WORK/data" ]; then
    pg_ctl -D "$WORK/data" -m immediate stop >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

rm -rf "$WORK"; mkdir -p "$WORK"
initdb -D "$WORK/data" -U postgres --no-locale --encoding=UTF8 >/dev/null
pg_ctl -D "$WORK/data" -o "-p $PORT -k $WORK -c listen_addresses=''" -l "$WORK/pg.log" -w start >/dev/null
createdb -h "$WORK" -p "$PORT" -U postgres "$DB"

psql_run() { psql -h "$WORK" -p "$PORT" -U postgres -d "$DB" -v ON_ERROR_STOP=1 -q "$@"; }

echo "applying harness + migrations"
psql_run -f "$ROOT/supabase/tests/00_local_harness.sql"
for f in "$ROOT"/supabase/migrations/*.sql; do
  echo "  $(basename "$f")"
  psql_run -f "$f"
done
psql_run -f "$ROOT/supabase/tests/01_grants.sql"

echo
echo "running RLS assertions"
psql -h "$WORK" -p "$PORT" -U postgres -d "$DB" -v ON_ERROR_STOP=1 -f "$ROOT/supabase/tests/rls_test.sql"
