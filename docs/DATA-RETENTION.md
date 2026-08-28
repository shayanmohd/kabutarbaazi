# What deleting an account removes, and what it keeps

This table is the source of truth for the privacy policy and for the Play Data Safety form.
Play checks that the app, the policy and the form agree, so change all three together.

## Removed immediately

| Data | Mechanism |
|---|---|
| Profile: username, display name, bio, avatar, city, region | `auth.users` cascade |
| Phone number | `private_contacts` cascade |
| All listings and their photos and videos | cascade, plus an R2 prefix sweep |
| All reels, thumbnails, likes and comments | cascade, plus the same sweep |
| Community memberships, posts, post media, likes, comments | cascade |
| Follows, in both directions | cascade |
| Saved listings | cascade |
| Conversations and every message in them | cascade |
| Blocks the user created | cascade |
| Push notification tokens | cascade |
| Every media object under `u/<user id>/` in R2 | prefix sweep in `delete-account` |

## Kept, and why

| Data | Why | For how long |
|---|---|---|
| Reports filed **against** this account | Deleting an account must not erase the evidence of abuse. `target_owner_id` becomes NULL and only a username snapshot remains. | 12 months |
| Reports this account **filed** against others | The reported content is still under review and the report has to stay actionable. Reporter identity is cleared. | 12 months |
| Admin audit actions | Moderation must be auditable. | 12 months |
| Contact reveal log | Abuse trail for phone number harvesting. Viewer id is cleared. | 90 days |

## Consequence worth stating plainly

Deleting an account deletes the **other party's copy of the conversation too**. That follows from
one conversation row per pair of users. It is disclosed in the in-app confirmation dialog and in
the privacy policy rather than being discovered.

## The public URL

Play requires a deletion route that works without installing the app. `web/delete-account.html`
posts to `deletion_requests`, which an admin processes. The in-app path is immediate; the web
path is manual and stated as such on the page.
