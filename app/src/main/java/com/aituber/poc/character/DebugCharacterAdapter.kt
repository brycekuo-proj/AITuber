package com.aituber.poc.character

import com.aituber.poc.state.UniversalStateSnapshot

class DebugCharacterAdapter(
    private val onRender: (UniversalStateSnapshot) -> Unit
) : CharacterAdapter {
    override val characterId = "debug-text-character"

    override fun render(snapshot: UniversalStateSnapshot) {
        onRender(snapshot)
    }
}
