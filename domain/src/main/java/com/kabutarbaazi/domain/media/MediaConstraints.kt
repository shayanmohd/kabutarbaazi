package com.kabutarbaazi.domain.media

/**
 * Upload caps. These are enforced twice on purpose: here for immediate user feedback, and again
 * inside the presign Edge Function, which is the boundary a modified client cannot cross.
 *
 * Sized against Cloudflare R2's free tier: 10 GB stored, egress free. Egress being free is the
 * whole reason media is not in Supabase Storage, whose 5 GB/month would cover roughly a thousand
 * reel views in total. Storage is the binding constraint here, and it is cheap to extend at
 * about $0.015 per GB-month.
 *
 * At a 12 MB ceiling per reel, 10 GB is roughly 850 reels.
 */
object MediaConstraints {

    const val REEL_MAX_DURATION_MS = 60_000
    const val REEL_HARD_REJECT_DURATION_MS = 90_000
    const val REEL_MAX_BYTES = 15L * 1024 * 1024

    const val LISTING_VIDEO_MAX_DURATION_MS = 30_000
    const val LISTING_VIDEO_MAX_BYTES = 8L * 1024 * 1024

    const val IMAGE_MAX_BYTES = 2L * 1024 * 1024
    const val AVATAR_MAX_BYTES = 200L * 1024
    const val THUMB_MAX_BYTES = 60L * 1024

    const val LISTING_MAX_IMAGES = 6
    const val LISTING_MAX_VIDEOS = 1
    const val POST_MAX_IMAGES = 4

    const val IMAGE_LONGEST_EDGE = 1440
    const val AVATAR_EDGE = 512
    const val THUMB_LONGEST_EDGE = 480

    fun maxBytes(kind: MediaKind): Long = when (kind) {
        MediaKind.ReelVideo -> REEL_MAX_BYTES
        MediaKind.ListingVideo -> LISTING_VIDEO_MAX_BYTES
        MediaKind.ListingImage, MediaKind.PostImage -> IMAGE_MAX_BYTES
        MediaKind.Avatar -> AVATAR_MAX_BYTES
        MediaKind.Thumbnail -> THUMB_MAX_BYTES
    }

    fun maxDurationMs(kind: MediaKind): Int? = when (kind) {
        MediaKind.ReelVideo -> REEL_MAX_DURATION_MS
        MediaKind.ListingVideo -> LISTING_VIDEO_MAX_DURATION_MS
        else -> null
    }

    /** Content types the presign function will sign. Anything else is refused. */
    fun allowedContentType(kind: MediaKind): String = when (kind) {
        MediaKind.ReelVideo, MediaKind.ListingVideo -> "video/mp4"
        else -> "image/webp"
    }

    fun check(kind: MediaKind, bytes: Long, durationMs: Int? = null): MediaRejection? {
        if (bytes <= 0) return MediaRejection.Empty
        if (bytes > maxBytes(kind)) return MediaRejection.TooLarge
        val maxDuration = maxDurationMs(kind)
        if (maxDuration != null) {
            if (durationMs == null) return MediaRejection.UnknownDuration
            if (kind == MediaKind.ReelVideo && durationMs > REEL_HARD_REJECT_DURATION_MS) {
                return MediaRejection.TooLong
            }
            if (kind != MediaKind.ReelVideo && durationMs > maxDuration) return MediaRejection.TooLong
        }
        return null
    }
}

enum class MediaKind { ReelVideo, ListingVideo, ListingImage, PostImage, Avatar, Thumbnail }

enum class MediaRejection { Empty, TooLarge, TooLong, UnknownDuration }
