package com.aituber.poc.character.live2d.install

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import com.aituber.poc.character.live2d.Live2DCharacterProfiles
import com.aituber.poc.character.live2d.Live2DProfileStore
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Downloads or imports a creator package, converts it and registers it.
 * All work runs on one background thread; callbacks arrive on the main thread.
 *
 * The package is converted on the phone on purpose: most creators forbid
 * redistribution, so AITuber never hosts converted models — each user
 * fetches the original from the creator and converts it locally.
 */
class CharacterPackageInstaller(context: Context) {

    sealed class Outcome {
        data class Installed(val manifest: CharacterManifest, val warnings: List<String>) : Outcome()
        data class Failed(val error: PackageError, val detail: String) : Outcome()
    }

    /** Store metadata for an install started from a store card. */
    data class StoreInfo(
        val storeId: String,
        val displayName: String,
        val sourceUrl: String,
        val license: String = "",
        val credit: String = ""
    )

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    /**
     * Downloads a package ZIP and installs it. [headers] supplies per-request headers,
     * e.g. the in-app download page's cookies and user agent for a logged-in BOOTH download.
     */
    fun installFromUrl(
        url: String,
        store: StoreInfo,
        headers: (URL) -> Map<String, String> = { emptyMap() },
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
        onDone: (Outcome) -> Unit
    ) {
        executor.execute {
            val download = File(appContext.cacheDir, "package-download-${System.nanoTime()}.zip")
            val outcome = try {
                downloadTo(url, download, headers) { done, total -> main.post { onProgress(done, total) } }
                convertAndInstall(download, "store_${slug(store.storeId)}", store.displayName, store.toSource(url))
            } catch (e: PackageException) {
                Outcome.Failed(e.error, e.message ?: "")
            } catch (e: IOException) {
                Outcome.Failed(PackageError.DOWNLOAD_FAILED, e.message ?: e.javaClass.simpleName)
            } catch (e: Throwable) {
                // Includes OutOfMemoryError: an Error escaping this thread would kill the app.
                Log.e(TAG, "install failed", e)
                Outcome.Failed(PackageError.INTERNAL, e.message ?: e.javaClass.simpleName)
            } finally {
                download.delete()
            }
            main.post { onDone(outcome) }
        }
    }

    /**
     * Downloads individual public files (e.g. raw GitHub files of just the Live2D
     * folder), packs them into a ZIP keeping their relative paths, and installs it.
     */
    fun installFromFiles(
        urls: List<String>,
        store: StoreInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
        onDone: (Outcome) -> Unit
    ) {
        executor.execute {
            val zip = File(appContext.cacheDir, "package-files-${System.nanoTime()}.zip")
            val part = File(appContext.cacheDir, "package-part-${System.nanoTime()}")
            val outcome = try {
                val names = relativeNames(urls)
                var doneBytes = 0L
                java.util.zip.ZipOutputStream(zip.outputStream()).use { out ->
                    urls.forEachIndexed { index, url ->
                        val base = doneBytes
                        downloadTo(url, part) { done, _ -> main.post { onProgress(base + done, -1L) } }
                        doneBytes += part.length()
                        out.putNextEntry(java.util.zip.ZipEntry(names[index]))
                        part.inputStream().use { it.copyTo(out) }
                        out.closeEntry()
                    }
                }
                convertAndInstall(zip, "store_${slug(store.storeId)}", store.displayName, store.toSource(urls.first()))
            } catch (e: PackageException) {
                Outcome.Failed(e.error, e.message ?: "")
            } catch (e: IOException) {
                Outcome.Failed(PackageError.DOWNLOAD_FAILED, e.message ?: e.javaClass.simpleName)
            } catch (e: Throwable) {
                Log.e(TAG, "install failed", e)
                Outcome.Failed(PackageError.INTERNAL, e.message ?: e.javaClass.simpleName)
            } finally {
                zip.delete()
                part.delete()
            }
            main.post { onDone(outcome) }
        }
    }

    /**
     * Imports a ZIP the user picked (e.g. downloaded from BOOTH in the browser).
     * [store] is set when the import was started from a store card.
     */
    fun installFromUri(uri: Uri, store: StoreInfo?, onDone: (Outcome) -> Unit) {
        executor.execute {
            val copy = File(appContext.cacheDir, "package-import-${System.nanoTime()}.zip")
            val outcome = try {
                val originalName = displayName(uri) ?: "package.zip"
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    copyCapped(input, copy, PackageLimits().maxArchiveBytes)
                } ?: throw PackageException(PackageError.STORAGE_FAILED, "cannot open $uri")
                val id = if (store != null) {
                    "store_${slug(store.storeId)}"
                } else {
                    "local_${slug(originalName.substringBeforeLast('.'))}_${shortHash(copy)}"
                }
                val source = store?.toSource("")?.copy(originalFileName = originalName)
                    ?: CharacterSource(originalFileName = originalName)
                convertAndInstall(copy, id, store?.displayName, source)
            } catch (e: PackageException) {
                Outcome.Failed(e.error, e.message ?: "")
            } catch (e: IOException) {
                Outcome.Failed(PackageError.STORAGE_FAILED, e.message ?: e.javaClass.simpleName)
            } catch (e: Throwable) {
                Log.e(TAG, "import failed", e)
                Outcome.Failed(PackageError.INTERNAL, e.message ?: e.javaClass.simpleName)
            } finally {
                copy.delete()
            }
            main.post { onDone(outcome) }
        }
    }

    private fun convertAndInstall(
        packageFile: File,
        id: String,
        displayName: String?,
        source: CharacterSource
    ): Outcome {
        val root = InstalledCharacterRegistry.root(appContext)
        val staging = InstalledCharacterRegistry.createStagingDir(root, id)
        try {
            val result = PackageArchive.open(packageFile).use { archive ->
                Live2DPackageConverter().convert(
                    archive,
                    staging,
                    Live2DPackageConverter.Request(id, displayName, source)
                )
            }
            InstalledCharacterRegistry.commit(root, staging, id)
            InstalledCharacterRegistry.reload(appContext)
            // Select it even if the store screen is gone by the time we finish.
            Live2DProfileStore.save(appContext, Live2DCharacterProfiles.byId(id))
            InstalledCharacterRegistry.lastInstalledId = id
            Log.i(TAG, "installed id=$id warnings=${result.warnings}")
            return Outcome.Installed(result.manifest, result.warnings)
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    private fun downloadTo(
        url: String,
        target: File,
        headers: (URL) -> Map<String, String> = { emptyMap() },
        onProgress: (Long, Long) -> Unit
    ) {
        var current = URL(url)
        repeat(MAX_REDIRECTS) {
            if (current.protocol != "https") throw PackageException(PackageError.DOWNLOAD_FAILED, "HTTPS only")
            val connection = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "AITuber-Android")
                // Cookies are looked up per hop so a redirect to a CDN does not carry site cookies.
                headers(current).forEach { (name, value) -> setRequestProperty(name, value) }
            }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: throw PackageException(PackageError.DOWNLOAD_FAILED, "redirect without Location")
                    current = URL(current, location)
                    return@repeat
                }
                if (code != HttpURLConnection.HTTP_OK) {
                    throw PackageException(PackageError.DOWNLOAD_FAILED, "HTTP $code")
                }
                val total = connection.contentLengthLong
                val max = PackageLimits().maxArchiveBytes
                if (total > max) throw PackageException(PackageError.ARCHIVE_TOO_LARGE, "$total bytes")
                connection.inputStream.use { input ->
                    copyCapped(input, target, max) { done -> onProgress(done, total) }
                }
                return
            } finally {
                connection.disconnect()
            }
        }
        throw PackageException(PackageError.DOWNLOAD_FAILED, "too many redirects")
    }

    private fun copyCapped(input: InputStream, target: File, max: Long, onProgress: ((Long) -> Unit)? = null) {
        target.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            var lastReport = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > max) throw PackageException(PackageError.ARCHIVE_TOO_LARGE, "> $max bytes")
                out.write(buffer, 0, n)
                if (onProgress != null && total - lastReport >= 256 * 1024) {
                    lastReport = total
                    onProgress(total)
                }
            }
            onProgress?.invoke(total)
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun StoreInfo.toSource(downloadUrl: String) = CharacterSource(
        storeId = storeId,
        sourceUrl = sourceUrl.ifBlank { downloadUrl },
        license = license,
        credit = credit
    )

    companion object {
        private const val TAG = "CharacterInstaller"
        private const val MAX_REDIRECTS = 5

        /** One install at a time, process-wide. */
        private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
            Thread(r, "aituber-character-install").apply { isDaemon = true }
        }

        /**
         * ZIP entry names for [urls]: each URL's decoded path relative to the
         * directory all of them share (".../main/Live2D/a.moc3" -> "Live2D/a.moc3").
         */
        internal fun relativeNames(urls: List<String>): List<String> {
            require(urls.isNotEmpty()) { "no files" }
            val paths = urls.map { java.net.URI(it).path.trimStart('/') }
            val dirs = paths.map { it.substringBeforeLast('/', "").split('/').filter(String::isNotEmpty) }
            var common = dirs.first()
            dirs.drop(1).forEach { d ->
                common = common.zip(d).takeWhile { (a, b) -> a == b }.map { it.first }
            }
            val prefix = if (common.isEmpty()) "" else common.joinToString("/") + "/"
            return paths.map { path ->
                val name = path.removePrefix(prefix)
                requireNotNull(PackageArchive.normalizePath(name)) { "bad file path $path" }
            }
        }

        /** ASCII id fragment; ids must match CharacterManifest.isValidId. */
        fun slug(value: String): String {
            val cleaned = value.lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .take(40)
            return cleaned.ifEmpty { "model" }
        }

        private fun shortHash(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().take(4).joinToString("") { "%02x".format(it) }
        }
    }
}
