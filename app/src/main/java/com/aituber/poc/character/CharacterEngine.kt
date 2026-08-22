package com.aituber.poc.character

import com.aituber.poc.state.UniversalStateSnapshot

class CharacterEngine(
    private val adapter: CharacterAdapter
) {
    fun bind(snapshot: UniversalStateSnapshot) {
        adapter.render(snapshot)
    }
}
