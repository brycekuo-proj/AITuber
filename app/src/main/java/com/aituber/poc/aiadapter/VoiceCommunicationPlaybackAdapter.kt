package com.aituber.poc.aiadapter

import com.aituber.poc.state.UniversalAiState

class VoiceCommunicationPlaybackAdapter {
    // Diagnostic-only: active voice communication playback is a session signal, not actual AI speech.
    fun evaluate(activePlaybackCount: Int, hasVoiceCommunicationSpeech: Boolean): VoiceCommunicationPlaybackResult {
        val state = when {
            activePlaybackCount == 0 -> UniversalAiState.IDLE
            hasVoiceCommunicationSpeech -> UniversalAiState.UNKNOWN
            else -> UniversalAiState.UNKNOWN
        }
        return VoiceCommunicationPlaybackResult(
            state = state,
            signalSource = "Actual speaking signal not established"
        )
    }
}

data class VoiceCommunicationPlaybackResult(
    val state: UniversalAiState,
    val signalSource: String
)
