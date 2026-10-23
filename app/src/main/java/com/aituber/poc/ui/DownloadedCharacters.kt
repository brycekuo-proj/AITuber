package com.aituber.poc.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.aituber.poc.character.live2d.Live2DCharacterProfile
import com.aituber.poc.character.live2d.Live2DCharacterProfiles
import com.aituber.poc.character.live2d.Live2DModelSource
import com.aituber.poc.character.live2d.Live2DProfileStore
import com.aituber.poc.character.live2d.install.InstalledCharacterRegistry
import com.aituber.poc.overlay.CharacterOverlayService
import java.util.Locale

/**
 * Deleting characters downloaded from the store (built-in characters cannot be deleted).
 * Used by the store cards and the "我的角色" list. Main thread only.
 */
internal object DownloadedCharacters {
    /** Replaces a deleted character wherever it was in use. */
    val fallback: Live2DCharacterProfile
        get() = Live2DCharacterProfiles.Tororo

    /** Lets the home preview or the floating character let go of the files before they go. */
    private const val RELEASE_WAIT_MS = 600L

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    @Volatile
    private var deleting = false

    fun isDownloaded(profile: Live2DCharacterProfile): Boolean =
        profile.modelSource == Live2DModelSource.APP_PRIVATE

    /** The downloaded characters, oldest install first. */
    fun list(): List<Live2DCharacterProfile> = Live2DCharacterProfiles.all.filter(::isDownloaded)

    fun sizeBytes(activity: Activity, id: String): Long =
        InstalledCharacterRegistry.sizeBytes(InstalledCharacterRegistry.root(activity), id)

    fun formatSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "${bytes / 1024L} KB"
        else -> "$bytes B"
    }

    /**
     * Asks first, then deletes. [releaseUsers] runs right before the files are deleted, so a
     * screen showing the character can switch to [fallback]; [onDone] gets whether it worked.
     */
    fun confirmDelete(
        activity: Activity,
        profile: Live2DCharacterProfile,
        releaseUsers: () -> Unit = {},
        onDone: (Boolean) -> Unit
    ): AlertDialog? {
        if (!isDownloaded(profile)) return null
        val size = formatSize(sizeBytes(activity, profile.id))
        val floating = CharacterOverlayService.isRunning &&
            CharacterOverlayService.requestedLive2DProfileId == profile.id
        val message = buildString {
            append("會從手機刪除「${profile.displayName}」的模型檔案（$size），之後想用可以再到角色商城下載。")
            if (floating) {
                append("\n\n這個角色正在浮動顯示，刪除後會換成 ${fallback.displayName}。")
            }
        }
        return AlertDialog.Builder(activity)
            .setTitle("刪除角色？")
            .setMessage(message)
            .setPositiveButton("刪除") { _, _ -> delete(activity, profile, releaseUsers, onDone) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun delete(
        activity: Activity,
        profile: Live2DCharacterProfile,
        releaseUsers: () -> Unit,
        onDone: (Boolean) -> Unit
    ) {
        if (deleting) return
        deleting = true
        val appContext = activity.applicationContext

        // Nothing may keep pointing at the character: the saved choice, the floating window.
        if (Live2DProfileStore.load(appContext).id == profile.id) {
            Live2DProfileStore.save(appContext, fallback)
        }
        val restartOverlay = CharacterOverlayService.isRunning &&
            CharacterOverlayService.requestedLive2DProfileId == profile.id
        if (restartOverlay) {
            CharacterOverlayService.requestedLive2DProfileId = Live2DProfileStore.load(appContext).id
            appContext.stopService(Intent(appContext, CharacterOverlayService::class.java))
        }
        releaseUsers()

        mainHandler.postDelayed({
            Thread {
                val removed = runCatching {
                    InstalledCharacterRegistry.uninstall(appContext, profile.id)
                }.getOrDefault(false)
                mainHandler.post {
                    deleting = false
                    if (restartOverlay && !CharacterOverlayService.isRunning) {
                        // Bring the floating character back, now as the fallback.
                        runCatching {
                            appContext.startService(Intent(appContext, CharacterOverlayService::class.java))
                        }
                    }
                    Toast.makeText(
                        appContext,
                        if (removed) "已刪除「${profile.displayName}」" else "刪除失敗，請再試一次",
                        Toast.LENGTH_SHORT
                    ).show()
                    onDone(removed)
                }
            }.start()
        }, RELEASE_WAIT_MS)
    }
}
