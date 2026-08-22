package com.aituber.poc.character

import com.aituber.poc.overlay.MouthOverlayView
import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot

class MinimalMouthCharacterAdapter(
    private val mouthView: MouthOverlayView
) : CharacterAdapter {
    override val characterId = "minimal-mouth-overlay"

    override fun render(snapshot: UniversalStateSnapshot) {
        mouthView.render(snapshot.state)
        if (snapshot.state != UniversalAiState.SPEAKING) {
            mouthView.setMouthOpenRatio(0f)
        }
    }
}
