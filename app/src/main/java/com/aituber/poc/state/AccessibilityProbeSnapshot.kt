package com.aituber.poc.state

data class AccessibilityEventSummary(
    val elapsedTimestampMs: Long,
    val eventType: String,
    val rootNodeAvailable: Boolean,
    val voiceUiCandidateDetected: String,
    val signalSummary: String
)

data class AccessibilityProbeSnapshot(
    val serviceStatus: String,
    val chatGptWindowDetected: String,
    val lastEventType: String,
    val lastEventElapsedMs: Long?,
    val rootNodeAvailable: String,
    val voiceUiCandidateDetected: String,
    val candidateState: String,
    val candidateSignalSummary: String,
    val eventCount: Int,
    val windowChangeCount: Int,
    val nodeTreeChangeCount: Int,
    val distinctViewIdsObserved: List<String>,
    val distinctContentDescriptionStatePatterns: List<String>,
    val lastEventSummaries: List<AccessibilityEventSummary>
) {
    companion object {
        fun empty() = AccessibilityProbeSnapshot(
            serviceStatus = "DISABLED",
            chatGptWindowDetected = "NO",
            lastEventType = "n/a",
            lastEventElapsedMs = null,
            rootNodeAvailable = "NO",
            voiceUiCandidateDetected = "UNKNOWN",
            candidateState = "UNKNOWN",
            candidateSignalSummary = "n/a",
            eventCount = 0,
            windowChangeCount = 0,
            nodeTreeChangeCount = 0,
            distinctViewIdsObserved = emptyList(),
            distinctContentDescriptionStatePatterns = emptyList(),
            lastEventSummaries = emptyList()
        )
    }
}
