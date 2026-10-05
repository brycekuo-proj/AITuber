package com.aituber.poc.character.live2d.install

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** Limits for untrusted creator packages. */
data class PackageLimits(
    val maxArchiveBytes: Long = 300L * 1024 * 1024,
    val maxEntries: Int = 20_000,
    val maxEntryBytes: Long = 128L * 1024 * 1024,
    val maxJsonBytes: Long = 16L * 1024 * 1024,
    val maxTotalExtractedBytes: Long = 512L * 1024 * 1024
)

enum class ArchiveKind { ZIP, RAR, SEVEN_ZIP, UNKNOWN }

object ArchiveSniffer {
    fun sniff(file: File): ArchiveKind {
        val head = ByteArray(8)
        val read = file.inputStream().use { it.read(head) }
        if (read < 4) return ArchiveKind.UNKNOWN
        return when {
            head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() -> ArchiveKind.ZIP
            head[0] == 'R'.code.toByte() && head[1] == 'a'.code.toByte() &&
                head[2] == 'r'.code.toByte() && head[3] == '!'.code.toByte() -> ArchiveKind.RAR
            head[0] == '7'.code.toByte() && head[1] == 'z'.code.toByte() &&
                (head[2].toInt() and 0xFF) == 0xBC && (head[3].toInt() and 0xFF) == 0xAF -> ArchiveKind.SEVEN_ZIP
            else -> ArchiveKind.UNKNOWN
        }
    }
}

/**
 * A ZIP package with entry names decoded correctly and normalized.
 *
 * Creator packages from BOOTH are usually zipped on Japanese Windows, so the
 * file names are Shift_JIS without the ZIP UTF-8 flag; Chinese packages use
 * GBK. Opening them as UTF-8 either throws or produces garbage, and then
 * model3.json references no longer match any entry.
 *
 * The archive is opened as ISO-8859-1, which maps every byte to one char and
 * never fails, so the raw name bytes can be recovered and decoded with the
 * charset that fits the whole archive.
 */
class PackageArchive private constructor(
    private val zip: ZipFile,
    val entries: List<Entry>,
    val nameCharset: String,
    private val limits: PackageLimits
) : Closeable {

    /** [path] is normalized: forward slashes, no leading "./" or "/", no "." / ".." segments. */
    class Entry internal constructor(
        val path: String,
        val size: Long,
        internal val zipEntry: ZipEntry,
        /** Original name bytes when the name was not stored as UTF-8 (null otherwise). */
        internal val rawName: ByteArray? = null
    ) {
        val lowerPath: String = path.lowercase()
        val fileName: String = path.substringAfterLast('/')
        val directory: String = path.substringBeforeLast('/', "")
    }

    private val byLowerPath: Map<String, List<Entry>> = entries.groupBy { it.lowerPath }
    private val byExactPath: Map<String, Entry> = entries.associateBy { it.path }

    /**
     * Alternative decodings of non-UTF-8 names. The archive-wide charset guess
     * can be wrong (GBK bytes are often also valid Shift_JIS), so a reference
     * that matches nothing is retried against each other charset.
     */
    private val alternativeNames: Map<String, Entry> by lazy {
        val map = HashMap<String, Entry>()
        entries.forEach { entry ->
            val raw = entry.rawName ?: return@forEach
            FALLBACK_CHARSETS.forEach { charset ->
                if (!Charset.isSupported(charset)) return@forEach
                val decoded = decodeStrict(raw, charset) ?: return@forEach
                normalizePath(decoded)?.lowercase()?.let { map.putIfAbsent(it, entry) }
            }
        }
        map
    }

    /** Exact match first, then case-insensitive (Windows-authored packages), then other charsets. */
    fun find(path: String): Entry? {
        val normalized = normalizePath(path) ?: return null
        byExactPath[normalized]?.let { return it }
        byLowerPath[normalized.lowercase()]?.singleOrNull()?.let { return it }
        return alternativeNames[normalized.lowercase()]
    }

    fun readBytes(entry: Entry, maxBytes: Long = limits.maxEntryBytes): ByteArray {
        if (entry.size > maxBytes) {
            throw PackageException(PackageError.FILE_TOO_LARGE, "${entry.path} (${entry.size} bytes)")
        }
        zip.getInputStream(entry.zipEntry).use { input ->
            return readCapped(input, maxBytes, entry.path, sizeHint = entry.size)
        }
    }

    fun openStream(entry: Entry): InputStream = zip.getInputStream(entry.zipEntry)

    override fun close() = zip.close()

    companion object {
        private val LATIN1: Charset = Charsets.ISO_8859_1

        /** Charsets tried for names that are not valid UTF-8, in order. */
        private val FALLBACK_CHARSETS = listOf("UTF-8", "Shift_JIS", "windows-31j", "GBK", "Big5")

        fun open(file: File, limits: PackageLimits = PackageLimits()): PackageArchive {
            if (file.length() > limits.maxArchiveBytes) {
                throw PackageException(PackageError.ARCHIVE_TOO_LARGE, "${file.length()} bytes")
            }
            when (ArchiveSniffer.sniff(file)) {
                ArchiveKind.ZIP -> Unit
                ArchiveKind.RAR -> throw PackageException(PackageError.UNSUPPORTED_ARCHIVE_RAR, file.name)
                ArchiveKind.SEVEN_ZIP -> throw PackageException(PackageError.UNSUPPORTED_ARCHIVE_7Z, file.name)
                ArchiveKind.UNKNOWN -> throw PackageException(PackageError.NOT_AN_ARCHIVE, file.name)
            }
            val zip = try {
                ZipFile(file, LATIN1)
            } catch (e: ZipException) {
                throw PackageException(PackageError.CORRUPT_ARCHIVE, e.message ?: "ZipException")
            }
            try {
                val raw = ArrayList<ZipEntry>()
                val enumeration = zip.entries()
                while (enumeration.hasMoreElements()) {
                    raw += enumeration.nextElement()
                    if (raw.size > limits.maxEntries) {
                        throw PackageException(PackageError.TOO_MANY_FILES, "> ${limits.maxEntries}")
                    }
                }
                val rawNames = raw.map { recoverRawName(it.name) }
                val charset = pickCharset(rawNames)
                val entries = raw.mapIndexedNotNull { index, zipEntry ->
                    if (zipEntry.isDirectory) return@mapIndexedNotNull null
                    val decoded = rawNames[index].decode(charset)
                    val path = normalizePath(decoded) ?: return@mapIndexedNotNull null
                    if (path.startsWith("__MACOSX/") || path.substringAfterLast('/').startsWith("._")) {
                        return@mapIndexedNotNull null
                    }
                    val raw = rawNames[index].takeIf { it.alreadyDecoded == null && it.bytes.any { b -> b < 0 } }?.bytes
                    Entry(path, zipEntry.size, zipEntry, raw)
                }
                return PackageArchive(zip, entries, charset, limits)
            } catch (e: Throwable) {
                zip.close()
                throw e
            }
        }

        /**
         * Name as seen through ISO-8859-1: chars > 0xFF mean ZipFile already
         * decoded it as UTF-8 (the entry had the UTF-8 flag), otherwise every
         * char is one original byte.
         */
        private class RawName(val alreadyDecoded: String?, val bytes: ByteArray) {
            fun decode(charset: String): String =
                alreadyDecoded ?: decodeStrict(bytes, charset) ?: String(bytes, LATIN1)
        }

        private fun recoverRawName(latin1Name: String): RawName =
            if (latin1Name.any { it.code > 0xFF }) {
                RawName(latin1Name, ByteArray(0))
            } else {
                RawName(null, latin1Name.toByteArray(LATIN1))
            }

        private fun pickCharset(names: List<RawName>): String {
            val undecoded = names.filter { it.alreadyDecoded == null && it.bytes.any { b -> b < 0 } }
            if (undecoded.isEmpty()) return "UTF-8"
            for (candidate in FALLBACK_CHARSETS) {
                if (!Charset.isSupported(candidate)) continue
                if (undecoded.all { decodeStrict(it.bytes, candidate) != null }) return candidate
            }
            return "ISO-8859-1"
        }

        private fun decodeStrict(bytes: ByteArray, charsetName: String): String? = try {
            Charset.forName(charsetName).newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }

        /**
         * Normalizes a path from an archive or a model3.json reference.
         * Returns null for anything that escapes the package root.
         */
        fun normalizePath(path: String): String? {
            val parts = ArrayList<String>()
            for (segment in path.replace('\\', '/').split('/')) {
                when (segment) {
                    "", "." -> Unit
                    ".." -> if (parts.isEmpty()) return null else parts.removeAt(parts.size - 1)
                    else -> {
                        if (segment.indexOf('\u0000') >= 0) return null
                        // Drive letters ("C:") are not relative paths.
                        if (parts.isEmpty() && segment.length == 2 && segment[1] == ':') return null
                        parts += segment
                    }
                }
            }
            return if (parts.isEmpty()) null else parts.joinToString("/")
        }

        /** Resolves [reference] relative to [baseDir] (both normalized, baseDir may be ""). */
        fun resolve(baseDir: String, reference: String): String? {
            val cleaned = reference.replace('\\', '/').trim()
            if (cleaned.isEmpty() || cleaned.startsWith("/")) return null
            return normalizePath(if (baseDir.isEmpty()) cleaned else "$baseDir/$cleaned")
        }

        internal fun readCapped(input: InputStream, maxBytes: Long, label: String, sizeHint: Long = -1): ByteArray {
            // Pre-size from the ZIP header so large textures are not copied while growing.
            val initial = if (sizeHint in 1..maxBytes) sizeHint.toInt() + 1 else 64 * 1024
            val out = ByteArrayOutputStream(initial)
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > maxBytes) throw PackageException(PackageError.FILE_TOO_LARGE, label)
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }
    }
}
