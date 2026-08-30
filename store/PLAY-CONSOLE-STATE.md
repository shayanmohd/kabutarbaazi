# Play Console state - KabutarBaazi

Everything except screenshots and the final submit is done. This file is the resume point.

- Developer account: SocialSure Private Limited (**Organization account**, so no
  12-tester / 14-day closed-testing gate; production is open)
- Developer id: `8128457256815350105`
- App id: `4974765176529395168`
- Package: `com.socialsure.kabutarbaazi` (permanent, claimed)
- Status: **Draft**, changes staged but **not submitted for review**

## Done

App content, 10 of 10:

| Declaration | Answer |
|---|---|
| Privacy policy | https://shayanmohd.github.io/kabutarbaazi/privacy-policy.html |
| App access | Restricted. Reviewer credentials `demo_reviewer` / `KabutarDemo2026` |
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

1. **Phone screenshots.** The only incomplete dashboard item (10 of 11). Play
   requires 2 to 8 at exactly 16:9 or 9:16. The six in `store/screenshots/` are
   placeholders captioned "PLACEHOLDER - not for store use" and must not ship.
   Reseed the app with real pigeon photos, retake, then attach.
2. **Submit for review.** Deliberately not done. Clicking Submit also needs the
   dialog confirmed with **Send changes for review**, or the submission is
   silently cancelled.

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
