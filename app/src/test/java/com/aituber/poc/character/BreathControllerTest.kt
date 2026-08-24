package com.aituber.poc.character

import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot
import kotlin.random.Random
import org.junit.Assert.assertEquals
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

        assertEquals(1_600L, frame.cycleDurationMs)
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

    private fun snapshot(state: UniversalAiState) = UniversalStateSnapshot(
        targetApp = "ChatGPT (com.openai.chatgpt)",
        detectionMethod = "test",
        state = state,
        audioLevel = null,
        captureStatus = "test"
    )
}
