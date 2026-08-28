package com.aituber.poc.character.staticpng

import kotlin.math.roundToLong

data class StaticPngBlinkKeyframe(
    val delayMs: Long,
    val shape: StaticPngEyeShape
)

object StaticPngBlinkMotion {
    const val MIN_INTERVAL_MS = 3_000L
    const val MAX_INTERVAL_MS = 6_000L
    const val BLINK_DURATION_MS = 160L
    const val DOUBLE_BLINK_PROBABILITY = 0.12f
    const val DOUBLE_BLINK_DELAY_MS = 180L

    val BLINK_SEQUENCE = listOf(
        StaticPngBlinkKeyframe(delayMs = 0L, shape = StaticPngEyeShape.OPEN),
        StaticPngBlinkKeyframe(delayMs = 35L, shape = StaticPngEyeShape.HALF),
        StaticPngBlinkKeyframe(delayMs = 70L, shape = StaticPngEyeShape.CLOSED),
        StaticPngBlinkKeyframe(delayMs = 105L, shape = StaticPngEyeShape.HALF),
        StaticPngBlinkKeyframe(delayMs = BLINK_DURATION_MS, shape = StaticPngEyeShape.OPEN)
    )

    fun intervalMs(unitRandom: Float): Long {
        val unit = unitRandom.coerceIn(0f, 1f)
        return (MIN_INTERVAL_MS + ((MAX_INTERVAL_MS - MIN_INTERVAL_MS) * unit)).roundToLong()
    }

    fun shouldDoubleBlink(unitRandom: Float): Boolean =
        unitRandom.coerceIn(0f, 1f) < DOUBLE_BLINK_PROBABILITY
}

class StaticPngBlinkLoopState {
    var autoBlinkEnabled: Boolean = false
        private set
    var blinkRunning: Boolean = false
        private set
    var blinkScheduled: Boolean = false
        private set
    var scheduleCount: Int = 0
        private set

    fun startAutoBlink(): Boolean {
        autoBlinkEnabled = true
        return scheduleNext()
    }

    fun stopAutoBlink() {
        autoBlinkEnabled = false
        blinkRunning = false
        blinkScheduled = false
    }

    fun markScheduledBlinkStarted() {
        if (!blinkScheduled) return
        blinkScheduled = false
        blinkRunning = true
    }

    fun markBlinkFinished() {
        blinkRunning = false
    }

    fun scheduleNext(): Boolean {
        if (!autoBlinkEnabled || blinkScheduled || blinkRunning) return false
        blinkScheduled = true
        scheduleCount += 1
        return true
    }
}
