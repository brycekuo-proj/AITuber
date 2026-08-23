package com.aituber.poc.character

import com.aituber.poc.overlay.MouthRenderDiagnostics
import com.aituber.poc.overlay.MouthViewPort

class MinimalMouthCharacterAdapter(
    private val mouthView: MouthViewPort
) : CharacterAdapter {
    override val characterId = "minimal-mouth-overlay"

    override fun render(frame: CharacterParameterFrame) {
        val ratio = frame.mouthOpen.coerceIn(0f, 1f)
        MouthRenderDiagnostics.recordAdapterRender(
            speaking = frame.speaking,
            lastRatio = ratio
        )
        mouthView.render(frame)
        mouthView.setMouthOpenRatio(ratio)
    }
}
