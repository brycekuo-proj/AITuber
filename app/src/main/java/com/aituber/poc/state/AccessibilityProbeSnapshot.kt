package com.aituber.poc.state

data class SafeAccessibilityNodeMetadata(
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
    val signatureTransitions: List<AccessibilitySignatureTransition>
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
            signatureTransitions = emptyList()
        )
    }
}
