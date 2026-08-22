package com.aituber.poc.poc

import com.aituber.poc.aiadapter.CaptureStatus
import com.aituber.poc.state.AccessibilityProbeSnapshot
import com.aituber.poc.state.PlaybackProbeSnapshot
import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot
import java.util.concurrent.CopyOnWriteArraySet

object CaptureSessionState {
    private val listeners = CopyOnWriteArraySet<(UniversalStateSnapshot) -> Unit>()

    @Volatile
    private var currentSnapshot = UniversalStateSnapshot(
        targetApp = ChatGptTarget.label,
        detectionMethod = DetectionMethod.PLAYBACK_CAPTURE.label,
        state = UniversalAiState.UNKNOWN,
        audioLevel = null,
        captureStatus = CaptureStatus.NOT_STARTED
    )

    @Volatile
    private var accessibilityTestPhase = "UNMARKED"

    fun current(): UniversalStateSnapshot = currentSnapshot

    fun currentAccessibilityTestPhase(): String = accessibilityTestPhase

    fun markAccessibilityTestPhase(phase: String) {
        accessibilityTestPhase = phase
        currentSnapshot = currentSnapshot.copy(
            accessibilityProbe = currentSnapshot.accessibilityProbe.copy(currentTestPhase = phase)
        )
        listeners.forEach { listener -> listener(currentSnapshot) }
    }

    fun update(snapshot: UniversalStateSnapshot) {
        currentSnapshot = snapshot.copy(
            playbackProbe = currentSnapshot.playbackProbe,
            accessibilityProbe = currentSnapshot.accessibilityProbe
        )
        listeners.forEach { listener -> listener(currentSnapshot) }
    }

    fun updatePlaybackProbe(snapshot: PlaybackProbeSnapshot) {
        currentSnapshot = currentSnapshot.copy(playbackProbe = snapshot)
        listeners.forEach { listener -> listener(currentSnapshot) }
    }

    fun updateAccessibilityProbe(snapshot: AccessibilityProbeSnapshot) {
        currentSnapshot = currentSnapshot.copy(accessibilityProbe = snapshot)
        listeners.forEach { listener -> listener(currentSnapshot) }
    }

    fun subscribe(listener: (UniversalStateSnapshot) -> Unit) {
        listeners.add(listener)
        listener(currentSnapshot)
    }

    fun unsubscribe(listener: (UniversalStateSnapshot) -> Unit) {
        listeners.remove(listener)
    }
}
