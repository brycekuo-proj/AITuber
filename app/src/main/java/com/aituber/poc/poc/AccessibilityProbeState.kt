package com.aituber.poc.poc

object AccessibilityProbeState {
    private val accumulator = AccessibilityProbeAccumulator()

    fun setServiceEnabled(enabled: Boolean) {
        CaptureSessionState.updateAccessibilityProbe(accumulator.setServiceEnabled(enabled))
    }

    fun recordEvent(
        eventType: Int,
        elapsedTimestampMs: Long,
        rootAvailable: Boolean,
        viewIds: Set<String>,
        contentDescriptionStatePatterns: Set<String>,
        candidateSummary: String,
        candidateDetected: String
    ) {
        CaptureSessionState.updateAccessibilityProbe(
            accumulator.recordEvent(
                eventType = eventType,
                elapsedTimestampMs = elapsedTimestampMs,
                rootAvailable = rootAvailable,
                viewIds = viewIds,
                contentDescriptionStatePatterns = contentDescriptionStatePatterns,
                candidateSummary = candidateSummary,
                candidateDetected = candidateDetected
            )
        )
    }
}
