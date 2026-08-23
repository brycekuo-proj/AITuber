package com.aituber.poc.overlay

import android.view.WindowManager

object OverlayWindowConfig {
    const val TOUCH_PASSTHROUGH = true

    fun flags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    fun isTouchable(flags: Int): Boolean =
        flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE == 0
}
