package com.aituber.poc.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class Live2DOverlayDragMathTest {
    private val bounds = Live2DOverlayBounds(
        screenWidth = 1080,
        usableBottom = 1919,
        overlayWidth = 810,
        overlayHeight = 1170,
        minY = 70
    )

    @Test
    fun defaultPositionUsedWhenNoSavedLocation() {
        val initial = Live2DOverlayDragMath.initialPosition(
            defaultPosition = Live2DOverlayPosition(260, 70),
            savedPosition = null,
            bounds = bounds
        )

        assertEquals(Live2DOverlayPosition(260, 70), initial)
    }

    @Test
    fun savedPositionRestoredWhenAvailable() {
        val initial = Live2DOverlayDragMath.initialPosition(
            defaultPosition = Live2DOverlayPosition(260, 70),
            savedPosition = Live2DOverlayPosition(120, 300),
            bounds = bounds
        )

        assertEquals(Live2DOverlayPosition(120, 300), initial)
    }

    @Test
    fun positionClampedToScreenBounds() {
        val clamped = Live2DOverlayDragMath.clamp(
            position = Live2DOverlayPosition(-50, 2000),
            bounds = bounds
        )

        assertEquals(0, clamped.x)
        assertEquals(749, clamped.y)
    }

    @Test
    fun savedPositionReclampedAfterScreenSizeChange() {
        val smallerBounds = Live2DOverlayBounds(
            screenWidth = 720,
            usableBottom = 1050,
            overlayWidth = 540,
            overlayHeight = 820,
            minY = 50
        )

        val initial = Live2DOverlayDragMath.initialPosition(
            defaultPosition = Live2DOverlayPosition(173, 50),
            savedPosition = Live2DOverlayPosition(900, 900),
            bounds = smallerBounds
        )

        assertEquals(Live2DOverlayPosition(180, 230), initial)
    }
}
