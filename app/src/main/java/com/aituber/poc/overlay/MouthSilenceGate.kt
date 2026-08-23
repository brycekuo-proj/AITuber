package com.aituber.poc.overlay

import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.VisualizerWaveformMetrics

class MouthSilenceGate(
    private val config: Config = Config()
) {
    private var lastAudibleMs: Long? = null
    private var gateState = MouthGateState.SILENT

    fun evaluate(
        universalState: UniversalAiState,
        metrics: VisualizerWaveformMetrics,
        visualizerAvailable: Boolean,
        nowMs: Long
    ): MouthSilenceGateFrame {
        if (universalState != UniversalAiState.SPEAKING || !visualizerAvailable) {
            gateState = MouthGateState.SILENT
            lastAudibleMs = null
            return frame(metrics, nowMs, audible = false, silenceDurationMs = 0L, holdRemainingMs = 0L)
        }

        val audible = isAudible(metrics)
        if (audible) {
            lastAudibleMs = nowMs
            gateState = MouthGateState.ACTIVE
            return frame(metrics, nowMs, audible = true, silenceDurationMs = 0L, holdRemainingMs = config.silenceHoldMs)
        }

        val lastAudible = lastAudibleMs
        val silenceDuration = lastAudible?.let { nowMs - it } ?: Long.MAX_VALUE
        val holdRemaining = (config.silenceHoldMs - silenceDuration).coerceAtLeast(0L)
        gateState = if (lastAudible != null && holdRemaining > 0L) {
            MouthGateState.ACTIVE
        } else {
            MouthGateState.SILENT
        }
        return frame(metrics, nowMs, audible = false, silenceDurationMs = silenceDuration, holdRemainingMs = holdRemaining)
    }

    fun reset() {
        lastAudibleMs = null
        gateState = MouthGateState.SILENT
    }

    private fun isAudible(metrics: VisualizerWaveformMetrics): Boolean {
        return metrics.rms >= config.mouthRmsFloor ||
            (metrics.peak >= config.mouthPeakFloor && metrics.activityRatio >= config.mouthActivityFloor)
    }

    private fun frame(
        metrics: VisualizerWaveformMetrics,
        nowMs: Long,
        audible: Boolean,
        silenceDurationMs: Long,
        holdRemainingMs: Long
    ) = MouthSilenceGateFrame(
        mouthActive = gateState == MouthGateState.ACTIVE,
        audible = audible,
        gateState = gateState,
        lastAudibleTimeMs = lastAudibleMs,
        silenceDurationMs = silenceDurationMs,
        silenceHoldRemainingMs = holdRemainingMs,
        rms = metrics.rms,
        peak = metrics.peak,
        activityRatio = metrics.activityRatio,
        timestampMs = nowMs
    )

    data class Config(
        val mouthRmsFloor: Double = 0.025,
        val mouthPeakFloor: Double = 0.10,
        val mouthActivityFloor: Double = 0.08,
        val silenceHoldMs: Long = 80L
    )
}

enum class MouthGateState {
    ACTIVE,
    SILENT
}

data class MouthSilenceGateFrame(
    val mouthActive: Boolean,
    val audible: Boolean,
    val gateState: MouthGateState,
    val lastAudibleTimeMs: Long?,
    val silenceDurationMs: Long,
    val silenceHoldRemainingMs: Long,
    val rms: Double,
    val peak: Double,
    val activityRatio: Double,
    val timestampMs: Long
)
