package com.aituber.poc.poc

import com.aituber.poc.aiadapter.CaptureStatus

class CaptureSessionStartupSequence(
    private val actions: Actions
) {
    fun run() {
        actions.startForeground()
        actions.recordVisualizerTrace("startCapture entered")
        actions.publish(CaptureStatus.SERVICE_STARTING)
        actions.startPlaybackProbe()
        actions.recordVisualizerTrace("playbackProbe started")
        actions.startVisualizer()
    }

    interface Actions {
        fun startForeground()
        fun recordVisualizerTrace(step: String)
        fun publish(status: String)
        fun startPlaybackProbe()
        fun startVisualizer()
    }
}
