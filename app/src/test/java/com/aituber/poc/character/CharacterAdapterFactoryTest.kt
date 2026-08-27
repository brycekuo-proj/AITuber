package com.aituber.poc.character

import com.aituber.poc.character.live2d.Live2DCharacterAdapter
import com.aituber.poc.character.staticpng.StaticPngCharacterAdapter
import com.aituber.poc.character.statevideo.StateVideoCharacterAdapter
import com.aituber.poc.character.statevideo.StateVideoStateSink
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

    @Test
    fun live2dAvailableUsesLive2dAdapter() {
        val live2d = Live2DCharacterAdapter(
            sdkAvailable = true,
            parameterSink = object : com.aituber.poc.character.live2d.Live2DParameterSink {
                override fun setParameter(parameterId: String, value: Float): Boolean = true
            }
        )

        val selection = CharacterAdapterFactory.create(
            requestedMode = CharacterMode.LIVE2D,
            mouthView = FakeMouthView(),
            live2dAdapter = live2d
        )

        assertEquals(CharacterMode.LIVE2D, selection.requestedMode)
        assertTrue(selection.adapter is Live2DCharacterAdapter)
        assertEquals("n/a", selection.fallbackReason)
    }

    @Test
    fun stateVideoModeUsesStateVideoAdapter() {
        val stateVideo = StateVideoCharacterAdapter(object : StateVideoStateSink {
            override fun renderState(state: UniversalAiState) = Unit
        })

        val selection = CharacterAdapterFactory.create(
            requestedMode = CharacterMode.STATE_VIDEO,
            mouthView = FakeMouthView(),
            stateVideoAdapter = stateVideo
        )

        assertEquals(CharacterMode.STATE_VIDEO, selection.requestedMode)
        assertTrue(selection.adapter is StateVideoCharacterAdapter)
        assertEquals("n/a", selection.fallbackReason)
    }

    @Test
    fun missingStateVideoAdapterFallsBackSafely() {
        val selection = CharacterAdapterFactory.create(
            requestedMode = CharacterMode.STATE_VIDEO,
            mouthView = FakeMouthView()
        )

        assertEquals(CharacterMode.STATE_VIDEO, selection.requestedMode)
        assertTrue(selection.adapter is MinimalMouthCharacterAdapter)
        assertEquals("STATE_VIDEO_UNAVAILABLE_FALLBACK_MINIMAL_MOUTH", selection.fallbackReason)
    }

    @Test
    fun staticPngModeUsesStaticPngAdapter() {
        val staticPng = StaticPngCharacterAdapter()

        val selection = CharacterAdapterFactory.create(
            requestedMode = CharacterMode.STATIC_PNG,
            mouthView = FakeMouthView(),
            staticPngAdapter = staticPng
        )

        assertEquals(CharacterMode.STATIC_PNG, selection.requestedMode)
        assertTrue(selection.adapter is StaticPngCharacterAdapter)
        assertEquals("n/a", selection.fallbackReason)
    }

    @Test
    fun missingStaticPngAdapterFallsBackSafely() {
        val selection = CharacterAdapterFactory.create(
            requestedMode = CharacterMode.STATIC_PNG,
            mouthView = FakeMouthView()
        )

        assertEquals(CharacterMode.STATIC_PNG, selection.requestedMode)
        assertTrue(selection.adapter is MinimalMouthCharacterAdapter)
        assertEquals("STATIC_PNG_UNAVAILABLE_FALLBACK_MINIMAL_MOUTH", selection.fallbackReason)
    }

    private class FakeMouthView : MouthViewPort {
        override fun render(nextState: UniversalAiState) = Unit
        override fun setMouthOpenRatio(ratio: Float) = Unit
    }
}
