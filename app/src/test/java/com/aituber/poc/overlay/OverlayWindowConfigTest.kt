package com.aituber.poc.overlay

import android.view.WindowManager
import org.junit.Assert.assertEquals
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

    @Test
    fun overlayWindowTypeIsApplicationOverlayOnAndroidOAndLater() {
        val type = OverlayWindowConfig.windowType(sdkInt = 26)

        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, type)
    }

    @Test
    fun experimentalWindowAlphaIsZeroPointSevenNine() {
        assertEquals(0.79f, OverlayWindowConfig.LIVE2D_EXPERIMENTAL_WINDOW_ALPHA, 0.001f)
    }

    @Test
    fun touchThroughExperimentIsEnabled() {
        assertTrue(OverlayWindowConfig.LIVE2D_TOUCH_THROUGH_EXPERIMENT_ENABLED)
    }
}
