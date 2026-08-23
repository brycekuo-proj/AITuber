package com.aituber.poc.character

import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot

class CharacterEngine(
    private val adapter: CharacterAdapter
) {
    fun bind(snapshot: UniversalStateSnapshot, mouthOpen: Float): CharacterParameterFrame {
        val frame = CharacterParameterFrame(
            mouthOpen = if (snapshot.state == UniversalAiState.SPEAKING) mouthOpen else 0f,
            speaking = snapshot.state == UniversalAiState.SPEAKING
        ).clamped()
        CharacterDiagnostics.recordFrame(
            adapterId = adapter.characterId,
            frame = frame,
            mouthOutput = frame.mouthOpen
        )
        adapter.render(frame)
        return frame
    }
}
