package com.aituber.poc.poc

import com.aituber.poc.state.AccessibilityProbeEvent
import com.aituber.poc.state.AccessibilityProbeSnapshot
import com.aituber.poc.state.AccessibilitySignatureTransition
import com.aituber.poc.state.SafeAccessibilityNodeMetadata

class AccessibilityProbeLogger(
    private val targetPackageName: String = ChatGptTarget.packageName,
    private val eventCapacity: Int = 100,
    private val transitionCapacity: Int = 50
) {
    private var observedPackage = "n/a"
    private var eventCount = 0
    private var rootNodeAvailable = "NO"
    private var voiceUiCandidateNodes = 0
    private var currentUiSignature = "n/a"
    private var currentUiSignatureChanged = "NO"
    private var lastUiChangeElapsedMs: Long? = null
    private var lastValidChatGptSignature = "n/a"
    private var validSignatureEventCount = 0
    private var signatureTransitionCount = 0
    private var ignoredEmptyEvents = 0
    private var duplicateSignatureEvents = 0
    private val lastEvents = ArrayDeque<AccessibilityProbeEvent>(eventCapacity)
    private val signatureTransitions = ArrayDeque<AccessibilitySignatureTransition>(transitionCapacity)

    fun record(
        elapsedTimestampMs: Long,
        packageName: String,
        eventType: String,
        rootAvailable: Boolean,
        nodes: List<SafeAccessibilityNodeMetadata>
    ): AccessibilityProbeSnapshot {
        observedPackage = packageName
        if (packageName != targetPackageName) {
            return snapshot()
        }

        eventCount += 1
        rootNodeAvailable = if (rootAvailable) "YES" else "NO"
        voiceUiCandidateNodes = nodes.size
        val signature = AccessibilityUiSignature.signature(nodes)
        val valid = rootAvailable && nodes.isNotEmpty() && signature != "empty"
        currentUiSignatureChanged = "NO"

        if (!valid) {
            ignoredEmptyEvents += 1
            recordEvent(
                AccessibilityProbeEvent(
                    elapsedTimestampMs = elapsedTimestampMs,
                    eventType = eventType,
                    uiSignature = signature,
                    candidateNodeCount = nodes.size,
                    ignored = true
                )
            )
            return snapshot()
        }

        validSignatureEventCount += 1
        currentUiSignature = signature
        recordEvent(
            AccessibilityProbeEvent(
                elapsedTimestampMs = elapsedTimestampMs,
                eventType = eventType,
                uiSignature = signature,
                candidateNodeCount = nodes.size,
                ignored = false
            )
        )

        if (lastValidChatGptSignature == "n/a") {
            recordTransition(elapsedTimestampMs, "n/a", signature, nodes.size, eventType)
            lastValidChatGptSignature = signature
            currentUiSignatureChanged = "YES"
            lastUiChangeElapsedMs = elapsedTimestampMs
        } else if (signature != lastValidChatGptSignature) {
            val oldSignature = lastValidChatGptSignature
            recordTransition(elapsedTimestampMs, oldSignature, signature, nodes.size, eventType)
            lastValidChatGptSignature = signature
            currentUiSignatureChanged = "YES"
            lastUiChangeElapsedMs = elapsedTimestampMs
        } else {
            duplicateSignatureEvents += 1
        }

        return snapshot()
    }

    fun snapshot(enabled: String = "ENABLED"): AccessibilityProbeSnapshot {
        return AccessibilityProbeSnapshot.empty().copy(
            enabled = enabled,
            observedPackage = observedPackage,
            eventCount = eventCount,
            rootNodeAvailable = rootNodeAvailable,
            voiceUiCandidateNodes = voiceUiCandidateNodes,
            uiSignature = currentUiSignature,
            uiSignatureChanged = currentUiSignatureChanged,
            lastUiChangeElapsedMs = lastUiChangeElapsedMs,
            candidateState = "UNKNOWN",
            lastEvents = lastEvents.toList(),
            lastValidChatGptSignature = lastValidChatGptSignature,
            validSignatureEventCount = validSignatureEventCount,
            signatureTransitionCount = signatureTransitionCount,
            ignoredEmptyEvents = ignoredEmptyEvents,
            duplicateSignatureEvents = duplicateSignatureEvents,
            signatureTransitions = signatureTransitions.toList()
        )
    }

    private fun recordEvent(event: AccessibilityProbeEvent) {
        if (lastEvents.size == eventCapacity) {
            lastEvents.removeFirst()
        }
        lastEvents.addLast(event)
    }

    private fun recordTransition(
        elapsedTimestampMs: Long,
        oldSignature: String,
        newSignature: String,
        nodeCount: Int,
        eventType: String
    ) {
        if (signatureTransitions.size == transitionCapacity) {
            signatureTransitions.removeFirst()
        }
        signatureTransitionCount += 1
        signatureTransitions.addLast(
            AccessibilitySignatureTransition(
                elapsedTimestampMs = elapsedTimestampMs,
                oldSignature = oldSignature,
                newSignature = newSignature,
                candidateNodeCount = nodeCount,
                eventType = eventType
            )
        )
    }
}
