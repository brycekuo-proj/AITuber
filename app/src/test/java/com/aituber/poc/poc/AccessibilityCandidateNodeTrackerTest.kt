package com.aituber.poc.poc

import com.aituber.poc.state.SafeAccessibilityNodeMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityCandidateNodeTrackerTest {
    @Test
    fun stableIdentityIsDeterministicForSameMetadata() {
        val node = node(viewIdResourceName = "com.openai.chatgpt:id/voice", treePath = "0.1")

        assertEquals(
            AccessibilityCandidateNodeTracker.stableIdentity(node),
            AccessibilityCandidateNodeTracker.stableIdentity(node)
        )
    }

    @Test
    fun metadataChangeIncrementsChangeCounters() {
        val tracker = AccessibilityCandidateNodeTracker()
        val first = node(viewIdResourceName = "id/dynamic", childCount = 1)
        val second = first.copy(childCount = 2)

        tracker.record(1L, listOf(first))
        tracker.record(1001L, listOf(second))

        val candidate = tracker.topCandidates(1).first()
        assertEquals(1, candidate.metadataChangeCount)
        assertEquals(1, candidate.childCountChangeCount)
    }

    @Test
    fun boundsChangeIncrementsBoundsCounterForStableIdNode() {
        val tracker = AccessibilityCandidateNodeTracker()
        val first = node(viewIdResourceName = "id/dynamic", boundsInScreen = "100,900,200,1000")
        val second = first.copy(boundsInScreen = "110,900,210,1000")

        tracker.record(1L, listOf(first))
        tracker.record(1001L, listOf(second))

        val candidate = tracker.topCandidates(1).first()
        assertEquals(1, candidate.metadataChangeCount)
        assertEquals(1, candidate.boundsChangeCount)
    }

    @Test
    fun duplicateSnapshotDoesNotIncreaseChangeCount() {
        val tracker = AccessibilityCandidateNodeTracker()
        val node = node(viewIdResourceName = "id/static")

        tracker.record(1L, listOf(node))
        tracker.record(1001L, listOf(node))

        val candidate = tracker.topCandidates(1).first()
        assertEquals(0, candidate.metadataChangeCount)
    }

    @Test
    fun fullscreenRootNodeRanksBelowDynamicLocalNode() {
        val tracker = AccessibilityCandidateNodeTracker()
        val root = node(treePath = "0", boundsInScreen = "0,0,1080,2400", childCount = 10)
        val local = node(treePath = "0.3.1", viewIdResourceName = "id/local", boundsInScreen = "440,900,640,1100", childCount = 1)

        tracker.record(1L, listOf(root, local))
        tracker.record(1001L, listOf(root.copy(childCount = 11), local.copy(childCount = 2)))

        val top = tracker.topCandidates(2)
        assertEquals("CENTER", top.first().regionHint)
        assertTrue(top.first().stableId.contains("local"))
    }

    @Test
    fun rankingIsDeterministic() {
        val tracker = AccessibilityCandidateNodeTracker()
        val first = node(treePath = "0.1", viewIdResourceName = "id/a", boundsInScreen = "100,900,200,1000")
        val second = node(treePath = "0.2", viewIdResourceName = "id/b", boundsInScreen = "300,900,400,1000")

        tracker.record(1L, listOf(first, second))
        tracker.record(1001L, listOf(first.copy(childCount = 1), second.copy(childCount = 1)))

        val rankingA = tracker.topCandidates(2).map { it.stableId }
        val rankingB = tracker.topCandidates(2).map { it.stableId }

        assertEquals(rankingA, rankingB)
    }

    private fun node(
        treePath: String = "0",
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
