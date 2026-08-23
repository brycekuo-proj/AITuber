package com.aituber.poc.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Live2DOverlayPlacementTest {
    @Test
    fun samsungA16SizedViewportUsesTopRightPlacement() {
        val placement = Live2DOverlayPlacement.compute(screenWidth = 1080, screenHeight = 2340)

        assertEquals(810, placement.width)
        assertEquals(1170, placement.height)
        assertEquals(2.25f, placement.displayScale, 0.001f)
        assertEquals("TOP_RIGHT", placement.anchor)
        assertEquals(0.03f, placement.topSafeMarginFraction, 0.001f)
        assertEquals((2340 * 0.03f).toInt(), placement.offsetY)
        assertEquals((1080 * 0.01f).toInt(), placement.rightMarginPx)
        assertEquals(1080 - placement.width - placement.rightMarginPx, placement.offsetX)
        assertEquals(0.18f, placement.bottomSafeZoneFraction, 0.001f)
        assertEquals(421, placement.bottomSafeZonePx)
        assertTrue(placement.offsetY + placement.height <= 2340 - placement.bottomSafeZonePx)
    }

    @Test
    fun placementIsClampedForNarrowScreens() {
        val placement = Live2DOverlayPlacement.compute(screenWidth = 720, screenHeight = 1280)

        assertTrue(placement.width <= 720)
        assertTrue(placement.height <= 1280)
        assertEquals((720 * 0.75f).toInt(), placement.width)
        assertEquals(((1280 - placement.bottomSafeZonePx) * 0.78f).toInt(), placement.height)
    }

    @Test
    fun requestedDisplayScaleIsApproximatelyTwoPointTwoFive() {
        assertEquals(2.25f, Live2DOverlayPlacement.DEFAULT_DISPLAY_SCALE, 0.001f)
    }

    @Test
    fun anchorIsTopRight() {
        val placement = Live2DOverlayPlacement.compute(screenWidth = 1080, screenHeight = 2340)

        assertEquals("TOP_RIGHT", placement.anchor)
    }

    @Test
    fun rightMarginIsApproximatelyOnePercent() {
        val placement = Live2DOverlayPlacement.compute(screenWidth = 1080, screenHeight = 2340)

        assertEquals(0.01f, placement.rightMarginFraction, 0.001f)
        assertEquals((1080 * 0.01f).toInt(), placement.rightMarginPx)
    }

    @Test
    fun topRightXUsesLeftCoordinateFormula() {
        val placement = Live2DOverlayPlacement.compute(screenWidth = 1080, screenHeight = 2340)

        assertEquals(1080 - placement.width - placement.rightMarginPx, placement.offsetX)
    }

    @Test
    fun overlayTopUsesTopSafeMargin() {
        val placement = Live2DOverlayPlacement.compute(screenWidth = 1080, screenHeight = 2340)

        assertEquals(0.03f, placement.topSafeMarginFraction, 0.001f)
        assertEquals((2340 * 0.03f).toInt(), placement.topSafeMarginPx)
        assertTrue(placement.offsetY >= placement.topSafeMarginPx)
    }

    @Test
    fun overlayTopRespectsSystemTopInset() {
        val placement = Live2DOverlayPlacement.compute(
            screenWidth = 1080,
            screenHeight = 2340,
            systemTopInsetPx = 96,
            topInsetMarginPx = 12
        )

        assertEquals(108, placement.offsetY)
    }

    @Test
    fun bottomSafeZoneIsApproximatelyEighteenPercent() {
        val placement = Live2DOverlayPlacement.compute(screenWidth = 1080, screenHeight = 2340)

        assertEquals(0.18f, placement.bottomSafeZoneFraction, 0.001f)
        assertEquals((2340 * 0.18f).toInt(), placement.bottomSafeZonePx)
    }

    @Test
    fun overlayBottomDoesNotEnterBottomSafeZone() {
        listOf(
            720 to 1280,
            1080 to 2340,
            1440 to 3200,
            1200 to 1920
        ).forEach { (screenWidth, screenHeight) ->
            val placement = Live2DOverlayPlacement.compute(screenWidth, screenHeight)
            val usableBottom = screenHeight - placement.bottomSafeZonePx

            assertTrue(placement.offsetY >= 0)
            assertTrue(placement.offsetY + placement.height <= usableBottom)
        }
    }

    @Test
    fun placementKeepsRightSideAndTopOffsetsResolutionSafe() {
        val small = Live2DOverlayPlacement.compute(screenWidth = 720, screenHeight = 1280)
        val large = Live2DOverlayPlacement.compute(screenWidth = 1440, screenHeight = 3200)

        assertTrue(large.offsetX > small.offsetX)
        assertTrue(large.offsetY > small.offsetY)
        assertTrue(small.offsetY + small.height <= 1280 - small.bottomSafeZonePx)
        assertTrue(large.offsetY + large.height <= 3200 - large.bottomSafeZonePx)
    }

    @Test
    fun overlayWindowRemainsWithinScreenBoundsForMultipleResolutions() {
        listOf(
            720 to 1280,
            1080 to 2340,
            1440 to 3200,
            1200 to 1920
        ).forEach { (screenWidth, screenHeight) ->
            val placement = Live2DOverlayPlacement.compute(screenWidth, screenHeight)
            val right = placement.offsetX + placement.width
            val bottom = placement.offsetY + placement.height

            assertTrue(placement.offsetX >= 0)
            assertTrue(right <= screenWidth - placement.rightMarginPx)
            assertTrue(placement.offsetY >= 0)
            assertTrue(bottom <= screenHeight - placement.bottomSafeZonePx)
        }
    }
}
