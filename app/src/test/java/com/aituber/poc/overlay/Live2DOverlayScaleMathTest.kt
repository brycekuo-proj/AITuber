package com.aituber.poc.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Live2DOverlayScaleMathTest {
    @Test
    fun minimumScaleMatchesTwoStandardAppIconsInHeight() {
        val range = Live2DOverlayScaleMath.scaleRange(screenHeight = 2340, density = 3f)
        val dimensions = Live2DOverlayScaleMath.dimensionsForScale(range.minScale)

        assertEquals(288, dimensions.height)
        assertTrue(range.minScale < range.defaultScale)
    }

    @Test
    fun defaultScaleRemainsCurrentApprovedSize() {
        val dimensions = Live2DOverlayScaleMath.dimensionsForScale(Live2DOverlayScaleMath.DEFAULT_SCALE)

        assertEquals(810, dimensions.width)
        assertEquals(1170, dimensions.height)
    }

    @Test
    fun maximumScaleAllowsHeadChestCloseUpAroundSixtyPercentScreenHeight() {
        val range = Live2DOverlayScaleMath.scaleRange(screenHeight = 2340, density = 3f)
        val dimensions = Live2DOverlayScaleMath.dimensionsForScale(range.maxScale)
        val headChestHeight = dimensions.height * Live2DOverlayScaleMath.HEAD_CHEST_MODEL_HEIGHT_FRACTION

        assertTrue(headChestHeight >= 2340 * 0.55f)
        assertTrue(headChestHeight <= 2340 * 0.65f)
        assertTrue(range.maxScale > range.defaultScale)
    }

    @Test
    fun maximumScaleIsSignificantlyLargerThanPreviousConservativeRange() {
        val range = Live2DOverlayScaleMath.scaleRange(screenHeight = 2340, density = 3f)
        val previousMaxScale = 2340f * 0.50f / 0.45f / Live2DOverlayScaleMath.BASE_HEIGHT_PX

        assertTrue(range.maxScale >= previousMaxScale * 1.8f)
        assertTrue(range.maxScale <= previousMaxScale * 2.2f)
    }

    @Test
    fun scaleClampsToCalculatedVisualRange() {
        val range = Live2DOverlayScaleMath.scaleRange(screenHeight = 2340, density = 3f)

        assertEquals(range.minScale, Live2DOverlayScaleMath.clampScale(0.01f, range), 0.001f)
        assertEquals(range.maxScale, Live2DOverlayScaleMath.clampScale(99f, range), 0.001f)
    }

    @Test
    fun targetSizeIsContinuousBetweenMinAndMax() {
        val range = Live2DOverlayScaleMath.scaleRange(screenHeight = 2340, density = 3f)
        val low = Live2DOverlayScaleMath.dimensionsForScale(range.minScale)
        val normal = Live2DOverlayScaleMath.dimensionsForScale(range.defaultScale)
        val high = Live2DOverlayScaleMath.dimensionsForScale(range.maxScale)

        assertTrue(low.height < normal.height)
        assertTrue(normal.height < high.height)
    }

    @Test
    fun savedScaleRestoreCanUseExpandedMaximumRange() {
        val placement = Live2DOverlayPlacement.compute(
            screenWidth = 1080,
            screenHeight = 2340,
            density = 3f,
            requestedScale = 8.5f
        )

        assertEquals(8.5f, placement.displayScale, 0.001f)
        assertTrue(placement.displayScale > placement.defaultScale)
        assertTrue(placement.displayScale < placement.maxScale)
    }

    @Test
    fun maximumScaleStillWorksWithHalfOffscreenDragClamp() {
        val range = Live2DOverlayScaleMath.scaleRange(screenHeight = 2340, density = 3f)
        val dimensions = Live2DOverlayScaleMath.dimensionsForScale(range.maxScale)
        val clamped = Live2DOverlayDragMath.clamp(
            position = Live2DOverlayPosition(-10_000, 10_000),
            bounds = Live2DOverlayBounds(
                screenWidth = 1080,
                screenHeight = 2340,
                overlayWidth = dimensions.width,
                overlayHeight = dimensions.height
            )
        )

        assertEquals(-(dimensions.width * 0.5f).toInt(), clamped.x)
        assertEquals(1170, clamped.y)
    }

    @Test
    fun visibleHeightPercentReflectsCurrentOverlayHeight() {
        assertEquals(50.0, Live2DOverlayScaleMath.visibleHeightPercent(500, 1000), 0.001)
    }
}
