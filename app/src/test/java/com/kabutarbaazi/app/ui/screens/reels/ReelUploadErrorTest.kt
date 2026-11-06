package com.kabutarbaazi.app.ui.screens.reels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelUploadErrorTest {

    @Test
    fun `an unknown failure names the step and the cause`() {
        val msg = reelUploadError(
            "saving",
            IllegalStateException("new row for relation \"reels\" violates check constraint\nURL: https://x"),
        )
        assertTrue(msg, msg.contains("while saving"))
        assertTrue(msg, msg.contains("violates check constraint"))
        assertTrue(msg, !msg.contains("URL:"))
    }

    @Test
    fun `a cause without a message falls back to its type`() {
        val msg = reelUploadError("compressing", RuntimeException())
        assertTrue(msg, msg.contains("(RuntimeException)"))
    }

    @Test
    fun `size and rate limits keep their plain messages`() {
        assertEquals(
            "That video is still too big after compressing. Try a shorter clip.",
            reelUploadError("uploading", IllegalStateException("media_rejected:TooLarge")),
        )
        assertEquals(
            "Too many uploads just now. Please wait a few minutes.",
            reelUploadError("uploading", IllegalStateException("rate_limited")),
        )
    }
}
