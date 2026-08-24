package com.aituber.poc.character.live2d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Live2DFallbackHeadIdleControllerTest {
    @Test
    fun fallbackHeadIdleEnabledOnlyWhenOfficialIdleAbsent() {
        assertFalse(Live2DFallbackHeadIdleController(Live2DCharacterProfiles.Haru) { 0L }.enabled)
        assertTrue(Live2DFallbackHeadIdleController(Live2DCharacterProfiles.LoafDog) { 0L }.enabled)
    }

    @Test
    fun dogFallbackMapsOnlyParamAngleXAndY() {
        val mapping = Live2DCharacterProfiles.LoafDog.parameterMapping

        assertEquals("ParamAngleX", mapping.headX)
        assertEquals("ParamAngleY", mapping.headY)
        assertNull(mapping.headZ)
    }

    @Test
    fun fallbackHeadIdleIsSubtleAndWithinConfiguredAmplitude() {
        var now = 1_000L
        val controller = Live2DFallbackHeadIdleController(Live2DCharacterProfiles.LoafDog) { now }

        val frame = controller.update()

        assertTrue(kotlin.math.abs(frame.headX) <= Live2DCharacterProfiles.LoafDog.fallbackHeadIdle.headXAmplitude)
        assertTrue(kotlin.math.abs(frame.headY) <= Live2DCharacterProfiles.LoafDog.fallbackHeadIdle.headYAmplitude)
        assertEquals("NATURAL", frame.cycle)
    }

    @Test
    fun testEarsUsesLargerHeadInputsNotEarOutputs() {
        var now = 10_000L
        val controller = Live2DFallbackHeadIdleController(Live2DCharacterProfiles.LoafDog) { now }

        controller.startEarTest()
        now += 650L
        val frame = controller.update()

        assertTrue(frame.testActive)
        assertEquals("TEST_EARS", frame.cycle)
        assertTrue(kotlin.math.abs(frame.headX) <= Live2DCharacterProfiles.LoafDog.fallbackHeadIdle.testHeadXAmplitude)
        assertTrue(kotlin.math.abs(frame.headY) <= Live2DCharacterProfiles.LoafDog.fallbackHeadIdle.testHeadYAmplitude)
        assertNotEquals(0f, frame.headX)
    }

    @Test
    fun testEarsReturnsToSubtleFallbackAfterDuration() {
        var now = 20_000L
        val controller = Live2DFallbackHeadIdleController(Live2DCharacterProfiles.LoafDog) { now }

        controller.startEarTest()
        now += Live2DCharacterProfiles.LoafDog.fallbackHeadIdle.testDurationMs + 1L
        val frame = controller.update()

        assertFalse(frame.testActive)
        assertEquals("NATURAL", frame.cycle)
    }
}
