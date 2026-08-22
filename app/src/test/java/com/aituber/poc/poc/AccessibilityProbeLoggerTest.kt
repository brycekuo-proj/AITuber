package com.aituber.poc.poc

import com.aituber.poc.state.SafeAccessibilityNodeMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityProbeLoggerTest {
    @Test
    fun emptyRootNullEventDoesNotOverwriteLastValidSignature() {
        val logger = AccessibilityProbeLogger()
        val valid = logger.record(
            elapsedTimestampMs = 1L,
            packageName = ChatGptTarget.packageName,
            eventType = "WINDOW_CONTENT_CHANGED",
            rootAvailable = true,
            nodes = listOf(node(viewIdResourceName = "id/voice"))
        )

        val ignored = logger.record(
            elapsedTimestampMs = 2L,
            packageName = ChatGptTarget.packageName,
            eventType = "WINDOW_CONTENT_CHANGED",
            rootAvailable = false,
            nodes = emptyList()
        )

        assertEquals(valid.lastValidChatGptSignature, ignored.lastValidChatGptSignature)
        assertEquals(valid.lastValidChatGptSignature, ignored.uiSignature)
        assertEquals(1, ignored.ignoredEmptyEvents)
        assertEquals(1, ignored.signatureTransitionCount)
    }

    @Test
    fun nonChatGptPackageEventDoesNotEnterValidHistory() {
        val logger = AccessibilityProbeLogger()

        val snapshot = logger.record(
            elapsedTimestampMs = 1L,
            packageName = "com.example.other",
            eventType = "WINDOW_CONTENT_CHANGED",
            rootAvailable = true,
            nodes = listOf(node(viewIdResourceName = "id/voice"))
        )

        assertEquals("n/a", snapshot.lastValidChatGptSignature)
        assertEquals(0, snapshot.validSignatureEventCount)
        assertEquals(0, snapshot.signatureTransitionCount)
        assertEquals(0, snapshot.lastEvents.size)
    }

    @Test
    fun sameSignatureDoesNotAddTransition() {
        val logger = AccessibilityProbeLogger()
        val nodes = listOf(node(viewIdResourceName = "id/voice"))

        logger.record(1L, ChatGptTarget.packageName, "WINDOW_CONTENT_CHANGED", true, nodes)
        val snapshot = logger.record(2L, ChatGptTarget.packageName, "WINDOW_CONTENT_CHANGED", true, nodes)

        assertEquals(2, snapshot.validSignatureEventCount)
        assertEquals(1, snapshot.signatureTransitionCount)
        assertEquals(1, snapshot.duplicateSignatureEvents)
    }

    @Test
    fun signatureChangeAddsTransition() {
        val logger = AccessibilityProbeLogger()

        logger.record(
            elapsedTimestampMs = 1L,
            packageName = ChatGptTarget.packageName,
            eventType = "WINDOW_CONTENT_CHANGED",
            rootAvailable = true,
            nodes = listOf(node(viewIdResourceName = "id/voice", boundsInScreen = "0,0,100,100"))
        )
        val snapshot = logger.record(
            elapsedTimestampMs = 2L,
            packageName = ChatGptTarget.packageName,
            eventType = "WINDOW_CONTENT_CHANGED",
            rootAvailable = true,
            nodes = listOf(node(viewIdResourceName = "id/voice", boundsInScreen = "0,0,120,120"))
        )

        assertEquals(2, snapshot.signatureTransitionCount)
        assertEquals(2, snapshot.signatureTransitions.size)
        assertEquals("YES", snapshot.uiSignatureChanged)
    }

    @Test
    fun duplicateCounterIncrementsForRepeatedValidEventsOnly() {
        val logger = AccessibilityProbeLogger()
        val nodes = listOf(node(viewIdResourceName = "id/voice"))

        logger.record(1L, ChatGptTarget.packageName, "WINDOW_CONTENT_CHANGED", true, nodes)
        logger.record(2L, ChatGptTarget.packageName, "WINDOW_CONTENT_CHANGED", true, nodes)
        val snapshot = logger.record(3L, ChatGptTarget.packageName, "WINDOW_CONTENT_CHANGED", true, nodes)

        assertEquals(2, snapshot.duplicateSignatureEvents)
        assertEquals(0, snapshot.ignoredEmptyEvents)
    }

    private fun node(
        className: String = "android.view.View",
        viewIdResourceName: String = "n/a",
        contentDescription: String = "n/a",
        stateDescription: String = "n/a",
        selected: Boolean = false,
        checked: Boolean = false,
        enabled: Boolean = true,
        focused: Boolean = false,
        clickable: Boolean = false,
        boundsInScreen: String = "0,0,1,1",
        childCount: Int = 0,
        hasText: Boolean = false,
        textLength: Int = 0
    ) = SafeAccessibilityNodeMetadata(
        className = className,
        viewIdResourceName = viewIdResourceName,
        contentDescription = contentDescription,
        stateDescription = stateDescription,
        selected = selected,
        checked = checked,
        enabled = enabled,
        focused = focused,
        clickable = clickable,
        boundsInScreen = boundsInScreen,
        childCount = childCount,
        hasText = hasText,
        textLength = textLength
    )
}
