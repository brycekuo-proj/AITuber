package com.aituber.poc.character.staticpng

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticPngChestBreathMotionTest {
    @Test
    fun chestBreathDefaultsToVisibleLocalAmplitude() {
        assertEquals(50, StaticPngChestBreathMotion.DEFAULT_AMPLITUDE_PERCENT)

        val peak = StaticPngChestBreathMotion.frame(
            elapsedMs = StaticPngBreathMotion.DEFAULT_PERIOD_MS / 2L,
            amplitudePercent = 50,
            periodMs = StaticPngBreathMotion.DEFAULT_PERIOD_MS
        )

        assertEquals(1f, peak.inhale, 0.0001f)
        assertEquals(1f + StaticPngChestBreathMotion.MAX_SCALE_X_DELTA * 0.5f, peak.scaleX, 0.0001f)
        assertEquals(1f + StaticPngChestBreathMotion.MAX_SCALE_Y_DELTA * 0.5f, peak.scaleY, 0.0001f)
        assertTrue(peak.offsetYFraction < 0f)
    }

    @Test
    fun chestAmplitudeClampsAndZeroIsNoLocalTransform() {
        StaticPngChestBreathMotion.setAmplitudePercent(-5)
        assertEquals(StaticPngChestBreathMotion.AMPLITUDE_MIN_PERCENT, StaticPngChestBreathMotion.amplitudePercent)
        StaticPngChestBreathMotion.setAmplitudePercent(125)
        assertEquals(StaticPngChestBreathMotion.AMPLITUDE_MAX_PERCENT, StaticPngChestBreathMotion.amplitudePercent)

        val peak = StaticPngChestBreathMotion.frame(
            elapsedMs = StaticPngBreathMotion.DEFAULT_PERIOD_MS / 2L,
            amplitudePercent = 0,
            periodMs = StaticPngBreathMotion.DEFAULT_PERIOD_MS
        )

        assertEquals(1f, peak.scaleX, 0.0001f)
        assertEquals(1f, peak.scaleY, 0.0001f)
        assertEquals(0f, peak.offsetYFraction, 0.0001f)

        StaticPngChestBreathMotion.setAmplitudePercent(StaticPngChestBreathMotion.DEFAULT_AMPLITUDE_PERCENT)
    }

    @Test
    fun sourceRoiIsChestOnlyWithinXianxiaMasterCoordinates() {
        val roi = StaticPngChestBreathMotion.ROI

        assertTrue(roi.x > 320)
        assertTrue(roi.right < 710)
        assertTrue(roi.y > 400)
        assertTrue(roi.bottom < 760)
        assertTrue(roi.y > StaticPngCharacterPackage.XianxiaFemale.eyeReplacementPatch.y)
        assertTrue(roi.y > StaticPngCharacterPackage.XianxiaFemale.mouthReplacementPatch.y)
        assertTrue(roi.bottom < StaticPngCharacterPackage.XianxiaFemale.sourceHeightPx / 2)
    }

    @Test
    fun fitCenterMapsSourceRoiWithHorizontalLetterbox() {
        val transform = StaticPngFitCenterTransform.from(
            viewWidth = 1200,
            viewHeight = 2000,
            sourceWidth = StaticPngCharacterPackage.XianxiaFemale.sourceWidthPx,
            sourceHeight = StaticPngCharacterPackage.XianxiaFemale.sourceHeightPx
        )
        val bounds = transform.map(StaticPngChestBreathMotion.ROI)

        assertEquals(1f, transform.scale, 0.0001f)
        assertEquals(88.5f, transform.offsetX, 0.0001f)
        assertEquals(0f, transform.offsetY, 0.0001f)
        assertEquals(444.5f, bounds.left, 0.0001f)
        assertEquals(440f, bounds.top, 0.0001f)
        assertEquals(312f, bounds.width, 0.0001f)
        assertEquals(265f, bounds.height, 0.0001f)
    }

    @Test
    fun peakTransformExpandsAroundLowerChestPivotAndMovesUp() {
        val base = StaticPngViewRect(left = 100f, top = 200f, right = 400f, bottom = 500f)
        val peak = StaticPngChestBreathMotion.frame(
            elapsedMs = StaticPngBreathMotion.DEFAULT_PERIOD_MS / 2L,
            amplitudePercent = 100,
            periodMs = StaticPngBreathMotion.DEFAULT_PERIOD_MS
        )
        val transformed = peak.transformedBounds(base)

        assertTrue(transformed.left < base.left)
        assertTrue(transformed.right > base.right)
        assertTrue(transformed.top < base.top)
        assertTrue(transformed.bottom <= base.bottom)
    }

    @Test
    fun normalizedBoundsStayInSourceCoordinateSpace() {
        assertEquals(
            "0.348,0.220 0.305x0.132",
            StaticPngChestBreathMotion.normalizedRoi(
                StaticPngCharacterPackage.XianxiaFemale.sourceWidthPx,
                StaticPngCharacterPackage.XianxiaFemale.sourceHeightPx
            )
        )
    }
}
