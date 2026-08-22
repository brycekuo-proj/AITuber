package com.aituber.poc.state

data class SafeAccessibilityNodeMetadata(
    val treePath: String,
    val className: String,
    val viewIdResourceName: String,
    val contentDescription: String,
    val stateDescription: String,
    val selected: Boolean,
    val checked: Boolean,
    val enabled: Boolean,
    val focused: Boolean,
    val clickable: Boolean,
    val boundsInScreen: String,
    val childCount: Int,
    val hasText: Boolean,
    val textLength: Int
)

data class AccessibilityCandidateNodeSummary(
    val stableId: String,
    val className: String,
    val viewIdResourceName: String,
    val boundsInScreen: String,
    val regionHint: String,
    val observedCount: Int,
    val metadataChangeCount: Int,
    val boundsChangeCount: Int,
    val childCountChangeCount: Int,
    val stateFlagChangeCount: Int,
    val lastChangedElapsedMs: Long?,
    val recentChangeRatePerSecond: Double
)

data class AccessibilityCandidateSnapshotChange(
    val elapsedTimestampMs: Long,
    val candidateId: String,
    val changedFields: String
)

data class CenterVoiceCandidateSample(
    val elapsedTimestampMs: Long,
    val phase: String,
    val present: Boolean,
    val boundsInScreen: String,
    val width: Int,
    val height: Int,
    val childCount: Int,
    val metadataChanged: Boolean,
    val boundsChanged: Boolean,
    val childCountChanged: Boolean,
    val recentChangeCount: Int,
    val changeRate1s: Double,
    val changeRate3s: Double
)

data class AccessibilityProbeEvent(
    val elapsedTimestampMs: Long,
    val eventType: String,
    val uiSignature: String,
    val candidateNodeCount: Int,
    val ignored: Boolean
)

data class AccessibilitySignatureTransition(
    val elapsedTimestampMs: Long,
    val oldSignature: String,
    val newSignature: String,
    val candidateNodeCount: Int,
    val eventType: String
)

data class AccessibilityProbeSnapshot(
    val enabled: String,
    val observedPackage: String,
    val eventCount: Int,
    val rootNodeAvailable: String,
    val voiceUiCandidateNodes: Int,
    val uiSignature: String,
    val uiSignatureChanged: String,
    val lastUiChangeElapsedMs: Long?,
    val candidateState: String,
    val lastEvents: List<AccessibilityProbeEvent>,
    val lastValidChatGptSignature: String,
    val validSignatureEventCount: Int,
    val signatureTransitionCount: Int,
    val ignoredEmptyEvents: Int,
    val duplicateSignatureEvents: Int,
    val signatureTransitions: List<AccessibilitySignatureTransition>,
    val trackedAccessibilityNodes: Int,
    val dynamicCandidateCount: Int,
    val topDynamicCandidateNodes: List<AccessibilityCandidateNodeSummary>,
    val topCandidateSnapshotHistory: List<AccessibilityCandidateSnapshotChange>,
    val centerCandidatePresent: String,
    val centerCandidateBounds: String,
    val centerChildCount: Int,
    val centerChangeRate1s: Double,
    val centerChangeRate3s: Double,
    val currentTestPhase: String,
    val quietAverageRate: Double,
    val userAverageRate: Double,
    val aiAverageRate: Double,
    val centerProbeSampleCount: Int,
    val centerHistory: List<CenterVoiceCandidateSample>
) {
    companion object {
        fun empty() = AccessibilityProbeSnapshot(
            enabled = "DISABLED",
            observedPackage = "n/a",
            eventCount = 0,
            rootNodeAvailable = "NO",
            voiceUiCandidateNodes = 0,
            uiSignature = "n/a",
            uiSignatureChanged = "NO",
            lastUiChangeElapsedMs = null,
            candidateState = "UNKNOWN",
            lastEvents = emptyList(),
            lastValidChatGptSignature = "n/a",
            validSignatureEventCount = 0,
            signatureTransitionCount = 0,
            ignoredEmptyEvents = 0,
            duplicateSignatureEvents = 0,
            signatureTransitions = emptyList(),
            trackedAccessibilityNodes = 0,
            dynamicCandidateCount = 0,
            topDynamicCandidateNodes = emptyList(),
            topCandidateSnapshotHistory = emptyList(),
            centerCandidatePresent = "NO",
            centerCandidateBounds = "n/a",
            centerChildCount = 0,
            centerChangeRate1s = 0.0,
            centerChangeRate3s = 0.0,
            currentTestPhase = "UNMARKED",
            quietAverageRate = 0.0,
            userAverageRate = 0.0,
            aiAverageRate = 0.0,
            centerProbeSampleCount = 0,
            centerHistory = emptyList()
        )
    }
}
