package com.aituber.poc.character.statevideo

import com.aituber.poc.state.UniversalAiState

data class StateVideoCharacterPackage(
    val id: String,
    val displayName: String,
    val runtimeType: String,
    val states: Map<UniversalAiState, String>
) {
    fun clipFor(state: UniversalAiState): String? = states[state]

    companion object {
        val WhitehairFemale = StateVideoCharacterPackage(
            id = "whitehair_female",
            displayName = "Whitehair Female",
            runtimeType = "STATE_VIDEO",
            states = mapOf(
                UniversalAiState.IDLE to "characters/whitehair_female/clips/whitehair_female__hailuo__idle__3s__v1.mp4",
                UniversalAiState.LISTENING to "characters/whitehair_female/clips/whitehair_female__hailuo__listening__3s__v1.mp4",
                UniversalAiState.THINKING to "characters/whitehair_female/clips/whitehair_female__hailuo__thinking__3s__v1.mp4",
                UniversalAiState.SPEAKING to "characters/whitehair_female/clips/whitehair_female__hailuo__speaking__3s__v2.mp4"
            )
        )
    }
}

interface StateVideoStateSink {
    fun renderState(state: UniversalAiState)
}
