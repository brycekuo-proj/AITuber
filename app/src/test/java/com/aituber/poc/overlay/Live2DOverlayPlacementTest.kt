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

        assertEquals((Live2DOverlayScaleMath.BASE_WIDTH_PX * 2.25f).toInt(), placement.width)
        assertEquals((Live2DOverlayScaleMath.BASE_HEIGHT_PX * 2.25f).toInt(), placement.height)
        assertEquals(2.25f, placement.displayScale, 0.001f)
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
    fun defaultA16PlacementDoesNotEnterBottomSafeZone() {
        val placement = Live2DOverlayPlacement.compute(screenWidth = 1080, screenHeight = 2340)
        val usableBottom = 2340 - placement.bottomSafeZonePx

        assertTrue(placement.offsetY >= 0)
        assertTrue(placement.offsetY + placement.height <= usableBottom)
    }

    @Test
    fun placementKeepsRightSideAndTopOffsetsResolutionSafe() {
        val small = Live2DOverlayPlacement.compute(screenWidth = 720, screenHeight = 1280)
        val large = Live2DOverlayPlacement.compute(screenWidth = 1440, screenHeight = 3200)

        assertTrue(large.offsetY > small.offsetY)
        assertEquals(720 - small.width - small.rightMarginPx, small.offsetX)
        assertEquals(1440 - large.width - large.rightMarginPx, large.offsetX)
    }

    @Test
    fun topRightPlacementKeepsRightEdgeInsideScreenForMultipleResolutions() {
        listOf(
            720 to 1280,
            1080 to 2340,
            1440 to 3200,
            1200 to 1920
        ).forEach { (screenWidth, screenHeight) ->
            val placement = Live2DOverlayPlacement.compute(screenWidth, screenHeight)
            val right = placement.offsetX + placement.width

            assertTrue(right <= screenWidth - placement.rightMarginPx)
            assertTrue(placement.offsetY >= 0)
        }
    }

    @Test
    fun computedScaleRangeUsesTwoAppIconsForMinimumVisualHeight() {
        val density = 3f
        val range = Live2DOverlayScaleMath.scaleRange(screenHeight = 2340, density = density)
        val minDimensions = Live2DOverlayScaleMath.dimensionsForScale(range.minScale)

        assertEquals((48f * 2f * density).toInt(), minDimensions.height)
        assertTrue(range.minScale < range.defaultScale)
    }

    @Test
    fun defaultScaleKeepsApprovedTwoPointTwoFiveAppearance() {
        val placement = Live2DOverlayPlacement.compute(
            screenWidth = 1080,
            screenHeight = 2340,
            density = 3f
        )

        assertEquals(2.25f, placement.defaultScale, 0.001f)
        assertEquals(2.25f, placement.displayScale, 0.001f)
        assertEquals(810, placement.width)
        assertEquals(1170, placement.height)
    }

    @Test
    fun maximumScaleSupportsCloseUpUpperBodyAroundHalfScreenHeight() {
        val placement = Live2DOverlayPlacement.compute(
            screenWidth = 1080,
            screenHeight = 2340,
            density = 3f,
            requestedScale = 99f
        )

        val expectedMaxHeight = 2340 * 0.50f / 0.45f
        assertEquals(expectedMaxHeight, placement.height.toFloat(), 1.0f)
        assertTrue(placement.maxScale > placement.defaultScale)
    }

    @Test
    fun scaleIsContinuousBetweenMinimumAndMaximum() {
        val range = Live2DOverlayScaleMath.scaleRange(screenHeight = 2340, density = 3f)
        val low = Live2DOverlayScaleMath.dimensionsForScale(range.minScale)
        val middle = Live2DOverlayScaleMath.dimensionsForScale((range.minScale + range.maxScale) / 2f)
        val high = Live2DOverlayScaleMath.dimensionsForScale(range.maxScale)

        assertTrue(low.height < middle.height)
        assertTrue(middle.height < high.height)
    }

    @Test
    fun resizeAroundFocusKeepsPinchFocusStable() {
        val currentPosition = Live2DOverlayPosition(200, 100)
        val currentSize = Live2DOverlayDimensions(800, 1000)
        val nextSize = Live2DOverlayDimensions(1200, 1500)
        val nextPosition = Live2DOverlayScaleMath.resizeAroundFocus(
            currentPosition = currentPosition,
            currentSize = currentSize,
            nextSize = nextSize,
            focusX = 600f,
            focusY = 600f,
            bounds = Live2DOverlayBounds(
                screenWidth = 1080,
                screenHeight = 2340,
                overlayWidth = nextSize.width,
                overlayHeight = nextSize.height
            )
        )

        assertEquals(0, nextPosition.x)
        assertEquals(-150, nextPosition.y)
    }
}
