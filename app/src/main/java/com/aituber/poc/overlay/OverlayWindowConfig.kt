package com.aituber.poc.overlay

import android.os.Build
import android.view.WindowManager

object OverlayWindowConfig {
    const val OVERLAY_ALPHA_MIN_PERCENT = 10
    const val OVERLAY_ALPHA_MAX_PERCENT = 100
    const val DEFAULT_OVERLAY_ALPHA_PERCENT = 80

    @Volatile
    var overlayAlphaPercent: Int = DEFAULT_OVERLAY_ALPHA_PERCENT
        private set

    fun setOverlayAlphaPercent(value: Int) {
        overlayAlphaPercent = value.coerceIn(OVERLAY_ALPHA_MIN_PERCENT, OVERLAY_ALPHA_MAX_PERCENT)
    }

    fun overlayAlpha(): Float = overlayAlphaPercent / 100f

    fun live2dFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    fun minimalMouthFlags(): Int =
        live2dFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    fun windowType(sdkInt: Int = Build.VERSION.SDK_INT): Int =
        if (sdkInt >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    fun isTouchable(flags: Int): Boolean =
        flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE == 0

    fun hasNotTouchable(flags: Int): Boolean =
        flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0

    fun hasNotFocusable(flags: Int): Boolean =
        flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0
}
