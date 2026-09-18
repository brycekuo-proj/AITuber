package com.aituber.poc.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BroadwayPressSpecTest {
    @Test
    fun pressDownSpecMatchesApprovedV1() {
        assertEquals(0.965f, BroadwayPressSpec.PRESS_SCALE)
        assertEquals(4f, BroadwayPressSpec.PRESS_OFFSET_DP)
        assertEquals(60L, BroadwayPressSpec.PRESS_DOWN_MS)
        assertEquals(120L, BroadwayPressSpec.RELEASE_MS)
    }
}
