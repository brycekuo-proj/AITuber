package com.aituber.poc.character.staticpng

import kotlin.math.PI
import kotlin.math.sin

data class StaticPngIdleMotionFrame(
    val phase: Float,
    val offsetYDp: Float,
    val scale: Float
)

object StaticPngIdleMotion {
    const val PERIOD_MS = 4_200L
    const val OFFSET_Y_DP = 2f
    const val SCALE_AMPLITUDE = 0.004f

    fun frame(elapsedMs: Long): StaticPngIdleMotionFrame {
        val wrappedMs = elapsedMs.floorMod(PERIOD_MS)
        val phase = wrappedMs.toFloat() / PERIOD_MS.toFloat()
        val wave = sin(phase.toDouble() * PI * 2.0).toFloat()
        return StaticPngIdleMotionFrame(
            phase = phase,
            offsetYDp = wave * OFFSET_Y_DP,
            scale = 1f + wave * SCALE_AMPLITUDE
        )
    }

    private fun Long.floorMod(modulus: Long): Long {
        val remainder = this % modulus
        return if (remainder >= 0L) remainder else remainder + modulus
    }
}
