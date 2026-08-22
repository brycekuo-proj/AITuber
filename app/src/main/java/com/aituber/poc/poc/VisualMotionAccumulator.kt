package com.aituber.poc.poc

import com.aituber.poc.state.VisualMotionSample
import com.aituber.poc.state.VisualMotionSnapshot

class VisualMotionAccumulator(
    private val historyCapacity: Int = 300
) {
    private val samples = ArrayDeque<VisualMotionSample>(historyCapacity)
    private var active = "NO"
    private var roiBounds = "n/a"
    private var peak = 0.0
    private var validFrames = 0
    private var skippedFrames = 0
    private var manualPhase = "UNMARKED"
    private var testStartElapsedMs: Long? = null

    fun start(roiBounds: String, automatedTest: Boolean, now: Long) {
        this.roiBounds = roiBounds
        active = "YES"
        peak = 0.0
        validFrames = 0
        skippedFrames = 0
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

    fun record(now: Long, score: Double): VisualMotionSnapshot {
        validFrames += 1
        peak = maxOf(peak, score)
        val phase = phaseFor(now)
        val avg1 = rollingAverage(now, 1_000L, pendingScore = score)
        val avg3 = rollingAverage(now, 3_000L, pendingScore = score)
        val sample = VisualMotionSample(now, phase, score, avg1, avg3)
        if (samples.size == historyCapacity) samples.removeFirst()
        samples.addLast(sample)
        return snapshot()
    }

    fun snapshot(): VisualMotionSnapshot {
        val last = samples.lastOrNull()
        val quiet = samples.filter { it.phase == "QUIET" }
        val user = samples.filter { it.phase == "USER" }
        val ai = samples.filter { it.phase == "AI" }
        val quietAvg = quiet.averageScore()
        val userAvg = user.averageScore()
        val aiAvg = ai.averageScore()
        return VisualMotionSnapshot.empty().copy(
            active = active,
            roiBounds = roiBounds,
            currentMotionScore = last?.motionScore ?: 0.0,
            average1s = last?.average1s ?: 0.0,
            average3s = last?.average3s ?: 0.0,
            peakMotionScore = peak,
            validFrameCount = validFrames,
            skippedFrameCount = skippedFrames,
            currentTestPhase = phaseFor(last?.elapsedTimestampMs ?: testStartElapsedMs ?: 0L),
            quietAverageMotion = quietAvg,
            userAverageMotion = userAvg,
            aiAverageMotion = aiAvg,
            quietPeakMotion = quiet.maxScore(),
            userPeakMotion = user.maxScore(),
            aiPeakMotion = ai.maxScore(),
            aiQuietRatio = ratio(aiAvg, quietAvg),
            aiUserRatio = ratio(aiAvg, userAvg),
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

    private fun rollingAverage(now: Long, windowMs: Long, pendingScore: Double): Double {
        val values = samples.filter { now - it.elapsedTimestampMs < windowMs }.map { it.motionScore } + pendingScore
        return values.average()
    }

    private fun ratio(numerator: Double, denominator: Double): Double {
        return if (denominator <= 0.000001) 0.0 else numerator / denominator
    }

    private fun List<VisualMotionSample>.averageScore(): Double {
        return if (isEmpty()) 0.0 else map { it.motionScore }.average()
    }

    private fun List<VisualMotionSample>.maxScore(): Double {
        return maxOfOrNull { it.motionScore } ?: 0.0
    }
}
