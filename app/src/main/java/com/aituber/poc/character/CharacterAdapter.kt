package com.aituber.poc.character

interface CharacterAdapter {
    val characterId: String
    fun render(frame: CharacterParameterFrame)
}
