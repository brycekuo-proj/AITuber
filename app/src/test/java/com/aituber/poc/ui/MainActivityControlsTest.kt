package com.aituber.poc.ui

import com.aituber.poc.character.CharacterCapabilities
import com.aituber.poc.character.CharacterMode
import org.junit.Assert.assertEquals
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
        assertEquals(
            listOf(
                "START CAPTURE",
                "OVERLAY: OFF",
                "CHARACTER: MINIMAL",
                "SHOW DIAGNOSTICS"
            ),
            MainActivity.primaryControlLabels
        )
    }

    @Test
    fun captureControlLabelReflectsState() {
        assertEquals("START CAPTURE", DebugControlLabels.capture(captureActive = false))
        assertEquals("STOP CAPTURE", DebugControlLabels.capture(captureActive = true))
    }

    @Test
    fun overlayControlLabelReflectsState() {
        assertEquals("OVERLAY: OFF", DebugControlLabels.overlay(overlayEnabled = false))
        assertEquals("OVERLAY: ON", DebugControlLabels.overlay(overlayEnabled = true))
    }

    @Test
    fun characterModeControlLabelReflectsRequestedMode() {
        assertEquals("CHARACTER: MINIMAL", DebugControlLabels.character(CharacterMode.MINIMAL_MOUTH))
        assertEquals("CHARACTER: LIVE2D", DebugControlLabels.character(CharacterMode.LIVE2D))
    }

    @Test
    fun diagnosticsControlLabelReflectsExpandedState() {
        assertEquals("SHOW DIAGNOSTICS", DebugControlLabels.diagnostics(expanded = false))
        assertEquals("HIDE DIAGNOSTICS", DebugControlLabels.diagnostics(expanded = true))
    }

    @Test
    fun blinkTestRemainsAvailableInDiagnostics() {
        assertEquals("TEST BLINK", DebugControlLabels.TEST_BLINK)
    }

    @Test
    fun unsupportedBreathTestIsClearlyMarkedAndDisabled() {
        assertEquals("TEST BREATH - UNSUPPORTED", DebugControlLabels.testBreath(CharacterCapabilities.BREATH_NOT_FOUND))
        assertFalse(DebugControlLabels.testBreathEnabled(CharacterCapabilities.BREATH_NOT_FOUND))
        assertFalse(DebugControlLabels.testBreathEnabled(CharacterCapabilities.BREATH_UNAVAILABLE))
    }

    @Test
    fun supportedBreathTestIsEnabled() {
        assertEquals("TEST BREATH", DebugControlLabels.testBreath(CharacterCapabilities.BREATH_APPLIED))
        assertTrue(DebugControlLabels.testBreathEnabled(CharacterCapabilities.BREATH_APPLIED))
    }

    @Test
    fun unsupportedIdleTestIsClearlyMarkedAndDisabled() {
        assertEquals("TEST IDLE - UNSUPPORTED", DebugControlLabels.testIdle(idleMotionCount = 0))
        assertFalse(DebugControlLabels.testIdleEnabled(idleMotionCount = 0))
    }

    @Test
    fun supportedIdleTestIsEnabled() {
        assertEquals("TEST IDLE", DebugControlLabels.testIdle(idleMotionCount = 2))
        assertTrue(DebugControlLabels.testIdleEnabled(idleMotionCount = 2))
    }

    @Test
    fun unsupportedPhysicsTestIsClearlyMarkedAndDisabled() {
        assertEquals("TEST PHYSICS - UNSUPPORTED", DebugControlLabels.testPhysics("NO"))
        assertFalse(DebugControlLabels.testPhysicsEnabled("NO"))
    }

    @Test
    fun supportedPhysicsTestIsEnabled() {
        assertEquals("TEST PHYSICS", DebugControlLabels.testPhysics("YES"))
        assertTrue(DebugControlLabels.testPhysicsEnabled("YES"))
    }
}
