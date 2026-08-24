package com.aituber.poc.character

import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterEngineTest {
    @Test
    fun engineOutputsCharacterParameterFrameFromMouthOpen() {
        val frames = mutableListOf<CharacterParameterFrame>()
        val engine = CharacterEngine(DebugCharacterAdapter { frame -> frames.add(frame) })

        val frame = engine.bind(snapshot(UniversalAiState.SPEAKING), mouthOpen = 0.5f)

        assertEquals(0.5f, frame.mouthOpen, 0.0001f)
        assertEquals(true, frame.speaking)
        assertEquals(1f, frame.eyeLeftOpen!!, 0.0001f)
        assertEquals(1f, frame.eyeRightOpen!!, 0.0001f)
        assertEquals(frame, frames.single())
    }

    @Test
    fun engineClampsMouthOpen() {
        val engine = CharacterEngine(DebugCharacterAdapter {})

        val low = engine.bind(snapshot(UniversalAiState.SPEAKING), mouthOpen = -1f)
        val high = engine.bind(snapshot(UniversalAiState.SPEAKING), mouthOpen = 2f)

        assertEquals(0f, low.mouthOpen, 0.0001f)
        assertEquals(1f, high.mouthOpen, 0.0001f)
    }

    @Test
    fun nonSpeakingStateForcesClosedMouth() {
        val engine = CharacterEngine(DebugCharacterAdapter {})

        val frame = engine.bind(snapshot(UniversalAiState.IDLE), mouthOpen = 0.8f)

        assertEquals(false, frame.speaking)
        assertEquals(0f, frame.mouthOpen, 0.0001f)
    }

    @Test
    fun characterParameterFrameIsLive2dSdkIndependent() {
        val frame = CharacterParameterFrame(mouthOpen = 0.4f, speaking = true, breath = 0.5f)

        assertEquals("com.aituber.poc.character.CharacterParameterFrame", frame::class.java.name)
        assertEquals(0.4f, frame.mouthOpen, 0.0001f)
    }

    private fun snapshot(state: UniversalAiState) = UniversalStateSnapshot(
        targetApp = "ChatGPT (com.openai.chatgpt)",
        detectionMethod = "test",
        state = state,
        audioLevel = null,
        captureStatus = "test"
    )
}
