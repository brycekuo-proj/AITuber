package com.aituber.poc.overlay

import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.VisualizerWaveformMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(MouthCloseMode.NORMAL_RELEASE, frame.closeMode)
        assertTrue(frame.targetOpen!! in 0.02..0.15)
        assertEquals(MouthLoudnessBand.QUIET, frame.loudness.band)
    }

    @Test
    fun mediumRmsProducesMediumMouth() {
        val mapper = MouthAmplitudeMapper()

        val frame = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.35, peak = 0.80, activityRatio = 0.60),
            nowMs = 1_000L
        )

        assertEquals(MouthDriveMode.RMS, frame.mode)
        assertTrue(frame.targetOpen!! in 0.40..0.65)
        assertEquals(MouthLoudnessBand.NORMAL, frame.loudness.band)
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

        assertTrue(frame.targetOpen!! in 0.35..0.40)
        assertTrue(frame.smoothedOpen > 0.20)
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
        assertEquals(MouthCloseMode.SILENCE_FAST_CLOSE, frame.closeMode)
        assertEquals(0.0, frame.targetOpen!!, 0.0001)
        assertEquals(40L, frame.activeTimeConstantMs)
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
        assertEquals(MouthCloseMode.NORMAL_RELEASE, frame.closeMode)
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
        assertEquals(MouthCloseMode.SILENCE_FAST_CLOSE, frame.closeMode)
        assertEquals(0.0, frame.targetOpen!!, 0.0001)
        assertTrue(frame.smoothedOpen >= 0.0)
    }

    @Test
    fun speakingWithMouthActiveKeepsMinOpenAndNormalReleaseMode() {
        val mapper = MouthAmplitudeMapper()

        val frame = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.081, peak = 0.20, activityRatio = 0.10),
            mouthActive = true,
            visualizerAvailable = true,
            nowMs = 1_000L
        )

        assertEquals(MouthDriveMode.RMS, frame.mode)
        assertEquals(MouthCloseMode.NORMAL_RELEASE, frame.closeMode)
        assertTrue(frame.targetOpen!! >= 0.02)
        assertTrue(frame.targetOpen!! < 0.05)
        assertEquals(100L, frame.activeTimeConstantMs)
    }

    @Test
    fun quietAudibleRmsProducesSmallMouth() {
        val mapper = MouthAmplitudeMapper()

        val frame = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.061, peak = 0.16, activityRatio = 0.12),
            mouthActive = true,
            visualizerAvailable = true,
            nowMs = 1_000L
        )

        assertTrue(frame.targetOpen!! in 0.02..0.15)
        assertEquals(MouthLoudnessBand.QUIET, frame.loudness.band)
    }

    @Test
    fun lowRmsProducesLowerTargetThanNormalRms() {
        val mapper = MouthAmplitudeMapper()

        val low = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.12, peak = 0.25, activityRatio = 0.20),
            mouthActive = true,
            visualizerAvailable = true,
            nowMs = 1_000L
        )
        val normal = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.26, peak = 0.65, activityRatio = 0.50),
            mouthActive = true,
            visualizerAvailable = true,
            nowMs = 1_100L
        )

        assertTrue(low.targetOpen!! in 0.02..0.10)
        assertTrue(normal.targetOpen!! in 0.25..0.40)
        assertTrue(low.targetOpen < normal.targetOpen)
    }

    @Test
    fun highRmsAndHighPeakProducesLargeMouth() {
        val mapper = MouthAmplitudeMapper()

        val frame = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.40, peak = 0.95, activityRatio = 0.80),
            mouthActive = true,
            visualizerAvailable = true,
            nowMs = 1_000L
        )

        assertTrue(frame.targetOpen!! >= 0.70)
        assertEquals(MouthLoudnessBand.LOUD, frame.loudness.band)
    }

    @Test
    fun veryHighRmsCanApproachMaximumMouth() {
        val mapper = MouthAmplitudeMapper()

        val frame = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.45, peak = 0.95, activityRatio = 0.90),
            mouthActive = true,
            visualizerAvailable = true,
            nowMs = 1_000L
        )

        assertTrue(frame.targetOpen!! >= 0.90)
        assertEquals(MouthLoudnessBand.VERY_LOUD, frame.loudness.band)
    }

    @Test
    fun lowRmsWithHighTransientPeakDoesNotForceMaximumMouth() {
        val mapper = MouthAmplitudeMapper()

        val frame = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.07, peak = 0.99, activityRatio = 0.90),
            mouthActive = true,
            visualizerAvailable = true,
            nowMs = 1_000L
        )

        assertTrue(frame.loudness.peakNormalized > 0.99)
        assertTrue(frame.targetOpen!! < 0.35)
    }

    @Test
    fun lowNormalHighTargetsHaveClearSpacing() {
        val mapper = MouthAmplitudeMapper()
        val low = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.12, peak = 0.25, activityRatio = 0.20),
            mouthActive = true,
            visualizerAvailable = true,
            nowMs = 1_000L
        ).targetOpen!!
        val normal = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.30, peak = 0.80, activityRatio = 0.60),
            mouthActive = true,
            visualizerAvailable = true,
            nowMs = 1_100L
        ).targetOpen!!
        val high = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.42, peak = 0.95, activityRatio = 0.80),
            mouthActive = true,
            visualizerAvailable = true,
            nowMs = 1_200L
        ).targetOpen!!

        assertTrue(low <= 0.10)
        assertTrue(normal in 0.35..0.60)
        assertTrue(high >= 0.80)
        assertTrue(normal - low >= 0.25)
        assertTrue(high - normal >= 0.25)
    }

    @Test
    fun targetOpenGenerallyIncreasesWithRms() {
        val mapper = MouthAmplitudeMapper()
        val targets = listOf(0.07, 0.12, 0.26, 0.35, 0.45).mapIndexed { index, rms ->
            mapper.evaluate(
                UniversalAiState.SPEAKING,
                VisualizerWaveformMetrics(rms = rms, peak = 0.60, activityRatio = 0.50),
                mouthActive = true,
                visualizerAvailable = true,
                nowMs = 1_000L + index * 100L
            ).targetOpen!!
        }

        targets.zipWithNext().forEach { (previous, next) ->
            assertTrue(next >= previous)
        }
    }

    @Test
    fun speakingWithMouthInactiveUsesSilenceCloseInsteadOfNormalRelease() {
        val mapper = MouthAmplitudeMapper()
        seedOpenMouth(mapper)

        val frame = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics.zero(),
            mouthActive = false,
            visualizerAvailable = true,
            nowMs = 1_150L
        )

        assertEquals(0.0, frame.targetOpen!!, 0.0001)
        assertEquals(MouthCloseMode.SILENCE_FAST_CLOSE, frame.closeMode)
        assertEquals(40L, frame.activeTimeConstantMs)
        assertEquals(1_150L, frame.silenceCloseStartTimeMs)
        assertEquals(0L, frame.silenceCloseDurationMs)
    }

    @Test
    fun silenceFastCloseIsFasterThanNormalReleaseForSameElapsedTime() {
        val fastCloseMapper = MouthAmplitudeMapper()
        val normalReleaseMapper = MouthAmplitudeMapper()
        seedOpenMouth(fastCloseMapper)
        seedOpenMouth(normalReleaseMapper)

        val fastClose = fastCloseMapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics.zero(),
            mouthActive = false,
            visualizerAvailable = true,
            nowMs = 1_150L
        )
        val normalRelease = normalReleaseMapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.08, peak = 0.20, activityRatio = 0.20),
            mouthActive = true,
            visualizerAvailable = true,
            nowMs = 1_150L
        )

        assertEquals(MouthCloseMode.SILENCE_FAST_CLOSE, fastClose.closeMode)
        assertEquals(MouthCloseMode.NORMAL_RELEASE, normalRelease.closeMode)
        assertTrue(fastClose.smoothedOpen < normalRelease.smoothedOpen)
    }

    @Test
    fun silenceFastCloseSnapsClosedBelowThreshold() {
        val mapper = MouthAmplitudeMapper()
        seedOpenMouth(mapper)
        mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics.zero(),
            mouthActive = false,
            visualizerAvailable = true,
            nowMs = 1_150L
        )

        val frame = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics.zero(),
            mouthActive = false,
            visualizerAvailable = true,
            nowMs = 1_190L
        )

        assertEquals(MouthCloseMode.SILENCE_FAST_CLOSE, frame.closeMode)
        assertEquals(0.12, frame.silenceSnapClosedThreshold, 0.0001)
        assertEquals(0.0, frame.smoothedOpen, 0.0001)
        assertEquals(1, frame.closedSnapCount)
        assertEquals(1_190L, frame.lastClosedSnapTimeMs)
        assertTrue(frame.shouldRender)
    }

    @Test
    fun normalAudibleRmsModeDoesNotSnapSmallOpenValueClosed() {
        val mapper = MouthAmplitudeMapper()

        val frame = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.081, peak = 0.20, activityRatio = 0.10),
            mouthActive = true,
            visualizerAvailable = true,
            nowMs = 1_000L
        )

        assertEquals(MouthCloseMode.NORMAL_RELEASE, frame.closeMode)
        assertTrue(frame.smoothedOpen > 0.0)
        assertTrue(frame.smoothedOpen <= 0.12)
        assertEquals(0, frame.closedSnapCount)
        assertEquals(null, frame.lastClosedSnapTimeMs)
    }

    @Test
    fun snapClosedThenAudioRecoveryUsesRmsAttackFromZero() {
        val mapper = MouthAmplitudeMapper()
        seedOpenMouth(mapper)
        mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics.zero(),
            mouthActive = false,
            visualizerAvailable = true,
            nowMs = 1_150L
        )
        val snapped = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics.zero(),
            mouthActive = false,
            visualizerAvailable = true,
            nowMs = 1_190L
        )

        val reopened = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.30, peak = 0.80, activityRatio = 0.70),
            mouthActive = true,
            visualizerAvailable = true,
            nowMs = 1_220L
        )

        assertEquals(0.0, snapped.smoothedOpen, 0.0001)
        assertEquals(MouthCloseMode.NORMAL_RELEASE, reopened.closeMode)
        assertEquals(100L, reopened.activeTimeConstantMs)
        assertTrue(reopened.smoothedOpen > 0.0)
        assertTrue(reopened.targetOpen!! > reopened.smoothedOpen)
    }

    @Test
    fun silentToActiveImmediatelyReturnsToRmsMode() {
        val mapper = MouthAmplitudeMapper()
        seedOpenMouth(mapper)
        mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics.zero(),
            mouthActive = false,
            visualizerAvailable = true,
            nowMs = 1_150L
        )

        val frame = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.30, peak = 0.80, activityRatio = 0.70),
            mouthActive = true,
            visualizerAvailable = true,
            nowMs = 1_180L
        )

        assertEquals(MouthDriveMode.RMS, frame.mode)
        assertEquals(MouthCloseMode.NORMAL_RELEASE, frame.closeMode)
        assertTrue(frame.targetOpen!! > 0.35)
        assertEquals(null, frame.silenceCloseStartTimeMs)
    }

    @Test
    fun idleClosesImmediatelyWithClosedMode() {
        val mapper = MouthAmplitudeMapper()
        seedOpenMouth(mapper)

        val frame = mapper.evaluate(
            UniversalAiState.IDLE,
            VisualizerWaveformMetrics(rms = 0.40, peak = 0.99, activityRatio = 0.90),
            mouthActive = true,
            visualizerAvailable = true,
            nowMs = 1_150L
        )

        assertEquals(MouthDriveMode.CLOSED, frame.mode)
        assertEquals(MouthCloseMode.CLOSED, frame.closeMode)
        assertEquals(0.0, frame.targetOpen!!, 0.0001)
        assertEquals(0.0, frame.smoothedOpen, 0.0001)
        assertTrue(frame.shouldRender)
    }

    @Test
    fun silenceCloseDiagnosticsTrackDurationUntilAudioReturns() {
        val mapper = MouthAmplitudeMapper()
        seedOpenMouth(mapper)
        mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics.zero(),
            mouthActive = false,
            visualizerAvailable = true,
            nowMs = 1_150L
        )

        val stillSilent = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics.zero(),
            mouthActive = false,
            visualizerAvailable = true,
            nowMs = 1_220L
        )
        val audible = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics(rms = 0.30, peak = 0.80, activityRatio = 0.70),
            mouthActive = true,
            visualizerAvailable = true,
            nowMs = 1_250L
        )

        assertEquals(1_150L, stillSilent.silenceCloseStartTimeMs)
        assertEquals(70L, stillSilent.silenceCloseDurationMs)
        assertEquals(null, audible.silenceCloseStartTimeMs)
        assertEquals(0L, audible.silenceCloseDurationMs)
    }

    @Test
    fun silenceFrameMaySkipRenderWhenAlreadyClosedButAccumulatorStillCloses() {
        val mapper = MouthAmplitudeMapper()

        val frame = mapper.evaluate(
            UniversalAiState.SPEAKING,
            VisualizerWaveformMetrics.zero(),
            mouthActive = false,
            visualizerAvailable = true,
            nowMs = 1_000L
        )

        assertEquals(0.0, frame.smoothedOpen, 0.0001)
        assertFalse(frame.shouldRender)
        assertEquals(MouthCloseMode.SILENCE_FAST_CLOSE, frame.closeMode)
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

    private fun seedOpenMouth(mapper: MouthAmplitudeMapper) {
        val loud = VisualizerWaveformMetrics(rms = 0.50, peak = 0.95, activityRatio = 0.90)
        mapper.evaluate(UniversalAiState.SPEAKING, loud, mouthActive = true, visualizerAvailable = true, nowMs = 1_000L)
        mapper.evaluate(UniversalAiState.SPEAKING, loud, mouthActive = true, visualizerAvailable = true, nowMs = 1_100L)
    }
}
