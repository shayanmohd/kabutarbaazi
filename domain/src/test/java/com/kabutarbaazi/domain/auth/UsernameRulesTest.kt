package com.kabutarbaazi.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameRulesTest {

    @Test fun `accepts ordinary usernames`() {
        listOf("ravi", "imran_qureshi", "shabbir.bhai", "gurpreet99", "a1b").forEach {
            assertNull("expected $it to be valid", UsernameRules.validate(it))
        }
    }

    @Test fun `normalizes case and surrounding space`() {
        assertEquals("ravikumar", UsernameRules.normalize("  RaviKumar  "))
    }

    @Test fun `normalization folds full-width characters to ascii`() {
        // NFKC turns full-width Latin into plain Latin, so two visually identical usernames
        // cannot both be registered.
        assertEquals("ravi", UsernameRules.normalize("Ｒａｖｉ"))
    }

    @Test fun `rejects too short and too long`() {
        assertEquals(UsernameError.TooShort, UsernameRules.validate("ab"))
        assertEquals(UsernameError.TooLong, UsernameRules.validate("a".repeat(21)))
    }

    @Test fun `rejects usernames not starting with a letter`() {
        assertEquals(UsernameError.MustStartWithLetter, UsernameRules.validate("1ravi"))
        assertEquals(UsernameError.MustStartWithLetter, UsernameRules.validate("_ravi"))
    }

    @Test fun `rejects illegal characters including spaces and devanagari`() {
        assertEquals(UsernameError.IllegalCharacters, UsernameRules.validate("ravi kumar"))
        assertEquals(UsernameError.IllegalCharacters, UsernameRules.validate("ravi-kumar"))
        assertEquals(UsernameError.IllegalCharacters, UsernameRules.validate("रवि"))
    }

    @Test fun `rejects adjacent separators so lookalikes cannot both exist`() {
        assertEquals(UsernameError.AdjacentSeparators, UsernameRules.validate("ravi__kumar"))
        assertEquals(UsernameError.AdjacentSeparators, UsernameRules.validate("ravi..kumar"))
        assertEquals(UsernameError.AdjacentSeparators, UsernameRules.validate("ravi._kumar"))
    }

    @Test fun `rejects trailing separator`() {
        assertEquals(UsernameError.TrailingSeparator, UsernameRules.validate("ravi_"))
        assertEquals(UsernameError.TrailingSeparator, UsernameRules.validate("ravi."))
    }

    @Test fun `rejects reserved names that could impersonate the app`() {
        listOf("admin", "support", "kabutarbaazi", "official", "ADMIN").forEach {
            assertEquals("expected $it reserved", UsernameError.Reserved, UsernameRules.validate(it))
        }
    }

    @Test fun `empty input is reported as empty not too short`() {
        assertEquals(UsernameError.Empty, UsernameRules.validate("   "))
    }

    @Test fun `synthetic email is deterministic and lowercase`() {
        assertEquals("ravikumar@users.kamapathy.app", UsernameRules.syntheticEmail("RaviKumar"))
        assertEquals(
            UsernameRules.syntheticEmail("ravikumar"),
            UsernameRules.syntheticEmail("  RAVIKUMAR "),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `synthetic email refuses an invalid username`() {
        UsernameRules.syntheticEmail("ab")
    }

    @Test fun `synthetic email domain is a subdomain we control`() {
        // A synthetic address pointed at a domain we do not own would leak every signup.
        assertTrue(UsernameRules.EMAIL_DOMAIN.endsWith("kamapathy.app"))
    }
}
