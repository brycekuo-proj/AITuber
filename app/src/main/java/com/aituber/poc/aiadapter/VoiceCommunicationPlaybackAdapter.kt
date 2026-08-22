package com.aituber.poc.aiadapter

import com.aituber.poc.state.UniversalAiState

class VoiceCommunicationPlaybackAdapter {
    fun evaluate(activePlaybackCount: Int, hasVoiceCommunicationSpeech: Boolean): VoiceCommunicationPlaybackResult {
        val state = when {
            activePlaybackCount == 0 -> UniversalAiState.IDLE
            hasVoiceCommunicationSpeech -> UniversalAiState.SPEAKING
            else -> UniversalAiState.UNKNOWN
        }
        return VoiceCommunicationPlaybackResult(
            state = state,
            signalSource = "VoiceCommunicationPlaybackAdapter heuristic candidate"
        )
    }
}

data class VoiceCommunicationPlaybackResult(
    val state: UniversalAiState,
    val signalSource: String
)
