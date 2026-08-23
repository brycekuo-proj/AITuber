package com.aituber.poc.character

import android.os.SystemClock

object CharacterDiagnostics {
    @Volatile
    private var current = CharacterDiagnosticsSnapshot.empty()

    @Synchronized
    fun recordRequestedMode(
        requestedMode: CharacterMode,
        overlayRunning: Boolean
    ) {
        current = current.copy(
            characterMode = requestedMode.name,
            activeCharacterAdapter = if (overlayRunning) current.activeCharacterAdapter else "none",
            live2dLifecycleState = if (requestedMode == CharacterMode.LIVE2D) {
                if (overlayRunning) "REQUESTED" else "REQUESTED_OVERLAY_DISABLED"
            } else {
                "DISABLED"
            },
            live2dFallbackReason = if (requestedMode == CharacterMode.LIVE2D) {
                if (overlayRunning) "WAITING_FOR_SURFACE" else "WAITING_FOR_OVERLAY"
            } else {
                "n/a"
            }
        )
    }

    @Synchronized
    fun configure(
        requestedMode: CharacterMode,
        activeAdapterId: String,
        fallbackReason: String,
        live2dLifecycleState: String = if (requestedMode == CharacterMode.LIVE2D) "WAITING_FOR_SURFACE" else "DISABLED"
    ) {
        current = current.copy(
            characterMode = requestedMode.name,
            activeCharacterAdapter = activeAdapterId,
            live2dFallbackReason = fallbackReason,
            live2dLifecycleState = live2dLifecycleState
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
            live2dRuntimeLoaded = if (snapshot.runtimeLoaded) "YES" else "NO",
            live2dCoreLoaded = if (snapshot.coreLoaded) "YES" else "NO",
            live2dModelLoaded = if (snapshot.modelLoaded) "YES" else "NO",
            live2dModelName = snapshot.modelName,
            live2dMouthParameterId = snapshot.mouthParameterId,
            live2dMouthParameterValue = snapshot.mouthParameterValue?.toDouble(),
            live2dInputMouthOpen = snapshot.inputMouthOpen.toDouble(),
            live2dRenderFps = snapshot.renderFps,
            live2dNativeFrameCount = snapshot.nativeFrameCount,
            live2dSurfaceWidth = snapshot.surfaceWidth,
            live2dSurfaceHeight = snapshot.surfaceHeight,
            live2dTextureCount = snapshot.textureCount,
            live2dTexturesLoaded = snapshot.texturesLoaded,
            live2dLastTexturePath = snapshot.lastTexturePath,
            live2dLastTextureError = snapshot.lastTextureError,
            live2dGlTextureIds = snapshot.glTextureIds,
            live2dPoseFile = snapshot.poseFile,
            live2dPoseLoaded = if (snapshot.poseLoaded) "YES" else "NO",
            live2dPoseActive = if (snapshot.poseActive) "YES" else "NO",
            live2dFallbackReason = snapshot.fallbackReason,
            live2dMouthParameterStatus = snapshot.mouthParameterStatus,
            live2dLastError = snapshot.lastError,
            live2dLifecycleState = snapshot.lifecycleState
        )
    }

    @Synchronized
    fun recordLive2DDisplayTransform(
        displayScale: Float,
        offsetX: Int,
        offsetY: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        anchor: String,
        topSafeMarginFraction: Float,
        topSafeMarginPx: Int,
        bottomSafeZoneFraction: Float,
        bottomSafeZonePx: Int
    ) {
        current = current.copy(
            live2dDisplayScale = displayScale.toDouble(),
            live2dDisplayOffsetX = offsetX,
            live2dDisplayOffsetY = offsetY,
            live2dViewportWidth = viewportWidth,
            live2dViewportHeight = viewportHeight,
            live2dAnchor = anchor,
            live2dTopSafeMarginPercent = topSafeMarginFraction.toDouble() * 100.0,
            live2dTopSafeMarginPx = topSafeMarginPx,
            live2dBottomSafeZonePercent = bottomSafeZoneFraction.toDouble() * 100.0,
            live2dBottomSafeZonePx = bottomSafeZonePx
        )
    }

    @Synchronized
    fun reset(requestedMode: CharacterMode = CharacterMode.MINIMAL_MOUTH) {
        current = CharacterDiagnosticsSnapshot.empty().copy(
            characterMode = requestedMode.name,
            activeCharacterAdapter = "none",
            live2dLifecycleState = if (requestedMode == CharacterMode.LIVE2D) {
                "REQUESTED_OVERLAY_DISABLED"
            } else {
                "DISABLED"
            },
            live2dFallbackReason = if (requestedMode == CharacterMode.LIVE2D) {
                "WAITING_FOR_OVERLAY"
            } else {
                "LIVE2D_DISABLED"
            }
        )
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
    val live2dRuntimeLoaded: String,
    val live2dCoreLoaded: String,
    val live2dModelLoaded: String,
    val live2dModelName: String,
    val live2dMouthParameterId: String,
    val live2dMouthParameterValue: Double?,
    val live2dInputMouthOpen: Double,
    val live2dRenderFps: Double,
    val live2dNativeFrameCount: Long,
    val live2dSurfaceWidth: Int,
    val live2dSurfaceHeight: Int,
    val live2dDisplayScale: Double,
    val live2dDisplayOffsetX: Int,
    val live2dDisplayOffsetY: Int,
    val live2dViewportWidth: Int,
    val live2dViewportHeight: Int,
    val live2dAnchor: String,
    val live2dTopSafeMarginPercent: Double,
    val live2dTopSafeMarginPx: Int,
    val live2dBottomSafeZonePercent: Double,
    val live2dBottomSafeZonePx: Int,
    val live2dTextureCount: Int,
    val live2dTexturesLoaded: Int,
    val live2dLastTexturePath: String,
    val live2dLastTextureError: String,
    val live2dGlTextureIds: String,
    val live2dPoseFile: String,
    val live2dPoseLoaded: String,
    val live2dPoseActive: String,
    val live2dLifecycleState: String,
    val live2dFallbackReason: String,
    val live2dMouthParameterStatus: String,
    val live2dLastError: String,
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
            live2dRuntimeLoaded = "NO",
            live2dCoreLoaded = "NO",
            live2dModelLoaded = "NO",
            live2dModelName = "n/a",
            live2dMouthParameterId = "ParamMouthOpenY",
            live2dMouthParameterValue = null,
            live2dInputMouthOpen = 0.0,
            live2dRenderFps = 0.0,
            live2dNativeFrameCount = 0L,
            live2dSurfaceWidth = 0,
            live2dSurfaceHeight = 0,
            live2dDisplayScale = 0.0,
            live2dDisplayOffsetX = 0,
            live2dDisplayOffsetY = 0,
            live2dViewportWidth = 0,
            live2dViewportHeight = 0,
            live2dAnchor = "n/a",
            live2dTopSafeMarginPercent = 0.0,
            live2dTopSafeMarginPx = 0,
            live2dBottomSafeZonePercent = 0.0,
            live2dBottomSafeZonePx = 0,
            live2dTextureCount = 0,
            live2dTexturesLoaded = 0,
            live2dLastTexturePath = "n/a",
            live2dLastTextureError = "n/a",
            live2dGlTextureIds = "[]",
            live2dPoseFile = "n/a",
            live2dPoseLoaded = "NO",
            live2dPoseActive = "NO",
            live2dLifecycleState = "DISABLED",
            live2dFallbackReason = "LIVE2D_DISABLED",
            live2dMouthParameterStatus = "UNAVAILABLE",
            live2dLastError = "n/a",
            lastFrameTimestampMs = null
        )
    }
}

data class Live2DDiagnosticsSnapshot(
    val available: Boolean,
    val runtimeLoaded: Boolean = available,
    val coreLoaded: Boolean = available,
    val modelLoaded: Boolean,
    val modelName: String,
    val mouthParameterId: String,
    val mouthParameterValue: Float?,
    val inputMouthOpen: Float = mouthParameterValue ?: 0f,
    val renderFps: Double,
    val nativeFrameCount: Long = 0L,
    val surfaceWidth: Int = 0,
    val surfaceHeight: Int = 0,
    val textureCount: Int = 0,
    val texturesLoaded: Int = 0,
    val lastTexturePath: String = "n/a",
    val lastTextureError: String = "n/a",
    val glTextureIds: String = "[]",
    val poseFile: String = "n/a",
    val poseLoaded: Boolean = false,
    val poseActive: Boolean = false,
    val lifecycleState: String = "DISABLED",
    val fallbackReason: String,
    val mouthParameterStatus: String,
    val lastError: String = "n/a"
)

enum class CharacterMode {
    MINIMAL_MOUTH,
    LIVE2D
}
