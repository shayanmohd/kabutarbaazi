# Play Console state - KabutarBaazi

Submitted to Google Play production. This file is the record of what was declared.

- Developer account: SocialSure Private Limited (**Organization account**, so no
  12-tester / 14-day closed-testing gate; production is open)
- Developer id: `8128457256815350105`
- App id: `4974765176529395168`
- Package: `com.socialsure.kabutarbaazi` (permanent, claimed)
- Status: **1.0.0 live since 2026-08-30.** 1.0.1 rejected 2026-09-04 (reviewer credentials no longer
  worked), fixed and resubmitted 2026-09-13; in review.

## Done

App content, 10 of 10:

| Declaration | Answer |
|---|---|
| Privacy policy | https://shayanmohd.github.io/kabutarbaazi/privacy-policy.html |
| App access | Restricted. Two credential sets: `demo_reviewer` for the review, `demo_deletetest` for testing deletion (passwords in the gitignored `docs/PLAY-APP-ACCESS.md`) |
| Ads | Contains no ads |
| Content rating | IARC complete: Teen / 12+ / Parental guidance, "Users Interact" |
| Target audience | 18 and over only (keeps the app out of Families policy) |
| Data safety | 8 types over 5 categories; nothing shared with third parties |
| Government apps | No |
| Financial features | None |
| Health | None |
| Advertising ID | Not used |

Store settings: category Shopping, contact email, website.

Store listing (saved): app name, short description 66/80, full description
1743/4000, app icon 512x512, feature graphic 1024x500.

Production release (saved as draft): `KabutarBaazi 1.0.0`, `app-release.aab`
version 1 (1.0.0), en-US release notes, signed by Play App Signing.

Countries: all 177 plus Rest of World.

## Remaining

Nothing blocking. Review is with Google.

- **Chat push is not active yet.** Add `FCM_CLIENT_EMAIL`, `FCM_PRIVATE_KEY` and
  `WEBHOOK_SECRET` to `supabase/.env.local`, then
  `supabase secrets set --env-file supabase/.env.local --project-ref lhlrhgocjuwmxwbjsbch`.
  Everything else in the chat path is deployed; messages arrive live over realtime
  either way, only the background notification is missing.

## Seed content

Six example ads use CC0 photographs, three reels are re-encoded CC BY-SA clips,
and the Delhi group has five posts from two real member rows. Every source,
author and licence is listed in `docs/credits.html`, which is linked from the
docs index because CC BY-SA requires attribution to be public.

Listing breeds were changed to Lahori, Mookee, Jacobin and Fantail so that each
title matches the bird actually pictured. No CC0 photographs exist for Banka,
Rampoori, Lakka, Sialkoti, Golden or Teddy, and kabutarbaaz would spot a
mislabelled bird immediately.

## Traps hit here, worth remembering

- The store listing save control is **"Save as draft"**, not "Save".
- `fillInput` sets the DOM value but not Angular's form model, so a save
  persists empty strings. Use `cdp('Input.insertText', ...)` after focusing the
  field, which fires a real input event.
- Never press Tab to blur a textarea; it inserts a literal tab character.
- Graphics attached to a listing are lost if the save that follows fails.
  Attach, save, then reload and confirm before moving on.
- The country list uses `[role=checkbox]`, not `input[type=checkbox]`, and
  index 0 is "Select all rows".
- The countries URL needs the track id (`4699244846177526382`); the driver's
  helper builds `tracks/null` before a release exists.
- The store listing task on the dashboard only ticks after the wizard is taken
  through **Next -> AI asset declaration -> Save**. Filling the fields and saving
  the draft is not enough, and the checklist caches: reload with
  `cdp('Page.reload', { ignoreCache: true })` or it keeps reporting 10 of 11.
- `submitState().setupIncomplete` caches the same way. It kept reporting `true`
  after the dashboard section had already vanished; a hard reload flipped it to
  a live "Submit 11 changes for review".
- `PC.boot()` state does not survive a heredoc. Skipping it builds
  `developers/null/app/null`, which redirects and looks like a broken page.

## 2026-09-04 rejection: "Login credentials are incorrect"

The declared credentials were typed correctly; the account behind them no longer existed.
`username_available('demo_reviewer')` returned true while `imranq` and `salman_kbz` were intact,
so `demo_reviewer` alone had been deleted between the 1.0.0 approval and the 1.0.1 review. Google
says reviewers cannot create accounts, and they test account deletion with what they are given.

Fix applied: recreated `demo_reviewer` with the same password, added a second set named
"Account deletion test" (`demo_deletetest`) whose instructions say deleting it is expected, and
told the main set not to be deleted. Both were signed into through the 1.0.1 release build before
resubmitting.

**Before any future submission, sign in with both accounts.** A deleted reviewer account costs a
full review cycle and is invisible from the console until the rejection arrives.
