package com.aituber.poc.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterStoreCatalogTest {
    @Test
    fun marketplaceIncludesExpandedFreeCharacterPool() {
        assertEquals(60, CharacterStoreCatalog.entries.size)
    }

    @Test
    fun everyMarketplaceUrlUsesHttps() {
        CharacterStoreCatalog.entries.forEach { entry ->
            assertTrue(entry.sourceUrl.startsWith("https://"))
            assertTrue(entry.downloadUrl.startsWith("https://"))
            assertTrue(entry.previewUrl.isBlank() || entry.previewUrl.startsWith("https://"))
        }
    }

    @Test
    fun marketplaceIdsAreUnique() {
        val ids = CharacterStoreCatalog.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
