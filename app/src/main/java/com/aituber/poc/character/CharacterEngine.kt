package com.aituber.poc.character

import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot

class CharacterEngine(
    private val adapter: CharacterAdapter,
    private val blinkController: BlinkController = BlinkController()
) {
    fun bind(snapshot: UniversalStateSnapshot, mouthOpen: Float): CharacterParameterFrame {
        val blink = blinkController.update()
        val frame = CharacterParameterFrame(
            mouthOpen = if (snapshot.state == UniversalAiState.SPEAKING) mouthOpen else 0f,
            speaking = snapshot.state == UniversalAiState.SPEAKING,
            eyeLeftOpen = blink.leftEyeOpen,
            eyeRightOpen = blink.rightEyeOpen
        ).clamped()
        BlinkDiagnostics.update(enabled = true, frame = blink)
        CharacterDiagnostics.recordFrame(
            adapterId = adapter.characterId,
            frame = frame,
            mouthOutput = frame.mouthOpen
        )
        adapter.render(frame)
        return frame
    }

    fun forceBlink() {
        blinkController.forceBlink()
    }

    fun resetBlink() {
        blinkController.reset()
        BlinkDiagnostics.reset()
    }
}
