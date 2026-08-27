package com.aituber.poc.character.staticpng

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticPngIdleMotionTest {
    @Test
    fun idleMotionParametersStayInLightMotionRange() {
        assertTrue(StaticPngIdleMotion.PERIOD_MS in 3_500L..5_000L)
        assertTrue(StaticPngIdleMotion.OFFSET_Y_DP in 1f..3f)
        assertTrue(StaticPngIdleMotion.SCALE_AMPLITUDE <= 0.005f)
    }

    @Test
    fun idleMotionStartsAtNeutralTransform() {
        val frame = StaticPngIdleMotion.frame(0L)

        assertEquals(0f, frame.phase, 0.0001f)
        assertEquals(0f, frame.offsetYDp, 0.0001f)
        assertEquals(1f, frame.scale, 0.0001f)
    }

    @Test
    fun idleMotionStaysInsideConfiguredBounds() {
        val samples = (0L..StaticPngIdleMotion.PERIOD_MS step 100L).map {
            StaticPngIdleMotion.frame(it)
        }

        samples.forEach { frame ->
            assertTrue(frame.phase >= 0f)
            assertTrue(frame.phase < 1f || frame.phase == 0f)
            assertTrue(frame.offsetYDp >= -2.001f)
            assertTrue(frame.offsetYDp <= 2.001f)
            assertTrue(frame.scale >= 0.995f)
            assertTrue(frame.scale <= 1.005f)
        }
    }

    @Test
    fun idleMotionRepeatsWithoutAccumulatedDrift() {
        val start = StaticPngIdleMotion.frame(125L)
        val nextCycle = StaticPngIdleMotion.frame(125L + StaticPngIdleMotion.PERIOD_MS)

        assertEquals(start.phase, nextCycle.phase, 0.0001f)
        assertEquals(start.offsetYDp, nextCycle.offsetYDp, 0.0001f)
        assertEquals(start.scale, nextCycle.scale, 0.0001f)
    }
}
