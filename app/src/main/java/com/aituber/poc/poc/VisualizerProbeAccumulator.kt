package com.aituber.poc.poc

import com.aituber.poc.state.VisualizerPhaseSummary
import com.aituber.poc.state.VisualizerProbeSample
import com.aituber.poc.state.VisualizerProbeSnapshot
import com.aituber.poc.state.VisualizerWaveformMetrics

class VisualizerProbeAccumulator(
    private val historyCapacity: Int = 300
) {
    private val samples = ArrayDeque<VisualizerProbeSample>(historyCapacity)
    private var initStatus = "Not started"
    private var enabled = "NO"
    private var captureSize = 0
    private var captureRate = 0
    private var callbackCount = 0L
    private var currentMetrics = VisualizerWaveformMetrics.zero()
    private var testStartElapsedMs: Long? = null
    private var derivedSpeaking = "UNKNOWN"
    private var detectorThresholds = "rms>=0.12 or peak>=0.35 + activity>=0.20"
    private var detectorAttackRelease = "attack=150ms release=300ms"
    private var detectorHysteresisState = "state=UNKNOWN candidate=false candidateSince=n/a silentSince=n/a"
    private var detectorTransitionCount = 0
    private var detectorLastTransitionElapsedMs: Long? = null

    fun start(
        now: Long,
        initStatus: String,
        captureSize: Int,
        captureRate: Int,
        automatedTest: Boolean = true
    ) {
        samples.clear()
        callbackCount = 0L
        currentMetrics = VisualizerWaveformMetrics.zero()
        testStartElapsedMs = if (automatedTest) now else null
        derivedSpeaking = "IDLE"
        detectorHysteresisState = "state=IDLE candidate=false candidateSince=n/a silentSince=n/a"
        detectorTransitionCount = 0
        detectorLastTransitionElapsedMs = null
        this.initStatus = initStatus
        this.enabled = "YES"
        this.captureSize = captureSize
        this.captureRate = captureRate
    }

    fun fail(initStatus: String): VisualizerProbeSnapshot {
        samples.clear()
        callbackCount = 0L
        currentMetrics = VisualizerWaveformMetrics.zero()
        testStartElapsedMs = null
        derivedSpeaking = "UNKNOWN"
        detectorHysteresisState = "state=UNKNOWN candidate=false candidateSince=n/a silentSince=n/a"
        this.initStatus = initStatus
        enabled = "NO"
        captureSize = 0
        captureRate = 0
        return snapshot()
    }

    fun stop() {
        enabled = "NO"
        testStartElapsedMs = null
        derivedSpeaking = "IDLE"
        detectorHysteresisState = "state=IDLE candidate=false candidateSince=n/a silentSince=n/a"
    }

    fun record(now: Long, metrics: VisualizerWaveformMetrics): VisualizerProbeSnapshot {
        callbackCount += 1
        currentMetrics = metrics
        val sample = VisualizerProbeSample(now, phaseFor(now), metrics)
        if (samples.size == historyCapacity) samples.removeFirst()
        samples.addLast(sample)
        return snapshot()
    }

    fun updateDetector(decision: VisualizerSpeakingDecision) {
        derivedSpeaking = decision.state.name
        detectorThresholds = decision.diagnostics.thresholdSummary
        detectorAttackRelease = decision.diagnostics.attackReleaseSummary
        detectorHysteresisState = decision.diagnostics.hysteresisState
        detectorTransitionCount = decision.diagnostics.transitionCount
        detectorLastTransitionElapsedMs = decision.diagnostics.lastTransitionElapsedMs
    }

    fun snapshot(): VisualizerProbeSnapshot {
        val quiet = samples.filter { it.phase == "QUIET" }
        val user = samples.filter { it.phase == "USER" }
        val ai = samples.filter { it.phase == "AI" }
        val quietSummary = quiet.summary()
        val userSummary = user.summary()
        val aiSummary = ai.summary()
        return VisualizerProbeSnapshot.empty().copy(
            initStatus = initStatus,
            enabled = enabled,
            captureSize = captureSize,
            captureRate = captureRate,
            waveformCallbackCount = callbackCount,
            currentMetrics = currentMetrics,
            quietSummary = quietSummary,
            userSummary = userSummary,
            aiSummary = aiSummary,
            aiQuietRmsRatio = ratio(aiSummary.rmsAverage, quietSummary.rmsAverage),
            aiUserRmsRatio = ratio(aiSummary.rmsAverage, userSummary.rmsAverage),
            aiQuietPeakRatio = ratio(aiSummary.peakAverage, quietSummary.peakAverage),
            aiUserPeakRatio = ratio(aiSummary.peakAverage, userSummary.peakAverage),
            outputMixSignalStatus = outputMixSignalStatus(),
            derivedSpeaking = derivedSpeaking,
            detectorThresholds = detectorThresholds,
            detectorAttackRelease = detectorAttackRelease,
            detectorHysteresisState = detectorHysteresisState,
            detectorTransitionCount = detectorTransitionCount,
            detectorLastTransitionElapsedMs = detectorLastTransitionElapsedMs,
            currentTestPhase = samples.lastOrNull()?.phase ?: "UNMARKED",
            history = samples.toList()
        )
    }

    fun phaseFor(now: Long): String {
        val start = testStartElapsedMs ?: return "UNMARKED"
        val elapsed = now - start
        return when {
            elapsed < 0L -> "UNMARKED"
            elapsed < 5_000L -> "QUIET"
            elapsed < 15_000L -> "USER"
            elapsed < 25_000L -> "AI"
            elapsed <= 30_000L -> "QUIET"
            else -> "DONE"
        }
    }

    private fun outputMixSignalStatus(): String {
        if (enabled != "YES") return initStatus
        if (callbackCount == 0L) return "Waiting for waveform callback"
        val hasSignal = samples.any {
            it.metrics.rms > SIGNAL_EPSILON || it.metrics.peak > SIGNAL_EPSILON || it.metrics.activityRatio > 0.0
        }
        return if (hasSignal) "Receiving waveform activity" else "Visualizer output mix unavailable/zero"
    }

    private fun ratio(numerator: Double, denominator: Double): Double {
        return if (denominator <= 0.000001) 0.0 else numerator / denominator
    }

    private fun List<VisualizerProbeSample>.summary(): VisualizerPhaseSummary {
        if (isEmpty()) return VisualizerPhaseSummary.empty()
        return VisualizerPhaseSummary(
            rmsAverage = sumOf { it.metrics.rms } / size,
            peakAverage = sumOf { it.metrics.peak } / size,
            activityAverage = sumOf { it.metrics.activityRatio } / size,
            rmsPeak = maxOfOrNull { it.metrics.rms } ?: 0.0,
            peakPeak = maxOfOrNull { it.metrics.peak } ?: 0.0
        )
    }

    companion object {
        private const val SIGNAL_EPSILON = 0.000001
    }
}
