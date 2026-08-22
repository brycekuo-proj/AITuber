package com.aituber.poc.poc

import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.VisualizerWaveformMetrics
import org.junit.Assert.assertEquals
import org.junit.Test

class VisualizerSpeakingDetectorTest {
    @Test
    fun voiceSessionInactiveMapsToIdle() {
        val detector = VisualizerSpeakingDetector()

        val decision = detector.evaluate(
            now = 0L,
            metrics = aiLike(),
            voiceSessionActive = false,
            visualizerAvailable = true
        )

        assertEquals(UniversalAiState.IDLE, decision.state)
    }

    @Test
    fun rmsAboveThresholdLongEnoughMapsToSpeaking() {
        val detector = VisualizerSpeakingDetector()

        detector.evaluate(0L, aiLike(), voiceSessionActive = true, visualizerAvailable = true)
        val decision = detector.evaluate(160L, aiLike(), voiceSessionActive = true, visualizerAvailable = true)

        assertEquals(UniversalAiState.SPEAKING, decision.state)
    }

    @Test
    fun shortSpikeBelowAttackTimeDoesNotEnterSpeaking() {
        val detector = VisualizerSpeakingDetector()

        detector.evaluate(0L, aiLike(), voiceSessionActive = true, visualizerAvailable = true)
        val decision = detector.evaluate(100L, silent(), voiceSessionActive = true, visualizerAvailable = true)

        assertEquals(UniversalAiState.IDLE, decision.state)
    }

    @Test
    fun sustainedSilenceAfterReleaseMapsBackToIdle() {
        val detector = VisualizerSpeakingDetector()

        detector.evaluate(0L, aiLike(), voiceSessionActive = true, visualizerAvailable = true)
        detector.evaluate(160L, aiLike(), voiceSessionActive = true, visualizerAvailable = true)
        detector.evaluate(200L, silent(), voiceSessionActive = true, visualizerAvailable = true)
        val decision = detector.evaluate(510L, silent(), voiceSessionActive = true, visualizerAvailable = true)

        assertEquals(UniversalAiState.IDLE, decision.state)
    }

    @Test
    fun visualizerUnavailableMapsToUnknown() {
        val detector = VisualizerSpeakingDetector()

        val decision = detector.evaluate(
            now = 0L,
            metrics = silent(),
            voiceSessionActive = true,
            visualizerAvailable = false
        )

        assertEquals(UniversalAiState.UNKNOWN, decision.state)
    }

    @Test
    fun userLikeSampleDoesNotEasilyEnterSpeaking() {
        val detector = VisualizerSpeakingDetector()

        detector.evaluate(0L, userLike(), voiceSessionActive = true, visualizerAvailable = true)
        val decision = detector.evaluate(300L, userLike(), voiceSessionActive = true, visualizerAvailable = true)

        assertEquals(UniversalAiState.IDLE, decision.state)
    }

    @Test
    fun aiLikeSampleEntersSpeaking() {
        val detector = VisualizerSpeakingDetector()

        detector.evaluate(0L, aiLike(), voiceSessionActive = true, visualizerAvailable = true)
        val decision = detector.evaluate(160L, aiLike(), voiceSessionActive = true, visualizerAvailable = true)

        assertEquals(UniversalAiState.SPEAKING, decision.state)
    }

    private fun silent() = VisualizerWaveformMetrics(rms = 0.0, peak = 0.0, activityRatio = 0.0)

    private fun userLike() = VisualizerWaveformMetrics(rms = 0.08, peak = 0.18, activityRatio = 0.15)

    private fun aiLike() = VisualizerWaveformMetrics(rms = 0.32, peak = 0.89, activityRatio = 0.75)
}
