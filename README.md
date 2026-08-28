# KabutarBaazi

A marketplace and community app for pigeon keepers (kabutarbaaz) across India and Pakistan.
Buy and sell birds, post short videos, and talk to keepers in your own area.

Native Kotlin + Jetpack Compose on Supabase, with media on Cloudflare R2.

## Layout

```
domain/     pure-JVM rules: usernames, phones, prices, media caps, moderation. 94 tests, no emulator.
app/        Compose UI, 9 repositories, on-device image and video compression
supabase/   schema, RLS policies, seeds, Deno Edge Functions, and a local RLS test harness
docs/       privacy policy, community rules, account-deletion page (served by GitHub Pages)
store/      Play listing copy, icon, feature graphic, screenshots
```

## Verify

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home

./gradlew :domain:test              # 94 JVM tests, the fast correctness gate
./gradlew assembleDebug             # must stay green
bash supabase/tests/run_tests.sh    # 53 RLS assertions on a throwaway local Postgres
```

`run_tests.sh` spins up its own Postgres, stubs the pieces Supabase provides, applies every
migration and asserts the policies. It needs no Docker and never touches the live project.

## Invariants

1. **RLS is the security boundary.** The Android client speaks to Postgres directly through
   PostgREST, so a wrong policy is a data leak rather than a 403. Never add a table without a
   policy, and never write a policy without a `TO` clause: without one it applies to `PUBLIC`,
   which includes the `anon` role whose key ships inside the APK. That exact mistake made the
   whole listings feed world-readable once; `0006_restrict_anon.sql` is the fix and the anon
   assertions in `rls_test.sql` are the regression guard.
2. **supabase-kt cannot go past 3.1.4** while Kotlin is 2.1.0. Newer versions carry Kotlin 2.2
   metadata that the 2.1.0 compiler refuses.
3. **One `SimpleCache` per process**, in `AppContainer`. Two on the same directory throw.
4. **Reels play off `pagerState.settledPage`, never `currentPage`.** `currentPage` updates
   mid-fling and a fast flick then races several videos into playing at once.
5. **The player pool lives in the composable, never a ViewModel.** A ViewModel-held ExoPlayer
   survives configuration changes and leaks its Surface.
6. **Padding is Start/End, never Left/Right.** Urdu is a shipped locale.
7. **Prices are always ASCII digits.** `NumberFormat` under `ur-PK` emits Eastern Arabic-Indic
   digits, which reads as a bug to the very users it is meant to serve.
8. **`AUTO_HIDE_THRESHOLD` exists in Kotlin and in SQL.** Change both in one commit.

## Traps

- Supabase Auth **"Confirm email" must stay OFF**. With it on, every signup creates an
  unconfirmed user who can never log in, and the mail goes to a synthetic address with no mailbox.
- Free Supabase projects pause after a week of inactivity. It looks like a total outage.
- "Hide" leaves the bytes reachable at their R2 URL. Only admin **Remove** deletes the object.
- Emulator screenshots are 1080x2400 (9:20) and Play rejects them. They must be 16:9 or 9:16.
- `google-services.json` is gitignored, so the Firebase plugin is applied conditionally. A fresh
  clone builds fine without it, just without push.

## Setup for a fresh clone

`local.properties` needs `SUPABASE_URL`, `SUPABASE_ANON_KEY` and `MEDIA_BASE_URL`. Without them
the app builds and shows a screen explaining exactly what is missing rather than crashing.
Full steps are in `docs/BUILDING.md`.
