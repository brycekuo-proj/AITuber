package com.aituber.poc.character.live2d

import kotlin.math.PI
import kotlin.math.sin

class Live2DFallbackHeadIdleController(
    private val profile: Live2DCharacterProfile,
    private val nowMs: () -> Long = { android.os.SystemClock.elapsedRealtime() }
) {
    private var startMs: Long = nowMs()
    private var testStartMs: Long? = null
    private var lastFrame = Live2DFallbackHeadFrame.disabled()

    val enabled: Boolean
        get() = profile.fallbackHeadIdle.enabled &&
            !profile.capabilities.idleMotion &&
            profile.parameterMapping.headX != null &&
            profile.parameterMapping.headY != null

    fun update(): Live2DFallbackHeadFrame {
        if (!enabled) {
            lastFrame = Live2DFallbackHeadFrame.disabled()
            return lastFrame
        }
        val now = nowMs()
        val testStart = testStartMs
        val config = profile.fallbackHeadIdle
        val frame = if (testStart != null && now - testStart <= config.testDurationMs) {
            testFrame(now - testStart, config)
        } else {
            if (testStart != null) testStartMs = null
            naturalFrame(now - startMs, config)
        }
        lastFrame = frame
        return frame
    }

    fun startEarTest() {
        if (enabled) {
            testStartMs = nowMs()
        }
    }

    fun snapshot(): Live2DFallbackHeadFrame = lastFrame

    private fun naturalFrame(elapsedMs: Long, config: Live2DFallbackHeadIdleConfig): Live2DFallbackHeadFrame {
        val xPhase = elapsedMs.toDouble() / config.headXPeriodMs.toDouble() * 2.0 * PI
        val yPhase = elapsedMs.toDouble() / config.headYPeriodMs.toDouble() * 2.0 * PI + PI / 3.0
        val slowMod = 0.82 + 0.18 * sin(elapsedMs.toDouble() / 11_000.0 * 2.0 * PI)
        return Live2DFallbackHeadFrame(
            enabled = true,
            testActive = false,
            headX = (sin(xPhase) * config.headXAmplitude * slowMod).toFloat(),
            headY = (sin(yPhase) * config.headYAmplitude * slowMod).toFloat(),
            cycle = "NATURAL"
        )
    }

    private fun testFrame(elapsedMs: Long, config: Live2DFallbackHeadIdleConfig): Live2DFallbackHeadFrame {
        val phase = elapsedMs.toDouble() / config.testDurationMs.toDouble()
        val wave = sin(phase * 2.0 * PI)
        val yWave = sin(phase * 2.0 * PI + PI / 2.0)
        return Live2DFallbackHeadFrame(
            enabled = true,
            testActive = true,
            headX = (wave * config.testHeadXAmplitude).toFloat(),
            headY = (yWave * config.testHeadYAmplitude).toFloat(),
            cycle = "TEST_EARS"
        )
    }
}

data class Live2DFallbackHeadFrame(
    val enabled: Boolean,
    val testActive: Boolean,
    val headX: Float,
    val headY: Float,
    val cycle: String
) {
    companion object {
        fun disabled() = Live2DFallbackHeadFrame(
            enabled = false,
            testActive = false,
            headX = 0f,
            headY = 0f,
            cycle = "DISABLED"
        )
    }
}
