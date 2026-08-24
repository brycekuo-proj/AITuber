package com.aituber.poc.character

import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkControllerTest {
    @Test
    fun idleIntervalStaysWithinConfiguredRange() {
        var now = 0L
        val controller = BlinkController(random = Random(7), nowMs = { now })

        val frame = controller.update(now)

        assertTrue(frame.nextBlinkInMs in 2_000L..6_000L)
    }

    @Test
    fun blinkSequenceMovesOpenClosingClosedOpeningOpen() {
        var now = 1_000L
        val controller = BlinkController(
            random = Random(1),
            nowMs = { now },
            config = BlinkConfig(
                minIdleIntervalMs = 2_000L,
                maxIdleIntervalMs = 2_000L,
                doubleBlinkChance = 0f,
                closeMs = 100L,
                closedHoldMs = 50L,
                openMs = 100L
            )
        )

        controller.forceBlink(now)
        assertEquals(BlinkState.CLOSING, controller.update(now).state)
        now += 50L
        assertEquals(0.5f, controller.update(now).leftEyeOpen, 0.001f)
        now += 50L
        assertEquals(BlinkState.CLOSED, controller.update(now).state)
        assertEquals(0f, controller.update(now).leftEyeOpen, 0.001f)
        now += 50L
        assertEquals(BlinkState.OPENING, controller.update(now).state)
        now += 50L
        assertEquals(0.5f, controller.update(now).leftEyeOpen, 0.001f)
        now += 50L
        assertEquals(BlinkState.OPEN, controller.update(now).state)
        assertEquals(1f, controller.update(now).leftEyeOpen, 0.001f)
    }

    @Test
    fun doubleBlinkCanUseShortFollowUpInterval() {
        var now = 0L
        val controller = BlinkController(
            random = Random(2),
            nowMs = { now },
            config = BlinkConfig(
                minIdleIntervalMs = 2_000L,
                maxIdleIntervalMs = 2_000L,
                doubleBlinkChance = 1f,
                doubleBlinkMinMs = 160L,
                doubleBlinkMaxMs = 320L,
                closeMs = 10L,
                closedHoldMs = 10L,
                openMs = 10L
            )
        )

        controller.forceBlink(now)
        now += 30L
        val frame = controller.update(now)

        assertEquals(BlinkState.OPEN, frame.state)
        assertTrue(frame.nextBlinkInMs in 160L..320L)
    }

    @Test
    fun eyeValuesAreClampedInCharacterParameterFrame() {
        val frame = CharacterParameterFrame(
            mouthOpen = 0f,
            speaking = false,
            eyeLeftOpen = -1f,
            eyeRightOpen = 2f
        ).clamped()

        assertEquals(0f, frame.eyeLeftOpen!!, 0.001f)
        assertEquals(1f, frame.eyeRightOpen!!, 0.001f)
    }

    @Test
    fun mouthValuesAreUnaffectedByBlink() {
        var now = 0L
        val engine = CharacterEngine(
            adapter = DebugCharacterAdapter {},
            blinkController = BlinkController(random = Random(1), nowMs = { now })
        )

        val open = engine.bind(snapshot(UniversalAiState.SPEAKING), mouthOpen = 0.62f)
        engine.forceBlink()
        val blinking = engine.bind(snapshot(UniversalAiState.SPEAKING), mouthOpen = 0.62f)

        assertEquals(0.62f, open.mouthOpen, 0.001f)
        assertEquals(0.62f, blinking.mouthOpen, 0.001f)
    }

    private fun snapshot(state: UniversalAiState) = UniversalStateSnapshot(
        targetApp = "ChatGPT (com.openai.chatgpt)",
        detectionMethod = "test",
        state = state,
        audioLevel = null,
        captureStatus = "test"
    )
}
