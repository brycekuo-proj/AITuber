package com.aituber.poc.poc

import com.aituber.poc.aiadapter.CaptureStatus

class CaptureSessionStartupSequence(
    private val actions: Actions
) {
    fun run() {
        actions.startForeground()
        actions.recordVisualizerTrace("startCapture entered")
        actions.publish(CaptureStatus.SERVICE_STARTING)
        actions.recordCaptureTrace("playbackProbe start requested")
        actions.startPlaybackProbe()
        actions.recordVisualizerTrace("playbackProbe started")
        actions.recordCaptureTrace("visualizer start requested")
        actions.startVisualizer()
    }

    interface Actions {
        fun startForeground()
        fun recordCaptureTrace(step: String)
        fun recordVisualizerTrace(step: String)
        fun publish(status: String)
        fun startPlaybackProbe()
        fun startVisualizer()
    }
}
