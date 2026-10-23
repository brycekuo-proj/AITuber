package com.aituber.poc.ui

import com.aituber.poc.character.live2d.Live2DCharacterProfiles
import com.aituber.poc.character.live2d.Live2DModelSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadedCharactersTest {
    @After
    fun resetInstalled() {
        Live2DCharacterProfiles.setInstalled(emptyList())
    }

    @Test
    fun sizesReadLikeAPhoneStorageScreen() {
        assertEquals("512 B", DownloadedCharacters.formatSize(512L))
        assertEquals("2 KB", DownloadedCharacters.formatSize(2_048L))
        assertEquals("1.5 MB", DownloadedCharacters.formatSize(1_572_864L))
        assertEquals("34.0 MB", DownloadedCharacters.formatSize(34L * 1024L * 1024L))
    }

    @Test
    fun onlyDownloadedCharactersCanBeDeleted() {
        val downloaded = Live2DCharacterProfiles.Tororo.copy(
            id = "store_hiyori_momose",
            displayName = "Hiyori",
            assetDir = "store_hiyori_momose/model",
            modelSource = Live2DModelSource.APP_PRIVATE
        )
        Live2DCharacterProfiles.setInstalled(listOf(downloaded))

        Live2DCharacterProfiles.builtIns.forEach { assertFalse(DownloadedCharacters.isDownloaded(it)) }
        assertTrue(DownloadedCharacters.isDownloaded(downloaded))
        assertEquals(listOf("store_hiyori_momose"), DownloadedCharacters.list().map { it.id })
        // The replacement after a deletion is always a built-in, never another download.
        assertFalse(DownloadedCharacters.isDownloaded(DownloadedCharacters.fallback))
    }
}
