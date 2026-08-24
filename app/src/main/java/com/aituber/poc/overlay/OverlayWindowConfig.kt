package com.aituber.poc.overlay

import android.os.Build
import android.view.WindowManager

object OverlayWindowConfig {
    const val TOUCH_PASSTHROUGH = true
    const val LIVE2D_TOUCH_THROUGH_EXPERIMENT_ENABLED = true
    const val LIVE2D_EXPERIMENTAL_WINDOW_ALPHA = 0.79f

    fun flags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

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
