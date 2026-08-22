package com.aituber.poc.overlay

import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.VisualizerWaveformMetrics
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

class MouthAmplitudeMapper(
    private val config: Config = Config()
) {
    private var smoothedOpen = 0.0
    private var lastSmoothingMs: Long? = null
    private var lastRenderMs: Long? = null

    fun evaluate(
        state: UniversalAiState,
        metrics: VisualizerWaveformMetrics,
        nowMs: Long
    ): MouthDriveFrame {
        if (state != UniversalAiState.SPEAKING) {
            smoothedOpen = 0.0
            lastSmoothingMs = nowMs
            lastRenderMs = nowMs
            return MouthDriveFrame(
                mode = MouthDriveMode.CLOSED,
                targetOpen = 0.0,
                smoothedOpen = 0.0,
                shouldRender = true
            )
        }

        if (!metrics.hasUsableAmplitude(config.usableAmplitudeFloor)) {
            return MouthDriveFrame(
                mode = MouthDriveMode.PSEUDO_FALLBACK,
                targetOpen = null,
                smoothedOpen = smoothedOpen,
                shouldRender = false
            )
        }

        val raw = max(
            normalize(metrics.rms, config.rmsMin, config.rmsMax),
            normalize(metrics.peak, config.peakMin, config.peakMax) * config.peakWeight
        ).coerceIn(0.0, 1.0)
        val targetOpen = (config.minSpeakingOpen + raw * (1.0 - config.minSpeakingOpen)).coerceIn(0.0, 1.0)
        val previousTime = lastSmoothingMs ?: nowMs - config.renderIntervalMs
        val elapsedMs = (nowMs - previousTime).coerceAtLeast(1L)
        val timeConstantMs = if (targetOpen >= smoothedOpen) config.attackMs else config.releaseMs
        val alpha = 1.0 - exp(-elapsedMs.toDouble() / timeConstantMs.toDouble())
        val nextSmoothed = smoothedOpen + (targetOpen - smoothedOpen) * alpha
        smoothedOpen = if (abs(nextSmoothed - smoothedOpen) < config.deadband) {
            smoothedOpen
        } else {
            nextSmoothed.coerceIn(0.0, 1.0)
        }
        lastSmoothingMs = nowMs

        val shouldRender = lastRenderMs?.let { nowMs - it >= config.renderIntervalMs } ?: true
        if (shouldRender) {
            lastRenderMs = nowMs
        }
        return MouthDriveFrame(
            mode = MouthDriveMode.RMS,
            targetOpen = targetOpen,
            smoothedOpen = smoothedOpen,
            shouldRender = shouldRender
        )
    }

    fun reset() {
        smoothedOpen = 0.0
        lastSmoothingMs = null
        lastRenderMs = null
    }

    private fun normalize(value: Double, low: Double, high: Double): Double {
        if (high <= low) return 0.0
        return ((value - low) / (high - low)).coerceIn(0.0, 1.0)
    }

    private fun VisualizerWaveformMetrics.hasUsableAmplitude(floor: Double): Boolean {
        return rms > floor || peak > floor || activityRatio > floor
    }

    data class Config(
        val rmsMin: Double = 0.08,
        val rmsMax: Double = 0.50,
        val peakMin: Double = 0.20,
        val peakMax: Double = 0.95,
        val peakWeight: Double = 0.65,
        val minSpeakingOpen: Double = 0.18,
        val attackMs: Long = 100L,
        val releaseMs: Long = 180L,
        val deadband: Double = 0.04,
        val renderIntervalMs: Long = 50L,
        val usableAmplitudeFloor: Double = 0.001
    )
}

enum class MouthDriveMode {
    RMS,
    PSEUDO_FALLBACK,
    CLOSED
}

data class MouthDriveFrame(
    val mode: MouthDriveMode,
    val targetOpen: Double?,
    val smoothedOpen: Double,
    val shouldRender: Boolean
)
