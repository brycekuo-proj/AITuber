package com.aituber.poc.character.staticpng

import com.aituber.poc.character.CharacterAdapter
import com.aituber.poc.character.CharacterParameterFrame

class StaticPngCharacterAdapter(
    private val overlayView: StaticPngOverlayView? = null
) : CharacterAdapter {
    override val characterId = "static-png-character-adapter"

    override fun render(frame: CharacterParameterFrame) {
        overlayView?.show()
        overlayView?.setUniversalState(frame.universalState)
        overlayView?.setMouthOpenRatio(frame.mouthOpen)
    }

    fun renderDebugShape(shape: StaticPngMouthShape) {
        overlayView?.show()
        overlayView?.setMouthShapeForDebug(shape)
    }
}
