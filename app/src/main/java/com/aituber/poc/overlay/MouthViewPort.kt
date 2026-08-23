package com.aituber.poc.overlay

import com.aituber.poc.character.CharacterParameterFrame
import com.aituber.poc.state.UniversalAiState

interface MouthViewPort {
    fun render(nextState: UniversalAiState)
    fun render(frame: CharacterParameterFrame) {
        render(if (frame.speaking) UniversalAiState.SPEAKING else UniversalAiState.IDLE)
    }
    fun setMouthOpenRatio(ratio: Float)
}
