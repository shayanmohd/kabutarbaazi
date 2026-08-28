package com.kabutarbaazi.domain.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscodeTargetTest {

    @Test fun `scales a 1080p portrait clip down to 720 on the short side`() {
        val t = TranscodeTarget.forSource(1080, 1920)
        assertEquals(720, t.width)
        assertEquals(1280, t.height)
    }

    @Test fun `scales a landscape clip on its short side too`() {
        val t = TranscodeTarget.forSource(1920, 1080)
        assertEquals(1280, t.width)
        assertEquals(720, t.height)
    }

    @Test fun `never upscales a small source`() {
        val t = TranscodeTarget.forSource(480, 854)
        assertEquals(480, t.width)
        assertEquals(854, t.height)
    }

    @Test fun `dimensions are always even`() {
        // H.264 with 4:2:0 chroma cannot encode odd dimensions, and several hardware encoders
        // fail outright rather than rounding for you.
        listOf(1079 to 1921, 721 to 1281, 999 to 333).forEach { (w, h) ->
            val t = TranscodeTarget.forSource(w, h)
            assertEquals("width odd for $w x $h", 0, t.width % 2)
            assertEquals("height odd for $w x $h", 0, t.height % 2)
        }
    }

    @Test fun `never spends more bits than the source had`() {
        val t = TranscodeTarget.forSource(720, 1280, sourceBitrate = 800_000)
        assertEquals(800_000, t.videoBitrate)
    }

    @Test fun `caps bitrate for a high bitrate source`() {
        val t = TranscodeTarget.forSource(1080, 1920, sourceBitrate = 12_000_000)
        assertEquals(TranscodeTarget.VIDEO_BITRATE, t.videoBitrate)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects nonsense dimensions`() {
        TranscodeTarget.forSource(0, 1920)
    }

    @Test fun `a thirty second reel lands comfortably inside the upload cap`() {
        val t = TranscodeTarget.forSource(1080, 1920)
        val bytes = TranscodeTarget.estimatedBytes(30_000, t)
        assertTrue("30s estimated at $bytes bytes", bytes < MediaConstraints.REEL_MAX_BYTES)
        // Sanity: roughly 6 MB, matching the plan's 4-5 MB working figure plus audio.
        assertTrue(bytes in 5_000_000..8_000_000)
    }
}
