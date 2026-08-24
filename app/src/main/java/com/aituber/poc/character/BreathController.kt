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
    private var testStartMs: Long? = null

    fun update(): BreathFrame = update(nowMs())

    fun update(nowMs: Long): BreathFrame {
        testFrame(nowMs)?.let { return it }
        advance(nowMs)
        val elapsedInCycle = (nowMs - cycleStartMs).coerceAtLeast(0L)
        val phase = elapsedInCycle.toDouble() / cycleDurationMs.toDouble() * 2.0 * PI
        val normalized = (0.5 + 0.5 * cycleAmplitude * sin(phase)).toFloat().coerceIn(0f, 1f)
        return BreathFrame(
            normalized = normalized,
            cycleDurationMs = cycleDurationMs,
            breathCount = breathCount,
            intensity = config.naturalIntensity,
            testActive = false,
            testPhase = "NATURAL"
        )
    }

    fun forceTestBreath(nowMs: Long = this.nowMs()) {
        testStartMs = nowMs
    }

    fun reset(nowMs: Long = this.nowMs()) {
        cycleStartMs = nowMs
        cycleDurationMs = nextCycleDurationMs()
        cycleAmplitude = nextAmplitude()
        breathCount = 0L
        testStartMs = null
    }

    private fun testFrame(nowMs: Long): BreathFrame? {
        val start = testStartMs ?: return null
        val elapsed = (nowMs - start).coerceAtLeast(0L)
        if (elapsed >= config.testCycleDurationMs) {
            testStartMs = null
            return null
        }
        val inhaleEnd = config.testInhaleMs
        val peakEnd = inhaleEnd + config.testPeakHoldMs
        val normalized = when {
            elapsed < inhaleEnd -> elapsed.toFloat() / inhaleEnd.toFloat()
            elapsed < peakEnd -> 1f
            else -> {
                val exhaleElapsed = elapsed - peakEnd
                1f - (exhaleElapsed.toFloat() / config.testExhaleMs.toFloat())
            }
        }.coerceIn(0f, 1f)
        val phase = when {
            elapsed < inhaleEnd -> "TEST_INHALE"
            elapsed < peakEnd -> "TEST_PEAK"
            else -> "TEST_EXHALE"
        }
        return BreathFrame(
            normalized = normalized,
            cycleDurationMs = config.testCycleDurationMs,
            breathCount = breathCount,
            intensity = config.testIntensity,
            testActive = true,
            testPhase = phase
        )
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
    val naturalIntensity: Float = 0.30f,
    val testCycleDurationMs: Long = 1_800L,
    val testInhaleMs: Long = 800L,
    val testPeakHoldMs: Long = 100L,
    val testExhaleMs: Long = 900L,
    val testIntensity: Float = 1.0f
)

data class BreathFrame(
    val normalized: Float,
    val cycleDurationMs: Long,
    val breathCount: Long,
    val intensity: Float,
    val testActive: Boolean,
    val testPhase: String
)
