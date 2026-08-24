package com.aituber.poc.overlay

import android.content.Context

class Live2DOverlayPositionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): Live2DOverlayPosition? {
        if (!preferences.getBoolean(KEY_SAVED, false)) return null
        return Live2DOverlayPosition(
            x = preferences.getInt(KEY_X, 0),
            y = preferences.getInt(KEY_Y, 0)
        )
    }

    fun save(position: Live2DOverlayPosition) {
        preferences.edit()
            .putBoolean(KEY_SAVED, true)
            .putInt(KEY_X, position.x)
            .putInt(KEY_Y, position.y)
            .apply()
    }

    fun isSaved(): Boolean = preferences.getBoolean(KEY_SAVED, false)

    companion object {
        private const val PREFERENCES_NAME = "aituber_live2d_overlay"
        private const val KEY_X = "live2d_overlay_x"
        private const val KEY_Y = "live2d_overlay_y"
        private const val KEY_SAVED = "live2d_overlay_position_saved"
    }
}
