package com.aituber.poc.character

import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreathControllerTest {
    @Test
    fun normalizedBreathStaysWithinZeroOne() {
        var now = 0L
        val controller = BreathController(random = Random(3), nowMs = { now })

        repeat(200) {
            val frame = controller.update(now)
            assertTrue(frame.normalized in 0f..1f)
            now += 100L
        }
    }

    @Test
    fun periodStaysWithinConfiguredRange() {
        val controller = BreathController(random = Random(4), nowMs = { 0L })

        val frame = controller.update(0L)

        assertTrue(frame.cycleDurationMs in 3_500L..5_500L)
    }

    @Test
    fun waveformIsSmoothSineLikeAndNeutralAtCycleStart() {
        val controller = BreathController(
            random = Random(1),
            nowMs = { 0L },
            config = BreathConfig(
                minCycleDurationMs = 4_000L,
                maxCycleDurationMs = 4_000L,
                minAmplitude = 1f,
                maxAmplitude = 1f
            )
        )

        assertEquals(0.5f, controller.update(0L).normalized, 0.001f)
        assertEquals(1.0f, controller.update(1_000L).normalized, 0.001f)
        assertEquals(0.5f, controller.update(2_000L).normalized, 0.001f)
        assertEquals(0.0f, controller.update(3_000L).normalized, 0.001f)
        assertEquals(0.5f, controller.update(4_000L).normalized, 0.001f)
    }

    @Test
    fun forceTestBreathUsesShortObservableCycleWithoutChangingDefaultConfig() {
        val controller = BreathController(random = Random(1), nowMs = { 0L })

        controller.forceTestBreath(0L)
        val frame = controller.update(0L)

        assertTrue(frame.testActive)
        assertEquals(1_800L, frame.cycleDurationMs)
        assertTrue(frame.cycleDurationMs in 1_500L..2_000L)
        assertEquals(1.0f, frame.intensity, 0.0001f)
    }

    @Test
    fun testBreathUsesStrongerAmplitudeThanNaturalBreathing() {
        val controller = BreathController(random = Random(1), nowMs = { 0L })

        val natural = controller.update(0L)
        controller.forceTestBreath(0L)
        val testPeak = controller.update(850L)

        assertFalse(natural.testActive)
        assertEquals(0.30f, natural.intensity, 0.0001f)
        assertTrue(testPeak.testActive)
        assertTrue(testPeak.intensity > natural.intensity)
        assertEquals(1.0f, testPeak.normalized, 0.0001f)
    }

    @Test
    fun testBreathSweepsWithinZeroOne() {
        val controller = BreathController(random = Random(1), nowMs = { 0L })

        controller.forceTestBreath(0L)

        assertEquals(0.0f, controller.update(0L).normalized, 0.0001f)
        assertEquals(1.0f, controller.update(800L).normalized, 0.0001f)
        assertEquals(1.0f, controller.update(850L).normalized, 0.0001f)
        assertEquals(0.5f, controller.update(1_350L).normalized, 0.0001f)
        assertTrue(controller.update(1_799L).normalized in 0f..1f)
    }

    @Test
    fun afterTestBreathEndsControlReturnsToNormalController() {
        val controller = BreathController(random = Random(1), nowMs = { 0L })

        controller.forceTestBreath(0L)
        assertTrue(controller.update(1_700L).testActive)
        val restored = controller.update(1_800L)

        assertFalse(restored.testActive)
        assertEquals("NATURAL", restored.testPhase)
        assertEquals(0.30f, restored.intensity, 0.0001f)
    }

    @Test
    fun breathDoesNotAffectMouthOrBlinkEyes() {
        var now = 0L
        val engine = CharacterEngine(
            adapter = DebugCharacterAdapter {},
            blinkController = BlinkController(random = Random(1), nowMs = { now }),
            breathController = BreathController(
                random = Random(1),
                nowMs = { now },
                config = BreathConfig(
                    minCycleDurationMs = 4_000L,
                    maxCycleDurationMs = 4_000L,
                    minAmplitude = 1f,
                    maxAmplitude = 1f
                )
            )
        )

        val start = engine.bind(snapshot(UniversalAiState.SPEAKING), mouthOpen = 0.7f)
        now = 1_000L
        val inhale = engine.bind(snapshot(UniversalAiState.SPEAKING), mouthOpen = 0.7f)

        assertEquals(0.7f, start.mouthOpen, 0.001f)
        assertEquals(0.7f, inhale.mouthOpen, 0.001f)
        assertEquals(1f, start.eyeLeftOpen!!, 0.001f)
        assertEquals(1f, inhale.eyeLeftOpen!!, 0.001f)
        assertTrue(inhale.breath!! > start.breath!!)
    }

    @Test
    fun forceTestBreathDoesNotAffectMouthOrBlinkEyes() {
        val frames = mutableListOf<CharacterParameterFrame>()
        val engine = CharacterEngine(adapter = DebugCharacterAdapter { frames.add(it) })

        engine.forceTestBreath()
        val frame = engine.bind(snapshot(UniversalAiState.SPEAKING), mouthOpen = 0.6f)

        assertEquals(0.6f, frame.mouthOpen, 0.001f)
        assertEquals(1f, frame.eyeLeftOpen!!, 0.001f)
        assertEquals(1f, frame.eyeRightOpen!!, 0.001f)
        assertEquals(frame, frames.single())
    }

    private fun snapshot(state: UniversalAiState) = UniversalStateSnapshot(
        targetApp = "ChatGPT (com.openai.chatgpt)",
        detectionMethod = "test",
        state = state,
        audioLevel = null,
        captureStatus = "test"
    )
}
