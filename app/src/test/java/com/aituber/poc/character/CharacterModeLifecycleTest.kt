package com.aituber.poc.character

import com.aituber.poc.character.live2d.Live2DCharacterAdapter
import com.aituber.poc.character.live2d.Live2DParameterSink
import com.aituber.poc.overlay.MouthViewPort
import com.aituber.poc.state.UniversalAiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterModeLifecycleTest {
    @After
    fun tearDown() {
        CharacterDiagnostics.reset()
    }

    @Test
    fun overlayDisabledLive2DRequestRemainsLive2D() {
        CharacterDiagnostics.recordRequestedMode(
            requestedMode = CharacterMode.LIVE2D,
            overlayRunning = false
        )

        val snapshot = CharacterDiagnostics.snapshot()

        assertEquals(CharacterMode.LIVE2D.name, snapshot.characterMode)
        assertEquals("none", snapshot.activeCharacterAdapter)
        assertEquals("REQUESTED_OVERLAY_DISABLED", snapshot.live2dLifecycleState)
        assertEquals("WAITING_FOR_OVERLAY", snapshot.live2dFallbackReason)
    }

    @Test
    fun live2dRequestedSurfaceNotCreatedDoesNotFallback() {
        val adapter = Live2DCharacterAdapter(
            sdkAvailable = true,
            parameterSink = object : Live2DParameterSink {
                override fun setParameter(parameterId: String, value: Float): Boolean = true
            }
        )

        val selection = CharacterAdapterFactory.create(
            requestedMode = CharacterMode.LIVE2D,
            mouthView = FakeMouthView(),
            live2dAdapter = adapter
        )

        assertEquals(CharacterMode.LIVE2D, selection.requestedMode)
        assertTrue(selection.adapter is Live2DCharacterAdapter)
        assertEquals("n/a", selection.fallbackReason)
    }

    @Test
    fun live2dRequestedActualUnavailableFallsBackButRequestRemainsLive2D() {
        val selection = CharacterAdapterFactory.create(
            requestedMode = CharacterMode.LIVE2D,
            mouthView = FakeMouthView(),
            live2dAdapter = Live2DCharacterAdapter(sdkAvailable = false)
        )
        CharacterDiagnostics.configure(
            requestedMode = selection.requestedMode,
            activeAdapterId = selection.adapter.characterId,
            fallbackReason = "LIVE2D_RUNTIME_FAILURE: native library unavailable",
            live2dLifecycleState = "FAILED"
        )

        val snapshot = CharacterDiagnostics.snapshot()

        assertEquals(CharacterMode.LIVE2D.name, snapshot.characterMode)
        assertEquals("minimal-mouth-overlay", snapshot.activeCharacterAdapter)
        assertEquals("FAILED", snapshot.live2dLifecycleState)
        assertEquals("LIVE2D_RUNTIME_FAILURE: native library unavailable", snapshot.live2dFallbackReason)
    }

    @Test
    fun explicitMinimalMouthSelectionChangesRequestedMode() {
        CharacterDiagnostics.recordRequestedMode(
            requestedMode = CharacterMode.LIVE2D,
            overlayRunning = false
        )

        CharacterDiagnostics.recordRequestedMode(
            requestedMode = CharacterMode.MINIMAL_MOUTH,
            overlayRunning = false
        )

        val snapshot = CharacterDiagnostics.snapshot()

        assertEquals(CharacterMode.MINIMAL_MOUTH.name, snapshot.characterMode)
        assertEquals("DISABLED", snapshot.live2dLifecycleState)
    }

    @Test
    fun overlayDisablePreservesLive2DRequestForNextEnable() {
        CharacterDiagnostics.recordRequestedMode(
            requestedMode = CharacterMode.LIVE2D,
            overlayRunning = true
        )

        CharacterDiagnostics.reset(requestedMode = CharacterMode.LIVE2D)

        val snapshot = CharacterDiagnostics.snapshot()

        assertEquals(CharacterMode.LIVE2D.name, snapshot.characterMode)
        assertEquals("REQUESTED_OVERLAY_DISABLED", snapshot.live2dLifecycleState)
        assertEquals("WAITING_FOR_OVERLAY", snapshot.live2dFallbackReason)
    }

    private class FakeMouthView : MouthViewPort {
        override fun render(nextState: UniversalAiState) = Unit
        override fun setMouthOpenRatio(ratio: Float) = Unit
    }
}
