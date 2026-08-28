package com.kabutarbaazi.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberTest {

    @Test fun `accepts valid indian mobiles`() {
        listOf("+919876543210", "+916000000000", "+918123456789").forEach {
            assertTrue("$it should be valid", PhoneNumber.isValid(it))
        }
    }

    @Test fun `rejects indian numbers starting below six`() {
        // Indian mobile numbers start 6-9. A landline cannot receive WhatsApp, so accepting one
        // would produce a contact button that silently goes nowhere.
        assertFalse(PhoneNumber.isValid("+915876543210"))
        assertFalse(PhoneNumber.isValid("+911123456789"))
    }

    @Test fun `accepts valid pakistani mobiles`() {
        assertTrue(PhoneNumber.isValid("+923001234567"))
        assertFalse(PhoneNumber.isValid("+924001234567"))
    }

    @Test fun `parse strips formatting and a habitual leading zero`() {
        assertEquals("+919876543210", PhoneNumber.parse("+91", "098765 43210"))
        assertEquals("+919876543210", PhoneNumber.parse("91", "98765-43210"))
        assertEquals("+919876543210", PhoneNumber.parse("+91", "(98765) 43210"))
    }

    @Test fun `parse returns null for unusable input`() {
        assertNull(PhoneNumber.parse("+91", ""))
        assertNull(PhoneNumber.parse("+91", "12345"))
        assertNull(PhoneNumber.parse("", "9876543210"))
    }

    @Test fun `wa me url drops the plus and percent encodes the prefill`() {
        assertEquals("https://wa.me/919876543210", PhoneNumber.waMeUrl("+919876543210"))
        assertEquals(
            "https://wa.me/919876543210?text=Salaam%2C%20kabutar%20ke%20baare%20mein",
            PhoneNumber.waMeUrl("+919876543210", "Salaam, kabutar ke baare mein"),
        )
    }

    @Test fun `wa me url encodes space as percent twenty not plus`() {
        // URLEncoder would emit '+', which WhatsApp renders literally as a plus sign.
        val url = PhoneNumber.waMeUrl("+919876543210", "do teddy")
        assertTrue(url.endsWith("text=do%20teddy"))
        assertFalse(url.contains("+teddy"))
    }

    @Test fun `wa me url handles devanagari and urdu prefill`() {
        val hi = PhoneNumber.waMeUrl("+919876543210", "कबूतर")
        assertTrue(hi.contains("%E0%A4%95"))
        val ur = PhoneNumber.waMeUrl("+923001234567", "کبوتر")
        assertTrue(ur.contains("%DA%A9"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `wa me url refuses an invalid number`() {
        PhoneNumber.waMeUrl("12345")
    }

    @Test fun `mask reveals only the last two digits`() {
        assertEquals("•••••• 10", PhoneNumber.mask("+919876543210"))
    }
}
