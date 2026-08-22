package com.aituber.poc.poc

import com.aituber.poc.state.VisualizerWaveformMetrics
import kotlin.math.abs
import kotlin.math.sqrt

class VisualizerWaveformAnalyzer(
    private val epsilon: Int = 3
) {
    fun metrics(waveform: ByteArray): VisualizerWaveformMetrics {
        if (waveform.isEmpty()) return VisualizerWaveformMetrics.zero()
        var squareSum = 0.0
        var peak = 0
        var active = 0
        waveform.forEach { value ->
            val centered = (value.toInt() and 0xff) - 128
            val amplitude = abs(centered)
            squareSum += centered * centered
            peak = maxOf(peak, amplitude)
            if (amplitude > epsilon) active += 1
        }
        return VisualizerWaveformMetrics(
            rms = (sqrt(squareSum / waveform.size) / 128.0).coerceIn(0.0, 1.0),
            peak = (peak / 128.0).coerceIn(0.0, 1.0),
            activityRatio = (active.toDouble() / waveform.size).coerceIn(0.0, 1.0)
        )
    }
}
