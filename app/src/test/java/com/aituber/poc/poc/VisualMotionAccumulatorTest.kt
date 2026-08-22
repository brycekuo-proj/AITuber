package com.aituber.poc.poc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualMotionAccumulatorTest {
    @Test
    fun rollingOneAndThreeSecondAveragesAreComputed() {
        val accumulator = VisualMotionAccumulator()
        accumulator.start("0,0,10,10", automatedTest = false, now = 0L)

        accumulator.record(0L, 0.0)
        accumulator.record(500L, 0.5)
        val snapshot = accumulator.record(1_500L, 1.0)

        assertEquals(1.0, snapshot.average1s, 0.001)
        assertEquals(0.5, snapshot.average3s, 0.001)
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
    fun stoppingAutomatedTestPreservesDonePhase() {
        val accumulator = VisualMotionAccumulator()
        accumulator.start("0,0,10,10", automatedTest = true, now = 0L)

        accumulator.record(16_000L, 0.6)
        accumulator.stop()

        assertEquals("DONE", accumulator.snapshot().currentTestPhase)
    }

    @Test
    fun phaseSummaryAveragesAreSeparated() {
        val accumulator = VisualMotionAccumulator()
        accumulator.start("0,0,10,10", automatedTest = true, now = 0L)

        accumulator.record(1_000L, 0.1)
        accumulator.record(6_000L, 0.2)
        val snapshot = accumulator.record(16_000L, 0.6)

        assertEquals(0.1, snapshot.quietAverageMotion, 0.001)
        assertEquals(0.2, snapshot.userAverageMotion, 0.001)
        assertEquals(0.6, snapshot.aiAverageMotion, 0.001)
        assertTrue(snapshot.aiQuietRatio > 5.0)
        assertTrue(snapshot.aiUserRatio > 2.0)
    }
}
