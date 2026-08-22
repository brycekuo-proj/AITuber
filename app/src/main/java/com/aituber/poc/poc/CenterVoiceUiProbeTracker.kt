package com.aituber.poc.poc

import com.aituber.poc.state.CenterVoiceCandidateSample
import com.aituber.poc.state.SafeAccessibilityNodeMetadata
import kotlin.math.abs
import kotlin.math.hypot

class CenterVoiceUiProbeTracker(
    private val historyCapacity: Int = 200
) {
    private var previousCandidate: SafeAccessibilityNodeMetadata? = null
    private val changeTimestamps = ArrayDeque<Long>()
    private val history = ArrayDeque<CenterVoiceCandidateSample>(historyCapacity)
    private val phaseRates = linkedMapOf(
        "QUIET" to mutableListOf<Double>(),
        "USER" to mutableListOf(),
        "AI" to mutableListOf()
    )

    fun record(
        elapsedTimestampMs: Long,
        phase: String,
        nodes: List<SafeAccessibilityNodeMetadata>
    ) {
        val candidate = selectCenterCandidate(nodes)
        val previous = previousCandidate
        val boundsChanged = candidate != null && previous != null && candidate.boundsInScreen != previous.boundsInScreen
        val childCountChanged = candidate != null && previous != null && candidate.childCount != previous.childCount
        val metadataChanged = candidate != null && previous != null && metadataKey(candidate) != metadataKey(previous)
        val changed = boundsChanged || childCountChanged || metadataChanged
        if (changed) {
            changeTimestamps.addLast(elapsedTimestampMs)
        }
        while (changeTimestamps.isNotEmpty() && elapsedTimestampMs - changeTimestamps.first() > 3_000L) {
            changeTimestamps.removeFirst()
        }
        val rate1s = rate(elapsedTimestampMs, 1_000L)
        val rate3s = rate(elapsedTimestampMs, 3_000L)
        val sample = CenterVoiceCandidateSample(
            elapsedTimestampMs = elapsedTimestampMs,
            phase = phase,
            present = candidate != null,
            boundsInScreen = candidate?.boundsInScreen ?: "n/a",
            width = candidate?.bounds()?.width ?: 0,
            height = candidate?.bounds()?.height ?: 0,
            childCount = candidate?.childCount ?: 0,
            metadataChanged = metadataChanged,
            boundsChanged = boundsChanged,
            childCountChanged = childCountChanged,
            recentChangeCount = changeTimestamps.size,
            changeRate1s = rate1s,
            changeRate3s = rate3s
        )
        recordSample(sample)
        phaseRates[phase]?.add(rate3s)
        previousCandidate = candidate
    }

    fun summary(): CenterVoiceUiProbeSummary {
        val last = history.lastOrNull()
        return CenterVoiceUiProbeSummary(
            present = if (last?.present == true) "YES" else "NO",
            bounds = last?.boundsInScreen ?: "n/a",
            childCount = last?.childCount ?: 0,
            changeRate1s = last?.changeRate1s ?: 0.0,
            changeRate3s = last?.changeRate3s ?: 0.0,
            sampleCount = history.size,
            quietAverageRate = phaseRates.averageFor("QUIET"),
            userAverageRate = phaseRates.averageFor("USER"),
            aiAverageRate = phaseRates.averageFor("AI"),
            history = history.toList()
        )
    }

    private fun recordSample(sample: CenterVoiceCandidateSample) {
        if (history.size == historyCapacity) {
            history.removeFirst()
        }
        history.addLast(sample)
    }

    private fun rate(now: Long, windowMs: Long): Double {
        val count = changeTimestamps.count { now - it < windowMs }
        return count * 1000.0 / windowMs
    }

    companion object {
        fun selectCenterCandidate(nodes: List<SafeAccessibilityNodeMetadata>): SafeAccessibilityNodeMetadata? {
            return nodes.mapNotNull { node ->
                val bounds = node.bounds() ?: return@mapNotNull null
                if (bounds.width <= 0 || bounds.height <= 0) return@mapNotNull null
                if (bounds.area >= FULLSCREEN_AREA_THRESHOLD) return@mapNotNull null
                val centerX = bounds.left + bounds.width / 2.0
                val centerY = bounds.top + bounds.height / 2.0
                if (centerX !in SCREEN_CENTER_X_RANGE || centerY !in SCREEN_CENTER_Y_RANGE) return@mapNotNull null
                val classPenalty = if (node.className.endsWith("View")) 0.0 else 400.0
                val distance = hypot(centerX - OBSERVED_CENTER_X, centerY - OBSERVED_CENTER_Y)
                val areaDelta = abs(bounds.area - OBSERVED_CENTER_AREA) / 1000.0
                val aspectDelta = abs(bounds.width - bounds.height).toDouble()
                CandidateScore(node, classPenalty + distance + areaDelta + aspectDelta)
            }.sortedWith(compareBy<CandidateScore> { it.score }.thenBy { it.node.treePath }).firstOrNull()?.node
        }

        private val SCREEN_CENTER_X_RANGE = 250.0..850.0
        private val SCREEN_CENTER_Y_RANGE = 650.0..1650.0
        private const val OBSERVED_CENTER_X = 540.5
        private const val OBSERVED_CENTER_Y = 1147.5
        private const val OBSERVED_CENTER_AREA = 494_209
        private const val FULLSCREEN_AREA_THRESHOLD = 1_200_000
    }

    data class CenterVoiceUiProbeSummary(
        val present: String,
        val bounds: String,
        val childCount: Int,
        val changeRate1s: Double,
        val changeRate3s: Double,
        val sampleCount: Int,
        val quietAverageRate: Double,
        val userAverageRate: Double,
        val aiAverageRate: Double,
        val history: List<CenterVoiceCandidateSample>
    )

    private data class CandidateScore(
        val node: SafeAccessibilityNodeMetadata,
        val score: Double
    )
}

private data class CenterBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int = right - left
    val height: Int = bottom - top
    val area: Int = width * height
}

private fun SafeAccessibilityNodeMetadata.bounds(): CenterBounds? {
    val parts = boundsInScreen.split(",").mapNotNull { it.toIntOrNull() }
    if (parts.size != 4) return null
    return CenterBounds(parts[0], parts[1], parts[2], parts[3])
}

private fun metadataKey(node: SafeAccessibilityNodeMetadata): String {
    return listOf(
        node.className,
        node.viewIdResourceName,
        node.childCount,
        node.selected,
        node.checked,
        node.enabled,
        node.focused,
        node.clickable,
        node.contentDescription != "n/a",
        node.stateDescription != "n/a",
        node.hasText,
        node.textLength
    ).joinToString("|")
}

private fun Map<String, List<Double>>.averageFor(phase: String): Double {
    val values = get(phase).orEmpty()
    return if (values.isEmpty()) 0.0 else values.average()
}
