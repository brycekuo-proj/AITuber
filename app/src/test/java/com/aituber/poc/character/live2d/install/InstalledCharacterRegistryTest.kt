package com.aituber.poc.character.live2d.install

import com.aituber.poc.character.live2d.Live2DCharacterProfiles
import com.aituber.poc.character.live2d.Live2DModelSource
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledCharacterRegistryTest {

    @After
    fun resetInstalled() {
        Live2DCharacterProfiles.setInstalled(emptyList())
    }

    private fun install(root: File, id: String): CharacterManifest {
        val staging = InstalledCharacterRegistry.createStagingDir(root, id)
        val result = PackageArchive.open(TestPackages.boothStylePackage()).use { archive ->
            Live2DPackageConverter().convert(archive, staging, Live2DPackageConverter.Request(id, "角色 $id"))
        }
        InstalledCharacterRegistry.commit(root, staging, id)
        return result.manifest
    }

    @Test
    fun installedCharactersAreScannedAndJoinTheCarousel() {
        val root = File(TestPackages.tempDir("files"), "characters")
        install(root, "store_goth")
        install(root, "local_other_1a2b3c4d")

        val manifests = InstalledCharacterRegistry.scan(root)
        assertEquals(setOf("store_goth", "local_other_1a2b3c4d"), manifests.map { it.id }.toSet())

        Live2DCharacterProfiles.setInstalled(manifests.map { it.toProfile() })
        val all = Live2DCharacterProfiles.all
        assertEquals(Live2DCharacterProfiles.builtIns.size + 2, all.size)
        val goth = Live2DCharacterProfiles.byId("store_goth")
        assertEquals(Live2DModelSource.APP_PRIVATE, goth.modelSource)
        assertEquals("store_goth/model", goth.assetDir)
        // Carousel wraps from the last built-in into installed characters and back.
        assertEquals(all[Live2DCharacterProfiles.builtIns.size].id, Live2DCharacterProfiles.next(Live2DCharacterProfiles.builtIns.last().id).id)
        assertEquals(Live2DCharacterProfiles.builtIns.first().id, Live2DCharacterProfiles.next(all.last().id).id)
    }

    @Test
    fun reinstallReplacesTheOldFolder() {
        val root = File(TestPackages.tempDir("files"), "characters")
        install(root, "store_goth")
        File(root, "store_goth/stale.txt").writeText("old")
        install(root, "store_goth")
        assertFalse(File(root, "store_goth/stale.txt").exists())
        assertEquals(listOf("store_goth"), root.list()!!.filter { !it.startsWith(".") })
    }

    @Test
    fun removeDeletesOnlyThatCharacter() {
        val root = File(TestPackages.tempDir("files"), "characters")
        install(root, "store_goth")
        install(root, "store_other")
        assertTrue(InstalledCharacterRegistry.sizeBytes(root, "store_goth") > 0L)

        assertTrue(InstalledCharacterRegistry.remove(root, "store_goth"))
        assertFalse(File(root, "store_goth").exists())
        assertEquals(listOf("store_other"), InstalledCharacterRegistry.scan(root).map { it.id })
        assertEquals(listOf("store_other"), root.list()!!.filter { !it.startsWith(".") })
        assertEquals(0L, InstalledCharacterRegistry.sizeBytes(root, "store_goth"))
        // Already gone counts as removed.
        assertTrue(InstalledCharacterRegistry.remove(root, "store_goth"))
    }

    @Test
    fun removeRefusesBuiltInsAndUnsafeIds() {
        val files = TestPackages.tempDir("files")
        val root = File(files, "characters").apply { mkdirs() }
        val outside = File(files, "keep.txt").apply { writeText("x") }
        val builtInFolder = File(root, "tororo").apply { mkdirs() }

        listOf("tororo", "haru", "../keep.txt", "..", "a/b", "", ".hidden").forEach { id ->
            assertTrue("should refuse \"$id\"", runCatching { InstalledCharacterRegistry.remove(root, id) }.isFailure)
        }
        assertTrue(outside.exists())
        assertTrue(builtInFolder.exists())
        assertEquals(0L, InstalledCharacterRegistry.sizeBytes(root, "../keep.txt"))
    }

    @Test
    fun brokenOrMismatchedFoldersAreSkipped() {
        val root = File(TestPackages.tempDir("files"), "characters")
        install(root, "store_goth")
        // Folder name does not match manifest id.
        File(root, "store_goth").copyRecursively(File(root, "renamed"))
        // Manifest is garbage.
        File(root, "garbage").mkdirs()
        File(root, "garbage/${CharacterManifest.FILE_NAME}").writeText("{not json")
        // Model files deleted.
        install(root, "store_gone")
        File(root, "store_gone/model/model.model3.json").delete()

        assertEquals(listOf("store_goth"), InstalledCharacterRegistry.scan(root).map { it.id })
    }

    @Test
    fun installedIdsCannotShadowBuiltIns() {
        val fake = CharacterManifest.fromJson(
            MiniJson.parseObject(
                """{"SchemaVersion":1,"Id":"haru","DisplayName":"Fake","Parameters":{}}""".toByteArray()
            )
        )
        Live2DCharacterProfiles.setInstalled(listOf(fake.toProfile()))
        assertEquals(Live2DModelSource.APK_ASSET, Live2DCharacterProfiles.byId("haru").modelSource)
        assertEquals(Live2DCharacterProfiles.builtIns.size, Live2DCharacterProfiles.all.size)
    }

    @Test
    fun manifestRejectsUnsafeValues() {
        fun parse(json: String) = runCatching { CharacterManifest.fromJson(MiniJson.parseObject(json.toByteArray())) }
        assertTrue(parse("""{"SchemaVersion":1,"Id":"../x","Parameters":{}}""").isFailure)
        assertTrue(parse("""{"SchemaVersion":1,"Id":"ok","ModelDir":"../../x","Parameters":{}}""").isFailure)
        assertTrue(parse("""{"SchemaVersion":1,"Id":"ok","Model3File":"a/b.model3.json","Parameters":{}}""").isFailure)
        assertTrue(parse("""{"SchemaVersion":99,"Id":"ok","Parameters":{}}""").isFailure)
        assertTrue(parse("""{"SchemaVersion":1,"Id":"ok","Parameters":{}}""").isSuccess)
    }

    @Test
    fun slugsAreValidIds() {
        assertEquals("goth_lolita", CharacterPackageInstaller.slug("goth_lolita"))
        assertEquals("model", CharacterPackageInstaller.slug("ゴスロリ少女"))
        assertEquals("scarlett_vts", CharacterPackageInstaller.slug("Scarlett_vts"))
        assertTrue(CharacterManifest.isValidId("store_" + CharacterPackageInstaller.slug("A".repeat(200))))
    }
}

class DirectFileNamesTest {
    @Test
    fun rawGithubFilesKeepTheirFolderLayout() {
        val base = "https://raw.githubusercontent.com/Speykious/arch-chan/main/"
        val names = CharacterPackageInstaller.relativeNames(
            listOf(
                base + "Live2D/arch%20chan%20model0.model3.json",
                base + "Live2D/Arch%20Chan%20Model0.2048/texture_00.png",
                base + "LICENSE.md"
            )
        )
        assertEquals(
            listOf("Live2D/arch chan model0.model3.json", "Live2D/Arch Chan Model0.2048/texture_00.png", "LICENSE.md"),
            names
        )
    }

    @Test
    fun singleFolderIsStripped() {
        val names = CharacterPackageInstaller.relativeNames(
            listOf("https://example.com/a/b/m.model3.json", "https://example.com/a/b/tex/t.png")
        )
        assertEquals(listOf("m.model3.json", "tex/t.png"), names)
    }
}
