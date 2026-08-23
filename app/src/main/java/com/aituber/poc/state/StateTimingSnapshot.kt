package com.aituber.poc.state

data class StateTimingSnapshot(
    val visualizerDerivedState: String,
    val visualizerDerivedLastChangeTimeMs: Long?,
    val visualizerLastSpeakingTimeMs: Long?,
    val resolvedUniversalState: String,
    val universalStateLastChangeTimeMs: Long?,
    val stateAuthority: String,
    val derivedToUniversalDelayMs: Long?,
    val lastStateWriter: String,
    val lastStateSource: String,
    val speakingHoldRemainingMs: Long
) {
    companion object {
        fun empty() = StateTimingSnapshot(
            visualizerDerivedState = UniversalAiState.UNKNOWN.name,
            visualizerDerivedLastChangeTimeMs = null,
            visualizerLastSpeakingTimeMs = null,
            resolvedUniversalState = UniversalAiState.UNKNOWN.name,
            universalStateLastChangeTimeMs = null,
            stateAuthority = "UNKNOWN",
            derivedToUniversalDelayMs = null,
            lastStateWriter = "n/a",
            lastStateSource = "n/a",
            speakingHoldRemainingMs = 0L
        )
    }
}
