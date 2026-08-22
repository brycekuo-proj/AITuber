package com.aituber.poc.poc

import com.aituber.poc.state.VisualizerWaveformMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualizerProbeAccumulatorTest {
    @Test
    fun phaseTimingMatchesThirtySecondTestPlan() {
        val accumulator = VisualizerProbeAccumulator()
        accumulator.start(1_000L, "Initialized Visualizer(0)", captureSize = 512, captureRate = 10_000)

        assertEquals("QUIET", accumulator.phaseFor(1_000L))
        assertEquals("USER", accumulator.phaseFor(6_000L))
        assertEquals("AI", accumulator.phaseFor(16_000L))
        assertEquals("QUIET", accumulator.phaseFor(26_000L))
        assertEquals("DONE", accumulator.phaseFor(32_000L))
    }

    @Test
    fun phaseSummariesAndRatiosAreComputed() {
        val accumulator = VisualizerProbeAccumulator()
        accumulator.start(0L, "Initialized Visualizer(0)", captureSize = 512, captureRate = 10_000)

        accumulator.record(1_000L, metrics(rms = 0.1, peak = 0.2, activity = 0.3))
        accumulator.record(6_000L, metrics(rms = 0.2, peak = 0.4, activity = 0.5))
        val snapshot = accumulator.record(16_000L, metrics(rms = 0.6, peak = 0.8, activity = 0.9))

        assertEquals(0.1, snapshot.quietSummary.rmsAverage, 0.001)
        assertEquals(0.2, snapshot.userSummary.rmsAverage, 0.001)
        assertEquals(0.6, snapshot.aiSummary.rmsAverage, 0.001)
        assertTrue(snapshot.aiQuietRmsRatio > 5.0)
        assertTrue(snapshot.aiUserRmsRatio > 2.0)
        assertTrue(snapshot.aiQuietPeakRatio > 3.0)
        assertTrue(snapshot.aiUserPeakRatio > 1.0)
    }

    @Test
    fun zeroWaveformSamplesAreMarkedUnavailableCandidate() {
        val accumulator = VisualizerProbeAccumulator()
        accumulator.start(0L, "Initialized Visualizer(0)", captureSize = 512, captureRate = 10_000)

        val snapshot = accumulator.record(1_000L, metrics(rms = 0.0, peak = 0.0, activity = 0.0))

        assertEquals("Visualizer output mix unavailable/zero", snapshot.outputMixSignalStatus)
    }

    @Test
    fun callbackCountAndCaptureConfigAreRetained() {
        val accumulator = VisualizerProbeAccumulator()
        accumulator.start(0L, "Initialized Visualizer(0)", captureSize = 256, captureRate = 5_000)

        val snapshot = accumulator.record(1_000L, metrics(rms = 0.1, peak = 0.2, activity = 0.3))

        assertEquals("YES", snapshot.enabled)
        assertEquals(256, snapshot.captureSize)
        assertEquals(5_000, snapshot.captureRate)
        assertEquals(1L, snapshot.waveformCallbackCount)
    }

    private fun metrics(rms: Double, peak: Double, activity: Double): VisualizerWaveformMetrics {
        return VisualizerWaveformMetrics(rms = rms, peak = peak, activityRatio = activity)
    }
}
