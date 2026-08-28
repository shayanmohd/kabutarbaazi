package com.kabutarbaazi.domain.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaConstraintsTest {

    @Test fun `accepts a reel inside the caps`() {
        assertNull(MediaConstraints.check(MediaKind.ReelVideo, bytes = 5L * 1024 * 1024, durationMs = 28_000))
    }

    @Test fun `rejects an oversized reel`() {
        assertEquals(
            MediaRejection.TooLarge,
            MediaConstraints.check(MediaKind.ReelVideo, bytes = 20L * 1024 * 1024, durationMs = 30_000),
        )
    }

    @Test fun `rejects a reel past the hard duration limit`() {
        assertEquals(
            MediaRejection.TooLong,
            MediaConstraints.check(MediaKind.ReelVideo, bytes = 1024, durationMs = 120_000),
        )
    }

    @Test fun `a reel between the target and the hard limit is accepted for trimming`() {
        // 60s is the target and 90s the hard reject, so a 75s clip is allowed through and the
        // upload screen offers to trim it rather than refusing outright.
        assertNull(MediaConstraints.check(MediaKind.ReelVideo, bytes = 1024, durationMs = 75_000))
    }

    @Test fun `listing video duration is enforced strictly`() {
        assertEquals(
            MediaRejection.TooLong,
            MediaConstraints.check(MediaKind.ListingVideo, bytes = 1024, durationMs = 45_000),
        )
    }

    @Test fun `video without a known duration is refused`() {
        assertEquals(
            MediaRejection.UnknownDuration,
            MediaConstraints.check(MediaKind.ReelVideo, bytes = 1024, durationMs = null),
        )
    }

    @Test fun `zero byte files are refused`() {
        assertEquals(MediaRejection.Empty, MediaConstraints.check(MediaKind.ListingImage, bytes = 0))
    }

    @Test fun `images need no duration`() {
        assertNull(MediaConstraints.check(MediaKind.ListingImage, bytes = 500L * 1024))
    }

    @Test fun `content types are restricted to what the presign function will sign`() {
        assertEquals("video/mp4", MediaConstraints.allowedContentType(MediaKind.ReelVideo))
        assertEquals("image/webp", MediaConstraints.allowedContentType(MediaKind.ListingImage))
        assertEquals("image/webp", MediaConstraints.allowedContentType(MediaKind.Avatar))
    }

    @Test fun `free tier runway is roughly what we planned for`() {
        // 10 GB of R2 free storage divided by the worst-case reel. If this assertion ever fails
        // the caps moved and the storage forecast in the plan is stale.
        val worstCaseReel = MediaConstraints.REEL_MAX_BYTES
        val runway = (10L * 1024 * 1024 * 1024) / worstCaseReel
        assert(runway in 600..900) { "expected 600-900 reels of runway, got $runway" }
    }
}
