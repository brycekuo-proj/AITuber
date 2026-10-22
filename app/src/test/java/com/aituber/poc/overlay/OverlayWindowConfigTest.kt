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
    fun overlayWindowAlphaDefaultsToEightyPercentAndClamps() {
        OverlayWindowConfig.setOverlayAlphaPercent(OverlayWindowConfig.DEFAULT_OVERLAY_ALPHA_PERCENT)
        assertEquals(80, OverlayWindowConfig.overlayAlphaPercent)
        assertEquals(0.80f, OverlayWindowConfig.overlayAlpha(), 0.001f)

        OverlayWindowConfig.setOverlayAlphaPercent(0)
        assertEquals(OverlayWindowConfig.OVERLAY_ALPHA_MIN_PERCENT, OverlayWindowConfig.overlayAlphaPercent)
        OverlayWindowConfig.setOverlayAlphaPercent(150)
        assertEquals(OverlayWindowConfig.OVERLAY_ALPHA_MAX_PERCENT, OverlayWindowConfig.overlayAlphaPercent)

        OverlayWindowConfig.setOverlayAlphaPercent(OverlayWindowConfig.DEFAULT_OVERLAY_ALPHA_PERCENT)
    }

    @Test
    fun passThroughFlagsLetTouchesReachTheAppUnderneath() {
        val flags = OverlayWindowConfig.passThroughFlags()

        assertTrue(OverlayWindowConfig.hasNotTouchable(flags))
        assertTrue(OverlayWindowConfig.hasNotFocusable(flags))
        assertTrue(flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS != 0)
    }

    @Test
    fun passThroughAlphaNeverExceedsTheAndroidTouchOcclusionLimit() {
        OverlayWindowConfig.setOverlayAlphaPercent(100)

        // A plain-view character (minimal mouth, static PNG) is one layer.
        val plain = OverlayWindowConfig.passThroughAlpha(hasSurfaceLayer = false)
        assertEquals(0.80f, plain, 0.0001f)
        assertTrue(OverlayWindowConfig.combinedOpacity(plain, layers = 1) <= OverlayWindowConfig.MAX_PASS_THROUGH_ALPHA + 0.0001f)

        // The Live2D GLSurfaceView adds a second layer at the same alpha; Android combines them.
        val surface = OverlayWindowConfig.passThroughAlpha(hasSurfaceLayer = true)
        assertTrue(OverlayWindowConfig.combinedOpacity(surface, layers = 2) <= OverlayWindowConfig.MAX_PASS_THROUGH_ALPHA)
        // The one-layer cap would still block touches with two layers (0.96).
        assertTrue(OverlayWindowConfig.combinedOpacity(0.8f, layers = 2) > OverlayWindowConfig.MAX_PASS_THROUGH_ALPHA)

        OverlayWindowConfig.setOverlayAlphaPercent(40)
        assertEquals(0.40f, OverlayWindowConfig.passThroughAlpha(hasSurfaceLayer = true), 0.0001f)

        OverlayWindowConfig.setOverlayAlphaPercent(OverlayWindowConfig.DEFAULT_OVERLAY_ALPHA_PERCENT)
    }

    @Test
    fun flagLayoutNoLimitsIsPreserved() {
        assertTrue(OverlayWindowConfig.live2dFlags() and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS != 0)
    }
}
