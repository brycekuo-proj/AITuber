package com.aituber.poc.poc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureStartupTraceTest {
    @Test
    fun startButtonClickAddsTraceAndCounter() {
        CaptureStartupTrace.resetForTests()

        CaptureStartupTrace.startButtonClicked()
        val snapshot = CaptureStartupTrace.snapshot()

        assertEquals(1, snapshot.startButtonClickCount)
        assertTrue(snapshot.trace.single().contains("START button clicked"))
    }

    @Test
    fun permissionGrantedPathCanRecordProjectionRequestCount() {
        CaptureStartupTrace.resetForTests()

        CaptureStartupTrace.recordAudioPermissionCheck(granted = true)
        CaptureStartupTrace.projectionRequestLaunched()
        val snapshot = CaptureStartupTrace.snapshot()

        assertEquals(1, snapshot.projectionRequestCount)
        assertTrue(snapshot.trace[0].contains("RECORD_AUDIO permission check granted=true"))
        assertTrue(snapshot.trace[1].contains("MediaProjection request launched"))
    }

    @Test
    fun resultOkRecordsServiceStartRequestCount() {
        CaptureStartupTrace.resetForTests()

        CaptureStartupTrace.projectionResultOk()
        CaptureStartupTrace.captureServiceStartRequested()
        val snapshot = CaptureStartupTrace.snapshot()

        assertEquals(1, snapshot.projectionResultOkCount)
        assertEquals(1, snapshot.captureServiceStartRequestCount)
    }

    @Test
    fun actionStartCanRecordStartCaptureCount() {
        CaptureStartupTrace.resetForTests()

        CaptureStartupTrace.serviceOnStartCommand("com.aituber.poc.action.START_CAPTURE")
        CaptureStartupTrace.startCaptureEntered()
        val snapshot = CaptureStartupTrace.snapshot()

        assertEquals(1, snapshot.serviceOnStartCommandCount)
        assertEquals(1, snapshot.startCaptureCount)
    }

    @Test
    fun wrongOrNullActionDoesNotIncrementStartCaptureCount() {
        CaptureStartupTrace.resetForTests()

        CaptureStartupTrace.serviceOnStartCommand(null)
        CaptureStartupTrace.record("unexpected service action: null")
        val snapshot = CaptureStartupTrace.snapshot()

        assertEquals(1, snapshot.serviceOnStartCommandCount)
        assertEquals(0, snapshot.startCaptureCount)
    }

    @Test
    fun traceHistoryPreservesOrderAndRepeatedStartCounts() {
        CaptureStartupTrace.resetForTests()

        CaptureStartupTrace.startButtonClicked()
        CaptureStartupTrace.startButtonClicked()
        CaptureStartupTrace.projectionRequestLaunched()
        CaptureStartupTrace.projectionRequestLaunched()
        val snapshot = CaptureStartupTrace.snapshot()

        assertEquals(2, snapshot.startButtonClickCount)
        assertEquals(2, snapshot.projectionRequestCount)
        assertTrue(snapshot.trace[0].contains("START button clicked"))
        assertTrue(snapshot.trace[1].contains("START button clicked"))
        assertTrue(snapshot.trace[2].contains("MediaProjection request launched"))
        assertTrue(snapshot.trace[3].contains("MediaProjection request launched"))
    }
}
