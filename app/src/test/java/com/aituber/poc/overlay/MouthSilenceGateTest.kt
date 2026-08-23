package com.aituber.poc.overlay

import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.VisualizerWaveformMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MouthSilenceGateTest {
    @Test
    fun speakingWithAudibleWaveformIsActive() {
        val gate = MouthSilenceGate()

        val frame = gate.evaluate(
            universalState = UniversalAiState.SPEAKING,
            metrics = VisualizerWaveformMetrics(rms = 0.10, peak = 0.20, activityRatio = 0.30),
            visualizerAvailable = true,
            nowMs = 1_000L
        )

        assertTrue(frame.audible)
        assertTrue(frame.mouthActive)
        assertEquals(MouthGateState.ACTIVE, frame.gateState)
    }

    @Test
    fun speakingSilenceWithinHoldRemainsTemporarilyActive() {
        val gate = MouthSilenceGate()
        gate.evaluate(UniversalAiState.SPEAKING, audibleMetrics(), visualizerAvailable = true, nowMs = 1_000L)

        val frame = gate.evaluate(UniversalAiState.SPEAKING, quietMetrics(), visualizerAvailable = true, nowMs = 1_019L)

        assertFalse(frame.audible)
        assertTrue(frame.mouthActive)
        assertEquals(1L, frame.silenceHoldRemainingMs)
    }

    @Test
    fun speakingSilenceAfterHoldBecomesSilent() {
        val gate = MouthSilenceGate()
        gate.evaluate(UniversalAiState.SPEAKING, audibleMetrics(), visualizerAvailable = true, nowMs = 1_000L)

        val frame = gate.evaluate(UniversalAiState.SPEAKING, quietMetrics(), visualizerAvailable = true, nowMs = 1_020L)

        assertFalse(frame.audible)
        assertFalse(frame.mouthActive)
        assertEquals(MouthGateState.SILENT, frame.gateState)
    }

    @Test
    fun defaultSilenceHoldIsTwentyMilliseconds() {
        val gate = MouthSilenceGate()
        gate.evaluate(UniversalAiState.SPEAKING, audibleMetrics(), visualizerAvailable = true, nowMs = 1_000L)

        val withinHold = gate.evaluate(UniversalAiState.SPEAKING, quietMetrics(), visualizerAvailable = true, nowMs = 1_019L)
        val afterHold = gate.evaluate(UniversalAiState.SPEAKING, quietMetrics(), visualizerAvailable = true, nowMs = 1_020L)

        assertTrue(withinHold.mouthActive)
        assertEquals(1L, withinHold.silenceHoldRemainingMs)
        assertFalse(afterHold.mouthActive)
        assertEquals(0L, afterHold.silenceHoldRemainingMs)
    }

    @Test
    fun soundAfterPauseImmediatelyReactivatesMouth() {
        val gate = MouthSilenceGate()
        gate.evaluate(UniversalAiState.SPEAKING, audibleMetrics(), visualizerAvailable = true, nowMs = 1_000L)
        gate.evaluate(UniversalAiState.SPEAKING, quietMetrics(), visualizerAvailable = true, nowMs = 1_100L)

        val frame = gate.evaluate(UniversalAiState.SPEAKING, audibleMetrics(), visualizerAvailable = true, nowMs = 1_120L)

        assertTrue(frame.audible)
        assertTrue(frame.mouthActive)
        assertEquals(1_120L, frame.lastAudibleTimeMs)
    }

    @Test
    fun idleAlwaysSilencesMouth() {
        val gate = MouthSilenceGate()

        val frame = gate.evaluate(
            universalState = UniversalAiState.IDLE,
            metrics = audibleMetrics(),
            visualizerAvailable = true,
            nowMs = 1_000L
        )

        assertFalse(frame.mouthActive)
        assertEquals(MouthGateState.SILENT, frame.gateState)
    }

    private fun audibleMetrics() = VisualizerWaveformMetrics(rms = 0.08, peak = 0.20, activityRatio = 0.20)

    private fun quietMetrics() = VisualizerWaveformMetrics.zero()
}
