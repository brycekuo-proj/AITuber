package com.aituber.poc.poc

import com.aituber.poc.state.VisualMotionMetrics
import com.aituber.poc.state.VisualMotionPhaseSummary
import com.aituber.poc.state.VisualMotionSample
import com.aituber.poc.state.VisualMotionSnapshot

class VisualMotionAccumulator(
    private val historyCapacity: Int = 300
) {
    private val samples = ArrayDeque<VisualMotionSample>(historyCapacity)
    private var active = "NO"
    private var roiBounds = "n/a"
    private var filteredPeakMean = 0.0
    private var rawPeakMean = 0.0
    private var validFrames = 0
    private var skippedFrames = 0
    private var totalProcessingMs = 0L
    private var manualPhase = "UNMARKED"
    private var testStartElapsedMs: Long? = null

    fun start(roiBounds: String, automatedTest: Boolean, now: Long) {
        this.roiBounds = roiBounds
        active = "YES"
        filteredPeakMean = 0.0
        rawPeakMean = 0.0
        validFrames = 0
        skippedFrames = 0
        totalProcessingMs = 0L
        manualPhase = "UNMARKED"
        samples.clear()
        testStartElapsedMs = if (automatedTest) now else null
    }

    fun stop() {
        active = "NO"
        manualPhase = if (testStartElapsedMs == null) "UNMARKED" else "DONE"
        testStartElapsedMs = null
    }

    fun skipFrame() {
        skippedFrames += 1
    }

    fun record(now: Long, metrics: VisualMotionMetrics, processingMs: Long = 0L): VisualMotionSnapshot {
        validFrames += 1
        totalProcessingMs += processingMs.coerceAtLeast(0L)
        rawPeakMean = maxOf(rawPeakMean, metrics.meanMotion)
        val phase = phaseFor(now)
        val excluded = excludedFromSummary(now, phase)
        if (!excluded) {
            filteredPeakMean = maxOf(filteredPeakMean, metrics.meanMotion)
        }
        val avg1 = rollingAverage(now, 1_000L, pendingMetrics = metrics)
        val avg3 = rollingAverage(now, 3_000L, pendingMetrics = metrics)
        val sample = VisualMotionSample(now, phase, metrics, avg1, avg3, excluded)
        if (samples.size == historyCapacity) samples.removeFirst()
        samples.addLast(sample)
        return snapshot()
    }

    fun snapshot(): VisualMotionSnapshot {
        val last = samples.lastOrNull()
        val quiet = samples.filter { it.phase == "QUIET" }
        val user = samples.filter { it.phase == "USER" }
        val ai = samples.filter { it.phase == "AI" }
        val quietSummary = quiet.summary()
        val userSummary = user.summary()
        val aiSummary = ai.summary()
        return VisualMotionSnapshot.empty().copy(
            active = active,
            roiBounds = roiBounds,
            currentMetrics = last?.metrics ?: VisualMotionMetrics.zero(),
            average1s = last?.average1s ?: VisualMotionMetrics.zero(),
            average3s = last?.average3s ?: VisualMotionMetrics.zero(),
            filteredPeakMotionScore = filteredPeakMean,
            rawPeakMotionScore = rawPeakMean,
            validFrameCount = validFrames,
            skippedFrameCount = skippedFrames,
            averageProcessingMs = if (validFrames == 0) 0.0 else totalProcessingMs.toDouble() / validFrames,
            currentTestPhase = phaseFor(last?.elapsedTimestampMs ?: testStartElapsedMs ?: 0L),
            quietSummary = quietSummary,
            userSummary = userSummary,
            aiSummary = aiSummary,
            aiQuietMeanRatio = ratio(aiSummary.meanAverage, quietSummary.meanAverage),
            aiUserMeanRatio = ratio(aiSummary.meanAverage, userSummary.meanAverage),
            aiQuietChangedPixelRatio = ratio(aiSummary.changedPixelRatioAverage, quietSummary.changedPixelRatioAverage),
            aiUserChangedPixelRatio = ratio(aiSummary.changedPixelRatioAverage, userSummary.changedPixelRatioAverage),
            aiQuietHighMotionRatio = ratio(aiSummary.highMotionPercentileAverage, quietSummary.highMotionPercentileAverage),
            aiUserHighMotionRatio = ratio(aiSummary.highMotionPercentileAverage, userSummary.highMotionPercentileAverage),
            aiQuietEdgeMotionRatio = ratio(aiSummary.edgeMotionAverage, quietSummary.edgeMotionAverage),
            aiUserEdgeMotionRatio = ratio(aiSummary.edgeMotionAverage, userSummary.edgeMotionAverage),
            aiQuietColorMotionRatio = ratio(aiSummary.colorMotionAverage, quietSummary.colorMotionAverage),
            aiUserColorMotionRatio = ratio(aiSummary.colorMotionAverage, userSummary.colorMotionAverage),
            history = samples.toList()
        )
    }

    fun phaseFor(now: Long): String {
        val start = testStartElapsedMs ?: return manualPhase
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

    fun excludedFromSummary(now: Long, phase: String): Boolean {
        val start = testStartElapsedMs ?: return false
        val elapsed = now - start
        if (elapsed < INITIAL_QUIET_EXCLUSION_MS) return true
        val phaseStart = when (phase) {
            "QUIET" -> if (elapsed < USER_PHASE_START_MS) 0L else FINAL_QUIET_PHASE_START_MS
            "USER" -> USER_PHASE_START_MS
            "AI" -> AI_PHASE_START_MS
            else -> return false
        }
        return elapsed - phaseStart < PHASE_SWITCH_EXCLUSION_MS
    }

    private fun rollingAverage(
        now: Long,
        windowMs: Long,
        pendingMetrics: VisualMotionMetrics
    ): VisualMotionMetrics {
        val values = samples.filter { now - it.elapsedTimestampMs < windowMs }.map { it.metrics } + pendingMetrics
        return values.averageMetrics()
    }

    private fun ratio(numerator: Double, denominator: Double): Double {
        return if (denominator <= 0.000001) 0.0 else numerator / denominator
    }

    private fun List<VisualMotionSample>.summary(): VisualMotionPhaseSummary {
        val rawPeak = maxOfOrNull { it.metrics.meanMotion } ?: 0.0
        val included = filterNot { it.excludedFromSummary }
        if (included.isEmpty()) {
            return VisualMotionPhaseSummary.empty().copy(rawPeakMeanMotion = rawPeak)
        }
        val average = included.map { it.metrics }.averageMetrics()
        return VisualMotionPhaseSummary(
            meanAverage = average.meanMotion,
            changedPixelRatioAverage = average.changedPixelRatio,
            highMotionPercentileAverage = average.highMotionPercentile,
            edgeMotionAverage = average.edgeMotion,
            colorMotionAverage = average.colorMotion,
            filteredPeakMeanMotion = included.maxOfOrNull { it.metrics.meanMotion } ?: 0.0,
            rawPeakMeanMotion = rawPeak
        )
    }

    private fun List<VisualMotionMetrics>.averageMetrics(): VisualMotionMetrics {
        if (isEmpty()) return VisualMotionMetrics.zero()
        return VisualMotionMetrics(
            meanMotion = sumOf { it.meanMotion } / size,
            changedPixelRatio = sumOf { it.changedPixelRatio } / size,
            highMotionPercentile = sumOf { it.highMotionPercentile } / size,
            edgeMotion = sumOf { it.edgeMotion } / size,
            colorMotion = sumOf { it.colorMotion } / size
        )
    }

    companion object {
        private const val INITIAL_QUIET_EXCLUSION_MS = 1_500L
        private const val PHASE_SWITCH_EXCLUSION_MS = 500L
        private const val USER_PHASE_START_MS = 5_000L
        private const val AI_PHASE_START_MS = 15_000L
        private const val FINAL_QUIET_PHASE_START_MS = 25_000L
    }
}
