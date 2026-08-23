package com.aituber.poc.overlay

import android.view.WindowManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayWindowConfigTest {
    @Test
    fun overlayWindowFlagsIncludeNotTouchable() {
        val flags = OverlayWindowConfig.flags()

        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertFalse(OverlayWindowConfig.isTouchable(flags))
    }

    @Test
    fun overlayWindowFlagsIncludeNotFocusable() {
        val flags = OverlayWindowConfig.flags()

        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
    }

    @Test
    fun touchPassthroughIsEnabledForOverlayWindow() {
        assertTrue(OverlayWindowConfig.TOUCH_PASSTHROUGH)
    }
}
