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
    fun highFrequencyUpdatesMoveSmoothedOpenTowardTarget() {
        val mapper = MouthAmplitudeMapper()
        val metrics = VisualizerWaveformMetrics(rms = 0.262, peak = 0.992, activityRatio = 0.8)
        var frame = mapper.evaluate(UniversalAiState.SPEAKING, metrics, nowMs = 1_000L)

        repeat(40) { index ->
            frame = mapper.evaluate(UniversalAiState.SPEAKING, metrics, nowMs = 1_005L + index * 5L)
        }

        assertTrue(frame.targetOpen!! in 0.70..0.73)
        assertTrue(frame.smoothedOpen > 0.45)
    }

    @Test
    fun deadbandDoesNotBlockInternalAccumulator() {
        val mapper = MouthAmplitudeMapper(
            MouthAmplitudeMapper.Config(deadband = 0.20)
        )
        val metrics = VisualizerWaveformMetrics(rms = 0.262, peak = 0.992, activityRatio = 0.8)
        var frame = mapper.evaluate(UniversalAiState.SPEAKING, metrics, nowMs = 1_000L)

        repeat(10) { index ->
            frame = mapper.evaluate(UniversalAiState.SPEAKING, metrics, nowMs = 1_005L + index * 5L)
        }

        assertTrue(frame.smoothedOpen > 0.20)
    }

    @Test
    fun deadbandComparesSmoothedOpenToLastRenderedOpen() {
        val mapper = MouthAmplitudeMapper()
        val metrics = VisualizerWaveformMetrics(rms = 0.262, peak = 0.992, activityRatio = 0.8)

        val first = mapper.evaluate(UniversalAiState.SPEAKING, metrics, nowMs = 1_000L)
        val tooSoon = mapper.evaluate(UniversalAiState.SPEAKING, metrics, nowMs = 1_005L)
        val later = mapper.evaluate(UniversalAiState.SPEAKING, metrics, nowMs = 1_055L)

        assertTrue(first.shouldRender)
        assertTrue(tooSoon.smoothedOpen > first.smoothedOpen)
        assertTrue(!tooSoon.shouldRender)
        assertTrue(later.shouldRender)
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
        assertTrue(frame.shouldRender)
    }

    @Test
    fun healthyZeroMetricsWhileSpeakingClosesInsteadOfPseudoFallback() {
        val mapper = MouthAmplitudeMapper()

        val frame = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics.zero(),
            mouthActive = false,
            visualizerAvailable = true,
            nowMs = 1_000L
        )

        assertEquals(MouthDriveMode.RMS, frame.mode)
        assertEquals(0.0, frame.targetOpen!!, 0.0001)
    }

    @Test
    fun visualizerUnavailableWhileSpeakingUsesPseudoFallback() {
        val mapper = MouthAmplitudeMapper()

        val frame = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics.zero(),
            mouthActive = false,
            visualizerAvailable = false,
            nowMs = 1_000L
        )

        assertEquals(MouthDriveMode.PSEUDO_FALLBACK, frame.mode)
        assertEquals(null, frame.targetOpen)
    }

    @Test
    fun speakingWithMouthInactiveReleasesTowardZeroWithoutChangingStateInput() {
        val mapper = MouthAmplitudeMapper()
        mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.407, peak = 0.992, activityRatio = 0.9),
            mouthActive = true,
            visualizerAvailable = true,
            nowMs = 1_000L
        )

        val frame = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics.zero(),
            mouthActive = false,
            visualizerAvailable = true,
            nowMs = 1_100L
        )

        assertEquals(MouthDriveMode.RMS, frame.mode)
        assertEquals(0.0, frame.targetOpen!!, 0.0001)
        assertTrue(frame.smoothedOpen >= 0.0)
    }

    @Test
    fun resolvedSpeakingDoesNotCloseMouthDrive() {
        val mapper = MouthAmplitudeMapper()

        val frame = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.407, peak = 0.992, activityRatio = 0.9),
            nowMs = 1_000L
        )

        assertEquals(MouthDriveMode.RMS, frame.mode)
        assertTrue(frame.targetOpen!! > 0.0)
    }

    @Test
    fun resolvedIdleClosesMouthDrive() {
        val mapper = MouthAmplitudeMapper()
        mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.407, peak = 0.992, activityRatio = 0.9),
            nowMs = 1_000L
        )

        val frame = mapper.evaluate(
            UniversalAiState.IDLE,
            VisualizerWaveformMetrics(rms = 0.407, peak = 0.992, activityRatio = 0.9),
            nowMs = 1_100L
        )

        assertEquals(MouthDriveMode.CLOSED, frame.mode)
        assertEquals(0.0, frame.smoothedOpen, 0.0001)
    }
}
