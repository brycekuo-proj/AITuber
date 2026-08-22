package com.aituber.poc.overlay

import com.aituber.poc.poc.CaptureSessionState
import com.aituber.poc.poc.CaptureStartupTrace
import com.aituber.poc.state.VisualizerProbeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayLifecycleTraceTest {
    @Test
    fun overlayTraceDoesNotResetCaptureStartupTrace() {
        CaptureStartupTrace.resetForTests()
        OverlayLifecycleTrace.resetForTests()

        CaptureStartupTrace.startButtonClicked()
        OverlayLifecycleTrace.record("overlay enable requested")
        val snapshot = CaptureSessionState.current()

        assertEquals(1, snapshot.captureStartupTrace.startButtonClickCount)
        assertTrue(snapshot.captureStartupTrace.trace.any { it.contains("START button clicked") })
        assertTrue(snapshot.overlayLifecycle.trace.any { it.contains("overlay enable requested") })
    }

    @Test
    fun captureRunningEnableOverlayKeepsVisualizerEnabled() {
        OverlayLifecycleTrace.resetForTests()
        CaptureSessionState.updateVisualizerProbe(
            VisualizerProbeSnapshot.empty().copy(
                enabled = "YES",
                waveformCallbackCount = 7L
            )
        )

        OverlayLifecycleTrace.record("overlay enable requested")
        OverlayLifecycleTrace.setAlive(true)
        val snapshot = CaptureSessionState.current()

        assertEquals("YES", snapshot.visualizerProbe.enabled)
        assertEquals(7L, snapshot.visualizerProbe.waveformCallbackCount)
        assertEquals("ENABLED", snapshot.overlayLifecycle.overlayServiceAlive)
    }

    @Test
    fun callbackCountCanContinueAfterOverlayEnable() {
        OverlayLifecycleTrace.resetForTests()
        CaptureSessionState.updateVisualizerProbe(
            VisualizerProbeSnapshot.empty().copy(
                enabled = "YES",
                waveformCallbackCount = 7L
            )
        )

        OverlayLifecycleTrace.record("overlay enable requested")
        CaptureSessionState.updateVisualizerProbe(
            CaptureSessionState.current().visualizerProbe.copy(waveformCallbackCount = 8L)
        )

        assertEquals(8L, CaptureSessionState.current().visualizerProbe.waveformCallbackCount)
    }

    @Test
    fun disableOverlayKeepsCaptureStartupState() {
        CaptureStartupTrace.resetForTests()
        CaptureStartupTrace.startButtonClicked()

        OverlayLifecycleTrace.record("overlay disable requested")
        OverlayLifecycleTrace.setAlive(false)
        val snapshot = CaptureSessionState.current()

        assertEquals(1, snapshot.captureStartupTrace.startButtonClickCount)
        assertEquals("DISABLED", snapshot.overlayLifecycle.overlayServiceAlive)
    }
}
