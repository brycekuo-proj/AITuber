package com.aituber.poc.character

import com.aituber.poc.overlay.MouthRenderDiagnostics
import com.aituber.poc.overlay.MouthViewPort
import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot

class MinimalMouthCharacterAdapter(
    private val mouthView: MouthViewPort
) : CharacterAdapter {
    override val characterId = "minimal-mouth-overlay"

    override fun render(snapshot: UniversalStateSnapshot) {
        val diagnosticRatio = if (snapshot.state == UniversalAiState.SPEAKING) {
            MouthRenderDiagnostics.snapshot().smoothedOpen.toFloat()
        } else {
            0f
        }
        MouthRenderDiagnostics.recordAdapterRender(
            state = snapshot.state,
            lastRatio = diagnosticRatio
        )
        mouthView.render(snapshot.state)
        if (snapshot.state != UniversalAiState.SPEAKING) {
            mouthView.setMouthOpenRatio(0f)
        }
    }
}
