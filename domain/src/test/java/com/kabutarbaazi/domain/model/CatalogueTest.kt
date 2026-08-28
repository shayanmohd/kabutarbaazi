package com.kabutarbaazi.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueTest {

    @Test fun `breed slugs are unique`() {
        assertEquals(Breeds.ALL.size, Breeds.ALL.map { it.slug }.toSet().size)
    }

    @Test fun `region codes are unique`() {
        assertEquals(Regions.ALL.size, Regions.ALL.map { it.code }.toSet().size)
    }

    @Test fun `every breed carries all three scripts`() {
        Breeds.ALL.forEach {
            assertTrue("${it.slug} missing English", it.nameEn.isNotBlank())
            assertTrue("${it.slug} missing Hindi", it.nameHi.isNotBlank())
            assertTrue("${it.slug} missing Urdu", it.nameUr.isNotBlank())
        }
    }

    @Test fun `every region carries all three scripts`() {
        Regions.ALL.forEach {
            assertTrue("${it.code} missing English", it.nameEn.isNotBlank())
            assertTrue("${it.code} missing Hindi", it.nameHi.isNotBlank())
            assertTrue("${it.code} missing Urdu", it.nameUr.isNotBlank())
        }
    }

    @Test fun `hindi names are actually devanagari`() {
        // Guards against a copy-paste that leaves the English name in the Hindi column.
        Breeds.ALL.filter { it.slug != "other" }.forEach {
            assertTrue("${it.slug} Hindi is not Devanagari", it.nameHi.any { c -> c in 'ऀ'..'ॿ' })
        }
    }

    @Test fun `urdu names are actually arabic script`() {
        Breeds.ALL.forEach {
            assertTrue("${it.slug} Urdu is not Arabic script", it.nameUr.any { c -> c in '؀'..'ۿ' })
        }
    }

    @Test fun `the regions users asked for are all present`() {
        listOf("delhi", "west_up", "east_up", "punjab", "haryana", "bihar").forEach {
            assertTrue("missing region $it", Regions.isValid(it))
        }
    }

    @Test fun `unknown slugs are rejected`() {
        assertFalse(Breeds.isValid("pterodactyl"))
        assertFalse(Regions.isValid("atlantis"))
    }

    @Test fun `other is present as an escape hatch in both catalogues`() {
        // Regional naming varies enormously; a closed list would turn sellers away.
        assertTrue(Breeds.isValid("other"))
        assertTrue(Regions.isValid("other"))
    }
}
