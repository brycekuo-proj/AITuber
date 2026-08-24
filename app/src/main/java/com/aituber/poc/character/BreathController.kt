package com.aituber.poc.character

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class BreathController(
    private val random: Random = Random.Default,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val config: BreathConfig = BreathConfig()
) {
    private var cycleStartMs: Long = nowMs()
    private var cycleDurationMs: Long = nextCycleDurationMs()
    private var cycleAmplitude: Float = nextAmplitude()
    private var breathCount: Long = 0L

    fun update(): BreathFrame = update(nowMs())

    fun update(nowMs: Long): BreathFrame {
        advance(nowMs)
        val elapsedInCycle = (nowMs - cycleStartMs).coerceAtLeast(0L)
        val phase = elapsedInCycle.toDouble() / cycleDurationMs.toDouble() * 2.0 * PI
        val normalized = (0.5 + 0.5 * cycleAmplitude * sin(phase)).toFloat().coerceIn(0f, 1f)
        return BreathFrame(
            normalized = normalized,
            cycleDurationMs = cycleDurationMs,
            breathCount = breathCount
        )
    }

    fun forceTestBreath(nowMs: Long = this.nowMs()) {
        cycleStartMs = nowMs
        cycleDurationMs = config.testCycleDurationMs
        cycleAmplitude = config.testAmplitude
    }

    fun reset(nowMs: Long = this.nowMs()) {
        cycleStartMs = nowMs
        cycleDurationMs = nextCycleDurationMs()
        cycleAmplitude = nextAmplitude()
        breathCount = 0L
    }

    private fun advance(nowMs: Long) {
        while (nowMs - cycleStartMs >= cycleDurationMs) {
            cycleStartMs += cycleDurationMs
            cycleDurationMs = nextCycleDurationMs()
            cycleAmplitude = nextAmplitude()
            breathCount += 1L
        }
    }

    private fun nextCycleDurationMs(): Long {
        return random.nextLong(config.minCycleDurationMs, config.maxCycleDurationMs + 1L)
    }

    private fun nextAmplitude(): Float {
        if (config.minAmplitude == config.maxAmplitude) return config.minAmplitude.coerceIn(0f, 1f)
        return random.nextDouble(config.minAmplitude.toDouble(), config.maxAmplitude.toDouble()).toFloat()
    }
}

data class BreathConfig(
    val minCycleDurationMs: Long = 3_500L,
    val maxCycleDurationMs: Long = 5_500L,
    val minAmplitude: Float = 0.88f,
    val maxAmplitude: Float = 1.0f,
    val testCycleDurationMs: Long = 1_600L,
    val testAmplitude: Float = 1.0f
)

data class BreathFrame(
    val normalized: Float,
    val cycleDurationMs: Long,
    val breathCount: Long
)
