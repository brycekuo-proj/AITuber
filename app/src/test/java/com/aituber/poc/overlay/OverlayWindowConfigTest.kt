package com.aituber.poc.overlay

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayWindowConfigTest {
    @Test
    fun live2dOverlayWindowFlagsDoNotIncludeNotTouchable() {
        val flags = OverlayWindowConfig.live2dFlags()

        assertFalse(flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertTrue(OverlayWindowConfig.isTouchable(flags))
    }

    @Test
    fun minimalMouthWindowFlagsStillIncludeNotTouchable() {
        val flags = OverlayWindowConfig.minimalMouthFlags()

        assertTrue(OverlayWindowConfig.hasNotTouchable(flags))
        assertFalse(OverlayWindowConfig.isTouchable(flags))
    }

    @Test
    fun live2dOverlayWindowFlagsIncludeNotFocusable() {
        val flags = OverlayWindowConfig.live2dFlags()

        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
    }

    @Test
    fun live2dWindowIsTouchableForDragging() {
        assertTrue(OverlayWindowConfig.isTouchable(OverlayWindowConfig.live2dFlags()))
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
    fun flagLayoutNoLimitsIsPreserved() {
        assertTrue(OverlayWindowConfig.live2dFlags() and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS != 0)
    }
}
