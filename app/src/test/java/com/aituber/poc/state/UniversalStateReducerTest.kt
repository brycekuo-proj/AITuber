package com.aituber.poc.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UniversalStateReducerTest {
    @Test
    fun movesToSpeakingAfterStableAudio() {
        val reducer = UniversalStateReducer()

        reducer.reduce("System playback", "Playback Capture", 0.04f, "Capturing")
        reducer.reduce("System playback", "Playback Capture", 0.04f, "Capturing")
        val snapshot = reducer.reduce("System playback", "Playback Capture", 0.04f, "Capturing")

        assertEquals(UniversalAiState.SPEAKING, snapshot.state)
        assertEquals(0.04f, snapshot.audioLevel)
    }

    @Test
    fun movesToIdleAfterStableLowAudio() {
        val reducer = UniversalStateReducer(requiredSpeakingFrames = 1, requiredIdleFrames = 2)

        reducer.reduce("System playback", "Playback Capture", 0.04f, "Capturing")
        reducer.reduce("System playback", "Playback Capture", 0.0f, "Capturing")
        val snapshot = reducer.reduce("System playback", "Playback Capture", 0.0f, "Capturing")

        assertEquals(UniversalAiState.IDLE, snapshot.state)
    }

    @Test
    fun unknownWhenNoSafeSignalExists() {
        val reducer = UniversalStateReducer()

        val snapshot = reducer.reduce("Unknown", "Unavailable", null, "Blocked")

        assertEquals(UniversalAiState.UNKNOWN, snapshot.state)
        assertNull(snapshot.audioLevel)
    }
}
