package com.aituber.poc.character.staticpng

import kotlin.math.PI
import kotlin.math.cos

data class StaticPngBreathMotionFrame(
    val phase: Float,
    val inhale: Float,
    val scaleX: Float,
    val scaleY: Float
)

object StaticPngBreathMotion {
    const val AMPLITUDE_MIN_PERCENT = 0
    const val AMPLITUDE_MAX_PERCENT = 100
    const val DEFAULT_AMPLITUDE_PERCENT = 50

    const val PERIOD_MIN_MS = 2_500L
    const val PERIOD_MAX_MS = 7_000L
    const val DEFAULT_PERIOD_MS = 4_800L

    const val MAX_SCALE_X_DELTA = 0.002f
    const val MAX_SCALE_Y_DELTA = 0.006f

    @Volatile
    var amplitudePercent: Int = DEFAULT_AMPLITUDE_PERCENT
        private set

    @Volatile
    var periodMs: Long = DEFAULT_PERIOD_MS
        private set

    fun setAmplitudePercent(value: Int) {
        amplitudePercent = value.coerceIn(AMPLITUDE_MIN_PERCENT, AMPLITUDE_MAX_PERCENT)
    }

    fun setPeriodMs(value: Long) {
        periodMs = value.coerceIn(PERIOD_MIN_MS, PERIOD_MAX_MS)
    }

    fun frame(
        elapsedMs: Long,
        amplitudePercent: Int = this.amplitudePercent,
        periodMs: Long = this.periodMs
    ): StaticPngBreathMotionFrame {
        val safePeriod = periodMs.coerceIn(PERIOD_MIN_MS, PERIOD_MAX_MS)
        val wrappedMs = elapsedMs.floorMod(safePeriod)
        val phase = wrappedMs.toFloat() / safePeriod.toFloat()
        val inhale = ((1.0 - cos(phase.toDouble() * PI * 2.0)) * 0.5).toFloat()
        val strength = amplitudePercent.coerceIn(AMPLITUDE_MIN_PERCENT, AMPLITUDE_MAX_PERCENT) / 100f
        return StaticPngBreathMotionFrame(
            phase = phase,
            inhale = inhale,
            scaleX = 1f + inhale * MAX_SCALE_X_DELTA * strength,
            scaleY = 1f + inhale * MAX_SCALE_Y_DELTA * strength
        )
    }

    private fun Long.floorMod(modulus: Long): Long {
        val remainder = this % modulus
        return if (remainder >= 0L) remainder else remainder + modulus
    }
}
