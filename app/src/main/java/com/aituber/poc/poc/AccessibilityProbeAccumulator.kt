package com.aituber.poc.poc

import android.view.accessibility.AccessibilityEvent
import com.aituber.poc.state.AccessibilityEventSummary
import com.aituber.poc.state.AccessibilityProbeSnapshot

class AccessibilityProbeAccumulator {
    private var serviceStatus = "DISABLED"
    private var chatGptWindowDetected = "NO"
    private var lastEventType = "n/a"
    private var lastEventElapsedMs: Long? = null
    private var rootNodeAvailable = "NO"
    private var voiceUiCandidateDetected = "UNKNOWN"
    private var candidateState = "UNKNOWN"
    private var candidateSignalSummary = "n/a"
    private var eventCount = 0
    private var windowChangeCount = 0
    private var nodeTreeChangeCount = 0
    private val distinctViewIdsObserved = linkedSetOf<String>()
    private val distinctContentDescriptionStatePatterns = linkedSetOf<String>()
    private val lastEventSummaries = ArrayDeque<AccessibilityEventSummary>(20)

    fun setServiceEnabled(enabled: Boolean): AccessibilityProbeSnapshot {
        serviceStatus = if (enabled) "ENABLED" else "DISABLED"
        return snapshot()
    }

    fun recordEvent(
        eventType: Int,
        elapsedTimestampMs: Long,
        rootAvailable: Boolean,
        viewIds: Set<String>,
        contentDescriptionStatePatterns: Set<String>,
        candidateSummary: String,
        candidateDetected: String
    ): AccessibilityProbeSnapshot {
        serviceStatus = "ENABLED"
        chatGptWindowDetected = "YES"
        lastEventType = eventTypeLabel(eventType)
        lastEventElapsedMs = elapsedTimestampMs
        rootNodeAvailable = if (rootAvailable) "YES" else "NO"
        voiceUiCandidateDetected = candidateDetected
        candidateState = "UNKNOWN"
        candidateSignalSummary = candidateSummary
        eventCount += 1

        if (eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            windowChangeCount += 1
        }
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            nodeTreeChangeCount += 1
        }

        distinctViewIdsObserved.addAll(viewIds)
        distinctContentDescriptionStatePatterns.addAll(contentDescriptionStatePatterns)
        recordSummary(
            AccessibilityEventSummary(
                elapsedTimestampMs = elapsedTimestampMs,
                eventType = lastEventType,
                rootNodeAvailable = rootAvailable,
                voiceUiCandidateDetected = candidateDetected,
                signalSummary = candidateSummary
            )
        )
        return snapshot()
    }

    fun snapshot() = AccessibilityProbeSnapshot(
        serviceStatus = serviceStatus,
        chatGptWindowDetected = chatGptWindowDetected,
        lastEventType = lastEventType,
        lastEventElapsedMs = lastEventElapsedMs,
        rootNodeAvailable = rootNodeAvailable,
        voiceUiCandidateDetected = voiceUiCandidateDetected,
        candidateState = candidateState,
        candidateSignalSummary = candidateSignalSummary,
        eventCount = eventCount,
        windowChangeCount = windowChangeCount,
        nodeTreeChangeCount = nodeTreeChangeCount,
        distinctViewIdsObserved = distinctViewIdsObserved.toList().takeLast(30),
        distinctContentDescriptionStatePatterns = distinctContentDescriptionStatePatterns.toList().takeLast(30),
        lastEventSummaries = lastEventSummaries.toList()
    )

    private fun recordSummary(summary: AccessibilityEventSummary) {
        if (lastEventSummaries.size == 20) {
            lastEventSummaries.removeFirst()
        }
        lastEventSummaries.addLast(summary)
    }

    private fun eventTypeLabel(eventType: Int): String {
        return when (eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "TYPE_VIEW_CLICKED"
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> "TYPE_VIEW_FOCUSED"
            AccessibilityEvent.TYPE_VIEW_SELECTED -> "TYPE_VIEW_SELECTED"
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "TYPE_VIEW_TEXT_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "TYPE_WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "TYPE_WINDOW_CONTENT_CHANGED"
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "TYPE_WINDOWS_CHANGED"
            else -> "TYPE_$eventType"
        }
    }
}
