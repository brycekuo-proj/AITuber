package com.aituber.poc.character

import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot

class CharacterEngine(
    private val adapter: CharacterAdapter,
    private val blinkController: BlinkController = BlinkController(),
    private val breathController: BreathController = BreathController()
) {
    fun bind(snapshot: UniversalStateSnapshot, mouthOpen: Float): CharacterParameterFrame {
        val blink = blinkController.update()
        val breath = breathController.update()
        val frame = CharacterParameterFrame(
            mouthOpen = if (snapshot.state == UniversalAiState.SPEAKING) mouthOpen else 0f,
            speaking = snapshot.state == UniversalAiState.SPEAKING,
            eyeLeftOpen = blink.leftEyeOpen,
            eyeRightOpen = blink.rightEyeOpen,
            breath = breath.normalized,
            breathIntensity = breath.intensity
        ).clamped()
        BlinkDiagnostics.update(enabled = true, frame = blink)
        BreathDiagnostics.update(enabled = true, frame = breath)
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

    fun forceTestBreath() {
        breathController.forceTestBreath()
    }

    fun resetBreath() {
        breathController.reset()
        BreathDiagnostics.reset()
    }
}
