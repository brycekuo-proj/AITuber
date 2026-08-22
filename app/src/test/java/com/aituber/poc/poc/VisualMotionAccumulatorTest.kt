package com.aituber.poc.poc

import com.aituber.poc.state.VisualMotionMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualMotionAccumulatorTest {
    @Test
    fun rollingOneAndThreeSecondAveragesAreComputed() {
        val accumulator = VisualMotionAccumulator()
        accumulator.start("0,0,10,10", automatedTest = false, now = 0L)

        accumulator.record(0L, metrics(mean = 0.0, color = 0.0))
        accumulator.record(500L, metrics(mean = 0.5, color = 0.2))
        val snapshot = accumulator.record(1_500L, metrics(mean = 1.0, color = 0.4))

        assertEquals(1.0, snapshot.average1s.meanMotion, 0.001)
        assertEquals(0.5, snapshot.average3s.meanMotion, 0.001)
        assertEquals(0.4, snapshot.average1s.colorMotion, 0.001)
        assertEquals(0.2, snapshot.average3s.colorMotion, 0.001)
    }

    @Test
    fun automatedPhaseTimingMatchesThirtySecondTestPlan() {
        val accumulator = VisualMotionAccumulator()
        accumulator.start("0,0,10,10", automatedTest = true, now = 1_000L)

        assertEquals("QUIET", accumulator.phaseFor(1_000L))
        assertEquals("USER", accumulator.phaseFor(6_000L))
        assertEquals("AI", accumulator.phaseFor(16_000L))
        assertEquals("QUIET", accumulator.phaseFor(26_000L))
        assertEquals("DONE", accumulator.phaseFor(32_000L))
    }

    @Test
    fun transitionExclusionWindowMarksExpectedSamples() {
        val accumulator = VisualMotionAccumulator()
        accumulator.start("0,0,10,10", automatedTest = true, now = 0L)

        assertTrue(accumulator.excludedFromSummary(1_000L, "QUIET"))
        assertTrue(accumulator.excludedFromSummary(5_100L, "USER"))
        assertTrue(accumulator.excludedFromSummary(15_100L, "AI"))
        assertTrue(accumulator.excludedFromSummary(25_100L, "QUIET"))
        assertTrue(!accumulator.excludedFromSummary(2_000L, "QUIET"))
        assertTrue(!accumulator.excludedFromSummary(6_000L, "USER"))
    }

    @Test
    fun excludedSamplesDoNotAffectPhaseSummaryOrFilteredPeak() {
        val accumulator = VisualMotionAccumulator()
        accumulator.start("0,0,10,10", automatedTest = true, now = 0L)

        accumulator.record(1_000L, metrics(mean = 0.9))
        val snapshot = accumulator.record(2_000L, metrics(mean = 0.1))

        assertEquals(0.1, snapshot.quietSummary.meanAverage, 0.001)
        assertEquals(0.1, snapshot.quietSummary.filteredPeakMeanMotion, 0.001)
        assertEquals(0.9, snapshot.quietSummary.rawPeakMeanMotion, 0.001)
        assertEquals(0.1, snapshot.filteredPeakMotionScore, 0.001)
        assertEquals(0.9, snapshot.rawPeakMotionScore, 0.001)
    }

    @Test
    fun stoppingAutomatedTestPreservesDonePhase() {
        val accumulator = VisualMotionAccumulator()
        accumulator.start("0,0,10,10", automatedTest = true, now = 0L)

        accumulator.record(16_000L, metrics(mean = 0.6))
        accumulator.stop()

        assertEquals("DONE", accumulator.snapshot().currentTestPhase)
    }

    @Test
    fun phaseSummaryAveragesAndRatiosAreSeparatedByMetric() {
        val accumulator = VisualMotionAccumulator()
        accumulator.start("0,0,10,10", automatedTest = true, now = 0L)

        accumulator.record(2_000L, metrics(mean = 0.1, changed = 0.2, high = 0.3, edge = 0.4, color = 0.5))
        accumulator.record(6_000L, metrics(mean = 0.2, changed = 0.3, high = 0.4, edge = 0.5, color = 0.6))
        val snapshot = accumulator.record(16_000L, metrics(mean = 0.6, changed = 0.9, high = 1.2, edge = 1.5, color = 1.8))

        assertEquals(0.1, snapshot.quietSummary.meanAverage, 0.001)
        assertEquals(0.2, snapshot.userSummary.meanAverage, 0.001)
        assertEquals(0.6, snapshot.aiSummary.meanAverage, 0.001)
        assertTrue(snapshot.aiQuietMeanRatio > 5.0)
        assertTrue(snapshot.aiUserMeanRatio > 2.0)
        assertTrue(snapshot.aiUserColorMotionRatio > 2.0)
    }

    @Test
    fun processingAverageIsTracked() {
        val accumulator = VisualMotionAccumulator()
        accumulator.start("0,0,10,10", automatedTest = false, now = 0L)

        accumulator.record(0L, metrics(mean = 0.1), processingMs = 4L)
        val snapshot = accumulator.record(100L, metrics(mean = 0.2), processingMs = 8L)

        assertEquals(6.0, snapshot.averageProcessingMs, 0.001)
    }

    private fun metrics(
        mean: Double,
        changed: Double = mean,
        high: Double = mean,
        edge: Double = mean,
        color: Double = mean
    ): VisualMotionMetrics {
        return VisualMotionMetrics(
            meanMotion = mean,
            changedPixelRatio = changed,
            highMotionPercentile = high,
            edgeMotion = edge,
            colorMotion = color
        )
    }
}
