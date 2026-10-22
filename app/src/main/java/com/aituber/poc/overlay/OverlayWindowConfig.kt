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

    /**
     * Android 12+ blocks touches that would pass through another app's overlay window
     * more opaque than this (InputManager#getMaximumObscuringOpacityForTouch, 0.8 by default).
     */
    const val MAX_PASS_THROUGH_ALPHA = 0.8f

    /** Window alpha while touches pass through the character to the app underneath. */
    fun passThroughAlpha(): Float = minOf(overlayAlpha(), MAX_PASS_THROUGH_ALPHA)

    /** The character while it is being moved or resized: it takes touches. */
    fun live2dFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    /** The character the rest of the time: every touch goes to the AI app underneath. */
    fun passThroughFlags(): Int =
        live2dFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    fun minimalMouthFlags(): Int = passThroughFlags()

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
