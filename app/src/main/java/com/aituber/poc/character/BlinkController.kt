package com.aituber.poc.character

import kotlin.random.Random

class BlinkController(
    private val random: Random = Random.Default,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val config: BlinkConfig = BlinkConfig()
) {
    private var state: BlinkState = BlinkState.OPEN
    private var stateStartMs: Long = nowMs()
    private var nextBlinkAtMs: Long = stateStartMs + nextIdleIntervalMs()
    private var blinkCount: Long = 0L

    fun update(): BlinkFrame = update(nowMs())

    fun update(nowMs: Long): BlinkFrame {
        advance(nowMs)
        val eyeOpen = when (state) {
            BlinkState.OPEN -> 1f
            BlinkState.CLOSING -> 1f - progress(nowMs, config.closeMs)
            BlinkState.CLOSED -> 0f
            BlinkState.OPENING -> progress(nowMs, config.openMs)
        }.coerceIn(0f, 1f)
        return BlinkFrame(
            leftEyeOpen = eyeOpen,
            rightEyeOpen = eyeOpen,
            state = state,
            nextBlinkInMs = (nextBlinkAtMs - nowMs).coerceAtLeast(0L),
            blinkCount = blinkCount
        )
    }

    fun forceBlink(nowMs: Long = this.nowMs()) {
        state = BlinkState.CLOSING
        stateStartMs = nowMs
        nextBlinkAtMs = nowMs
    }

    fun reset(nowMs: Long = this.nowMs()) {
        state = BlinkState.OPEN
        stateStartMs = nowMs
        nextBlinkAtMs = nowMs + nextIdleIntervalMs()
        blinkCount = 0L
    }

    fun peek(): BlinkFrame = update(nowMs())

    private fun advance(nowMs: Long) {
        var advanced: Boolean
        do {
            advanced = false
            when (state) {
                BlinkState.OPEN -> {
                    if (nowMs >= nextBlinkAtMs) {
                        state = BlinkState.CLOSING
                        stateStartMs = nextBlinkAtMs
                        advanced = true
                    }
                }
                BlinkState.CLOSING -> {
                    if (nowMs - stateStartMs >= config.closeMs) {
                        state = BlinkState.CLOSED
                        stateStartMs += config.closeMs
                        advanced = true
                    }
                }
                BlinkState.CLOSED -> {
                    if (nowMs - stateStartMs >= config.closedHoldMs) {
                        state = BlinkState.OPENING
                        stateStartMs += config.closedHoldMs
                        advanced = true
                    }
                }
                BlinkState.OPENING -> {
                    if (nowMs - stateStartMs >= config.openMs) {
                        blinkCount += 1L
                        state = BlinkState.OPEN
                        stateStartMs += config.openMs
                        nextBlinkAtMs = stateStartMs + nextIntervalAfterBlink()
                        advanced = true
                    }
                }
            }
        } while (advanced)
    }

    private fun progress(nowMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 1f
        return ((nowMs - stateStartMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    private fun nextIntervalAfterBlink(): Long {
        return if (random.nextFloat() < config.doubleBlinkChance) {
            random.nextLong(config.doubleBlinkMinMs, config.doubleBlinkMaxMs + 1L)
        } else {
            nextIdleIntervalMs()
        }
    }

    private fun nextIdleIntervalMs(): Long {
        return random.nextLong(config.minIdleIntervalMs, config.maxIdleIntervalMs + 1L)
    }
}

data class BlinkConfig(
    val minIdleIntervalMs: Long = 2_000L,
    val maxIdleIntervalMs: Long = 6_000L,
    val doubleBlinkChance: Float = 0.10f,
    val doubleBlinkMinMs: Long = 160L,
    val doubleBlinkMaxMs: Long = 320L,
    val closeMs: Long = 85L,
    val closedHoldMs: Long = 45L,
    val openMs: Long = 115L
)

data class BlinkFrame(
    val leftEyeOpen: Float,
    val rightEyeOpen: Float,
    val state: BlinkState,
    val nextBlinkInMs: Long,
    val blinkCount: Long
)

enum class BlinkState {
    OPEN,
    CLOSING,
    CLOSED,
    OPENING
}
