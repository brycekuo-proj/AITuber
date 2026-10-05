package com.aituber.poc.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterStoreCatalogTest {
    @Test
    fun marketplaceIncludesExpandedFreeCharacterPool() {
        assertEquals(49, CharacterStoreCatalog.entries.size)
    }

    @Test
    fun charactersWithoutAppPermissionAreNotListed() {
        val ids = CharacterStoreCatalog.entries.map { it.id }.toSet()
        EXCLUDED_WITHOUT_PERMISSION.forEach { assertTrue("$it must not be listed", it !in ids) }
    }

    @Test
    fun officialSamplesAreOneTapBuiltInOrWaitingForRights() {
        val official = CharacterStoreCatalog.entries.filter { it.source == "Live2D Official" }
        assertEquals(29, official.size)
        val direct = official.filter { it.downloadFiles.isNotEmpty() }.map { it.id }.toSet()
        assertEquals(
            setOf("hiyori_momose", "niziiro_mao", "mark_kun", "ren_foster", "rice_glassfield", "wankoromochi", "koharu", "kei"),
            direct
        )
        official.filter { it.downloadFiles.isNotEmpty() }.forEach { entry ->
            assertTrue(entry.downloadFiles.all { it.startsWith("https://raw.githubusercontent.com/Live2D/") })
            assertTrue(entry.downloadFiles.first().endsWith(".model3.json"))
        }
        assertEquals(setOf("tororo", "hijiki", "haru"), official.mapNotNull { it.builtInProfileId }.toSet())
        assertEquals(
            setOf("zundamon", "hatsune_miku_sample", "jin_natori", "tsumiki_harugasa"),
            official.filter { it.unavailableReason != null }.map { it.id }.toSet()
        )
    }

    @Test
    fun directFileDownloadsUseHttps() {
        CharacterStoreCatalog.entries.flatMap { it.downloadFiles }.forEach { assertTrue(it, it.startsWith("https://")) }
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
