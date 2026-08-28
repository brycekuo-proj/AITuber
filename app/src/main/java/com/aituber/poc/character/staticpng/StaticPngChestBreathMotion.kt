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
    val offsetYDp: Float
) {
    fun transformedBounds(base: StaticPngViewRect, density: Float = 1f): StaticPngViewRect {
        val pivotX = base.left + base.width * StaticPngChestBreathMotion.PIVOT_X_FRACTION
        val pivotY = base.top + base.height * StaticPngChestBreathMotion.PIVOT_Y_FRACTION
        val offsetY = offsetYDp * density
        return StaticPngViewRect(
            left = pivotX + (base.left - pivotX) * scaleX,
            top = pivotY + (base.top - pivotY) * scaleY + offsetY,
            right = pivotX + (base.right - pivotX) * scaleX,
            bottom = pivotY + (base.bottom - pivotY) * scaleY + offsetY
        )
    }

    fun localTransformString(): String = "sx=%.4f sy=%.4f liftDp=%.2f".format(scaleX, scaleY, offsetYDp)
}

object StaticPngChestBreathMotion {
    const val AMPLITUDE_MIN_PERCENT = 0
    const val AMPLITUDE_MAX_PERCENT = 100
    const val DEFAULT_AMPLITUDE_PERCENT = 50

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
        periodMs: Long = StaticPngBreathMotion.periodMs
    ): StaticPngChestBreathFrame {
        val safePeriod = periodMs.coerceIn(
            StaticPngBreathMotion.PERIOD_MIN_MS,
            StaticPngBreathMotion.PERIOD_MAX_MS
        )
        val wrappedMs = elapsedMs.floorMod(safePeriod)
        val phase = wrappedMs.toFloat() / safePeriod.toFloat()
        val inhale = ((1.0 - cos(phase.toDouble() * PI * 2.0)) * 0.5).toFloat()
        val strength = amplitudePercent.coerceIn(AMPLITUDE_MIN_PERCENT, AMPLITUDE_MAX_PERCENT) / 100f
        return StaticPngChestBreathFrame(
            phase = phase,
            inhale = inhale,
            scaleX = 1f + inhale * MAX_SCALE_X_DELTA * strength,
            scaleY = 1f + inhale * MAX_SCALE_Y_DELTA * strength,
            offsetYDp = inhale * MAX_LIFT_DP * strength
        )
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
