package com.aituber.poc.character.staticpng

import com.aituber.poc.overlay.Live2DOverlayScaleMath
import com.aituber.poc.state.UniversalAiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticPngChestBreathMotionTest {
    @Test
    fun chestBreathDefaultsToVisibleLocalAmplitude() {
        assertEquals(55, StaticPngChestBreathMotion.DEFAULT_AMPLITUDE_PERCENT)
        assertEquals(4_525L, StaticPngBreathMotion.DEFAULT_PERIOD_MS)

        val peak = StaticPngChestBreathMotion.frame(
            elapsedMs = StaticPngBreathMotion.DEFAULT_PERIOD_MS / 2L,
            amplitudePercent = 55,
            periodMs = StaticPngBreathMotion.DEFAULT_PERIOD_MS
        )

        assertEquals(1f, peak.inhale, 0.0001f)
        assertEquals(1f + StaticPngChestBreathMotion.MAX_SCALE_X_DELTA * 0.55f, peak.scaleX, 0.0001f)
        assertEquals(1f + StaticPngChestBreathMotion.MAX_SCALE_Y_DELTA * 0.55f, peak.scaleY, 0.0001f)
        assertEquals(peak.scaleX, peak.effectiveScaleX, 0.0001f)
        assertEquals(peak.scaleY, peak.effectiveScaleY, 0.0001f)
        assertTrue(peak.offsetYDp < 0f)
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
        assertEquals(0f, peak.offsetYDp, 0.0001f)

        StaticPngChestBreathMotion.setAmplitudePercent(StaticPngChestBreathMotion.DEFAULT_AMPLITUDE_PERCENT)
    }

    @Test
    fun sourcePieceBoundsAreChestOnlyWithinXianxiaMasterCoordinates() {
        val roi = StaticPngChestBreathMotion.PIECE_BOUNDS

        assertTrue(roi.x > 320)
        assertTrue(roi.right < 710)
        assertTrue(roi.y > 320)
        assertTrue(roi.bottom < 760)
        assertTrue(roi.y > StaticPngCharacterPackage.XianxiaFemale.eyeReplacementPatch.y)
        assertTrue(roi.y > StaticPngCharacterPackage.XianxiaFemale.mouthReplacementPatch.y)
        assertTrue(roi.bottom < StaticPngCharacterPackage.XianxiaFemale.sourceHeightPx / 2)
    }

    @Test
    fun fitCenterMapsChestPieceWithHorizontalLetterbox() {
        val transform = StaticPngFitCenterTransform.from(
            viewWidth = 1200,
            viewHeight = 2000,
            sourceWidth = StaticPngCharacterPackage.XianxiaFemale.sourceWidthPx,
            sourceHeight = StaticPngCharacterPackage.XianxiaFemale.sourceHeightPx
        )
        val bounds = transform.map(StaticPngChestBreathMotion.PIECE_BOUNDS)

        assertEquals(1f, transform.scale, 0.0001f)
        assertEquals(88.5f, transform.offsetX, 0.0001f)
        assertEquals(0f, transform.offsetY, 0.0001f)
        assertEquals(438.5f, bounds.left, 0.0001f)
        assertEquals(355f, bounds.top, 0.0001f)
        assertEquals(325f, bounds.width, 0.0001f)
        assertEquals(315f, bounds.height, 0.0001f)
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
            "0.342,0.177 0.318x0.157",
            StaticPngChestBreathMotion.normalizedPieceBounds(
                StaticPngCharacterPackage.XianxiaFemale.sourceWidthPx,
                StaticPngCharacterPackage.XianxiaFemale.sourceHeightPx
            )
        )
    }

    @Test
    fun resizeMathKeepsChestVisibleAtMinimumDefaultAndMaximumScale() {
        val density = 3f
        val range = Live2DOverlayScaleMath.scaleRange(screenHeight = 2340, density = density)
        val scales = listOf(range.minScale, range.defaultScale, range.maxScale)

        scales.forEach { displayScale ->
            val dimensions = Live2DOverlayScaleMath.dimensionsForScale(displayScale)
            val base = StaticPngFitCenterTransform.from(
                viewWidth = dimensions.width,
                viewHeight = dimensions.height,
                sourceWidth = StaticPngCharacterPackage.XianxiaFemale.sourceWidthPx,
                sourceHeight = StaticPngCharacterPackage.XianxiaFemale.sourceHeightPx
            ).map(StaticPngChestBreathMotion.PIECE_BOUNDS)
            val peak = StaticPngChestBreathMotion.frame(
                elapsedMs = StaticPngBreathMotion.DEFAULT_PERIOD_MS / 2L,
                amplitudePercent = StaticPngChestBreathMotion.DEFAULT_AMPLITUDE_PERCENT,
                periodMs = StaticPngBreathMotion.DEFAULT_PERIOD_MS,
                displayScale = displayScale,
                density = density,
                baseBounds = base
            )
            val transformed = peak.transformedBounds(base, density)

            assertTrue(base.width > 0f)
            assertTrue(base.height > 0f)
            assertTrue(transformed.width > 0f)
            assertTrue(transformed.height > 0f)
            assertTrue(transformed.left.isFinite())
            assertTrue(transformed.top.isFinite())
            assertTrue(transformed.right.isFinite())
            assertTrue(transformed.bottom.isFinite())
            assertTrue(peak.peakPixelDelta > 0f)
        }
    }

    @Test
    fun smallSizeCompensationOnlyAppliesBelowReferenceAndIsCapped() {
        val range = Live2DOverlayScaleMath.scaleRange(screenHeight = 2340, density = 3f)
        val minimum = StaticPngChestBreathMotion.smallSizeCompensation(range.minScale)
        val default = StaticPngChestBreathMotion.smallSizeCompensation(range.defaultScale)
        val maximum = StaticPngChestBreathMotion.smallSizeCompensation(range.maxScale)
        val invalid = StaticPngChestBreathMotion.smallSizeCompensation(Float.NaN)

        assertTrue(minimum > 1f)
        assertTrue(minimum <= StaticPngChestBreathMotion.MAX_SMALL_SIZE_COMPENSATION)
        assertEquals(1f, default, 0.0001f)
        assertEquals(1f, maximum, 0.0001f)
        assertEquals(StaticPngChestBreathMotion.MAX_SMALL_SIZE_COMPENSATION, invalid, 0.0001f)
    }

    @Test
    fun minimumScaleChestMotionStaysPerceptibleWithoutChangingDefaultSizeMotion() {
        val density = 3f
        val range = Live2DOverlayScaleMath.scaleRange(screenHeight = 2340, density = density)
        val minBase = chestBaseBoundsFor(range.minScale)
        val defaultBase = chestBaseBoundsFor(range.defaultScale)

        val minimumPeak = StaticPngChestBreathMotion.frame(
            elapsedMs = StaticPngBreathMotion.DEFAULT_PERIOD_MS / 2L,
            amplitudePercent = StaticPngChestBreathMotion.DEFAULT_AMPLITUDE_PERCENT,
            periodMs = StaticPngBreathMotion.DEFAULT_PERIOD_MS,
            displayScale = range.minScale,
            density = density,
            baseBounds = minBase
        )
        val defaultPeak = StaticPngChestBreathMotion.frame(
            elapsedMs = StaticPngBreathMotion.DEFAULT_PERIOD_MS / 2L,
            amplitudePercent = StaticPngChestBreathMotion.DEFAULT_AMPLITUDE_PERCENT,
            periodMs = StaticPngBreathMotion.DEFAULT_PERIOD_MS,
            displayScale = range.defaultScale,
            density = density,
            baseBounds = defaultBase
        )

        assertTrue(minimumPeak.currentPixelDelta >= 3f)
        assertTrue(minimumPeak.effectiveScaleY > minimumPeak.scaleY)
        assertEquals(defaultPeak.scaleX, defaultPeak.effectiveScaleX, 0.0001f)
        assertEquals(defaultPeak.scaleY, defaultPeak.effectiveScaleY, 0.0001f)
        assertEquals(defaultPeak.offsetYDp, defaultPeak.effectiveOffsetYDp, 0.0001f)
    }

    @Test
    fun approvedChestAssetBoundsStayUnchanged() {
        val piece = StaticPngCharacterPackage.XianxiaFemale.chestPiece

        assertEquals(
            "characters/xianxia_female/static/xianxia_female__chest__piece__v1.png",
            piece.assetPath
        )
        assertEquals(350, piece.bounds.x)
        assertEquals(355, piece.bounds.y)
        assertEquals(325, piece.bounds.width)
        assertEquals(315, piece.bounds.height)
    }

    @Test
    fun chestPieceIsVisibleInIdleAndDisabledInThinking() {
        val idle = StaticPngChestPieceState.resolve(UniversalAiState.IDLE)
        val thinking = StaticPngChestPieceState.resolve(UniversalAiState.THINKING)

        assertTrue(idle.visible)
        assertEquals(UniversalAiState.IDLE.name, idle.activeState)
        assertEquals(StaticPngChestPieceState.DISABLED_REASON_NONE, idle.disabledReason)

        assertFalse(thinking.visible)
        assertEquals(UniversalAiState.THINKING.name, thinking.activeState)
        assertEquals(StaticPngChestPieceState.DISABLED_REASON_THINKING, thinking.disabledReason)
    }

    @Test
    fun chestPieceRestoresWhenThinkingReturnsToIdle() {
        val thinking = StaticPngChestPieceState.resolve(UniversalAiState.THINKING)
        val restored = StaticPngChestPieceState.resolve(UniversalAiState.IDLE)

        assertFalse(thinking.visible)
        assertTrue(restored.visible)
        assertEquals(StaticPngChestPieceState.DISABLED_REASON_NONE, restored.disabledReason)
    }

    @Test
    fun thinkingDebugOverrideAlsoDisablesChestPiece() {
        val visibility = StaticPngChestPieceState.resolve(
            universalState = UniversalAiState.IDLE,
            thinkingDebugOverride = true
        )

        assertFalse(visibility.visible)
        assertEquals(StaticPngChestPieceState.DISABLED_REASON_THINKING, visibility.disabledReason)
    }

    private fun chestBaseBoundsFor(displayScale: Float): StaticPngViewRect {
        val dimensions = Live2DOverlayScaleMath.dimensionsForScale(displayScale)
        return StaticPngFitCenterTransform.from(
            viewWidth = dimensions.width,
            viewHeight = dimensions.height,
            sourceWidth = StaticPngCharacterPackage.XianxiaFemale.sourceWidthPx,
            sourceHeight = StaticPngCharacterPackage.XianxiaFemale.sourceHeightPx
        ).map(StaticPngChestBreathMotion.PIECE_BOUNDS)
    }
}
