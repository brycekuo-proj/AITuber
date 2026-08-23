package com.aituber.poc.character

import android.os.SystemClock

object CharacterDiagnostics {
    @Volatile
    private var current = CharacterDiagnosticsSnapshot.empty()

    @Synchronized
    fun configure(
        requestedMode: CharacterMode,
        activeAdapterId: String,
        fallbackReason: String
    ) {
        current = current.copy(
            characterMode = requestedMode.name,
            activeCharacterAdapter = activeAdapterId,
            live2dFallbackReason = fallbackReason
        )
    }

    @Synchronized
    fun recordFrame(
        adapterId: String,
        frame: CharacterParameterFrame,
        mouthOutput: Float
    ) {
        current = current.copy(
            activeCharacterAdapter = adapterId,
            characterFrameCount = current.characterFrameCount + 1,
            mouthParameterInput = frame.mouthOpen.toDouble(),
            mouthParameterOutput = mouthOutput.toDouble(),
            lastFrameTimestampMs = nowMs()
        )
    }

    @Synchronized
    fun recordLive2D(snapshot: Live2DDiagnosticsSnapshot) {
        current = current.copy(
            live2dAvailable = if (snapshot.available) "YES" else "NO",
            live2dModelLoaded = if (snapshot.modelLoaded) "YES" else "NO",
            live2dModelName = snapshot.modelName,
            live2dMouthParameterId = snapshot.mouthParameterId,
            live2dMouthParameterValue = snapshot.mouthParameterValue?.toDouble(),
            live2dRenderFps = snapshot.renderFps,
            live2dFallbackReason = snapshot.fallbackReason,
            live2dMouthParameterStatus = snapshot.mouthParameterStatus
        )
    }

    @Synchronized
    fun reset() {
        current = CharacterDiagnosticsSnapshot.empty()
    }

    fun snapshot(): CharacterDiagnosticsSnapshot = current

    private fun nowMs(): Long {
        return runCatching { SystemClock.elapsedRealtime() }.getOrElse { System.currentTimeMillis() }
    }
}

data class CharacterDiagnosticsSnapshot(
    val characterMode: String,
    val activeCharacterAdapter: String,
    val characterFrameCount: Long,
    val mouthParameterInput: Double,
    val mouthParameterOutput: Double,
    val live2dAvailable: String,
    val live2dModelLoaded: String,
    val live2dModelName: String,
    val live2dMouthParameterId: String,
    val live2dMouthParameterValue: Double?,
    val live2dRenderFps: Double,
    val live2dFallbackReason: String,
    val live2dMouthParameterStatus: String,
    val lastFrameTimestampMs: Long?
) {
    companion object {
        fun empty() = CharacterDiagnosticsSnapshot(
            characterMode = CharacterMode.MINIMAL_MOUTH.name,
            activeCharacterAdapter = "minimal-mouth-overlay",
            characterFrameCount = 0L,
            mouthParameterInput = 0.0,
            mouthParameterOutput = 0.0,
            live2dAvailable = "NO",
            live2dModelLoaded = "NO",
            live2dModelName = "n/a",
            live2dMouthParameterId = "ParamMouthOpenY",
            live2dMouthParameterValue = null,
            live2dRenderFps = 0.0,
            live2dFallbackReason = "LIVE2D_DISABLED",
            live2dMouthParameterStatus = "UNAVAILABLE",
            lastFrameTimestampMs = null
        )
    }
}

data class Live2DDiagnosticsSnapshot(
    val available: Boolean,
    val modelLoaded: Boolean,
    val modelName: String,
    val mouthParameterId: String,
    val mouthParameterValue: Float?,
    val renderFps: Double,
    val fallbackReason: String,
    val mouthParameterStatus: String
)

enum class CharacterMode {
    MINIMAL_MOUTH,
    LIVE2D
}
