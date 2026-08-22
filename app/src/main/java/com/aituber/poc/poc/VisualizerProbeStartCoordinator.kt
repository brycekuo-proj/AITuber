package com.aituber.poc.poc

class VisualizerProbeStartCoordinator(
    private val backend: Backend
) {
    private var running = false

    fun startNormalCapture() {
        start(automatedTest = false)
    }

    fun startThirtySecondTest() {
        start(automatedTest = true)
    }

    fun stop() {
        if (!running) return
        backend.stop()
        running = false
    }

    private fun start(automatedTest: Boolean) {
        if (running) {
            backend.stop()
        }
        backend.start(automatedTest)
        running = true
    }

    interface Backend {
        fun start(automatedTest: Boolean)
        fun stop()
    }
}
