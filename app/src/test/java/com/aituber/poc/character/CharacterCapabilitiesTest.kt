package com.aituber.poc.character

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterCapabilitiesTest {
    @Test
    fun runtimeBreathNotFoundStatusIsHandledAsUnsupportedWithoutFallbackRequirement() {
        assertFalse(CharacterCapabilities.supportsBreath(CharacterCapabilities.BREATH_NOT_FOUND))
    }

    @Test
    fun unavailableBreathStatusIsHandledAsUnsupported() {
        assertFalse(CharacterCapabilities.supportsBreath(CharacterCapabilities.BREATH_UNAVAILABLE))
    }

    @Test
    fun appliedBreathStatusEnablesBreathControls() {
        assertTrue(CharacterCapabilities.supportsBreath(CharacterCapabilities.BREATH_APPLIED))
    }
}
