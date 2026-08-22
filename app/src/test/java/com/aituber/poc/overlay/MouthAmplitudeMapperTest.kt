package com.aituber.poc.overlay

import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.VisualizerWaveformMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MouthAmplitudeMapperTest {
    @Test
    fun lowRmsProducesSmallMouth() {
        val mapper = MouthAmplitudeMapper()

        val frame = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.10, peak = 0.22, activityRatio = 0.25),
            nowMs = 1_000L
        )

        assertEquals(MouthDriveMode.RMS, frame.mode)
        assertTrue(frame.targetOpen!! in 0.18..0.35)
    }

    @Test
    fun mediumRmsProducesMediumMouth() {
        val mapper = MouthAmplitudeMapper()

        val frame = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.29, peak = 0.45, activityRatio = 0.40),
            nowMs = 1_000L
        )

        assertEquals(MouthDriveMode.RMS, frame.mode)
        assertTrue(frame.targetOpen!! in 0.45..0.70)
    }

    @Test
    fun highRmsProducesLargeMouth() {
        val mapper = MouthAmplitudeMapper()

        val frame = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.50, peak = 0.95, activityRatio = 0.80),
            nowMs = 1_000L
        )

        assertEquals(MouthDriveMode.RMS, frame.mode)
        assertTrue(frame.targetOpen!! > 0.95)
    }

    @Test
    fun clampDoesNotExceedOne() {
        val mapper = MouthAmplitudeMapper()

        val frame = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 1.20, peak = 2.0, activityRatio = 1.0),
            nowMs = 1_000L
        )

        assertEquals(1.0, frame.targetOpen!!, 0.0001)
    }

    @Test
    fun deadbandHoldsTinySmoothingChanges() {
        val mapper = MouthAmplitudeMapper(
            MouthAmplitudeMapper.Config(deadband = 0.20)
        )

        val first = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.10, peak = 0.22, activityRatio = 0.25),
            nowMs = 1_000L
        )

        assertEquals(0.0, first.smoothedOpen, 0.0001)
    }

    @Test
    fun smoothingDoesNotJumpImmediatelyToTarget() {
        val mapper = MouthAmplitudeMapper()

        val frame = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.50, peak = 0.95, activityRatio = 0.80),
            nowMs = 1_000L
        )

        assertTrue(frame.targetOpen!! > 0.95)
        assertTrue(frame.smoothedOpen in 0.20..0.70)
    }

    @Test
    fun nonSpeakingClosesMouth() {
        val mapper = MouthAmplitudeMapper()
        mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.50, peak = 0.95, activityRatio = 0.80),
            nowMs = 1_000L
        )

        val frame = mapper.evaluate(
            UniversalAiState.IDLE,
            VisualizerWaveformMetrics(rms = 0.50, peak = 0.95, activityRatio = 0.80),
            nowMs = 1_100L
        )

        assertEquals(MouthDriveMode.CLOSED, frame.mode)
        assertEquals(0.0, frame.targetOpen!!, 0.0001)
        assertEquals(0.0, frame.smoothedOpen, 0.0001)
    }

    @Test
    fun zeroMetricsWhileSpeakingUsesPseudoFallback() {
        val mapper = MouthAmplitudeMapper()

        val frame = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics.zero(),
            nowMs = 1_000L
        )

        assertEquals(MouthDriveMode.PSEUDO_FALLBACK, frame.mode)
        assertEquals(null, frame.targetOpen)
    }
}
