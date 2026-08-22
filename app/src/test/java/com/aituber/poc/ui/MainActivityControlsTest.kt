package com.aituber.poc.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityControlsTest {
    @Test
    fun legacyProbeControlsAreNotPrimaryControls() {
        val primary = MainActivity.primaryControlLabels.toSet()

        MainActivity.legacyProbeControlLabels.forEach { legacyLabel ->
            assertFalse(primary.contains(legacyLabel))
        }
    }

    @Test
    fun primaryControlsContainOnlyCurrentCaptureWorkflowActions() {
        assertTrue(MainActivity.primaryControlLabels.contains("START CHATGPT CAPTURE"))
        assertTrue(MainActivity.primaryControlLabels.contains("STOP"))
        assertTrue(MainActivity.primaryControlLabels.contains("ENABLE MOUTH OVERLAY"))
        assertTrue(MainActivity.primaryControlLabels.contains("DISABLE MOUTH OVERLAY"))
        assertTrue(MainActivity.primaryControlLabels.contains("SHOW DIAGNOSTICS"))
    }
}
