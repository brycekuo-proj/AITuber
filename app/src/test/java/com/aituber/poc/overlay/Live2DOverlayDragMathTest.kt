package com.aituber.poc.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class Live2DOverlayDragMathTest {
    private val bounds = Live2DOverlayBounds(
        screenWidth = 1080,
        screenHeight = 2340,
        overlayWidth = 810,
        overlayHeight = 1170
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
    fun savedPartialOffscreenPositionRestoredWhenAvailable() {
        val initial = Live2DOverlayDragMath.initialPosition(
            defaultPosition = Live2DOverlayPosition(260, 70),
            savedPosition = Live2DOverlayPosition(-300, -400),
            bounds = bounds
        )

        assertEquals(Live2DOverlayPosition(-300, -400), initial)
    }

    @Test
    fun leftEdgeAllowsHalfOverlayOffscreen() {
        val clamped = Live2DOverlayDragMath.clamp(
            position = Live2DOverlayPosition(-405, 70),
            bounds = bounds
        )

        assertEquals(-405, clamped.x)
    }

    @Test
    fun rightEdgeAllowsHalfOverlayOffscreen() {
        val clamped = Live2DOverlayDragMath.clamp(
            position = Live2DOverlayPosition(675, 70),
            bounds = bounds
        )

        assertEquals(675, clamped.x)
    }

    @Test
    fun topEdgeAllowsHalfOverlayOffscreen() {
        val clamped = Live2DOverlayDragMath.clamp(
            position = Live2DOverlayPosition(260, -585),
            bounds = bounds
        )

        assertEquals(-585, clamped.y)
    }

    @Test
    fun bottomEdgeAllowsHalfOverlayOffscreen() {
        val clamped = Live2DOverlayDragMath.clamp(
            position = Live2DOverlayPosition(260, 1755),
            bounds = bounds
        )

        assertEquals(1755, clamped.y)
    }

    @Test
    fun moreThanHalfOffscreenIsClamped() {
        val clamped = Live2DOverlayDragMath.clamp(
            position = Live2DOverlayPosition(-1000, 3000),
            bounds = bounds
        )

        assertEquals(Live2DOverlayPosition(-405, 1755), clamped)
    }

    @Test
    fun savedPositionReclampedAfterScreenSizeChange() {
        val smallerBounds = Live2DOverlayBounds(
            screenWidth = 720,
            screenHeight = 1280,
            overlayWidth = 540,
            overlayHeight = 820
        )

        val initial = Live2DOverlayDragMath.initialPosition(
            defaultPosition = Live2DOverlayPosition(173, 50),
            savedPosition = Live2DOverlayPosition(900, 900),
            bounds = smallerBounds
        )

        assertEquals(Live2DOverlayPosition(450, 870), initial)
    }

    @Test
    fun savedTooFarOffscreenPositionReclampedAfterScreenSizeChange() {
        val smallerBounds = Live2DOverlayBounds(
            screenWidth = 720,
            screenHeight = 1280,
            overlayWidth = 540,
            overlayHeight = 820
        )

        val initial = Live2DOverlayDragMath.initialPosition(
            defaultPosition = Live2DOverlayPosition(173, 50),
            savedPosition = Live2DOverlayPosition(-900, -900),
            bounds = smallerBounds
        )

        assertEquals(Live2DOverlayPosition(-270, -410), initial)
    }
}
