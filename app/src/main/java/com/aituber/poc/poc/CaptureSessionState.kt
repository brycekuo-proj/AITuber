package com.aituber.poc.poc

import com.aituber.poc.aiadapter.CaptureStatus
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

    fun current(): UniversalStateSnapshot = currentSnapshot

    fun update(snapshot: UniversalStateSnapshot) {
        currentSnapshot = snapshot.copy(playbackProbe = currentSnapshot.playbackProbe)
        listeners.forEach { listener -> listener(currentSnapshot) }
    }

    fun updatePlaybackProbe(snapshot: PlaybackProbeSnapshot) {
        currentSnapshot = currentSnapshot.copy(playbackProbe = snapshot)
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
