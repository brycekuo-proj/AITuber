package com.aituber.poc.poc

import com.aituber.poc.state.UniversalAiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AndroidPlaybackStateProbeTest {
    @Test
    fun voiceSessionActiveDoesNotMapUniversalStateToSpeaking() {
        val state = AndroidPlaybackStateProbe.universalStateForPlaybackProbe(voiceSessionActive = true)

        assertNotEquals(UniversalAiState.SPEAKING, state)
    }

    @Test
    fun voiceSessionActiveMapsUniversalStateToUnknown() {
        val state = AndroidPlaybackStateProbe.universalStateForPlaybackProbe(voiceSessionActive = true)

        assertEquals(UniversalAiState.UNKNOWN, state)
    }

    @Test
    fun noVoiceSessionMapsUniversalStateToIdle() {
        val state = AndroidPlaybackStateProbe.universalStateForPlaybackProbe(voiceSessionActive = false)

        assertEquals(UniversalAiState.IDLE, state)
    }

    @Test
    fun voiceSessionActiveUsesVisualizerDerivedSpeakingState() {
        val state = AndroidPlaybackStateProbe.universalStateForPlaybackAndVisualizer(
            voiceSessionActive = true,
            visualizerDerivedState = UniversalAiState.SPEAKING.name
        )

        assertEquals(UniversalAiState.SPEAKING, state)
    }

    @Test
    fun voiceSessionActiveUsesVisualizerDerivedIdleState() {
        val state = AndroidPlaybackStateProbe.universalStateForPlaybackAndVisualizer(
            voiceSessionActive = true,
            visualizerDerivedState = UniversalAiState.IDLE.name
        )

        assertEquals(UniversalAiState.IDLE, state)
    }

    @Test
    fun voiceSessionActiveWithUnknownVisualizerRemainsUnknown() {
        val state = AndroidPlaybackStateProbe.universalStateForPlaybackAndVisualizer(
            voiceSessionActive = true,
            visualizerDerivedState = UniversalAiState.UNKNOWN.name
        )

        assertEquals(UniversalAiState.UNKNOWN, state)
    }
}
