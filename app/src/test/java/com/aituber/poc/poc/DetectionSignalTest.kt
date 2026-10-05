package com.aituber.poc.poc

import com.aituber.poc.aiadapter.PcmMetrics
import com.aituber.poc.state.MouthSignal
import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot
import com.aituber.poc.state.VisualizerProbeSnapshot
import com.aituber.poc.state.VisualizerWaveformMetrics
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionSignalTest {

    @Test
    fun pcmMetricsUseTheVisualizerScale() {
        val halfScaleSine = ShortArray(4410) { i -> (16384 * sin(2 * PI * 440 * i / 44100.0)).toInt().toShort() }
        val metrics = PcmMetrics.of(halfScaleSine, halfScaleSine.size)
        assertEquals(0.5, metrics.peak, 0.01)
        assertEquals(0.5 / Math.sqrt(2.0), metrics.rms, 0.01)
        assertTrue(metrics.activityRatio > 0.9)

        val silence = PcmMetrics.of(ShortArray(1000), 1000)
        assertEquals(0.0, silence.rms, 0.0)
        assertEquals(0.0, silence.peak, 0.0)
        assertEquals(0.0, silence.activityRatio, 0.0)
    }

    @Test
    fun mouthFollowsTheSourceThatDecidedTheState() {
        val visualizerMetrics = VisualizerWaveformMetrics(rms = 0.2, peak = 0.4, activityRatio = 0.5)
        val playback = MouthSignal(
            MouthSignal.Source.PLAYBACK_CAPTURE,
            available = true,
            metrics = VisualizerWaveformMetrics(rms = 0.1, peak = 0.3, activityRatio = 0.9)
        )
        val snapshot = UniversalStateSnapshot(
            targetApp = "ChatGPT",
            detectionMethod = "test",
            state = UniversalAiState.SPEAKING,
            audioLevel = null,
            captureStatus = "test",
            visualizerProbe = VisualizerProbeSnapshot.empty().copy(
                enabled = "YES",
                waveformCallbackCount = 10L,
                currentMetrics = visualizerMetrics
            ),
            playbackSignal = playback
        )

        val readAloud = CaptureSessionState.mouthSignalFor(UniversalStateResolver.StateAuthority.PLAYBACK_CAPTURE, snapshot)
        assertEquals(playback, readAloud)

        val voiceMode = CaptureSessionState.mouthSignalFor(UniversalStateResolver.StateAuthority.VISUALIZER, snapshot)
        assertEquals(MouthSignal.Source.VISUALIZER, voiceMode.source)
        assertTrue(voiceMode.available)
        assertEquals(visualizerMetrics, voiceMode.metrics)

        val noVisualizer = CaptureSessionState.mouthSignalFor(
            UniversalStateResolver.StateAuthority.VISUALIZER,
            snapshot.copy(visualizerProbe = VisualizerProbeSnapshot.empty())
        )
        assertFalse(noVisualizer.available)
    }

    @Test
    fun targetLabelListsInstalledAppsOrFallsBack() {
        assertEquals(AiTargetApps.GENERIC_LABEL, AiTargetApps.label(emptyList()))
        assertEquals(
            "ChatGPT / Claude",
            AiTargetApps.label(
                listOf(
                    AiTargetApps.Installed(AiTargetApps.CHATGPT, 10001),
                    AiTargetApps.Installed(AiTargetApps.CLAUDE, 10002)
                )
            )
        )
        assertEquals(AiTargetApps.all.size, AiTargetApps.all.map { it.packageName }.toSet().size)
    }
}

class PcmNormalizationTest {
    @Test
    fun quietAndLoudSpeechLookAlikeAfterNormalization() {
        fun sine(amplitude: Double) = ShortArray(4410) { i -> (amplitude * sin(2 * PI * 220 * i / 44100.0)).toInt().toShort() }
        val quiet = PcmMetrics.normalized(sine(2000.0), 4410)
        val loud = PcmMetrics.normalized(sine(20000.0), 4410)
        assertEquals(loud.rms, quiet.rms, 0.01)
        assertEquals(1.0, quiet.peak, 0.0)
        assertEquals(1.0 / Math.sqrt(2.0), quiet.rms, 0.01)
    }

    @Test
    fun silenceIsNotAmplified() {
        val hiss = ShortArray(1000) { i -> (if (i % 2 == 0) 50 else -50).toShort() }
        val metrics = PcmMetrics.normalized(hiss, hiss.size)
        assertTrue(metrics.peak < 0.01)
        assertTrue(metrics.rms < 0.01)
    }
}
