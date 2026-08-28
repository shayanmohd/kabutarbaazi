// Mints a presigned R2 PUT URL.
//
// This is the boundary the Android client cannot cross. Everything the client sends is treated
// as a request, not an instruction: the storage key is built from the verified JWT subject, and
// every cap is re-checked here even though the app checks them too.
import { errorResponse, HttpError, json, requireUser } from "../_shared/auth.ts";
import { presignPut } from "../_shared/r2.ts";

type Kind = "reel_video" | "listing_video" | "listing_image" | "post_image" | "avatar" | "thumbnail";

// Mirrors :domain MediaConstraints.kt. Change both in one commit.
const CAPS: Record<Kind, { bytes: number; contentType: string }> = {
  reel_video:    { bytes: 15 * 1024 * 1024, contentType: "video/mp4" },
  listing_video: { bytes:  8 * 1024 * 1024, contentType: "video/mp4" },
  listing_image: { bytes:  2 * 1024 * 1024, contentType: "image/webp" },
  post_image:    { bytes:  2 * 1024 * 1024, contentType: "image/webp" },
  avatar:        { bytes:       200 * 1024, contentType: "image/webp" },
  thumbnail:     { bytes:        60 * 1024, contentType: "image/webp" },
};

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

/** Presigns per hour, per user. The only thing between the free tier and a runaway client. */
const HOURLY_GRANT_LIMIT = 40;

function buildKey(userId: string, kind: Kind, uploadId: string, index: number): string {
  // Everything a user owns lives under u/<their id>/, which makes account deletion a single
  // prefix sweep rather than a join across six tables.
  const base = `u/${userId}`;
  switch (kind) {
    case "avatar":        return `${base}/avatar.webp`;
    case "reel_video":    return `${base}/reels/${uploadId}/v.mp4`;
    case "thumbnail":     return `${base}/reels/${uploadId}/thumb.webp`;
    case "listing_video": return `${base}/listings/${uploadId}/v.mp4`;
    case "listing_image": return `${base}/listings/${uploadId}/${index}.webp`;
    case "post_image":    return `${base}/posts/${uploadId}/${index}.webp`;
  }
}

Deno.serve(async (req) => {
  try {
    if (req.method !== "POST") throw new HttpError(405, "method_not_allowed");
    const { id: userId, admin } = await requireUser(req);

    const { kind, uploadId, index = 0, contentType, bytes } = await req.json();

    const cap = CAPS[kind as Kind];
    if (!cap) throw new HttpError(400, "unknown_kind");
    if (contentType !== cap.contentType) throw new HttpError(400, "bad_content_type");
    if (typeof bytes !== "number" || bytes <= 0) throw new HttpError(400, "bad_size");
    if (bytes > cap.bytes) throw new HttpError(413, "too_large");
    if (typeof uploadId !== "string" || !UUID_V4.test(uploadId)) throw new HttpError(400, "bad_upload_id");
    if (!Number.isInteger(index) || index < 0 || index > 5) throw new HttpError(400, "bad_index");

    // A suspended user, or one who has not accepted the community rules, cannot obtain an
    // upload URL at all. This is the same gate the RLS insert policies apply, enforced one
    // step earlier so no bytes are spent.
    const { data: profile } = await admin
      .from("profiles")
      .select("terms_accepted_at, is_suspended")
      .eq("id", userId)
      .single();

    if (!profile) throw new HttpError(403, "no_profile");
    if (profile.is_suspended) throw new HttpError(403, "suspended");
    if (!profile.terms_accepted_at) throw new HttpError(403, "terms_not_accepted");

    const since = new Date(Date.now() - 3_600_000).toISOString();
    const { count } = await admin
      .from("upload_grants")
      .select("id", { count: "exact", head: true })
      .eq("user_id", userId)
      .gt("created_at", since);

    if ((count ?? 0) >= HOURLY_GRANT_LIMIT) throw new HttpError(429, "rate_limited");

    const key = buildKey(userId, kind as Kind, uploadId, index);
    const signed = await presignPut(key, contentType, bytes);

    await admin.from("upload_grants").insert({ user_id: userId, object_key: key });

    return json({ ...signed, key });
  } catch (e) {
    return errorResponse(e);
  }
});
