package com.aituber.poc.poc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualizerWaveformAnalyzerTest {
    @Test
    fun centeredWaveformProducesZeroMetrics() {
        val analyzer = VisualizerWaveformAnalyzer()
        val waveform = ByteArray(16) { 128.toByte() }

        val metrics = analyzer.metrics(waveform)

        assertEquals(0.0, metrics.rms, 0.0001)
        assertEquals(0.0, metrics.peak, 0.0001)
        assertEquals(0.0, metrics.activityRatio, 0.0001)
    }

    @Test
    fun syntheticWaveformProducesRmsAndPeakActivity() {
        val analyzer = VisualizerWaveformAnalyzer(epsilon = 3)
        val waveform = byteArrayOf(128.toByte(), 255.toByte(), 128.toByte(), 1.toByte())

        val metrics = analyzer.metrics(waveform)

        assertTrue(metrics.rms > 0.6)
        assertTrue(metrics.peak > 0.9)
        assertEquals(0.5, metrics.activityRatio, 0.0001)
    }

    @Test
    fun activityRatioUsesConfiguredEpsilon() {
        val analyzer = VisualizerWaveformAnalyzer(epsilon = 10)
        val waveform = byteArrayOf(128.toByte(), 132.toByte(), 140.toByte(), 120.toByte())

        val metrics = analyzer.metrics(waveform)

        assertEquals(0.25, metrics.activityRatio, 0.0001)
    }
}
