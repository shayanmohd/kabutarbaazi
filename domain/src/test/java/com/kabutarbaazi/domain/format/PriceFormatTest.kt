package com.kabutarbaazi.domain.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceFormatTest {

    @Test fun `groups in the south asian system not the western one`() {
        // A seller in Delhi reads 1,50,000 as one lakh fifty thousand. 150,000 reads as foreign.
        assertEquals("1,50,000", PriceFormat.groupSouthAsian(150_000))
        assertEquals("12,34,567", PriceFormat.groupSouthAsian(1_234_567))
        assertEquals("1,00,00,000", PriceFormat.groupSouthAsian(10_000_000))
    }

    @Test fun `short numbers are not grouped`() {
        assertEquals("0", PriceFormat.groupSouthAsian(0))
        assertEquals("999", PriceFormat.groupSouthAsian(999))
        assertEquals("1,000", PriceFormat.groupSouthAsian(1_000))
    }

    @Test fun `formats rupees from paise and drops minor units`() {
        assertEquals("₹12,500", PriceFormat.format(1_250_000))
        assertEquals("₹850", PriceFormat.format(85_000))
    }

    @Test fun `pakistani listings use Rs not the rupee sign`() {
        assertEquals("Rs4,500", PriceFormat.format(450_000, "PKR"))
    }

    @Test fun `compact form uses lakh and crore`() {
        assertEquals("₹850", PriceFormat.formatCompact(85_000))
        assertEquals("₹4.5K", PriceFormat.formatCompact(450_000))
        assertEquals("₹1.5L", PriceFormat.formatCompact(15_000_000))
        assertEquals("₹1.2Cr", PriceFormat.formatCompact(1_200_000_000))
    }

    @Test fun `compact form trims pointless decimals`() {
        assertEquals("₹2L", PriceFormat.formatCompact(20_000_000))
        assertEquals("₹5K", PriceFormat.formatCompact(500_000))
    }

    @Test fun `output digits are always ascii even for urdu speakers`() {
        // NumberFormat under ur-PK can emit Eastern Arabic-Indic digits. In a marketplace a price
        // shown as ۴۵۰۰ reads as a bug to exactly the users it is meant to serve.
        val s = PriceFormat.format(450_000) + PriceFormat.formatCompact(15_000_000)
        assertTrue(s.filter { it.isDigit() }.all { it in '0'..'9' })
    }

    @Test fun `parses what a user typed`() {
        assertEquals(450_000L, PriceFormat.parseToMinor("4500"))
        assertEquals(450_000L, PriceFormat.parseToMinor("4,500"))
        assertEquals(450_000L, PriceFormat.parseToMinor("₹ 4500"))
    }

    @Test fun `rejects empty and absurd prices`() {
        assertNull(PriceFormat.parseToMinor(""))
        assertNull(PriceFormat.parseToMinor("abc"))
        // Above ten lakh a pigeon listing is a typo or a scam.
        assertNull(PriceFormat.parseToMinor("99999999"))
    }
}
