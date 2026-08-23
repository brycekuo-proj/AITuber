package com.aituber.poc.poc

import com.aituber.poc.state.StateTimingSnapshot
import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.VisualizerProbeSnapshot

class UniversalStateResolver(
    private val speakingHoldMs: Long = 100L
) {
    private var currentState = UniversalAiState.UNKNOWN
    private var lastUniversalChangeMs: Long? = null
    private var lastVisualizerDerivedState = UniversalAiState.UNKNOWN
    private var lastVisualizerDerivedChangeMs: Long? = null
    private var lastVisualizerSpeakingMs: Long? = null
    private var lastAuthoritativeTimestampMs: Long = Long.MIN_VALUE
    private var lastWriter = "n/a"
    private var lastSource = "n/a"

    fun resolve(input: Input): Result {
        val voiceSessionActive = input.voiceSessionActive
        val visualizerDerivedState = input.visualizerDerivedState
        val visualizerHealthy = input.visualizerAvailable && visualizerDerivedState != UniversalAiState.UNKNOWN

        if (visualizerDerivedState != lastVisualizerDerivedState) {
            lastVisualizerDerivedState = visualizerDerivedState
            lastVisualizerDerivedChangeMs = input.sourceTimestampMs
        }
        if (visualizerDerivedState == UniversalAiState.SPEAKING) {
            lastVisualizerSpeakingMs = input.sourceTimestampMs
        }

        val authority = when {
            !voiceSessionActive -> StateAuthority.NO_ACTIVE_SESSION
            visualizerHealthy -> StateAuthority.VISUALIZER
            else -> StateAuthority.PLAYBACK_FALLBACK
        }
        val holdRemaining = speakingHoldRemaining(input.nowMs)
        val resolved = when (authority) {
            StateAuthority.NO_ACTIVE_SESSION -> UniversalAiState.IDLE
            StateAuthority.VISUALIZER -> when (visualizerDerivedState) {
                UniversalAiState.SPEAKING -> UniversalAiState.SPEAKING
                UniversalAiState.IDLE -> if (holdRemaining > 0L) UniversalAiState.SPEAKING else UniversalAiState.IDLE
                UniversalAiState.UNKNOWN -> currentState
                else -> currentState
            }
            StateAuthority.PLAYBACK_FALLBACK -> input.playbackState
        }

        if (input.sourceTimestampMs >= lastAuthoritativeTimestampMs || authority == StateAuthority.VISUALIZER) {
            updateResolvedState(
                resolved = resolved,
                sourceTimestampMs = input.sourceTimestampMs,
                writer = input.writer,
                source = authority.name
            )
        }

        val timing = StateTimingSnapshot(
            visualizerDerivedState = visualizerDerivedState.name,
            visualizerDerivedLastChangeTimeMs = lastVisualizerDerivedChangeMs,
            visualizerLastSpeakingTimeMs = lastVisualizerSpeakingMs,
            resolvedUniversalState = currentState.name,
            universalStateLastChangeTimeMs = lastUniversalChangeMs,
            stateAuthority = authority.name,
            derivedToUniversalDelayMs = derivedToUniversalDelay(),
            lastStateWriter = lastWriter,
            lastStateSource = lastSource,
            speakingHoldMs = speakingHoldMs,
            speakingHoldRemainingMs = speakingHoldRemaining(input.nowMs)
        )
        return Result(
            state = currentState,
            authority = authority,
            timing = timing
        )
    }

    fun reset(nowMs: Long) {
        currentState = UniversalAiState.UNKNOWN
        lastUniversalChangeMs = nowMs
        lastVisualizerDerivedState = UniversalAiState.UNKNOWN
        lastVisualizerDerivedChangeMs = null
        lastVisualizerSpeakingMs = null
        lastAuthoritativeTimestampMs = Long.MIN_VALUE
        lastWriter = "reset"
        lastSource = "reset"
    }

    private fun updateResolvedState(
        resolved: UniversalAiState,
        sourceTimestampMs: Long,
        writer: String,
        source: String
    ) {
        if (resolved != currentState) {
            currentState = resolved
            lastUniversalChangeMs = sourceTimestampMs
        }
        lastAuthoritativeTimestampMs = sourceTimestampMs
        lastWriter = writer
        lastSource = source
    }

    private fun speakingHoldRemaining(nowMs: Long): Long {
        val lastSpeaking = lastVisualizerSpeakingMs ?: return 0L
        return (speakingHoldMs - (nowMs - lastSpeaking)).coerceAtLeast(0L)
    }

    private fun derivedToUniversalDelay(): Long? {
        val derived = lastVisualizerDerivedChangeMs ?: return null
        val universal = lastUniversalChangeMs ?: return null
        return (universal - derived).coerceAtLeast(0L)
    }

    data class Input(
        val nowMs: Long,
        val sourceTimestampMs: Long,
        val writer: String,
        val voiceSessionActive: Boolean,
        val visualizerAvailable: Boolean,
        val visualizerDerivedState: UniversalAiState,
        val playbackState: UniversalAiState
    )

    data class Result(
        val state: UniversalAiState,
        val authority: StateAuthority,
        val timing: StateTimingSnapshot
    )

    enum class StateAuthority {
        VISUALIZER,
        PLAYBACK_FALLBACK,
        NO_ACTIVE_SESSION
    }

    companion object {
        fun visualizerState(snapshot: VisualizerProbeSnapshot): UniversalAiState {
            return runCatching { UniversalAiState.valueOf(snapshot.derivedSpeaking) }
                .getOrDefault(UniversalAiState.UNKNOWN)
        }

        fun visualizerAvailable(snapshot: VisualizerProbeSnapshot): Boolean {
            return snapshot.enabled == "YES" &&
                snapshot.waveformCallbackCount > 0L &&
                visualizerState(snapshot) != UniversalAiState.UNKNOWN
        }
    }
}
