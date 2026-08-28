package com.aituber.poc.character.staticpng

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticPngBreathMotionTest {
    @Test
    fun breathStartsAtRestAndReachesPeakHalfCycle() {
        val period = StaticPngBreathMotion.DEFAULT_PERIOD_MS
        val rest = StaticPngBreathMotion.frame(
            elapsedMs = 0L,
            amplitudePercent = 100,
            periodMs = period
        )
        val peak = StaticPngBreathMotion.frame(
            elapsedMs = period / 2L,
            amplitudePercent = 100,
            periodMs = period
        )

        assertEquals(0f, rest.inhale, 0.0001f)
        assertEquals(1f, rest.scaleX, 0.0001f)
        assertEquals(1f, rest.scaleY, 0.0001f)
        assertEquals(1f, peak.inhale, 0.0001f)
        assertEquals(1f + StaticPngBreathMotion.MAX_SCALE_X_DELTA, peak.scaleX, 0.0001f)
        assertEquals(1f + StaticPngBreathMotion.MAX_SCALE_Y_DELTA, peak.scaleY, 0.0001f)
    }

    @Test
    fun breathAmplitudeScalesMotionWithoutGoingBelowBaseline() {
        val peak = StaticPngBreathMotion.frame(
            elapsedMs = StaticPngBreathMotion.DEFAULT_PERIOD_MS / 2L,
            amplitudePercent = 50,
            periodMs = StaticPngBreathMotion.DEFAULT_PERIOD_MS
        )

        assertEquals(1f + StaticPngBreathMotion.MAX_SCALE_X_DELTA * 0.5f, peak.scaleX, 0.0001f)
        assertEquals(1f + StaticPngBreathMotion.MAX_SCALE_Y_DELTA * 0.5f, peak.scaleY, 0.0001f)
        assertTrue(peak.scaleX >= 1f)
        assertTrue(peak.scaleY >= 1f)
    }

    @Test
    fun breathRuntimeTuningClampsAndDefaultDoesNotSyncWithIdle() {
        StaticPngBreathMotion.setAmplitudePercent(-10)
        assertEquals(StaticPngBreathMotion.AMPLITUDE_MIN_PERCENT, StaticPngBreathMotion.amplitudePercent)
        StaticPngBreathMotion.setAmplitudePercent(150)
        assertEquals(StaticPngBreathMotion.AMPLITUDE_MAX_PERCENT, StaticPngBreathMotion.amplitudePercent)

        StaticPngBreathMotion.setPeriodMs(500L)
        assertEquals(StaticPngBreathMotion.PERIOD_MIN_MS, StaticPngBreathMotion.periodMs)
        StaticPngBreathMotion.setPeriodMs(20_000L)
        assertEquals(StaticPngBreathMotion.PERIOD_MAX_MS, StaticPngBreathMotion.periodMs)
        assertNotEquals(StaticPngIdleMotion.PERIOD_MS, StaticPngBreathMotion.DEFAULT_PERIOD_MS)

        StaticPngBreathMotion.setAmplitudePercent(StaticPngBreathMotion.DEFAULT_AMPLITUDE_PERCENT)
        StaticPngBreathMotion.setPeriodMs(StaticPngBreathMotion.DEFAULT_PERIOD_MS)
    }

    @Test
    fun wholeBodyBreathDefaultsOffSoChestBreathIsPrimaryStaticPngEffect() {
        assertEquals(0, StaticPngBreathMotion.DEFAULT_AMPLITUDE_PERCENT)
        val peak = StaticPngBreathMotion.frame(
            elapsedMs = StaticPngBreathMotion.DEFAULT_PERIOD_MS / 2L,
            amplitudePercent = StaticPngBreathMotion.DEFAULT_AMPLITUDE_PERCENT,
            periodMs = StaticPngBreathMotion.DEFAULT_PERIOD_MS
        )

        assertEquals(1f, peak.scaleX, 0.0001f)
        assertEquals(1f, peak.scaleY, 0.0001f)
    }
}
