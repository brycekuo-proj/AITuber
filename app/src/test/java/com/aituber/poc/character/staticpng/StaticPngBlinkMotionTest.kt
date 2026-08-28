package com.aituber.poc.character.staticpng

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticPngBlinkMotionTest {
    @Test
    fun blinkSequenceReturnsToOpen() {
        val sequence = StaticPngBlinkMotion.BLINK_SEQUENCE.map { it.shape }

        assertEquals(
            listOf(
                StaticPngEyeShape.OPEN,
                StaticPngEyeShape.HALF,
                StaticPngEyeShape.CLOSED,
                StaticPngEyeShape.HALF,
                StaticPngEyeShape.OPEN
            ),
            sequence
        )
    }

    @Test
    fun blinkDurationMatchesPocRange() {
        assertTrue(StaticPngBlinkMotion.BLINK_DURATION_MS in 120L..180L)
        assertEquals(
            StaticPngBlinkMotion.BLINK_DURATION_MS,
            StaticPngBlinkMotion.BLINK_SEQUENCE.last().delayMs
        )
    }

    @Test
    fun randomIntervalStaysInRequestedRange() {
        assertEquals(3_000L, StaticPngBlinkMotion.intervalMs(0f))
        assertEquals(6_000L, StaticPngBlinkMotion.intervalMs(1f))

        listOf(-1f, 0.3f, 0.7f, 2f).forEach { unit ->
            assertTrue(StaticPngBlinkMotion.intervalMs(unit) in 3_000L..6_000L)
        }
    }

    @Test
    fun doubleBlinkProbabilityIsLow() {
        assertTrue(StaticPngBlinkMotion.DOUBLE_BLINK_PROBABILITY in 0.10f..0.15f)
        assertTrue(StaticPngBlinkMotion.shouldDoubleBlink(0f))
        assertFalse(StaticPngBlinkMotion.shouldDoubleBlink(0.99f))
    }

    @Test
    fun mouthShapeRulesRemainUnchanged() {
        assertEquals(StaticPngMouthShape.CLOSED, StaticPngMouthShape.fromRatio(0.25f))
        assertEquals(StaticPngMouthShape.HALF, StaticPngMouthShape.fromRatio(0.251f))
        assertEquals(StaticPngMouthShape.HALF, StaticPngMouthShape.fromRatio(0.65f))
        assertEquals(StaticPngMouthShape.OPEN, StaticPngMouthShape.fromRatio(0.651f))
    }

    @Test
    fun repeatedStartDoesNotScheduleDuplicateBlink() {
        val state = StaticPngBlinkLoopState()

        assertTrue(state.startAutoBlink())
        assertFalse(state.startAutoBlink())

        assertEquals(1, state.scheduleCount)
        assertTrue(state.blinkScheduled)
    }

    @Test
    fun stopClearsScheduledAndRunningBlink() {
        val state = StaticPngBlinkLoopState()

        state.startAutoBlink()
        state.markScheduledBlinkStarted()
        state.stopAutoBlink()

        assertFalse(state.autoBlinkEnabled)
        assertFalse(state.blinkScheduled)
        assertFalse(state.blinkRunning)
    }

    @Test
    fun runningBlinkBlocksNewScheduleUntilFinished() {
        val state = StaticPngBlinkLoopState()

        state.startAutoBlink()
        state.markScheduledBlinkStarted()

        assertFalse(state.scheduleNext())

        state.markBlinkFinished()
        assertTrue(state.scheduleNext())
    }
}
