package com.aituber.poc.poc

import android.os.SystemClock
import com.aituber.poc.aiadapter.CaptureStatus
import com.aituber.poc.state.AccessibilityProbeSnapshot
import com.aituber.poc.state.CaptureStartupTraceSnapshot
import com.aituber.poc.state.MouthSignal
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

    /**
     * Updates arrive from the capture thread, the Visualizer callback and the main
     * thread at the same time. Each read-modify-write of [currentSnapshot] and the
     * resolver runs under this lock; listeners are notified outside it.
     */
    private val lock = Any()

    @Volatile
    private var currentSnapshot = UniversalStateSnapshot(
        targetApp = AiTargetApps.GENERIC_LABEL,
        detectionMethod = DetectionMethod.PLAYBACK_CAPTURE.label,
        state = UniversalAiState.UNKNOWN,
        audioLevel = null,
        captureStatus = CaptureStatus.NOT_STARTED
    )

    @Volatile
    private var accessibilityTestPhase = "UNMARKED"

    @Volatile
    private var playbackFallbackState = UniversalAiState.UNKNOWN

    /**
     * AITuber's own Claude chat, while its screen is open: it knows exactly when it
     * thinks and speaks, so it overrides the detectors. Null when no chat is open.
     */
    @Volatile
    private var directChatState: UniversalAiState? = null

    @Volatile
    private var directChatSignal: MouthSignal = MouthSignal.NONE

    fun current(): UniversalStateSnapshot = currentSnapshot

    fun currentAccessibilityTestPhase(): String = accessibilityTestPhase

    private inline fun mutate(block: (UniversalStateSnapshot) -> UniversalStateSnapshot) {
        val updated = synchronized(lock) {
            block(currentSnapshot).also { currentSnapshot = it }
        }
        listeners.forEach { listener -> listener(updated) }
    }

    fun markAccessibilityTestPhase(phase: String) {
        accessibilityTestPhase = phase
        mutate { current ->
            current.copy(accessibilityProbe = current.accessibilityProbe.copy(currentTestPhase = phase))
        }
    }

    fun update(snapshot: UniversalStateSnapshot) = mutate { current ->
        playbackFallbackState = snapshot.state
        resolve(
            snapshot.copy(
                playbackProbe = current.playbackProbe,
                accessibilityProbe = current.accessibilityProbe,
                visualMotion = current.visualMotion,
                visualizerProbe = current.visualizerProbe,
                captureStartupTrace = current.captureStartupTrace,
                overlayLifecycle = current.overlayLifecycle,
                stateTiming = current.stateTiming
            ),
            writer = "PlaybackCapture/Core update",
            playbackState = snapshot.state
        )
    }

    fun updatePlaybackProbe(snapshot: PlaybackProbeSnapshot) = mutate { current ->
        resolve(
            current.copy(playbackProbe = snapshot),
            writer = "Playback probe update",
            playbackState = playbackFallbackState
        )
    }

    /** [state] null ends the override (the chat screen closed). */
    fun updateDirectChat(state: UniversalAiState?, signal: MouthSignal) = mutate { current ->
        directChatState = state
        directChatSignal = if (state == null) MouthSignal.NONE else signal
        resolve(current, writer = "Claude chat", playbackState = playbackFallbackState)
    }

    fun updateAccessibilityProbe(snapshot: AccessibilityProbeSnapshot) = mutate { current ->
        current.copy(accessibilityProbe = snapshot)
    }

    fun updateVisualMotion(snapshot: VisualMotionSnapshot) = mutate { current ->
        current.copy(visualMotion = snapshot)
    }

    fun updateVisualizerProbe(snapshot: VisualizerProbeSnapshot) = mutate { current ->
        resolve(
            current.copy(visualizerProbe = snapshot),
            writer = "Visualizer probe update",
            playbackState = playbackFallbackState
        )
    }

    fun updateCaptureStartupTrace(snapshot: CaptureStartupTraceSnapshot) = mutate { current ->
        current.copy(captureStartupTrace = snapshot)
    }

    fun updateOverlayLifecycle(snapshot: OverlayLifecycleSnapshot) = mutate { current ->
        current.copy(overlayLifecycle = snapshot)
    }

    fun forceStateForDebug(state: UniversalAiState, writer: String = "Manual debug state") = mutate { current ->
        current.copy(state = state, speakingSignalSource = writer)
    }

    fun subscribe(listener: (UniversalStateSnapshot) -> Unit) {
        listeners.add(listener)
        listener(currentSnapshot)
    }

    fun unsubscribe(listener: (UniversalStateSnapshot) -> Unit) {
        listeners.remove(listener)
    }

    fun resetResolverForTests(nowMs: Long = elapsedRealtime()) {
        synchronized(lock) {
            resolver.reset(nowMs)
            playbackFallbackState = UniversalAiState.UNKNOWN
        }
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
        val chatState = directChatState
        if (chatState != null) {
            return snapshot.copy(
                state = chatState,
                speakingSignalSource = "AITuber Claude chat",
                stateTiming = result.timing,
                mouthSignal = directChatSignal
            )
        }
        return snapshot.copy(
            state = result.state,
            speakingSignalSource = when (result.authority) {
                UniversalStateResolver.StateAuthority.VISUALIZER -> "Visualizer(0) waveform RMS/Peak/Activity"
                UniversalStateResolver.StateAuthority.PLAYBACK_FALLBACK -> snapshot.speakingSignalSource
                UniversalStateResolver.StateAuthority.PLAYBACK_CAPTURE -> "Playback Capture (AI app media audio)"
                UniversalStateResolver.StateAuthority.NO_ACTIVE_SESSION -> "Voice session inactive"
            },
            stateTiming = result.timing,
            mouthSignal = mouthSignalFor(result.authority, snapshot)
        )
    }

    /** The mouth follows whichever source decided the state. */
    internal fun mouthSignalFor(
        authority: UniversalStateResolver.StateAuthority,
        snapshot: UniversalStateSnapshot
    ): MouthSignal = when (authority) {
        UniversalStateResolver.StateAuthority.PLAYBACK_FALLBACK,
        UniversalStateResolver.StateAuthority.PLAYBACK_CAPTURE -> snapshot.playbackSignal
        UniversalStateResolver.StateAuthority.VISUALIZER,
        UniversalStateResolver.StateAuthority.NO_ACTIVE_SESSION -> MouthSignal(
            source = MouthSignal.Source.VISUALIZER,
            available = snapshot.visualizerProbe.enabled == "YES" && snapshot.visualizerProbe.waveformCallbackCount > 0L,
            metrics = snapshot.visualizerProbe.currentMetrics
        )
    }

    private fun elapsedRealtime(): Long {
        return runCatching { SystemClock.elapsedRealtime() }.getOrElse { System.currentTimeMillis() }
    }
}
