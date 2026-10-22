package com.aituber.poc.settings

import android.content.Context
import android.content.SharedPreferences
import com.aituber.poc.poc.AiTargetApp
import com.aituber.poc.poc.AiTargetApps

/**
 * How AITuber starts and behaves on top of other apps. Read from any thread;
 * SharedPreferences keeps the values in memory after the first read.
 */
object AituberSettings {
    private const val PREFS = "aituber_settings"
    private const val KEY_AUTO_OPEN_AI_APP = "auto_open_ai_app"
    private const val KEY_AI_APP_PACKAGE = "ai_app_package"
    private const val KEY_AUTO_HIDE = "auto_hide_character"
    private const val KEY_PRECISE_CAPTURE = "precise_playback_capture"
    private const val KEY_NOTIFICATIONS_ASKED = "notifications_permission_asked"
    private const val KEY_PASS_THROUGH = "pass_through_character"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Open the AI app right after the character starts, so the user lands where the talking happens. */
    fun autoOpenAiApp(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_OPEN_AI_APP, true)

    fun setAutoOpenAiApp(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_OPEN_AI_APP, value).apply()
    }

    /** The AI app to open: the user's pick while it is installed, otherwise the first installed one. */
    fun aiApp(context: Context): AiTargetApp? {
        val installed = AiTargetApps.installed(context).map { it.app }
        val preferred = prefs(context).getString(KEY_AI_APP_PACKAGE, null)
        return installed.firstOrNull { it.packageName == preferred } ?: installed.firstOrNull()
    }

    fun setAiAppPackage(context: Context, packageName: String) {
        prefs(context).edit().putString(KEY_AI_APP_PACKAGE, packageName).apply()
    }

    /** Tuck the character into a bubble when the conversation stops; it comes back when the AI talks. */
    fun autoHideCharacter(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_HIDE, true)

    fun setAutoHideCharacter(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_HIDE, value).apply()
    }

    /**
     * Also run Audio Playback Capture. It hears the AI apps' own media audio (e.g. read-aloud),
     * but Android asks for the screen-recording consent on every start. Off: only the
     * speaker-output detector runs, which needs no consent.
     */
    fun preciseCapture(context: Context): Boolean = prefs(context).getBoolean(KEY_PRECISE_CAPTURE, false)

    fun setPreciseCapture(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PRECISE_CAPTURE, value).apply()
    }

    fun notificationsAsked(context: Context): Boolean = prefs(context).getBoolean(KEY_NOTIFICATIONS_ASKED, false)

    fun setNotificationsAsked(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATIONS_ASKED, value).apply()
    }

    /**
     * Touches go through the character to the AI app underneath. Android then needs the
     * Live2D character half transparent; off, it is solid and can be dragged and pinched directly.
     */
    fun passThroughCharacter(context: Context): Boolean = prefs(context).getBoolean(KEY_PASS_THROUGH, true)

    fun setPassThroughCharacter(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PASS_THROUGH, value).apply()
    }
}
