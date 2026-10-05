package com.aituber.poc.character.live2d.install

import java.io.File
import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PackageArchiveTest {

    @Test
    fun shiftJisNamesAreDecoded() {
        val zip = TestPackages.zip(
            mapOf("モデル/ゴスロリ少女.model3.json" to "{}".toByteArray()),
            Charset.forName("Shift_JIS")
        )
        PackageArchive.open(zip).use { archive ->
            assertEquals(listOf("モデル/ゴスロリ少女.model3.json"), archive.entries.map { it.path })
            assertTrue(archive.nameCharset == "Shift_JIS" || archive.nameCharset == "windows-31j")
        }
    }

    @Test
    fun gbkNamesAreDecoded() {
        val zip = TestPackages.zip(mapOf("模型/小狐狸.model3.json" to "{}".toByteArray()), Charset.forName("GBK"))
        PackageArchive.open(zip).use { archive ->
            // GBK bytes are not valid UTF-8; Shift_JIS usually rejects them too.
            val path = archive.entries.single().path
            assertTrue(path, path.endsWith(".model3.json"))
            assertFalse(path.contains('\uFFFD'))
        }
    }

    @Test
    fun utf8NamesAreDecoded() {
        val zip = TestPackages.zip(mapOf("雪熊少女/model.model3.json" to "{}".toByteArray()))
        PackageArchive.open(zip).use { archive ->
            assertEquals("雪熊少女/model.model3.json", archive.entries.single().path)
            assertEquals("UTF-8", archive.nameCharset)
        }
    }

    @Test
    fun macJunkAndDirectoriesAreSkipped() {
        val zip = TestPackages.zip(
            linkedMapOf(
                "a/b.txt" to ByteArray(1),
                "__MACOSX/a/._b.txt" to ByteArray(1),
                "a/._c.txt" to ByteArray(1)
            )
        )
        PackageArchive.open(zip).use { archive ->
            assertEquals(listOf("a/b.txt"), archive.entries.map { it.path })
        }
    }

    @Test
    fun zipSlipEntriesAreDropped() {
        val zip = TestPackages.zip(linkedMapOf("../../evil.so" to ByteArray(1), "ok/file.txt" to ByteArray(1)))
        PackageArchive.open(zip).use { archive ->
            assertEquals(listOf("ok/file.txt"), archive.entries.map { it.path })
        }
    }

    @Test
    fun findIsCaseInsensitiveAndAcceptsBackslashes() {
        val zip = TestPackages.zip(mapOf("Model/Tex.2048/Texture_00.png" to ByteArray(1)))
        PackageArchive.open(zip).use { archive ->
            assertNotNull(archive.find("model\\tex.2048\\TEXTURE_00.PNG"))
            assertNull(archive.find("model/tex.2048/other.png"))
        }
    }

    @Test
    fun normalizePathRejectsEscapes() {
        assertEquals("a/c", PackageArchive.normalizePath("./a/b/../c"))
        assertEquals("a/b", PackageArchive.normalizePath("a\\b"))
        assertNull(PackageArchive.normalizePath("../a"))
        assertNull(PackageArchive.normalizePath("C:\\models\\a.moc3"))
        assertNull(PackageArchive.normalizePath("a/\u0000b"))
        assertEquals("dir/x.png", PackageArchive.resolve("dir", "x.png"))
        assertNull(PackageArchive.resolve("dir", "/etc/passwd"))
        assertNull(PackageArchive.resolve("dir", "../../x"))
    }

    @Test
    fun rarAnd7zAreReportedClearly() {
        val rar = File.createTempFile("pkg", ".rar").apply { writeBytes("Rar!\u001A\u0007\u0000xxxx".toByteArray(Charsets.ISO_8859_1)) }
        val sevenZip = File.createTempFile("pkg", ".7z").apply {
            writeBytes(byteArrayOf('7'.code.toByte(), 'z'.code.toByte(), 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C, 0, 4))
        }
        val text = File.createTempFile("pkg", ".zip").apply { writeText("hello world") }
        assertError(PackageError.UNSUPPORTED_ARCHIVE_RAR) { PackageArchive.open(rar) }
        assertError(PackageError.UNSUPPORTED_ARCHIVE_7Z) { PackageArchive.open(sevenZip) }
        assertError(PackageError.NOT_AN_ARCHIVE) { PackageArchive.open(text) }
    }

    @Test
    fun oversizedEntryIsRejected() {
        val zip = TestPackages.zip(mapOf("big.bin" to ByteArray(2048)))
        PackageArchive.open(zip, PackageLimits(maxEntryBytes = 1024)).use { archive ->
            assertError(PackageError.FILE_TOO_LARGE) { archive.readBytes(archive.entries.single()) }
        }
    }

    private fun assertError(expected: PackageError, block: () -> Unit) {
        try {
            block()
            fail("Expected $expected")
        } catch (e: PackageException) {
            assertEquals(expected, e.error)
        }
    }
}
