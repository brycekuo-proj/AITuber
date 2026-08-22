package com.aituber.poc.poc

import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.VisualizerWaveformMetrics

class VisualizerSpeakingDetector(
    private val rmsThreshold: Double = 0.12,
    private val peakThreshold: Double = 0.35,
    private val activityThreshold: Double = 0.20,
    private val attackMs: Long = 150L,
    private val releaseMs: Long = 300L
) {
    private var state = UniversalAiState.IDLE
    private var candidateSinceMs: Long? = null
    private var silentSinceMs: Long? = null
    private var transitionCount = 0
    private var lastTransitionElapsedMs: Long? = null
    private var lastCandidate = false

    fun reset() {
        state = UniversalAiState.IDLE
        candidateSinceMs = null
        silentSinceMs = null
        transitionCount = 0
        lastTransitionElapsedMs = null
        lastCandidate = false
    }

    fun evaluate(
        now: Long,
        metrics: VisualizerWaveformMetrics,
        voiceSessionActive: Boolean,
        visualizerAvailable: Boolean
    ): VisualizerSpeakingDecision {
        if (!visualizerAvailable) {
            return setState(UniversalAiState.UNKNOWN, now, candidate = false)
        }
        if (!voiceSessionActive) {
            candidateSinceMs = null
            silentSinceMs = now
            return setState(UniversalAiState.IDLE, now, candidate = false)
        }

        val candidate = isSpeakingCandidate(metrics)
        lastCandidate = candidate
        if (candidate) {
            silentSinceMs = null
            if (candidateSinceMs == null) candidateSinceMs = now
            if (state != UniversalAiState.SPEAKING && now - (candidateSinceMs ?: now) >= attackMs) {
                return setState(UniversalAiState.SPEAKING, now, candidate = true)
            }
        } else {
            candidateSinceMs = null
            if (silentSinceMs == null) silentSinceMs = now
            if (state == UniversalAiState.SPEAKING && now - (silentSinceMs ?: now) >= releaseMs) {
                return setState(UniversalAiState.IDLE, now, candidate = false)
            }
            if (state != UniversalAiState.SPEAKING) {
                return setState(UniversalAiState.IDLE, now, candidate = false)
            }
        }

        return decision(candidate)
    }

    fun isSpeakingCandidate(metrics: VisualizerWaveformMetrics): Boolean {
        return metrics.rms >= rmsThreshold ||
            (metrics.peak >= peakThreshold && metrics.activityRatio >= activityThreshold)
    }

    fun diagnostics(): VisualizerDetectorDiagnostics {
        return VisualizerDetectorDiagnostics(
            thresholdSummary = "rms>=${"%.2f".format(rmsThreshold)} or peak>=${"%.2f".format(peakThreshold)} + activity>=${"%.2f".format(activityThreshold)}",
            attackReleaseSummary = "attack=${attackMs}ms release=${releaseMs}ms",
            hysteresisState = "state=${state.name} candidate=$lastCandidate candidateSince=${candidateSinceMs ?: "n/a"} silentSince=${silentSinceMs ?: "n/a"}",
            transitionCount = transitionCount,
            lastTransitionElapsedMs = lastTransitionElapsedMs
        )
    }

    private fun setState(
        nextState: UniversalAiState,
        now: Long,
        candidate: Boolean
    ): VisualizerSpeakingDecision {
        if (nextState != state) {
            state = nextState
            transitionCount += 1
            lastTransitionElapsedMs = now
        }
        lastCandidate = candidate
        return decision(candidate)
    }

    private fun decision(candidate: Boolean): VisualizerSpeakingDecision {
        return VisualizerSpeakingDecision(
            state = state,
            candidate = candidate,
            diagnostics = diagnostics()
        )
    }
}

data class VisualizerSpeakingDecision(
    val state: UniversalAiState,
    val candidate: Boolean,
    val diagnostics: VisualizerDetectorDiagnostics
)

data class VisualizerDetectorDiagnostics(
    val thresholdSummary: String,
    val attackReleaseSummary: String,
    val hysteresisState: String,
    val transitionCount: Int,
    val lastTransitionElapsedMs: Long?
)
