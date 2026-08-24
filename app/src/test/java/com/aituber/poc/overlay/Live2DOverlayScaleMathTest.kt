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
    fun maximumScaleAllowsUpperBodyCloseUpAroundHalfScreenHeight() {
        val range = Live2DOverlayScaleMath.scaleRange(screenHeight = 2340, density = 3f)
        val dimensions = Live2DOverlayScaleMath.dimensionsForScale(range.maxScale)
        val upperBodyHeight = dimensions.height * Live2DOverlayScaleMath.UPPER_BODY_MODEL_HEIGHT_FRACTION

        assertEquals(2340 * 0.50f, upperBodyHeight, 1.0f)
        assertTrue(range.maxScale > range.defaultScale)
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
        assertEquals(2340 - (dimensions.height * 0.5f).toInt(), clamped.y)
    }

    @Test
    fun visibleHeightPercentReflectsCurrentOverlayHeight() {
        assertEquals(50.0, Live2DOverlayScaleMath.visibleHeightPercent(500, 1000), 0.001)
    }
}
