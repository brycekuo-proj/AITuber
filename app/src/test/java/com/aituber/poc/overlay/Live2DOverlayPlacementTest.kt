package com.aituber.poc.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Live2DOverlayPlacementTest {
    @Test
    fun samsungA16SizedViewportUsesLargerRightBottomPlacement() {
        val placement = Live2DOverlayPlacement.compute(screenWidth = 1080, screenHeight = 2340)

        assertEquals(810, placement.width)
        assertEquals(1170, placement.height)
        assertEquals(2.25f, placement.displayScale, 0.001f)
        assertTrue(placement.offsetX > 0)
        assertTrue(placement.offsetY > 0)
        assertTrue(placement.offsetY + placement.height < 2340)
    }

    @Test
    fun placementIsClampedForNarrowScreens() {
        val placement = Live2DOverlayPlacement.compute(screenWidth = 720, screenHeight = 1280)

        assertTrue(placement.width <= 720)
        assertTrue(placement.height <= 1280)
        assertEquals((720 * 0.75f).toInt(), placement.width)
        assertEquals((1280 * 0.78f).toInt(), placement.height)
    }

    @Test
    fun requestedDisplayScaleIsApproximatelyTwoPointTwoFive() {
        assertEquals(2.25f, Live2DOverlayPlacement.DEFAULT_DISPLAY_SCALE, 0.001f)
    }

    @Test
    fun placementKeepsRightSideAndNearBottomOffsetsResolutionSafe() {
        val small = Live2DOverlayPlacement.compute(screenWidth = 720, screenHeight = 1280)
        val large = Live2DOverlayPlacement.compute(screenWidth = 1440, screenHeight = 3200)

        assertTrue(large.offsetX > small.offsetX)
        assertTrue(large.offsetY > small.offsetY)
        assertTrue(small.offsetY + small.height <= 1280)
        assertTrue(large.offsetY + large.height <= 3200)
    }
}
