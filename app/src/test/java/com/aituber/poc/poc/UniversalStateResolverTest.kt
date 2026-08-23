package com.aituber.poc.poc

import com.aituber.poc.state.UniversalAiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalStateResolverTest {
    @Test
    fun visualizerSpeakingOverridesPlaybackIdleWhenVoiceSessionActive() {
        val resolver = UniversalStateResolver()

        val result = resolver.resolve(
            input(
                visualizerDerivedState = UniversalAiState.SPEAKING,
                playbackState = UniversalAiState.IDLE
            )
        )

        assertEquals(UniversalAiState.SPEAKING, result.state)
        assertEquals("VISUALIZER", result.timing.stateAuthority)
    }

    @Test
    fun playbackIdleAfterVisualizerSpeakingDoesNotOverwriteSpeaking() {
        val resolver = UniversalStateResolver()
        resolver.resolve(input(visualizerDerivedState = UniversalAiState.SPEAKING, playbackState = UniversalAiState.IDLE, now = 1_000L))

        val result = resolver.resolve(
            input(
                visualizerDerivedState = UniversalAiState.SPEAKING,
                playbackState = UniversalAiState.IDLE,
                now = 1_050L,
                writer = "PlaybackCapture/Core update"
            )
        )

        assertEquals(UniversalAiState.SPEAKING, result.state)
        assertEquals("VISUALIZER", result.timing.stateAuthority)
    }

    @Test
    fun shortVisualizerIdleWithinHoldKeepsSpeaking() {
        val resolver = UniversalStateResolver(speakingHoldMs = 100L)
        resolver.resolve(input(visualizerDerivedState = UniversalAiState.SPEAKING, now = 1_000L))

        val result = resolver.resolve(input(visualizerDerivedState = UniversalAiState.IDLE, now = 1_050L))

        assertEquals(UniversalAiState.SPEAKING, result.state)
        assertEquals(50L, result.timing.speakingHoldRemainingMs)
    }

    @Test
    fun defaultUniversalSpeakingHoldIsOneHundredMs() {
        val resolver = UniversalStateResolver()
        resolver.resolve(input(visualizerDerivedState = UniversalAiState.SPEAKING, now = 1_000L))

        val result = resolver.resolve(input(visualizerDerivedState = UniversalAiState.IDLE, now = 1_050L))

        assertEquals(UniversalAiState.SPEAKING, result.state)
        assertEquals(100L, result.timing.speakingHoldMs)
        assertEquals(50L, result.timing.speakingHoldRemainingMs)
    }

    @Test
    fun visualizerIdleAfterHoldReturnsIdle() {
        val resolver = UniversalStateResolver(speakingHoldMs = 100L)
        resolver.resolve(input(visualizerDerivedState = UniversalAiState.SPEAKING, now = 1_000L))

        val result = resolver.resolve(input(visualizerDerivedState = UniversalAiState.IDLE, now = 1_101L))

        assertEquals(UniversalAiState.IDLE, result.state)
        assertEquals(0L, result.timing.speakingHoldRemainingMs)
    }

    @Test
    fun staleIdleTimestampCannotOverwriteNewerSpeaking() {
        val resolver = UniversalStateResolver()
        resolver.resolve(input(visualizerDerivedState = UniversalAiState.SPEAKING, now = 1_000L, sourceTimestamp = 1_000L))

        val result = resolver.resolve(
            input(
                visualizerAvailable = false,
                visualizerDerivedState = UniversalAiState.UNKNOWN,
                playbackState = UniversalAiState.IDLE,
                now = 1_100L,
                sourceTimestamp = 900L
            )
        )

        assertEquals(UniversalAiState.SPEAKING, result.state)
    }

    @Test
    fun visualizerUnavailableFallsBackToPlaybackState() {
        val resolver = UniversalStateResolver()

        val result = resolver.resolve(
            input(
                visualizerAvailable = false,
                visualizerDerivedState = UniversalAiState.UNKNOWN,
                playbackState = UniversalAiState.IDLE
            )
        )

        assertEquals(UniversalAiState.IDLE, result.state)
        assertEquals("PLAYBACK_FALLBACK", result.timing.stateAuthority)
    }

    @Test
    fun inactiveVoiceSessionResolvesIdle() {
        val resolver = UniversalStateResolver()

        val result = resolver.resolve(
            input(
                voiceSessionActive = false,
                visualizerDerivedState = UniversalAiState.SPEAKING,
                playbackState = UniversalAiState.SPEAKING
            )
        )

        assertEquals(UniversalAiState.IDLE, result.state)
        assertEquals("NO_ACTIVE_SESSION", result.timing.stateAuthority)
    }

    @Test
    fun timingDiagnosticsRecordDerivedUniversalAuthorityDelayAndHold() {
        val resolver = UniversalStateResolver(speakingHoldMs = 100L)

        val result = resolver.resolve(input(visualizerDerivedState = UniversalAiState.SPEAKING, now = 2_000L))

        assertEquals("SPEAKING", result.timing.visualizerDerivedState)
        assertEquals(2_000L, result.timing.visualizerDerivedLastChangeTimeMs)
        assertEquals(2_000L, result.timing.visualizerLastSpeakingTimeMs)
        assertEquals("SPEAKING", result.timing.resolvedUniversalState)
        assertEquals(2_000L, result.timing.universalStateLastChangeTimeMs)
        assertEquals("VISUALIZER", result.timing.stateAuthority)
        assertEquals(0L, result.timing.derivedToUniversalDelayMs)
        assertEquals("test", result.timing.lastStateWriter)
        assertEquals("VISUALIZER", result.timing.lastStateSource)
        assertEquals(100L, result.timing.speakingHoldMs)
        assertTrue(result.timing.speakingHoldRemainingMs > 0L)
    }

    private fun input(
        now: Long = 1_000L,
        sourceTimestamp: Long = now,
        writer: String = "test",
        voiceSessionActive: Boolean = true,
        visualizerAvailable: Boolean = true,
        visualizerDerivedState: UniversalAiState = UniversalAiState.SPEAKING,
        playbackState: UniversalAiState = UniversalAiState.IDLE
    ) = UniversalStateResolver.Input(
        nowMs = now,
        sourceTimestampMs = sourceTimestamp,
        writer = writer,
        voiceSessionActive = voiceSessionActive,
        visualizerAvailable = visualizerAvailable,
        visualizerDerivedState = visualizerDerivedState,
        playbackState = playbackState
    )
}
