package com.aituber.poc.state

data class CaptureStartupTraceSnapshot(
    val trace: List<String>,
    val startButtonClickCount: Int,
    val projectionRequestCount: Int,
    val projectionResultOkCount: Int,
    val captureServiceStartRequestCount: Int,
    val serviceOnCreateCount: Int,
    val serviceOnStartCommandCount: Int,
    val startCaptureCount: Int
) {
    companion object {
        fun empty() = CaptureStartupTraceSnapshot(
            trace = emptyList(),
            startButtonClickCount = 0,
            projectionRequestCount = 0,
            projectionResultOkCount = 0,
            captureServiceStartRequestCount = 0,
            serviceOnCreateCount = 0,
            serviceOnStartCommandCount = 0,
            startCaptureCount = 0
        )
    }
}
