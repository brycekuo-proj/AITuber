package com.aituber.poc.poc

import com.aituber.poc.state.AccessibilityCandidateNodeSummary
import com.aituber.poc.state.AccessibilityCandidateSnapshotChange
import com.aituber.poc.state.SafeAccessibilityNodeMetadata
import java.util.Locale
import kotlin.math.max

class AccessibilityCandidateNodeTracker(
    private val snapshotHistoryCapacity: Int = 30
) {
    private val trackedNodes = linkedMapOf<String, TrackedNode>()
    private val snapshotHistory = ArrayDeque<AccessibilityCandidateSnapshotChange>(snapshotHistoryCapacity)

    fun record(elapsedTimestampMs: Long, nodes: List<SafeAccessibilityNodeMetadata>) {
        nodes.forEach { node ->
            val identity = stableIdentity(node)
            val tracked = trackedNodes.getOrPut(identity) { TrackedNode(firstObservedElapsedMs = elapsedTimestampMs) }
            val change = tracked.record(elapsedTimestampMs, node)
            if (change.changedFields.isNotEmpty()) {
                recordSnapshotChange(
                    AccessibilityCandidateSnapshotChange(
                        elapsedTimestampMs = elapsedTimestampMs,
                        candidateId = identity.shortId(),
                        changedFields = change.changedFields.joinToString(",")
                    )
                )
            }
        }
    }

    fun trackedNodeCount(): Int = trackedNodes.size

    fun dynamicCandidateCount(): Int = trackedNodes.values.count { it.metadataChangeCount > 0 }

    fun topCandidates(limit: Int = 10): List<AccessibilityCandidateNodeSummary> {
        return trackedNodes.entries
            .map { (identity, tracked) -> tracked.summary(identity) }
            .sortedWith(
                compareByDescending<AccessibilityCandidateNodeSummary> { rankingScore(it) }
                    .thenBy { it.stableId }
            )
            .take(limit)
    }

    fun topCandidateSnapshotHistory(): List<AccessibilityCandidateSnapshotChange> = snapshotHistory.toList()

    private fun recordSnapshotChange(change: AccessibilityCandidateSnapshotChange) {
        if (snapshotHistory.size == snapshotHistoryCapacity) {
            snapshotHistory.removeFirst()
        }
        snapshotHistory.addLast(change)
    }

    private fun rankingScore(summary: AccessibilityCandidateNodeSummary): Double {
        val rootPenalty = if (summary.regionHint == "FULLSCREEN" || summary.stableId.contains("|path=0|")) 0.15 else 1.0
        val dynamicScore = summary.recentChangeRatePerSecond + summary.metadataChangeCount
        val persistenceScore = max(1, summary.observedCount).coerceAtMost(50) / 50.0
        return (dynamicScore * 2.0 + persistenceScore) * rootPenalty
    }

    companion object {
        fun stableIdentity(node: SafeAccessibilityNodeMetadata): String {
            return if (node.viewIdResourceName != "n/a" && node.viewIdResourceName.isNotBlank()) {
                "id=${node.viewIdResourceName}|class=${node.className}|path=${node.treePath}"
            } else {
                "id=none|class=${node.className}|bounds=${node.boundsInScreen}|path=${node.treePath}"
            }
        }

        fun regionHint(boundsInScreen: String): String {
            val bounds = parseBounds(boundsInScreen) ?: return "UNKNOWN"
            val width = bounds.right - bounds.left
            val height = bounds.bottom - bounds.top
            if (width <= 0 || height <= 0) return "UNKNOWN"
            val area = width * height
            val centerY = bounds.top + height / 2
            return when {
                area >= FULLSCREEN_AREA_THRESHOLD -> "FULLSCREEN"
                centerY < TOP_REGION_MAX_Y -> "TOP"
                centerY > BOTTOM_REGION_MIN_Y -> "BOTTOM"
                else -> "CENTER"
            }
        }

        private fun parseBounds(boundsInScreen: String): Bounds? {
            val parts = boundsInScreen.split(",").mapNotNull { it.toIntOrNull() }
            if (parts.size != 4) return null
            return Bounds(parts[0], parts[1], parts[2], parts[3])
        }

        private const val FULLSCREEN_AREA_THRESHOLD = 1_200_000
        private const val TOP_REGION_MAX_Y = 700
        private const val BOTTOM_REGION_MIN_Y = 1700
    }

    private data class Bounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    private data class NodeChange(val changedFields: List<String>)

    private class TrackedNode(
        private val firstObservedElapsedMs: Long
    ) {
        private var lastMetadata: SafeAccessibilityNodeMetadata? = null
        var observedCount = 0
            private set
        var metadataChangeCount = 0
            private set
        var boundsChangeCount = 0
            private set
        var childCountChangeCount = 0
            private set
        var stateFlagChangeCount = 0
            private set
        private var lastChangedElapsedMs: Long? = null

        fun record(elapsedTimestampMs: Long, node: SafeAccessibilityNodeMetadata): NodeChange {
            observedCount += 1
            val previous = lastMetadata
            lastMetadata = node
            if (previous == null) return NodeChange(emptyList())

            val changedFields = mutableListOf<String>()
            if (node.boundsInScreen != previous.boundsInScreen) {
                boundsChangeCount += 1
                changedFields.add("bounds")
            }
            if (node.childCount != previous.childCount) {
                childCountChangeCount += 1
                changedFields.add("childCount")
            }
            if (stateFlags(node) != stateFlags(previous)) {
                stateFlagChangeCount += 1
                changedFields.add("stateFlags")
            }
            if (contentMetadata(node) != contentMetadata(previous)) {
                changedFields.add("metadata")
            }
            if (changedFields.isNotEmpty()) {
                metadataChangeCount += 1
                lastChangedElapsedMs = elapsedTimestampMs
            }
            return NodeChange(changedFields)
        }

        fun summary(identity: String): AccessibilityCandidateNodeSummary {
            val metadata = lastMetadata ?: error("tracked node missing metadata")
            return AccessibilityCandidateNodeSummary(
                stableId = identity.shortId(),
                className = metadata.className,
                viewIdResourceName = metadata.viewIdResourceName,
                boundsInScreen = metadata.boundsInScreen,
                regionHint = regionHint(metadata.boundsInScreen),
                observedCount = observedCount,
                metadataChangeCount = metadataChangeCount,
                boundsChangeCount = boundsChangeCount,
                childCountChangeCount = childCountChangeCount,
                stateFlagChangeCount = stateFlagChangeCount,
                lastChangedElapsedMs = lastChangedElapsedMs,
                recentChangeRatePerSecond = changeRatePerSecond()
            )
        }

        private fun changeRatePerSecond(): Double {
            val lastChanged = lastChangedElapsedMs ?: return 0.0
            val windowMs = max(1L, lastChanged - firstObservedElapsedMs)
            return metadataChangeCount * 1000.0 / windowMs
        }

        private fun stateFlags(node: SafeAccessibilityNodeMetadata): String {
            return "${node.selected}|${node.checked}|${node.enabled}|${node.focused}|${node.clickable}"
        }

        private fun contentMetadata(node: SafeAccessibilityNodeMetadata): String {
            return listOf(
                node.className,
                node.viewIdResourceName,
                node.contentDescription != "n/a",
                node.stateDescription != "n/a",
                node.hasText,
                node.textLength
            ).joinToString("|")
        }
    }
}

private fun String.shortId(): String {
    val hash = Integer.toHexString(hashCode())
    val readable = replace("com.openai.chatgpt:id/", "")
        .replace("android.widget.", "")
        .replace("android.view.", "")
    return "${readable.take(48)}#$hash".lowercase(Locale.US)
}
