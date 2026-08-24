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

    fun loadScale(): Float? {
        if (!preferences.getBoolean(KEY_SCALE_SAVED, false)) return null
        return preferences.getFloat(KEY_SCALE, Live2DOverlayScaleMath.DEFAULT_SCALE)
    }

    fun saveScale(scale: Float) {
        preferences.edit()
            .putBoolean(KEY_SCALE_SAVED, true)
            .putFloat(KEY_SCALE, scale)
            .apply()
    }

    fun save(position: Live2DOverlayPosition, scale: Float) {
        preferences.edit()
            .putBoolean(KEY_SAVED, true)
            .putInt(KEY_X, position.x)
            .putInt(KEY_Y, position.y)
            .putBoolean(KEY_SCALE_SAVED, true)
            .putFloat(KEY_SCALE, scale)
            .apply()
    }

    fun isSaved(): Boolean = preferences.getBoolean(KEY_SAVED, false)

    fun isScaleSaved(): Boolean = preferences.getBoolean(KEY_SCALE_SAVED, false)

    companion object {
        private const val PREFERENCES_NAME = "aituber_live2d_overlay"
        private const val KEY_X = "live2d_overlay_x"
        private const val KEY_Y = "live2d_overlay_y"
        private const val KEY_SAVED = "live2d_overlay_position_saved"
        private const val KEY_SCALE = "live2d_overlay_scale"
        private const val KEY_SCALE_SAVED = "live2d_overlay_scale_saved"
    }
}
