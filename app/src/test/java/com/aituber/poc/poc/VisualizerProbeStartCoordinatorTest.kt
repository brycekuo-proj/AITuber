package com.aituber.poc.poc

import org.junit.Assert.assertEquals
import org.junit.Test

class VisualizerProbeStartCoordinatorTest {
    @Test
    fun normalCaptureStartStartsVisualizerBackend() {
        val backend = FakeBackend()
        val coordinator = VisualizerProbeStartCoordinator(backend)

        coordinator.startNormalCapture()

        assertEquals(listOf(false), backend.starts)
        assertEquals(0, backend.stopCount)
    }

    @Test
    fun stopReleasesVisualizerBackend() {
        val backend = FakeBackend()
        val coordinator = VisualizerProbeStartCoordinator(backend)

        coordinator.startNormalCapture()
        coordinator.stop()

        assertEquals(1, backend.stopCount)
    }

    @Test
    fun repeatedStartStopDoesNotLeaveDuplicateInstance() {
        val backend = FakeBackend()
        val coordinator = VisualizerProbeStartCoordinator(backend)

        coordinator.startNormalCapture()
        coordinator.startNormalCapture()
        coordinator.stop()

        assertEquals(listOf(false, false), backend.starts)
        assertEquals(2, backend.stopCount)
    }

    @Test
    fun normalCaptureAndThirtySecondTestShareSameBackendStartupPath() {
        val backend = FakeBackend()
        val coordinator = VisualizerProbeStartCoordinator(backend)

        coordinator.startNormalCapture()
        coordinator.startThirtySecondTest()

        assertEquals(listOf(false, true), backend.starts)
        assertEquals(1, backend.stopCount)
    }

    private class FakeBackend : VisualizerProbeStartCoordinator.Backend {
        val starts = mutableListOf<Boolean>()
        var stopCount = 0

        override fun start(automatedTest: Boolean) {
            starts += automatedTest
        }

        override fun stop() {
            stopCount += 1
        }
    }
}
