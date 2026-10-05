package com.aituber.poc.state

/**
 * Detector-neutral loudness for driving the mouth. The state layer picks the
 * source that currently decides SPEAKING / IDLE, so the character side never
 * needs to know which probe produced it (docs/ARCHITECTURE.md boundary rule).
 *
 * [metrics] uses the same 0..1 scale for every source: RMS, peak and the share
 * of samples above a small noise floor.
 */
data class MouthSignal(
    val source: Source,
    val available: Boolean,
    val metrics: VisualizerWaveformMetrics
) {
    enum class Source {
        NONE,
        VISUALIZER,
        PLAYBACK_CAPTURE,
        /** AITuber's own Claude chat reading its reply aloud. */
        DIRECT_CHAT
    }

    companion object {
        val NONE = MouthSignal(Source.NONE, available = false, metrics = VisualizerWaveformMetrics.zero())
    }
}
