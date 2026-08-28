package com.kabutarbaazi.domain.moderation

import com.kabutarbaazi.domain.moderation.ModerationRules.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModerationRulesTest {

    // -- threshold ---------------------------------------------------------------------------

    @Test fun `threshold constant matches the SQL trigger`() {
        // This same number lives in submit_report() in 0002_functions_triggers.sql.
        // If you change one, change both in the same commit.
        assertEquals(3, ModerationRules.AUTO_HIDE_THRESHOLD)
    }

    @Test fun `hides at three distinct reporters`() {
        assertFalse(ModerationRules.shouldAutoHide(2))
        assertTrue(ModerationRules.shouldAutoHide(3))
        assertTrue(ModerationRules.shouldAutoHide(9))
    }

    @Test fun `admin content is never auto hidden`() {
        assertFalse(ModerationRules.shouldAutoHide(9, targetOwnerIsAdmin = true))
    }

    @Test fun `fresh accounts cannot brigade a competitor`() {
        val twoHours = 2L * 60 * 60 * 1000
        val twoDays = 48L * 60 * 60 * 1000
        assertFalse(ModerationRules.reportCountsTowardThreshold(twoHours))
        assertTrue(ModerationRules.reportCountsTowardThreshold(twoDays))
    }

    // -- keyword screen ----------------------------------------------------------------------

    @Test fun `ordinary listings pass clean`() {
        listOf(
            "Teddy jodi for sale, 6 months old, healthy",
            "लक्का कबूतर बिकाऊ है, अच्छी नस्ल",
            "سیالکوٹی کبوتر برائے فروخت",
            "Golden pair, ring number available, Delhi",
        ).forEach {
            assertEquals("expected clean: $it", Severity.Clean, ModerationRules.screen(it).severity)
        }
    }

    @Test fun `breed names are never caught by the abuse filter`() {
        // The whole risk of a keyword filter here is a false positive on pigeon slang.
        listOf("lakka", "banka", "teddy", "kamagar", "mookee", "kalsira", "sherazi")
            .forEach {
                assertEquals("breed $it was flagged", Severity.Clean, ModerationRules.screen(it).severity)
            }
    }

    @Test fun `advance payment pressure is flagged for review not blocked`() {
        // Flagged, because an honest seller discussing payment must not be silenced.
        val r = ModerationRules.screen("Bhai advance payment karo phir bhejta hu")
        assertEquals(Severity.Review, r.severity)
        assertTrue(r.matched.isNotEmpty())
    }

    @Test fun `the army officer scam is flagged`() {
        assertEquals(
            Severity.Review,
            ModerationRules.screen("I am army officer posted in Jammu, army quota delivery").severity,
        )
    }

    @Test fun `otp harvesting is flagged`() {
        assertEquals(Severity.Review, ModerationRules.screen("apna OTP batao jaldi").severity)
        assertEquals(Severity.Review, ModerationRules.screen("please send me the otp").severity)
    }

    @Test fun `scan to receive qr fraud is flagged`() {
        assertEquals(Severity.Review, ModerationRules.screen("ye QR code scan karo paisa aa jayega").severity)
    }

    @Test fun `clear abuse is blocked outright`() {
        val r = ModerationRules.screen("tu gandu hai")
        assertEquals(Severity.Block, r.severity)
    }

    @Test fun `abuse detection survives padded letters`() {
        // Users pad letters to dodge naive filters.
        assertEquals(Severity.Block, ModerationRules.screen("fuuuuck off").severity)
    }

    @Test fun `abuse outranks scam when both are present`() {
        val r = ModerationRules.screen("advance payment karo bhenchod")
        assertEquals(Severity.Block, r.severity)
    }

    @Test fun `blank text is clean`() {
        assertEquals(Severity.Clean, ModerationRules.screen("   ").severity)
    }
}
