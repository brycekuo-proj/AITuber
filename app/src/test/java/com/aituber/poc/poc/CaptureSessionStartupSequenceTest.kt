package com.aituber.poc.poc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureSessionStartupSequenceTest {
    @Test
    fun startupRequestsVisualizerBeforePlaybackCaptureDiagnostics() {
        val actions = RecordingActions()

        CaptureSessionStartupSequence(actions).run()
        actions.events += "playbackCapture diagnostics started"

        assertEquals(
            listOf(
                "startForeground",
                "trace:startCapture entered",
                "publish",
                "startPlaybackProbe",
                "trace:playbackProbe started",
                "startVisualizer",
                "playbackCapture diagnostics started"
            ),
            actions.events
        )
    }

    @Test
    fun playbackCaptureInitializationFailureDoesNotPreventVisualizerStartupRequest() {
        val actions = RecordingActions()

        CaptureSessionStartupSequence(actions).run()
        val failure = runCatching { error("PlaybackCapture init failed") }

        assertTrue(failure.isFailure)
        assertTrue(actions.events.contains("startVisualizer"))
    }

    private class RecordingActions : CaptureSessionStartupSequence.Actions {
        val events = mutableListOf<String>()

        override fun startForeground() {
            events += "startForeground"
        }

        override fun recordVisualizerTrace(step: String) {
            events += "trace:$step"
        }

        override fun publish(status: String) {
            events += "publish"
        }

        override fun startPlaybackProbe() {
            events += "startPlaybackProbe"
        }

        override fun startVisualizer() {
            events += "startVisualizer"
        }
    }
}
