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
}
