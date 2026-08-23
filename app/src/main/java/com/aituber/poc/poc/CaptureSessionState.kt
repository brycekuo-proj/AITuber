package com.aituber.poc.poc

import android.os.SystemClock
import com.aituber.poc.aiadapter.CaptureStatus
import com.aituber.poc.state.AccessibilityProbeSnapshot
import com.aituber.poc.state.CaptureStartupTraceSnapshot
import com.aituber.poc.state.OverlayLifecycleSnapshot
import com.aituber.poc.state.PlaybackProbeSnapshot
import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot
import com.aituber.poc.state.VisualMotionSnapshot
import com.aituber.poc.state.VisualizerProbeSnapshot
import java.util.concurrent.CopyOnWriteArraySet

object CaptureSessionState {
    private val listeners = CopyOnWriteArraySet<(UniversalStateSnapshot) -> Unit>()
    private val resolver = UniversalStateResolver()

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

    @Volatile
    private var playbackFallbackState = UniversalAiState.UNKNOWN

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
        playbackFallbackState = snapshot.state
        currentSnapshot = resolve(
            snapshot.copy(
                playbackProbe = currentSnapshot.playbackProbe,
                accessibilityProbe = currentSnapshot.accessibilityProbe,
                visualMotion = currentSnapshot.visualMotion,
                visualizerProbe = currentSnapshot.visualizerProbe,
                captureStartupTrace = currentSnapshot.captureStartupTrace,
                overlayLifecycle = currentSnapshot.overlayLifecycle,
                stateTiming = currentSnapshot.stateTiming
            ),
            writer = "PlaybackCapture/Core update",
            playbackState = snapshot.state
        )
        listeners.forEach { listener -> listener(currentSnapshot) }
    }

    fun updatePlaybackProbe(snapshot: PlaybackProbeSnapshot) {
        currentSnapshot = resolve(
            currentSnapshot.copy(playbackProbe = snapshot),
            writer = "Playback probe update",
            playbackState = playbackFallbackState
        )
        listeners.forEach { listener -> listener(currentSnapshot) }
    }

    fun updateAccessibilityProbe(snapshot: AccessibilityProbeSnapshot) {
        currentSnapshot = currentSnapshot.copy(accessibilityProbe = snapshot)
        listeners.forEach { listener -> listener(currentSnapshot) }
    }

    fun updateVisualMotion(snapshot: VisualMotionSnapshot) {
        currentSnapshot = currentSnapshot.copy(visualMotion = snapshot)
        listeners.forEach { listener -> listener(currentSnapshot) }
    }

    fun updateVisualizerProbe(snapshot: VisualizerProbeSnapshot) {
        currentSnapshot = resolve(
            currentSnapshot.copy(visualizerProbe = snapshot),
            writer = "Visualizer probe update",
            playbackState = playbackFallbackState
        )
        listeners.forEach { listener -> listener(currentSnapshot) }
    }

    fun updateCaptureStartupTrace(snapshot: CaptureStartupTraceSnapshot) {
        currentSnapshot = currentSnapshot.copy(
            captureStartupTrace = snapshot,
            stateTiming = currentSnapshot.stateTiming
        )
        listeners.forEach { listener -> listener(currentSnapshot) }
    }

    fun updateOverlayLifecycle(snapshot: OverlayLifecycleSnapshot) {
        currentSnapshot = currentSnapshot.copy(
            overlayLifecycle = snapshot,
            stateTiming = currentSnapshot.stateTiming
        )
        listeners.forEach { listener -> listener(currentSnapshot) }
    }

    fun subscribe(listener: (UniversalStateSnapshot) -> Unit) {
        listeners.add(listener)
        listener(currentSnapshot)
    }

    fun unsubscribe(listener: (UniversalStateSnapshot) -> Unit) {
        listeners.remove(listener)
    }

    fun resetResolverForTests(nowMs: Long = elapsedRealtime()) {
        resolver.reset(nowMs)
        playbackFallbackState = UniversalAiState.UNKNOWN
        currentSnapshot = currentSnapshot.copy(stateTiming = currentSnapshot.stateTiming)
    }

    private fun resolve(
        snapshot: UniversalStateSnapshot,
        writer: String,
        playbackState: UniversalAiState
    ): UniversalStateSnapshot {
        val now = elapsedRealtime()
        val result = resolver.resolve(
            UniversalStateResolver.Input(
                nowMs = now,
                sourceTimestampMs = now,
                writer = writer,
                voiceSessionActive = snapshot.playbackProbe.voiceSessionActive == "YES",
                visualizerAvailable = UniversalStateResolver.visualizerAvailable(snapshot.visualizerProbe),
                visualizerDerivedState = UniversalStateResolver.visualizerState(snapshot.visualizerProbe),
                playbackState = playbackState
            )
        )
        return snapshot.copy(
            state = result.state,
            speakingSignalSource = when (result.authority) {
                UniversalStateResolver.StateAuthority.VISUALIZER -> "Visualizer(0) waveform RMS/Peak/Activity"
                UniversalStateResolver.StateAuthority.PLAYBACK_FALLBACK -> snapshot.speakingSignalSource
                UniversalStateResolver.StateAuthority.NO_ACTIVE_SESSION -> "Voice session inactive"
            },
            stateTiming = result.timing
        )
    }

    private fun elapsedRealtime(): Long {
        return runCatching { SystemClock.elapsedRealtime() }.getOrElse { System.currentTimeMillis() }
    }
}
