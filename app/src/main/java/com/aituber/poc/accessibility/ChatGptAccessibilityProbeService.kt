package com.aituber.poc.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aituber.poc.poc.AccessibilityProbeLogger
import com.aituber.poc.poc.CaptureSessionState
import com.aituber.poc.poc.ChatGptTarget
import com.aituber.poc.state.SafeAccessibilityNodeMetadata

class ChatGptAccessibilityProbeService : AccessibilityService() {
    private val logger = AccessibilityProbeLogger()

    override fun onServiceConnected() {
        publish(
            observedPackage = "n/a",
            eventType = "SERVICE_CONNECTED",
            root = null,
            rootAvailable = false
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (packageName != ChatGptTarget.packageName) return

        val root = rootInActiveWindow
        val rootMatchesTarget = root?.packageName?.toString() == ChatGptTarget.packageName
        publish(
            observedPackage = packageName,
            eventType = eventTypeLabel(event.eventType),
            root = if (rootMatchesTarget) root else null,
            rootAvailable = rootMatchesTarget
        )
    }

    override fun onInterrupt() {
        publish(
            observedPackage = "n/a",
            eventType = "INTERRUPTED",
            root = null,
            rootAvailable = false
        )
    }

    override fun onDestroy() {
        CaptureSessionState.updateAccessibilityProbe(logger.snapshot(enabled = "DISABLED"))
        super.onDestroy()
    }

    private fun publish(
        observedPackage: String,
        eventType: String,
        root: AccessibilityNodeInfo?,
        rootAvailable: Boolean
    ) {
        val now = SystemClock.elapsedRealtime()
        val nodes = root?.let { collectSafeMetadata(it) }.orEmpty()
        CaptureSessionState.updateAccessibilityProbe(
            logger.record(
                elapsedTimestampMs = now,
                packageName = observedPackage,
                eventType = eventType,
                rootAvailable = rootAvailable,
                nodes = nodes
            )
        )
    }

    private fun collectSafeMetadata(root: AccessibilityNodeInfo): List<SafeAccessibilityNodeMetadata> {
        val result = mutableListOf<SafeAccessibilityNodeMetadata>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isVisibleToUser) {
                result.add(node.toSafeMetadata())
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let { child -> queue.add(child) }
                }
            }
        }
        return result
    }

    private fun AccessibilityNodeInfo.toSafeMetadata(): SafeAccessibilityNodeMetadata {
        val bounds = Rect()
        getBoundsInScreen(bounds)
        val textValue = text
        return SafeAccessibilityNodeMetadata(
            className = className?.toString() ?: "n/a",
            viewIdResourceName = viewIdResourceName ?: "n/a",
            contentDescription = contentDescription?.toString() ?: "n/a",
            stateDescription = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                stateDescription?.toString() ?: "n/a"
            } else {
                "n/a"
            },
            selected = isSelected,
            checked = isChecked,
            enabled = isEnabled,
            focused = isFocused,
            clickable = isClickable,
            boundsInScreen = "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}",
            childCount = childCount,
            hasText = !textValue.isNullOrEmpty(),
            textLength = textValue?.length ?: 0
        )
    }

    private fun eventTypeLabel(type: Int): String {
        return when (type) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED"
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "WINDOWS_CHANGED"
            AccessibilityEvent.TYPE_VIEW_SELECTED -> "VIEW_SELECTED"
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> "VIEW_FOCUSED"
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED -> "VIEW_ACCESSIBILITY_FOCUSED"
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "VIEW_TEXT_CHANGED"
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> "VIEW_TEXT_SELECTION_CHANGED"
            else -> "EVENT_$type"
        }
    }
}
