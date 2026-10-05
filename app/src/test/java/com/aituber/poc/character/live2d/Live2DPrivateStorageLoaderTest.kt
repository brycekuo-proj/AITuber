package com.aituber.poc.character.live2d

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Live2DPrivateStorageLoaderTest {

    @Test
    fun builtInProfilesRemainApkAssets() {
        Live2DCharacterProfiles.all.forEach { profile ->
            assertEquals(Live2DModelSource.APK_ASSET, profile.modelSource)
        }
    }

    @Test
    fun privateCharacterDirectoryResolvesInsideCharactersRoot() {
        val filesDir = Files.createTempDirectory("aituber-files").toFile()
        val modelDir = File(filesDir, "characters/demo/model").apply { mkdirs() }

        val resolved = Live2DNativeBridge.resolvePrivateCharacterDir(
            filesDir = filesDir,
            relativeDir = "demo/model"
        )

        assertEquals(modelDir.canonicalPath, File(resolved).canonicalPath)
    }

    @Test(expected = IllegalArgumentException::class)
    fun privateCharacterDirectoryRejectsTraversal() {
        val filesDir = Files.createTempDirectory("aituber-files").toFile()
        File(filesDir, "characters").mkdirs()
        File(filesDir, "outside").mkdirs()

        Live2DNativeBridge.resolvePrivateCharacterDir(
            filesDir = filesDir,
            relativeDir = "../outside"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun privateCharacterDirectoryRejectsAbsolutePath() {
        val filesDir = Files.createTempDirectory("aituber-files").toFile()
        val outside = Files.createTempDirectory("aituber-outside").toFile()

        assertTrue(outside.isAbsolute)
        Live2DNativeBridge.resolvePrivateCharacterDir(
            filesDir = filesDir,
            relativeDir = outside.absolutePath
        )
    }
}
