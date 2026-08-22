package com.aituber.poc.poc

import android.os.SystemClock
import com.aituber.poc.state.CaptureStartupTraceSnapshot

object CaptureStartupTrace {
    private const val CAPACITY = 80
    private val trace = ArrayDeque<String>(CAPACITY)
    private var startButtonClickCount = 0
    private var projectionRequestCount = 0
    private var projectionResultOkCount = 0
    private var captureServiceStartRequestCount = 0
    private var serviceOnCreateCount = 0
    private var serviceOnStartCommandCount = 0
    private var startCaptureCount = 0

    @Synchronized
    fun record(step: String) {
        if (trace.size == CAPACITY) trace.removeFirst()
        trace.addLast("${elapsedRealtime()} | $step")
        publishLocked()
    }

    @Synchronized
    fun startButtonClicked() {
        startButtonClickCount += 1
        recordLocked("START button clicked")
    }

    @Synchronized
    fun recordAudioPermissionCheck(granted: Boolean) {
        recordLocked("RECORD_AUDIO permission check granted=$granted")
    }

    @Synchronized
    fun projectionRequestLaunched() {
        projectionRequestCount += 1
        recordLocked("MediaProjection request launched")
    }

    @Synchronized
    fun projectionResultOk() {
        projectionResultOkCount += 1
        recordLocked("MediaProjection RESULT_OK")
    }

    @Synchronized
    fun captureServiceStartRequested() {
        captureServiceStartRequestCount += 1
        recordLocked("startCaptureService called")
    }

    @Synchronized
    fun serviceOnCreate() {
        serviceOnCreateCount += 1
        recordLocked("CaptureSessionService onCreate")
    }

    @Synchronized
    fun serviceOnStartCommand(action: String?) {
        serviceOnStartCommandCount += 1
        recordLocked("onStartCommand received action=${action ?: "null"}")
    }

    @Synchronized
    fun startCaptureEntered() {
        startCaptureCount += 1
        recordLocked("startCapture entered")
    }

    @Synchronized
    fun snapshot(): CaptureStartupTraceSnapshot = snapshotLocked()

    @Synchronized
    fun resetForTests() {
        trace.clear()
        startButtonClickCount = 0
        projectionRequestCount = 0
        projectionResultOkCount = 0
        captureServiceStartRequestCount = 0
        serviceOnCreateCount = 0
        serviceOnStartCommandCount = 0
        startCaptureCount = 0
        publishLocked()
    }

    private fun recordLocked(step: String) {
        if (trace.size == CAPACITY) trace.removeFirst()
        trace.addLast("${elapsedRealtime()} | $step")
        publishLocked()
    }

    private fun publishLocked() {
        CaptureSessionState.updateCaptureStartupTrace(snapshotLocked())
    }

    private fun snapshotLocked() = CaptureStartupTraceSnapshot(
        trace = trace.toList(),
        startButtonClickCount = startButtonClickCount,
        projectionRequestCount = projectionRequestCount,
        projectionResultOkCount = projectionResultOkCount,
        captureServiceStartRequestCount = captureServiceStartRequestCount,
        serviceOnCreateCount = serviceOnCreateCount,
        serviceOnStartCommandCount = serviceOnStartCommandCount,
        startCaptureCount = startCaptureCount
    )

    private fun elapsedRealtime(): Long {
        return runCatching { SystemClock.elapsedRealtime() }.getOrElse { System.currentTimeMillis() }
    }
}
