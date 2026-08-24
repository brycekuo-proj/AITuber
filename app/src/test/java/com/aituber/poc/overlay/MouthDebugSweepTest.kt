package com.aituber.poc.overlay

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MouthDebugSweepTest {
    @Test
    fun testMouthUsesSteppedDiagnosticSweep() {
        assertArrayEquals(
            floatArrayOf(0f, 0.25f, 0.50f, 0.75f, 1.00f, 0f),
            MouthDebugSweep.RATIOS,
            0.0001f
        )
        assertEquals(500L, MouthDebugSweep.STEP_MS)
    }
}
