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
            live2dLeftEyeParameterStatus = snapshot.leftEyeParameterStatus,
            live2dRightEyeParameterStatus = snapshot.rightEyeParameterStatus,
            live2dLeftEyeOpen = snapshot.leftEyeOpen?.toDouble(),
            live2dRightEyeOpen = snapshot.rightEyeOpen?.toDouble(),
            live2dBreathParameterStatus = snapshot.breathParameterStatus,
            live2dBreathNormalized = snapshot.breathNormalized?.toDouble(),
            live2dBreathAppliedValue = snapshot.breathAppliedValue?.toDouble(),
            live2dBreathMin = snapshot.breathMin?.toDouble(),
            live2dBreathMax = snapshot.breathMax?.toDouble(),
            live2dBreathDefault = snapshot.breathDefault?.toDouble(),
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
            live2dIdleMotionEnabled = if (snapshot.idleMotionEnabled) "YES" else "NO",
            live2dIdleMotionStatus = snapshot.idleMotionStatus,
            live2dIdleMotionGroup = snapshot.idleMotionGroup,
            live2dIdleMotionFile = snapshot.idleMotionFile,
            live2dIdleMotionIndex = snapshot.idleMotionIndex,
            live2dIdleMotionPlaying = if (snapshot.idleMotionPlaying) "YES" else "NO",
            live2dIdleMotionCount = snapshot.idleMotionCount,
            live2dIdleMotionPlayCount = snapshot.idleMotionPlayCount,
            live2dLastIdleMotionError = snapshot.lastIdleMotionError,
            live2dFallbackReason = snapshot.fallbackReason,
            live2dMouthParameterStatus = snapshot.mouthParameterStatus,
            live2dLastError = snapshot.lastError,
            live2dLifecycleState = snapshot.lifecycleState
        )
    }

    @Synchronized
    fun recordLive2DDisplayTransform(
        displayScale: Float,
        minScale: Float,
        defaultScale: Float,
        maxScale: Float,
        visibleHeightPercent: Double,
        offsetX: Int,
        offsetY: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        anchor: String,
        rightMarginFraction: Float,
        rightMarginPx: Int,
        topSafeMarginFraction: Float,
        topSafeMarginPx: Int,
        bottomSafeZoneFraction: Float,
        bottomSafeZonePx: Int,
        windowType: Int,
        windowAlpha: Float,
        flagNotTouchable: Boolean,
        flagNotFocusable: Boolean,
        dragEnabled: Boolean,
        dragging: Boolean,
        windowTouchable: Boolean,
        positionSaved: Boolean
    ) {
        current = current.copy(
            live2dDisplayScale = displayScale.toDouble(),
            live2dMinScale = minScale.toDouble(),
            live2dDefaultScale = defaultScale.toDouble(),
            live2dMaxScale = maxScale.toDouble(),
            live2dVisibleHeightPercent = visibleHeightPercent,
            live2dDisplayOffsetX = offsetX,
            live2dDisplayOffsetY = offsetY,
            live2dViewportWidth = viewportWidth,
            live2dViewportHeight = viewportHeight,
            live2dAnchor = anchor,
            live2dRightMarginPercent = rightMarginFraction.toDouble() * 100.0,
            live2dRightMarginPx = rightMarginPx,
            live2dTopSafeMarginPercent = topSafeMarginFraction.toDouble() * 100.0,
            live2dTopSafeMarginPx = topSafeMarginPx,
            live2dBottomSafeZonePercent = bottomSafeZoneFraction.toDouble() * 100.0,
            live2dBottomSafeZonePx = bottomSafeZonePx,
            live2dOverlayWindowType = windowType.toString(),
            live2dOverlayWindowAlpha = windowAlpha.toDouble(),
            live2dFlagNotTouchable = if (flagNotTouchable) "YES" else "NO",
            live2dFlagNotFocusable = if (flagNotFocusable) "YES" else "NO",
            live2dDragEnabled = if (dragEnabled) "YES" else "NO",
            live2dDragging = if (dragging) "YES" else "NO",
            live2dWindowX = offsetX,
            live2dWindowY = offsetY,
            live2dWindowTouchable = if (windowTouchable) "YES" else "NO",
            live2dPositionSaved = if (positionSaved) "YES" else "NO"
        )
    }

    @Synchronized
    fun recordLive2DDragState(
        dragging: Boolean,
        x: Int,
        y: Int,
        positionSaved: Boolean
    ) {
        current = current.copy(
            live2dDragging = if (dragging) "YES" else "NO",
            live2dWindowX = x,
            live2dWindowY = y,
            live2dDisplayOffsetX = x,
            live2dDisplayOffsetY = y,
            live2dPositionSaved = if (positionSaved) "YES" else "NO"
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
    val live2dLeftEyeParameterStatus: String,
    val live2dRightEyeParameterStatus: String,
    val live2dLeftEyeOpen: Double?,
    val live2dRightEyeOpen: Double?,
    val live2dBreathParameterStatus: String,
    val live2dBreathNormalized: Double?,
    val live2dBreathAppliedValue: Double?,
    val live2dBreathMin: Double?,
    val live2dBreathMax: Double?,
    val live2dBreathDefault: Double?,
    val live2dRenderFps: Double,
    val live2dNativeFrameCount: Long,
    val live2dSurfaceWidth: Int,
    val live2dSurfaceHeight: Int,
    val live2dDisplayScale: Double,
    val live2dMinScale: Double,
    val live2dDefaultScale: Double,
    val live2dMaxScale: Double,
    val live2dVisibleHeightPercent: Double,
    val live2dDisplayOffsetX: Int,
    val live2dDisplayOffsetY: Int,
    val live2dViewportWidth: Int,
    val live2dViewportHeight: Int,
    val live2dAnchor: String,
    val live2dRightMarginPercent: Double,
    val live2dRightMarginPx: Int,
    val live2dTopSafeMarginPercent: Double,
    val live2dTopSafeMarginPx: Int,
    val live2dBottomSafeZonePercent: Double,
    val live2dBottomSafeZonePx: Int,
    val live2dOverlayWindowType: String,
    val live2dOverlayWindowAlpha: Double,
    val live2dFlagNotTouchable: String,
    val live2dFlagNotFocusable: String,
    val live2dDragEnabled: String,
    val live2dDragging: String,
    val live2dWindowX: Int,
    val live2dWindowY: Int,
    val live2dWindowTouchable: String,
    val live2dPositionSaved: String,
    val live2dTextureCount: Int,
    val live2dTexturesLoaded: Int,
    val live2dLastTexturePath: String,
    val live2dLastTextureError: String,
    val live2dGlTextureIds: String,
    val live2dPoseFile: String,
    val live2dPoseLoaded: String,
    val live2dPoseActive: String,
    val live2dIdleMotionEnabled: String,
    val live2dIdleMotionStatus: String,
    val live2dIdleMotionGroup: String,
    val live2dIdleMotionFile: String,
    val live2dIdleMotionIndex: Int,
    val live2dIdleMotionPlaying: String,
    val live2dIdleMotionCount: Int,
    val live2dIdleMotionPlayCount: Long,
    val live2dLastIdleMotionError: String,
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
            live2dLeftEyeParameterStatus = "UNAVAILABLE",
            live2dRightEyeParameterStatus = "UNAVAILABLE",
            live2dLeftEyeOpen = null,
            live2dRightEyeOpen = null,
            live2dBreathParameterStatus = "UNAVAILABLE",
            live2dBreathNormalized = null,
            live2dBreathAppliedValue = null,
            live2dBreathMin = null,
            live2dBreathMax = null,
            live2dBreathDefault = null,
            live2dRenderFps = 0.0,
            live2dNativeFrameCount = 0L,
            live2dSurfaceWidth = 0,
            live2dSurfaceHeight = 0,
            live2dDisplayScale = 0.0,
            live2dMinScale = 0.0,
            live2dDefaultScale = 0.0,
            live2dMaxScale = 0.0,
            live2dVisibleHeightPercent = 0.0,
            live2dDisplayOffsetX = 0,
            live2dDisplayOffsetY = 0,
            live2dViewportWidth = 0,
            live2dViewportHeight = 0,
            live2dAnchor = "n/a",
            live2dRightMarginPercent = 0.0,
            live2dRightMarginPx = 0,
            live2dTopSafeMarginPercent = 0.0,
            live2dTopSafeMarginPx = 0,
            live2dBottomSafeZonePercent = 0.0,
            live2dBottomSafeZonePx = 0,
            live2dOverlayWindowType = "n/a",
            live2dOverlayWindowAlpha = 1.0,
            live2dFlagNotTouchable = "n/a",
            live2dFlagNotFocusable = "n/a",
            live2dDragEnabled = "n/a",
            live2dDragging = "NO",
            live2dWindowX = 0,
            live2dWindowY = 0,
            live2dWindowTouchable = "n/a",
            live2dPositionSaved = "NO",
            live2dTextureCount = 0,
            live2dTexturesLoaded = 0,
            live2dLastTexturePath = "n/a",
            live2dLastTextureError = "n/a",
            live2dGlTextureIds = "[]",
            live2dPoseFile = "n/a",
            live2dPoseLoaded = "NO",
            live2dPoseActive = "NO",
            live2dIdleMotionEnabled = "NO",
            live2dIdleMotionStatus = "UNAVAILABLE",
            live2dIdleMotionGroup = "Idle",
            live2dIdleMotionFile = "n/a",
            live2dIdleMotionIndex = -1,
            live2dIdleMotionPlaying = "NO",
            live2dIdleMotionCount = 0,
            live2dIdleMotionPlayCount = 0L,
            live2dLastIdleMotionError = "n/a",
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
    val leftEyeParameterStatus: String = "UNAVAILABLE",
    val rightEyeParameterStatus: String = "UNAVAILABLE",
    val leftEyeOpen: Float? = null,
    val rightEyeOpen: Float? = null,
    val breathParameterStatus: String = "UNAVAILABLE",
    val breathNormalized: Float? = null,
    val breathAppliedValue: Float? = null,
    val breathMin: Float? = null,
    val breathMax: Float? = null,
    val breathDefault: Float? = null,
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
    val idleMotionEnabled: Boolean = false,
    val idleMotionStatus: String = "UNAVAILABLE",
    val idleMotionGroup: String = "Idle",
    val idleMotionFile: String = "n/a",
    val idleMotionIndex: Int = -1,
    val idleMotionPlaying: Boolean = false,
    val idleMotionCount: Int = 0,
    val idleMotionPlayCount: Long = 0L,
    val lastIdleMotionError: String = "n/a",
    val lifecycleState: String = "DISABLED",
    val fallbackReason: String,
    val mouthParameterStatus: String,
    val lastError: String = "n/a"
)

enum class CharacterMode {
    MINIMAL_MOUTH,
    LIVE2D
}
