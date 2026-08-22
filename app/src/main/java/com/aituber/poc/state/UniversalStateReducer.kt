package com.aituber.poc.state

class UniversalStateReducer(
    private val speakingThreshold: Float = 0.03f,
    private val idleThreshold: Float = 0.015f,
    private val requiredSpeakingFrames: Int = 3,
    private val requiredIdleFrames: Int = 8
) {
    private var speakingFrames = 0
    private var idleFrames = 0
    private var currentState = UniversalAiState.UNKNOWN

    fun reduce(
        targetApp: String,
        detectionMethod: String,
        level: Float?,
        captureStatus: String
    ): UniversalStateSnapshot {
        currentState = when {
            level == null -> UniversalAiState.UNKNOWN
            level >= speakingThreshold -> {
                speakingFrames += 1
                idleFrames = 0
                if (speakingFrames >= requiredSpeakingFrames) UniversalAiState.SPEAKING else currentState
            }
            level <= idleThreshold -> {
                idleFrames += 1
                speakingFrames = 0
                if (idleFrames >= requiredIdleFrames) UniversalAiState.IDLE else currentState
            }
            else -> currentState
        }

        return UniversalStateSnapshot(
            targetApp = targetApp,
            detectionMethod = detectionMethod,
            state = currentState,
            audioLevel = level,
            captureStatus = captureStatus
        )
    }
}
