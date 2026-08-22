package com.aituber.poc.overlay

import com.aituber.poc.state.UniversalAiState

interface MouthViewPort {
    fun render(nextState: UniversalAiState)
    fun setMouthOpenRatio(ratio: Float)
}
