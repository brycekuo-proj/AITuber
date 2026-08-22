package com.aituber.poc.aiadapter

import com.aituber.poc.state.UniversalAiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VoiceCommunicationPlaybackAdapterTest {
    @Test
    fun voiceCommunicationSpeechActiveDoesNotMapToSpeaking() {
        val adapter = VoiceCommunicationPlaybackAdapter()

        val result = adapter.evaluate(activePlaybackCount = 1, hasVoiceCommunicationSpeech = true)

        assertNotEquals(UniversalAiState.SPEAKING, result.state)
    }

    @Test
    fun voiceSessionActiveMapsToUnknown() {
        val adapter = VoiceCommunicationPlaybackAdapter()

        val result = adapter.evaluate(activePlaybackCount = 1, hasVoiceCommunicationSpeech = true)

        assertEquals(UniversalAiState.UNKNOWN, result.state)
    }

    @Test
    fun zeroActivePlaybackMapsToIdle() {
        val adapter = VoiceCommunicationPlaybackAdapter()

        val result = adapter.evaluate(activePlaybackCount = 0, hasVoiceCommunicationSpeech = false)

        assertEquals(UniversalAiState.IDLE, result.state)
    }

    @Test
    fun activePlaybackWithoutMatchingUsageAndContentTypeMapsToUnknown() {
        val adapter = VoiceCommunicationPlaybackAdapter()

        val result = adapter.evaluate(activePlaybackCount = 1, hasVoiceCommunicationSpeech = false)

        assertEquals(UniversalAiState.UNKNOWN, result.state)
    }

    @Test
    fun speakingToIdleTransitionFollowsActivePlaybackCount() {
        val adapter = VoiceCommunicationPlaybackAdapter()

        val activeVoiceSession = adapter.evaluate(activePlaybackCount = 1, hasVoiceCommunicationSpeech = true)
        val idle = adapter.evaluate(activePlaybackCount = 0, hasVoiceCommunicationSpeech = false)

        assertEquals(UniversalAiState.UNKNOWN, activeVoiceSession.state)
        assertEquals(UniversalAiState.IDLE, idle.state)
    }
}
