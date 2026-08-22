package com.aituber.poc.overlay

import android.os.SystemClock
import com.aituber.poc.poc.CaptureSessionState
import com.aituber.poc.state.OverlayLifecycleSnapshot

object OverlayLifecycleTrace {
    private const val CAPACITY = 50
    private val trace = ArrayDeque<String>(CAPACITY)
    private var overlayAlive = "DISABLED"

    @Synchronized
    fun record(step: String) {
        if (trace.size == CAPACITY) trace.removeFirst()
        trace.addLast("${elapsedRealtime()} | $step")
        publishLocked()
    }

    @Synchronized
    fun setAlive(alive: Boolean) {
        overlayAlive = if (alive) "ENABLED" else "DISABLED"
        publishLocked()
    }

    @Synchronized
    fun snapshot(): OverlayLifecycleSnapshot = snapshotLocked()

    @Synchronized
    fun resetForTests() {
        trace.clear()
        overlayAlive = "DISABLED"
        publishLocked()
    }

    private fun publishLocked() {
        CaptureSessionState.updateOverlayLifecycle(snapshotLocked())
    }

    private fun snapshotLocked() = OverlayLifecycleSnapshot(
        trace = trace.toList(),
        overlayServiceAlive = overlayAlive
    )

    private fun elapsedRealtime(): Long {
        return runCatching { SystemClock.elapsedRealtime() }.getOrElse { System.currentTimeMillis() }
    }
}
