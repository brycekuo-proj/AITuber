package com.aituber.poc.character

class DebugCharacterAdapter(
    private val onRender: (CharacterParameterFrame) -> Unit
) : CharacterAdapter {
    override val characterId = "debug-text-character"

    override fun render(frame: CharacterParameterFrame) {
        onRender(frame)
    }
}
