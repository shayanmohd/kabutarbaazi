// Deletes an account and everything it owns.
//
// Google Play requires both an in-app deletion path and a publicly reachable web URL. This
// backs the in-app path; the web form writes to deletion_requests for manual processing.
//
// What is deleted and what is kept is documented in docs/DATA-RETENTION.md and restated in the
// privacy policy, because Play checks that the two agree.
import { errorResponse, HttpError, json, requireUser } from "../_shared/auth.ts";
import { deleteObjects, listPrefix } from "../_shared/r2.ts";

Deno.serve(async (req) => {
  try {
    if (req.method !== "POST") throw new HttpError(405, "method_not_allowed");
    const { id: userId, admin } = await requireUser(req);

    // Media first. If the auth row went first, the JWT would stop verifying and the objects
    // would be orphaned in R2 with no way to find them again.
    const keys = await listPrefix(`u/${userId}/`);
    if (keys.length) await deleteObjects(keys);

    // Reports FILED AGAINST this user are deliberately preserved: target_owner_id is ON DELETE
    // SET NULL with a username snapshot, so deleting an account cannot erase the evidence
    // against it. Reports this user filed are anonymised the same way.
    //
    // Everything else the user owns cascades from profiles, which cascades from auth.users:
    // listings, listing media, reels, likes, comments, posts, memberships, follows, saved ads,
    // conversations, messages, blocks, device tokens.
    const { error } = await admin.auth.admin.deleteUser(userId);
    if (error) throw new HttpError(500, `delete_failed: ${error.message}`);

    return json({ deleted: true, mediaObjectsRemoved: keys.length });
  } catch (e) {
    return errorResponse(e);
  }
});
