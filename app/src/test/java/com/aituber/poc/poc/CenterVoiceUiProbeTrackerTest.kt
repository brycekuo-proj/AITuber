package com.aituber.poc.poc

import com.aituber.poc.state.SafeAccessibilityNodeMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CenterVoiceUiProbeTrackerTest {
    @Test
    fun centerCandidateSelectionIsDeterministic() {
        val nodes = listOf(
            node(treePath = "0.1", boundsInScreen = "189,796,892,1499"),
            node(treePath = "0.2", boundsInScreen = "200,820,870,1490")
        )

        val first = CenterVoiceUiProbeTracker.selectCenterCandidate(nodes)
        val second = CenterVoiceUiProbeTracker.selectCenterCandidate(nodes)

        assertEquals(first, second)
    }

    @Test
    fun fullscreenNodeIsNotSelected() {
        val candidate = CenterVoiceUiProbeTracker.selectCenterCandidate(
            listOf(node(boundsInScreen = "0,0,1080,2400"))
        )

        assertNull(candidate)
    }

    @Test
    fun smallBoundsOffsetStillMatchesCenterRegion() {
        val candidate = CenterVoiceUiProbeTracker.selectCenterCandidate(
            listOf(node(boundsInScreen = "196,805,899,1508"))
        )

        assertEquals("196,805,899,1508", candidate?.boundsInScreen)
    }

    @Test
    fun rollingOneAndThreeSecondRatesAreComputed() {
        val tracker = CenterVoiceUiProbeTracker()
        val base = node(childCount = 1)

        tracker.record(0L, "QUIET", listOf(base))
        tracker.record(500L, "QUIET", listOf(base.copy(childCount = 2)))
        tracker.record(1_500L, "QUIET", listOf(base.copy(childCount = 3)))

        val summary = tracker.summary()
        assertEquals(1.0, summary.changeRate1s, 0.01)
        assertEquals(2.0 / 3.0, summary.changeRate3s, 0.01)
    }

    @Test
    fun phaseMarkerIsWrittenToHistory() {
        val tracker = CenterVoiceUiProbeTracker()

        tracker.record(0L, "USER", listOf(node()))

        assertEquals("USER", tracker.summary().history.last().phase)
    }

    @Test
    fun averageRateIsSeparatedByPhase() {
        val tracker = CenterVoiceUiProbeTracker()
        val base = node(childCount = 1)

        tracker.record(0L, "QUIET", listOf(base))
        tracker.record(1_000L, "QUIET", listOf(base.copy(childCount = 2)))
        tracker.record(2_000L, "AI", listOf(base.copy(childCount = 3)))

        val summary = tracker.summary()
        assertTrue(summary.quietAverageRate > 0.0)
        assertTrue(summary.aiAverageRate > 0.0)
        assertEquals(0.0, summary.userAverageRate, 0.01)
    }

    private fun node(
        treePath: String = "0.1",
        className: String = "android.view.View",
        viewIdResourceName: String = "n/a",
        contentDescription: String = "n/a",
        stateDescription: String = "n/a",
        selected: Boolean = false,
        checked: Boolean = false,
        enabled: Boolean = true,
        focused: Boolean = false,
        clickable: Boolean = false,
        boundsInScreen: String = "189,796,892,1499",
        childCount: Int = 0,
        hasText: Boolean = false,
        textLength: Int = 0
    ) = SafeAccessibilityNodeMetadata(
        treePath = treePath,
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
