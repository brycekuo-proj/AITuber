package com.aituber.poc.character

import com.aituber.poc.character.live2d.Live2DCharacterAdapter
import com.aituber.poc.overlay.MouthViewPort
import com.aituber.poc.state.UniversalAiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterAdapterFactoryTest {
    @Test
    fun minimalMouthModeUsesMinimalMouthAdapter() {
        val selection = CharacterAdapterFactory.create(
            requestedMode = CharacterMode.MINIMAL_MOUTH,
            mouthView = FakeMouthView()
        )

        assertEquals(CharacterMode.MINIMAL_MOUTH, selection.requestedMode)
        assertTrue(selection.adapter is MinimalMouthCharacterAdapter)
        assertEquals("n/a", selection.fallbackReason)
    }

    @Test
    fun live2dUnavailableFallsBackToMinimalMouth() {
        val selection = CharacterAdapterFactory.create(
            requestedMode = CharacterMode.LIVE2D,
            mouthView = FakeMouthView(),
            live2dAdapter = Live2DCharacterAdapter(sdkAvailable = false)
        )

        assertEquals(CharacterMode.LIVE2D, selection.requestedMode)
        assertTrue(selection.adapter is MinimalMouthCharacterAdapter)
        assertEquals("LIVE2D_UNAVAILABLE_FALLBACK_MINIMAL_MOUTH", selection.fallbackReason)
    }

    private class FakeMouthView : MouthViewPort {
        override fun render(nextState: UniversalAiState) = Unit
        override fun setMouthOpenRatio(ratio: Float) = Unit
    }
}
