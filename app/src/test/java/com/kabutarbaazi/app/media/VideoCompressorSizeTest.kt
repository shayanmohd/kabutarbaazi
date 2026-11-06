package com.kabutarbaazi.app.media

import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Test

@UnstableApi
class VideoCompressorSizeTest {

    @Test
    fun `a portrait clip encoded as 1280x720 with a rotation flag is stored as 720x1280`() {
        // Measured on the emulator: a 1080x1920 source came out of Transformer as 1280x720
        // with rotation=-90, and the reel row was written as landscape.
        assertEquals(720 to 1280, VideoCompressor.displaySize(1280, 720, 720, 1280))
    }

    @Test
    fun `an export already in display orientation is kept`() {
        assertEquals(720 to 1280, VideoCompressor.displaySize(720, 1280, 720, 1280))
        assertEquals(1280 to 720, VideoCompressor.displaySize(1280, 720, 1280, 720))
    }

    @Test
    fun `an unknown export size falls back to the target`() {
        assertEquals(720 to 1280, VideoCompressor.displaySize(0, 0, 720, 1280))
    }
}
