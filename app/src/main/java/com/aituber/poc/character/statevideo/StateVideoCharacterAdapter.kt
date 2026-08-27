package com.aituber.poc.character.statevideo

import com.aituber.poc.character.CharacterAdapter
import com.aituber.poc.character.CharacterParameterFrame

class StateVideoCharacterAdapter(
    private val stateSink: StateVideoStateSink,
    private val characterPackage: StateVideoCharacterPackage = StateVideoCharacterPackage.WhitehairFemale
) : CharacterAdapter {
    override val characterId: String = "state-video-character-adapter"

    override fun render(frame: CharacterParameterFrame) {
        stateSink.renderState(frame.universalState)
    }
}
