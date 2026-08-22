package com.aituber.poc.state

data class VisualizerWaveformMetrics(
    val rms: Double,
    val peak: Double,
    val activityRatio: Double
) {
    companion object {
        fun zero() = VisualizerWaveformMetrics(
            rms = 0.0,
            peak = 0.0,
            activityRatio = 0.0
        )
    }
}

data class VisualizerProbeSample(
    val elapsedTimestampMs: Long,
    val phase: String,
    val metrics: VisualizerWaveformMetrics
)

data class VisualizerPhaseSummary(
    val rmsAverage: Double,
    val peakAverage: Double,
    val activityAverage: Double,
    val rmsPeak: Double,
    val peakPeak: Double
) {
    companion object {
        fun empty() = VisualizerPhaseSummary(
            rmsAverage = 0.0,
            peakAverage = 0.0,
            activityAverage = 0.0,
            rmsPeak = 0.0,
            peakPeak = 0.0
        )
    }
}

data class VisualizerProbeSnapshot(
    val initStatus: String,
    val enabled: String,
    val captureSize: Int,
    val captureRate: Int,
    val waveformCallbackCount: Long,
    val currentMetrics: VisualizerWaveformMetrics,
    val quietSummary: VisualizerPhaseSummary,
    val userSummary: VisualizerPhaseSummary,
    val aiSummary: VisualizerPhaseSummary,
    val aiQuietRmsRatio: Double,
    val aiUserRmsRatio: Double,
    val aiQuietPeakRatio: Double,
    val aiUserPeakRatio: Double,
    val outputMixSignalStatus: String,
    val derivedSpeaking: String,
    val detectorThresholds: String,
    val detectorAttackRelease: String,
    val detectorHysteresisState: String,
    val detectorTransitionCount: Int,
    val detectorLastTransitionElapsedMs: Long?,
    val currentTestPhase: String,
    val history: List<VisualizerProbeSample>
) {
    companion object {
        fun empty() = VisualizerProbeSnapshot(
            initStatus = "Not started",
            enabled = "NO",
            captureSize = 0,
            captureRate = 0,
            waveformCallbackCount = 0L,
            currentMetrics = VisualizerWaveformMetrics.zero(),
            quietSummary = VisualizerPhaseSummary.empty(),
            userSummary = VisualizerPhaseSummary.empty(),
            aiSummary = VisualizerPhaseSummary.empty(),
            aiQuietRmsRatio = 0.0,
            aiUserRmsRatio = 0.0,
            aiQuietPeakRatio = 0.0,
            aiUserPeakRatio = 0.0,
            outputMixSignalStatus = "Not started",
            derivedSpeaking = "UNKNOWN",
            detectorThresholds = "rms>=0.12 or peak>=0.35 + activity>=0.20",
            detectorAttackRelease = "attack=150ms release=300ms",
            detectorHysteresisState = "state=UNKNOWN candidate=false candidateSince=n/a silentSince=n/a",
            detectorTransitionCount = 0,
            detectorLastTransitionElapsedMs = null,
            currentTestPhase = "UNMARKED",
            history = emptyList()
        )
    }
}
