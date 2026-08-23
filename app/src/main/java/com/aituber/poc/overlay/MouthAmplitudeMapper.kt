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
    private var lastRenderedOpen = 0.0
    private var lastSmoothingMs: Long? = null
    private var lastRenderMs: Long? = null
    private var silenceCloseStartTimeMs: Long? = null

    fun evaluate(
        state: UniversalAiState,
        metrics: VisualizerWaveformMetrics,
        mouthActive: Boolean = true,
        visualizerAvailable: Boolean = true,
        nowMs: Long
    ): MouthDriveFrame {
        if (state != UniversalAiState.SPEAKING) {
            smoothedOpen = 0.0
            lastRenderedOpen = 0.0
            lastSmoothingMs = nowMs
            lastRenderMs = nowMs
            silenceCloseStartTimeMs = null
            return MouthDriveFrame(
                mode = MouthDriveMode.CLOSED,
                targetOpen = 0.0,
                smoothedOpen = 0.0,
                shouldRender = true,
                closeMode = MouthCloseMode.CLOSED,
                activeTimeConstantMs = 0L,
                silenceCloseStartTimeMs = null,
                silenceCloseDurationMs = 0L
            )
        }

        if (!visualizerAvailable) {
            silenceCloseStartTimeMs = null
            return MouthDriveFrame(
                mode = MouthDriveMode.PSEUDO_FALLBACK,
                targetOpen = null,
                smoothedOpen = smoothedOpen,
                shouldRender = false,
                closeMode = MouthCloseMode.NORMAL_RELEASE,
                activeTimeConstantMs = config.releaseMs,
                silenceCloseStartTimeMs = null,
                silenceCloseDurationMs = 0L
            )
        }

        if (!mouthActive) {
            if (silenceCloseStartTimeMs == null) {
                silenceCloseStartTimeMs = nowMs
            }
            return smoothToTarget(
                mode = MouthDriveMode.RMS,
                targetOpen = 0.0,
                nowMs = nowMs,
                closeMode = MouthCloseMode.SILENCE_FAST_CLOSE
            )
        }

        val raw = max(
            normalize(metrics.rms, config.rmsMin, config.rmsMax),
            normalize(metrics.peak, config.peakMin, config.peakMax) * config.peakWeight
        ).coerceIn(0.0, 1.0)
        val targetOpen = (config.minSpeakingOpen + raw * (1.0 - config.minSpeakingOpen)).coerceIn(0.0, 1.0)
        silenceCloseStartTimeMs = null
        return smoothToTarget(
            mode = MouthDriveMode.RMS,
            targetOpen = targetOpen,
            nowMs = nowMs,
            closeMode = MouthCloseMode.NORMAL_RELEASE
        )
    }

    fun reset() {
        smoothedOpen = 0.0
        lastRenderedOpen = 0.0
        lastSmoothingMs = null
        lastRenderMs = null
        silenceCloseStartTimeMs = null
    }

    private fun smoothToTarget(
        mode: MouthDriveMode,
        targetOpen: Double,
        nowMs: Long,
        closeMode: MouthCloseMode
    ): MouthDriveFrame {
        val previousTime = lastSmoothingMs ?: nowMs - config.renderIntervalMs
        val elapsedMs = (nowMs - previousTime).coerceAtLeast(1L)
        val timeConstantMs = when {
            closeMode == MouthCloseMode.SILENCE_FAST_CLOSE -> config.silenceCloseMs
            targetOpen >= smoothedOpen -> config.attackMs
            else -> config.releaseMs
        }
        val alpha = 1.0 - exp(-elapsedMs.toDouble() / timeConstantMs.toDouble())
        val nextSmoothed = smoothedOpen + (targetOpen - smoothedOpen) * alpha
        smoothedOpen = nextSmoothed.coerceIn(0.0, 1.0)
        lastSmoothingMs = nowMs

        val meaningfulChange = abs(smoothedOpen - lastRenderedOpen) >= config.deadband
        val intervalReached = lastRenderMs?.let { nowMs - it >= config.renderIntervalMs } ?: true
        val firstNonZeroFrame = lastRenderMs == null && smoothedOpen > 0.0
        val shouldRender = firstNonZeroFrame || (intervalReached && meaningfulChange)
        if (shouldRender) {
            lastRenderMs = nowMs
            lastRenderedOpen = smoothedOpen
        }
        val fastCloseStart = if (closeMode == MouthCloseMode.SILENCE_FAST_CLOSE) silenceCloseStartTimeMs else null
        return MouthDriveFrame(
            mode = mode,
            targetOpen = targetOpen,
            smoothedOpen = smoothedOpen,
            shouldRender = shouldRender,
            closeMode = closeMode,
            activeTimeConstantMs = timeConstantMs,
            silenceCloseStartTimeMs = fastCloseStart,
            silenceCloseDurationMs = fastCloseStart?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L
        )
    }

    private fun normalize(value: Double, low: Double, high: Double): Double {
        if (high <= low) return 0.0
        return ((value - low) / (high - low)).coerceIn(0.0, 1.0)
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
        val silenceCloseMs: Long = 70L,
        val deadband: Double = 0.04,
        val renderIntervalMs: Long = 50L
    )
}

enum class MouthDriveMode {
    RMS,
    PSEUDO_FALLBACK,
    CLOSED
}

enum class MouthCloseMode {
    NORMAL_RELEASE,
    SILENCE_FAST_CLOSE,
    CLOSED
}

data class MouthDriveFrame(
    val mode: MouthDriveMode,
    val targetOpen: Double?,
    val smoothedOpen: Double,
    val shouldRender: Boolean,
    val closeMode: MouthCloseMode,
    val activeTimeConstantMs: Long,
    val silenceCloseStartTimeMs: Long?,
    val silenceCloseDurationMs: Long
)
