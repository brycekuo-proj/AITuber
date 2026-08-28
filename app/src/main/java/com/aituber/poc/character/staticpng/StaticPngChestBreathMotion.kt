package com.aituber.poc.character.staticpng

import kotlin.math.PI
import kotlin.math.cos

data class StaticPngSourceRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height
}

data class StaticPngViewRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun compactString(): String = "%.1f,%.1f %.1fx%.1f".format(left, top, width, height)
}

data class StaticPngFitCenterTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float
) {
    fun map(sourceRect: StaticPngSourceRect): StaticPngViewRect {
        return StaticPngViewRect(
            left = offsetX + sourceRect.x * scale,
            top = offsetY + sourceRect.y * scale,
            right = offsetX + sourceRect.right * scale,
            bottom = offsetY + sourceRect.bottom * scale
        )
    }

    companion object {
        fun from(
            viewWidth: Int,
            viewHeight: Int,
            sourceWidth: Int,
            sourceHeight: Int
        ): StaticPngFitCenterTransform {
            if (viewWidth <= 0 || viewHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
                return StaticPngFitCenterTransform(scale = 0f, offsetX = 0f, offsetY = 0f)
            }
            val scale = minOf(
                viewWidth.toFloat() / sourceWidth.toFloat(),
                viewHeight.toFloat() / sourceHeight.toFloat()
            )
            return StaticPngFitCenterTransform(
                scale = scale,
                offsetX = (viewWidth - sourceWidth * scale) * 0.5f,
                offsetY = (viewHeight - sourceHeight * scale) * 0.5f
            )
        }
    }
}

data class StaticPngChestBreathFrame(
    val phase: Float,
    val inhale: Float,
    val scaleX: Float,
    val scaleY: Float,
    val offsetYDp: Float,
    val compensationFactor: Float = 1f,
    val effectiveScaleX: Float = scaleX,
    val effectiveScaleY: Float = scaleY,
    val effectiveOffsetYDp: Float = offsetYDp,
    val peakPixelDelta: Float = 0f,
    val currentPixelDelta: Float = 0f
) {
    fun transformedBounds(base: StaticPngViewRect, density: Float = 1f): StaticPngViewRect {
        val pivotX = base.left + base.width * StaticPngChestBreathMotion.PIVOT_X_FRACTION
        val pivotY = base.top + base.height * StaticPngChestBreathMotion.PIVOT_Y_FRACTION
        val offsetY = effectiveOffsetYDp * density
        return StaticPngViewRect(
            left = pivotX + (base.left - pivotX) * effectiveScaleX,
            top = pivotY + (base.top - pivotY) * effectiveScaleY + offsetY,
            right = pivotX + (base.right - pivotX) * effectiveScaleX,
            bottom = pivotY + (base.bottom - pivotY) * effectiveScaleY + offsetY
        )
    }

    fun localTransformString(): String = "sx=%.4f sy=%.4f liftDp=%.2f comp=%.2f peakPx=%.2f curPx=%.2f".format(
        effectiveScaleX,
        effectiveScaleY,
        effectiveOffsetYDp,
        compensationFactor,
        peakPixelDelta,
        currentPixelDelta
    )
}

object StaticPngChestBreathMotion {
    const val AMPLITUDE_MIN_PERCENT = 0
    const val AMPLITUDE_MAX_PERCENT = 100
    const val DEFAULT_AMPLITUDE_PERCENT = 55
    const val REFERENCE_DISPLAY_SCALE = 2.25f
    const val MAX_SMALL_SIZE_COMPENSATION = 2.5f

    val PIECE_BOUNDS = StaticPngSourceRect(
        x = 350,
        y = 355,
        width = 325,
        height = 315
    )

    const val PIVOT_X_FRACTION = 0.5f
    const val PIVOT_Y_FRACTION = 0.88f
    const val MAX_SCALE_X_DELTA = 0.020f
    const val MAX_SCALE_Y_DELTA = 0.040f
    const val MAX_LIFT_DP = -2.5f

    @Volatile
    var amplitudePercent: Int = DEFAULT_AMPLITUDE_PERCENT
        private set

    fun setAmplitudePercent(value: Int) {
        amplitudePercent = value.coerceIn(AMPLITUDE_MIN_PERCENT, AMPLITUDE_MAX_PERCENT)
    }

    fun frame(
        elapsedMs: Long,
        amplitudePercent: Int = this.amplitudePercent,
        periodMs: Long = StaticPngBreathMotion.periodMs,
        displayScale: Float = REFERENCE_DISPLAY_SCALE,
        density: Float = 1f,
        baseBounds: StaticPngViewRect? = null
    ): StaticPngChestBreathFrame {
        val safePeriod = periodMs.coerceIn(
            StaticPngBreathMotion.PERIOD_MIN_MS,
            StaticPngBreathMotion.PERIOD_MAX_MS
        )
        val wrappedMs = elapsedMs.floorMod(safePeriod)
        val phase = wrappedMs.toFloat() / safePeriod.toFloat()
        val inhale = ((1.0 - cos(phase.toDouble() * PI * 2.0)) * 0.5).toFloat()
        val strength = amplitudePercent.coerceIn(AMPLITUDE_MIN_PERCENT, AMPLITUDE_MAX_PERCENT) / 100f
        val compensation = smallSizeCompensation(displayScale)
        val scaleXDelta = inhale * MAX_SCALE_X_DELTA * strength
        val scaleYDelta = inhale * MAX_SCALE_Y_DELTA * strength
        val offsetY = inhale * MAX_LIFT_DP * strength
        val peakPixelDelta = baseBounds?.let {
            peakPixelDelta(
                baseHeightPx = it.height,
                amplitudePercent = amplitudePercent,
                compensationFactor = compensation,
                density = density
            )
        } ?: 0f
        return StaticPngChestBreathFrame(
            phase = phase,
            inhale = inhale,
            scaleX = 1f + scaleXDelta,
            scaleY = 1f + scaleYDelta,
            offsetYDp = offsetY,
            compensationFactor = compensation,
            effectiveScaleX = 1f + scaleXDelta * compensation,
            effectiveScaleY = 1f + scaleYDelta * compensation,
            effectiveOffsetYDp = offsetY,
            peakPixelDelta = peakPixelDelta,
            currentPixelDelta = peakPixelDelta * inhale
        )
    }

    fun smallSizeCompensation(displayScale: Float): Float {
        if (!displayScale.isFinite() || displayScale <= 0f) return MAX_SMALL_SIZE_COMPENSATION
        if (displayScale >= REFERENCE_DISPLAY_SCALE) return 1f
        val ratio = (displayScale / REFERENCE_DISPLAY_SCALE).coerceIn(0f, 1f)
        val t = 1f - ratio
        val eased = t * t * (3f - 2f * t)
        return 1f + eased * (MAX_SMALL_SIZE_COMPENSATION - 1f)
    }

    fun peakPixelDelta(
        baseHeightPx: Float,
        amplitudePercent: Int,
        compensationFactor: Float,
        density: Float
    ): Float {
        if (!baseHeightPx.isFinite() || baseHeightPx <= 0f) return 0f
        val strength = amplitudePercent.coerceIn(AMPLITUDE_MIN_PERCENT, AMPLITUDE_MAX_PERCENT) / 100f
        val localScaleDelta = baseHeightPx * MAX_SCALE_Y_DELTA * strength * compensationFactor
        val liftDelta = kotlin.math.abs(MAX_LIFT_DP) * density.coerceAtLeast(0f) * strength
        return localScaleDelta + liftDelta
    }

    fun normalizedPieceBounds(sourceWidth: Int, sourceHeight: Int): String {
        if (sourceWidth <= 0 || sourceHeight <= 0) return "n/a"
        return "%.3f,%.3f %.3fx%.3f".format(
            PIECE_BOUNDS.x.toFloat() / sourceWidth.toFloat(),
            PIECE_BOUNDS.y.toFloat() / sourceHeight.toFloat(),
            PIECE_BOUNDS.width.toFloat() / sourceWidth.toFloat(),
            PIECE_BOUNDS.height.toFloat() / sourceHeight.toFloat()
        )
    }

    private fun Long.floorMod(modulus: Long): Long {
        val remainder = this % modulus
        return if (remainder >= 0L) remainder else remainder + modulus
    }
}
