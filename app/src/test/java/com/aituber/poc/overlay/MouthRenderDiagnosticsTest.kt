package com.aituber.poc.overlay

import com.aituber.poc.character.MinimalMouthCharacterAdapter
import com.aituber.poc.character.CharacterParameterFrame
import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot
import com.aituber.poc.state.VisualizerWaveformMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MouthRenderDiagnosticsTest {
    @Test
    fun mapperTargetAndSmoothedValueEnterDiagnostics() {
        MouthRenderDiagnostics.reset()

        MouthRenderDiagnostics.recordMapper(MouthDriveMode.RMS, targetOpen = 0.72, smoothedOpen = 0.44)
        val snapshot = MouthRenderDiagnostics.snapshot()

        assertEquals("RMS", snapshot.mouthDriveMode)
        assertEquals(0.72, snapshot.mapperTargetOpen!!, 0.0001)
        assertEquals(0.44, snapshot.smoothedOpen, 0.0001)
        assertNotNull(snapshot.lastRenderTimestampMs)
    }

    @Test
    fun overlaySnapshotRecordsStateAndVisualizerMetrics() {
        MouthRenderDiagnostics.reset()

        MouthRenderDiagnostics.recordOverlaySnapshot(
            state = UniversalAiState.SPEAKING,
            metrics = VisualizerWaveformMetrics(rms = 0.321, peak = 0.876, activityRatio = 0.4),
            targetOpen = 0.8,
            smoothedOpen = 0.5
        )
        val snapshot = MouthRenderDiagnostics.snapshot()

        assertEquals("SPEAKING", snapshot.overlayReceivedState)
        assertEquals(0.321, snapshot.overlayReceivedRms, 0.0001)
        assertEquals(0.876, snapshot.overlayReceivedPeak, 0.0001)
        assertEquals(0.8, snapshot.mapperTargetOpen!!, 0.0001)
        assertEquals(0.5, snapshot.smoothedOpen, 0.0001)
    }

    @Test
    fun adapterRenderUpdatesCounterAndLastRatio() {
        MouthRenderDiagnostics.reset()
        MouthRenderDiagnostics.recordMapper(MouthDriveMode.RMS, targetOpen = 0.7, smoothedOpen = 0.52)
        val adapter = MinimalMouthCharacterAdapter(FakeMouthView())

        adapter.render(CharacterParameterFrame(mouthOpen = 0.52f, speaking = true))
        val diagnostics = MouthRenderDiagnostics.snapshot()

        assertEquals(1L, diagnostics.adapterRenderCount)
        assertEquals("SPEAKING", diagnostics.adapterLastState)
        assertEquals(0.52, diagnostics.adapterLastMouthRatio!!, 0.0001)
    }

    @Test
    fun characterEngineRenderCounterCanBeRecordedAtPipelineBoundary() {
        MouthRenderDiagnostics.reset()

        MouthRenderDiagnostics.recordCharacterEngineRender()

        assertEquals(1L, MouthRenderDiagnostics.snapshot().characterEngineRenderCount)
    }

    @Test
    fun setMouthOpenRatioDiagnosticsRecordsRequestedRatio() {
        MouthRenderDiagnostics.reset()

        MouthRenderDiagnostics.recordViewRequestedRatio(0.83f)
        val snapshot = MouthRenderDiagnostics.snapshot()

        assertEquals(1L, snapshot.viewSetMouthOpenRatioCount)
        assertEquals(0.83, snapshot.viewLastRequestedRatio, 0.0001)
    }

    @Test
    fun onDrawDiagnosticsRecordsDrawnRatioAndCalculatedHeight() {
        MouthRenderDiagnostics.reset()
        val height = MouthDrawMetrics.calculatedMouthHeight(
            state = UniversalAiState.SPEAKING,
            ratio = 1.0f,
            height = 90f
        )

        MouthRenderDiagnostics.recordDraw(
            state = UniversalAiState.SPEAKING,
            ratio = 1.0f,
            width = 180,
            height = 90,
            calculatedMouthHeight = height
        )
        val snapshot = MouthRenderDiagnostics.snapshot()

        assertEquals(1L, snapshot.viewOnDrawCount)
        assertEquals(1.0, snapshot.viewLastDrawnRatio, 0.0001)
        assertEquals(180, snapshot.viewWidth)
        assertEquals(90, snapshot.viewHeight)
        assertEquals(64.8, snapshot.calculatedMouthHeight, 0.0001)
        assertNotNull(snapshot.lastDrawTimestampMs)
    }

    @Test
    fun debugFullOpenTestCanEnterOneAndRestoreState() {
        val openStep = MouthDebugFullOpenTest.forcedOpenStep()
        val restoreStep = MouthDebugFullOpenTest.restoreStep(UniversalAiState.IDLE)

        assertEquals(UniversalAiState.SPEAKING, openStep.state)
        assertEquals(1.0f, openStep.ratio!!)
        assertEquals(UniversalAiState.IDLE, restoreStep.state)
        assertEquals(null, restoreStep.ratio)
    }

    @Test
    fun destroyBeforeQueuedRenderPreventsStaleDiagnostics() {
        MouthRenderDiagnostics.reset()
        val posted = mutableListOf<() -> Unit>()
        val dispatcher = OverlayRenderDispatcher(
            postToMain = { block -> posted.add(block) },
            isMainThread = { true },
            trace = {},
            renderOnMain = { MouthRenderDiagnostics.recordCharacterEngineRender() },
            startAnimationOnMain = {},
            stopAnimationOnMain = {}
        )

        dispatcher.onState(snapshot(UniversalAiState.SPEAKING))
        dispatcher.destroy()
        posted.forEach { it.invoke() }

        assertEquals(0L, MouthRenderDiagnostics.snapshot().characterEngineRenderCount)
    }

    private class FakeMouthView : MouthViewPort {
        override fun render(nextState: UniversalAiState) = Unit
        override fun setMouthOpenRatio(ratio: Float) = Unit
    }

    private fun snapshot(state: UniversalAiState) = UniversalStateSnapshot(
        targetApp = "ChatGPT (com.openai.chatgpt)",
        detectionMethod = "test",
        state = state,
        audioLevel = null,
        captureStatus = "test"
    )
}
