package com.aituber.poc.character

import com.aituber.poc.state.UniversalStateSnapshot

interface CharacterAdapter {
    val characterId: String
    fun render(snapshot: UniversalStateSnapshot)
}
