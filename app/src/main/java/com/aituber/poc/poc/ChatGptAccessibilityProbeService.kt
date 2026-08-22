package com.aituber.poc.poc

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ChatGptAccessibilityProbeService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityProbeState.setServiceEnabled(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != ChatGptTarget.packageName) return

        val root = rootInActiveWindow
        val scan = scanSafeTree(root)
        AccessibilityProbeState.recordEvent(
            eventType = event.eventType,
            elapsedTimestampMs = SystemClock.elapsedRealtime(),
            rootAvailable = root != null,
            viewIds = scan.viewIds,
            contentDescriptionStatePatterns = scan.contentDescriptionStatePatterns,
            candidateSummary = scan.candidateSummary,
            candidateDetected = scan.candidateDetected
        )
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        AccessibilityProbeState.setServiceEnabled(false)
        super.onDestroy()
    }

    private fun scanSafeTree(root: AccessibilityNodeInfo?): SafeTreeScan {
        if (root == null) {
            return SafeTreeScan(
                viewIds = emptySet(),
                contentDescriptionStatePatterns = emptySet(),
                candidateDetected = "UNKNOWN",
                candidateSummary = "rootNodeAvailable=false"
            )
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val viewIds = linkedSetOf<String>()
        val patterns = linkedSetOf<String>()
        val candidateSummaries = mutableListOf<String>()
        var visited = 0
        queue.add(root)

        while (queue.isNotEmpty() && visited < 250) {
            val node = queue.removeFirst()
            visited += 1

            val viewId = node.viewIdResourceName
            val viewIdSafe = viewId ?: "noViewId"
            if (viewId != null) {
                viewIds.add(viewId)
            }

            val contentDescriptionPresent = !node.contentDescription.isNullOrBlank()
            val pattern = buildString {
                append("class=").append(node.className ?: "unknown")
                append(" viewId=").append(viewIdSafe)
                append(" contentDescriptionPresent=").append(contentDescriptionPresent)
                append(" clickable=").append(node.isClickable)
                append(" enabled=").append(node.isEnabled)
                append(" selected=").append(node.isSelected)
                append(" focused=").append(node.isFocused)
                append(" visibleToUser=").append(node.isVisibleToUser)
            }
            patterns.add(pattern)

            if (isVoiceCandidateBySafeMetadata(viewId)) {
                candidateSummaries.add(pattern)
            }

            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { child -> queue.add(child) }
            }
        }

        val candidateDetected = when {
            candidateSummaries.isNotEmpty() -> "YES"
            visited > 0 -> "NO"
            else -> "UNKNOWN"
        }
        return SafeTreeScan(
            viewIds = viewIds,
            contentDescriptionStatePatterns = patterns,
            candidateDetected = candidateDetected,
            candidateSummary = candidateSummaries.take(3).joinToString(" | ").ifBlank {
                "visitedNodes=$visited candidateByViewId=false"
            }
        )
    }

    private fun isVoiceCandidateBySafeMetadata(viewId: String?): Boolean {
        val id = viewId?.lowercase() ?: return false
        return id.contains("voice") || id.contains("audio") || id.contains("speech")
    }

    private data class SafeTreeScan(
        val viewIds: Set<String>,
        val contentDescriptionStatePatterns: Set<String>,
        val candidateDetected: String,
        val candidateSummary: String
    )
}
