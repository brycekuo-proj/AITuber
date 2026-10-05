package com.aituber.poc.character.live2d.install

import android.content.Context
import android.util.Log
import com.aituber.poc.character.live2d.Live2DCharacterProfiles
import com.aituber.poc.character.live2d.Live2DNativeBridge
import java.io.File

/**
 * Characters installed from the store live in filesDir/characters/<id>/ with an
 * [CharacterManifest] each. This registry scans that folder and feeds the
 * profiles into [Live2DCharacterProfiles], so the home carousel, the overlay
 * service and the saved selection all see them without an APK rebuild.
 */
object InstalledCharacterRegistry {
    private const val TAG = "CharacterRegistry"
    private const val ROOT_DIR = "characters"
    private const val STAGING_PREFIX = ".staging-"
    private const val TRASH_PREFIX = ".trash-"

    @Volatile
    private var loaded = false

    /** Set after a successful install so the home screen can switch to it. */
    @Volatile
    var lastInstalledId: String? = null

    fun root(filesDir: File): File = File(filesDir, ROOT_DIR)

    fun root(context: Context): File = root(context.filesDir)

    /** Loads once per process. Cheap: reads one small JSON per character. */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            reload(context)
        }
    }

    @Synchronized
    fun reload(context: Context) {
        // An older native library cannot render them; keep them out of the carousel.
        val manifests = if (Live2DNativeBridge.supportsDownloadedCharacters) scan(root(context)) else emptyList()
        Live2DCharacterProfiles.setInstalled(manifests.map { it.toProfile() })
        loaded = true
    }

    fun manifests(context: Context): List<CharacterManifest> = scan(root(context))

    /** Installed and usable (listed in the carousel), not just present on disk. */
    fun isInstalled(context: Context, id: String): Boolean {
        ensureLoaded(context)
        return Live2DCharacterProfiles.all.any { it.id == id }
    }

    /** Pure file-system scan; skips staging folders and unreadable manifests. */
    fun scan(root: File): List<CharacterManifest> {
        val dirs = root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: return emptyList()
        return dirs.sortedBy { it.name }.mapNotNull { dir ->
            val manifestFile = File(dir, CharacterManifest.FILE_NAME)
            if (!manifestFile.isFile) return@mapNotNull null
            runCatching {
                val manifest = CharacterManifest.fromJson(MiniJson.parseObject(manifestFile.readBytes()))
                require(manifest.id == dir.name) { "Manifest id ${manifest.id} != folder ${dir.name}" }
                require(File(File(dir, manifest.modelDir), manifest.model3File).isFile) { "model3.json missing" }
                manifest
            }.onFailure {
                logWarning("Skipping broken character ${dir.name}: ${it.message}")
            }.getOrNull()
        }.sortedBy { it.conversion.installedAtMs }
    }

    /** A fresh empty folder next to the final location, so the final move is a rename. */
    fun createStagingDir(root: File, id: String): File {
        root.mkdirs()
        cleanupLeftovers(root)
        val dir = File(root, "$STAGING_PREFIX$id-${System.nanoTime()}")
        if (!dir.mkdirs()) throw PackageException(PackageError.STORAGE_FAILED, dir.path)
        return dir
    }

    /**
     * Moves a converted staging folder into place, replacing an older install
     * of the same id. The old folder is kept until the new one is in place.
     */
    fun commit(root: File, staging: File, id: String) {
        require(CharacterManifest.isValidId(id))
        val target = File(root, id)
        var trash: File? = null
        if (target.exists()) {
            trash = File(root, "$TRASH_PREFIX$id-${System.nanoTime()}")
            if (!target.renameTo(trash)) throw PackageException(PackageError.STORAGE_FAILED, "rename old $id")
        }
        if (!staging.renameTo(target)) {
            trash?.renameTo(target)
            throw PackageException(PackageError.STORAGE_FAILED, "rename staging $id")
        }
        trash?.deleteRecursively()
    }

    fun uninstall(context: Context, id: String): Boolean {
        require(CharacterManifest.isValidId(id))
        val dir = File(root(context), id)
        val removed = !dir.exists() || dir.deleteRecursively()
        reload(context)
        return removed
    }

    private fun cleanupLeftovers(root: File) {
        root.listFiles { f -> f.name.startsWith(STAGING_PREFIX) || f.name.startsWith(TRASH_PREFIX) }
            ?.forEach { it.deleteRecursively() }
    }

    private fun logWarning(message: String) {
        // android.util.Log is a stub in local unit tests.
        runCatching { Log.w(TAG, message) }
    }
}
