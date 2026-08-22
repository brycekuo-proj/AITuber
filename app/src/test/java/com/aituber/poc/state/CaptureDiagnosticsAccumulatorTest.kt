package com.aituber.poc.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureDiagnosticsAccumulatorTest {
    @Test
    fun peakOnlyIncreasesWithinSession() {
        val accumulator = CaptureDiagnosticsAccumulator()

        accumulator.onReadResult(2, shortArrayOf(Short.MAX_VALUE, Short.MAX_VALUE), 2, 100L)
        accumulator.onReadResult(2, shortArrayOf(100, 100), 2, 200L)

        assertEquals(1f, accumulator.snapshot().peakAudioLevel, 0.001f)
    }

    @Test
    fun resetClearsCountersAndPeak() {
        val accumulator = CaptureDiagnosticsAccumulator()

        accumulator.onReadResult(2, shortArrayOf(Short.MAX_VALUE, Short.MAX_VALUE), 2, 100L)
        accumulator.onState(UniversalAiState.IDLE)
        accumulator.onState(UniversalAiState.SPEAKING)
        val reset = accumulator.reset()

        assertEquals(0f, reset.peakAudioLevel, 0.0f)
        assertEquals(0L, reset.capturedSamples)
        assertEquals(0L, reset.nonZeroSamples)
        assertEquals(0, reset.speakingEvents)
        assertNull(reset.lastNonZeroAudioElapsedMs)
        assertNull(reset.lastReadResult)
        assertEquals(CaptureDiagnosticStatus.NO_SAMPLES, reset.diagnostic)
    }

    @Test
    fun countsOnlyIdleToSpeakingTransitions() {
        val accumulator = CaptureDiagnosticsAccumulator()

        accumulator.onState(UniversalAiState.UNKNOWN)
        accumulator.onState(UniversalAiState.SPEAKING)
        accumulator.onState(UniversalAiState.SPEAKING)
        accumulator.onState(UniversalAiState.IDLE)
        val snapshot = accumulator.onState(UniversalAiState.SPEAKING)

        assertEquals(1, snapshot.speakingEvents)
    }

    @Test
    fun distinguishesSilenceNoSamplesAndErrors() {
        val accumulator = CaptureDiagnosticsAccumulator()

        assertEquals(CaptureDiagnosticStatus.NO_SAMPLES, accumulator.snapshot().diagnostic)

        accumulator.onReadResult(3, shortArrayOf(0, 0, 0), 3, 100L)
        assertEquals(CaptureDiagnosticStatus.RECEIVING_SILENCE, accumulator.snapshot().diagnostic)
        assertEquals(3L, accumulator.snapshot().capturedSamples)
        assertEquals(0L, accumulator.snapshot().nonZeroSamples)

        accumulator.onReadError(-3)
        assertEquals(CaptureDiagnosticStatus.AUDIO_RECORD_ERROR, accumulator.snapshot().diagnostic)
    }
}
