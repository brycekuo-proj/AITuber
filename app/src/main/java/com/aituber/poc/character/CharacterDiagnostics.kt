package com.aituber.poc.character

import android.os.SystemClock
import com.aituber.poc.character.live2d.Live2DCharacterProfile

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
            runtimeType = requestedMode.runtimeTypeLabel(),
            activeCharacterAdapter = if (overlayRunning) current.activeCharacterAdapter else "none",
            live2dLifecycleState = if (requestedMode == CharacterMode.LIVE2D) {
                if (overlayRunning) "REQUESTED" else "REQUESTED_OVERLAY_DISABLED"
            } else {
                "DISABLED"
            },
            live2dFallbackReason = if (requestedMode == CharacterMode.LIVE2D) {
                if (overlayRunning) "WAITING_FOR_SURFACE" else "WAITING_FOR_OVERLAY"
            } else if (requestedMode == CharacterMode.STATE_VIDEO) {
                if (overlayRunning) "WAITING_FOR_PLAYER" else "WAITING_FOR_OVERLAY"
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
            runtimeType = requestedMode.runtimeTypeLabel(),
            activeCharacterAdapter = activeAdapterId,
            live2dFallbackReason = fallbackReason,
            live2dLifecycleState = live2dLifecycleState
        )
    }

    @Synchronized
    fun recordStateVideo(snapshot: StateVideoDiagnosticsSnapshot) {
        current = current.copy(
            runtimeType = "STATE_VIDEO",
            stateVideoStatus = snapshot.status,
            stateVideoCurrentState = snapshot.currentState,
            stateVideoCurrentClip = snapshot.currentClip,
            stateVideoPlayerReady = if (snapshot.playerReady) "YES" else "NO",
            stateVideoPlayerPlaying = if (snapshot.playerPlaying) "YES" else "NO",
            stateVideoLoopEnabled = if (snapshot.loopEnabled) "YES" else "NO",
            stateVideoMuted = if (snapshot.muted) "YES" else "NO",
            stateVideoWidth = snapshot.videoWidth,
            stateVideoHeight = snapshot.videoHeight,
            stateVideoLastStateSwitchMs = snapshot.lastStateSwitchMs,
            stateVideoLastVideoError = snapshot.lastVideoError
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
    fun recordLive2DProfile(profile: Live2DCharacterProfile) {
        current = current.copy(
            live2dProfileId = profile.id,
            live2dProfileName = profile.displayName,
            live2dModel3File = profile.model3File,
            live2dMappedMouthParameter = profile.parameterMapping.mouthOpen,
            live2dMappedLeftEyeParameter = profile.parameterMapping.eyeLeftOpen,
            live2dMappedRightEyeParameter = profile.parameterMapping.eyeRightOpen,
            live2dMappedBreathParameter = profile.parameterMapping.breath,
            live2dCapabilityIdle = if (profile.capabilities.idleMotion) "YES" else "NO",
            live2dCapabilityPhysics = if (profile.capabilities.physics) "YES" else "NO",
            live2dCapabilityPose = if (profile.capabilities.pose) "YES" else "NO",
            live2dCapabilityExpressions = if (profile.capabilities.expressions) "YES" else "NO",
            live2dModelName = profile.displayName,
            live2dMouthParameterId = profile.parameterMapping.mouthOpen,
            live2dMouthProfileScale = profile.mouthTuning.outputMax.toDouble(),
            live2dFallbackIdleEnabled = if (profile.fallbackHeadIdle.enabled && !profile.capabilities.idleMotion) "YES" else "NO",
            live2dFallbackHeadXMax = profile.fallbackHeadIdle.headXAmplitude.toDouble(),
            live2dFallbackHeadYMax = profile.fallbackHeadIdle.headYAmplitude.toDouble()
        )
    }

    @Synchronized
    fun recordLive2D(snapshot: Live2DDiagnosticsSnapshot) {
        current = current.copy(
            live2dAvailable = if (snapshot.available) "YES" else "NO",
            live2dProfileId = snapshot.profileId,
            live2dProfileName = snapshot.profileName,
            live2dModel3File = snapshot.model3File,
            live2dMappedMouthParameter = snapshot.mappedMouthParameter,
            live2dMappedLeftEyeParameter = snapshot.mappedLeftEyeParameter,
            live2dMappedRightEyeParameter = snapshot.mappedRightEyeParameter,
            live2dMappedBreathParameter = snapshot.mappedBreathParameter,
            live2dCapabilityIdle = if (snapshot.capabilityIdle) "YES" else "NO",
            live2dCapabilityPhysics = if (snapshot.capabilityPhysics) "YES" else "NO",
            live2dCapabilityPose = if (snapshot.capabilityPose) "YES" else "NO",
            live2dCapabilityExpressions = if (snapshot.capabilityExpressions) "YES" else "NO",
            live2dRuntimeLoaded = if (snapshot.runtimeLoaded) "YES" else "NO",
            live2dCoreLoaded = if (snapshot.coreLoaded) "YES" else "NO",
            live2dModelLoaded = if (snapshot.modelLoaded) "YES" else "NO",
            live2dModelName = snapshot.modelName,
            live2dMouthParameterId = snapshot.mouthParameterId,
            live2dMouthParameterValue = snapshot.mouthParameterValue?.toDouble(),
            live2dMouthSemanticValue = snapshot.mouthSemanticValue.toDouble(),
            live2dMouthProfileScale = snapshot.mouthProfileScale.toDouble(),
            live2dInputMouthOpen = snapshot.inputMouthOpen.toDouble(),
            live2dFallbackIdleEnabled = if (snapshot.fallbackIdleEnabled) "YES" else "NO",
            live2dFallbackHeadX = snapshot.fallbackHeadX.toDouble(),
            live2dFallbackHeadY = snapshot.fallbackHeadY.toDouble(),
            live2dFallbackHeadXMax = snapshot.fallbackHeadXMax.toDouble(),
            live2dFallbackHeadYMax = snapshot.fallbackHeadYMax.toDouble(),
            live2dFallbackIdleCycle = snapshot.fallbackIdleCycle,
            live2dPhysicsEarOutputsAvailable = if (snapshot.physicsEarOutputsAvailable) "YES" else "NO",
            live2dPhysicsEarJiggleX = snapshot.physicsEarJiggleX?.toDouble(),
            live2dPhysicsEarJiggleY = snapshot.physicsEarJiggleY?.toDouble(),
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
            live2dPhysicsEnabled = if (snapshot.physicsEnabled) "YES" else "NO",
            live2dPhysicsStatus = snapshot.physicsStatus,
            live2dPhysicsFile = snapshot.physicsFile,
            live2dPhysicsLoaded = if (snapshot.physicsLoaded) "YES" else "NO",
            live2dPhysicsUpdateCount = snapshot.physicsUpdateCount,
            live2dPhysicsLastDeltaMs = snapshot.physicsLastDeltaMs.toDouble(),
            live2dPhysicsInputCount = snapshot.physicsInputCount,
            live2dPhysicsOutputCount = snapshot.physicsOutputCount,
            live2dPhysicsOutputParameterIds = snapshot.physicsOutputParameterIds,
            live2dLastPhysicsError = snapshot.lastPhysicsError,
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
            runtimeType = requestedMode.runtimeTypeLabel(),
            activeCharacterAdapter = "none",
            live2dLifecycleState = if (requestedMode == CharacterMode.LIVE2D) {
                "REQUESTED_OVERLAY_DISABLED"
            } else {
                "DISABLED"
            },
            live2dFallbackReason = if (requestedMode == CharacterMode.LIVE2D) {
                "WAITING_FOR_OVERLAY"
            } else if (requestedMode == CharacterMode.STATE_VIDEO) {
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
    val runtimeType: String,
    val activeCharacterAdapter: String,
    val characterFrameCount: Long,
    val mouthParameterInput: Double,
    val mouthParameterOutput: Double,
    val live2dAvailable: String,
    val live2dProfileId: String,
    val live2dProfileName: String,
    val live2dModel3File: String,
    val live2dMappedMouthParameter: String,
    val live2dMappedLeftEyeParameter: String,
    val live2dMappedRightEyeParameter: String,
    val live2dMappedBreathParameter: String,
    val live2dCapabilityIdle: String,
    val live2dCapabilityPhysics: String,
    val live2dCapabilityPose: String,
    val live2dCapabilityExpressions: String,
    val live2dRuntimeLoaded: String,
    val live2dCoreLoaded: String,
    val live2dModelLoaded: String,
    val live2dModelName: String,
    val live2dMouthParameterId: String,
    val live2dMouthParameterValue: Double?,
    val live2dMouthSemanticValue: Double,
    val live2dMouthProfileScale: Double,
    val live2dInputMouthOpen: Double,
    val live2dFallbackIdleEnabled: String,
    val live2dFallbackHeadX: Double,
    val live2dFallbackHeadY: Double,
    val live2dFallbackHeadXMax: Double,
    val live2dFallbackHeadYMax: Double,
    val live2dFallbackIdleCycle: String,
    val live2dPhysicsEarOutputsAvailable: String,
    val live2dPhysicsEarJiggleX: Double?,
    val live2dPhysicsEarJiggleY: Double?,
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
    val live2dPhysicsEnabled: String,
    val live2dPhysicsStatus: String,
    val live2dPhysicsFile: String,
    val live2dPhysicsLoaded: String,
    val live2dPhysicsUpdateCount: Long,
    val live2dPhysicsLastDeltaMs: Double,
    val live2dPhysicsInputCount: Int,
    val live2dPhysicsOutputCount: Int,
    val live2dPhysicsOutputParameterIds: String,
    val live2dLastPhysicsError: String,
    val live2dLifecycleState: String,
    val live2dFallbackReason: String,
    val live2dMouthParameterStatus: String,
    val live2dLastError: String,
    val stateVideoStatus: String,
    val stateVideoCurrentState: String,
    val stateVideoCurrentClip: String,
    val stateVideoPlayerReady: String,
    val stateVideoPlayerPlaying: String,
    val stateVideoLoopEnabled: String,
    val stateVideoMuted: String,
    val stateVideoWidth: Int,
    val stateVideoHeight: Int,
    val stateVideoLastStateSwitchMs: Long?,
    val stateVideoLastVideoError: String,
    val lastFrameTimestampMs: Long?
) {
    companion object {
        fun empty() = CharacterDiagnosticsSnapshot(
            characterMode = CharacterMode.MINIMAL_MOUTH.name,
            runtimeType = CharacterMode.MINIMAL_MOUTH.runtimeTypeLabel(),
            activeCharacterAdapter = "minimal-mouth-overlay",
            characterFrameCount = 0L,
            mouthParameterInput = 0.0,
            mouthParameterOutput = 0.0,
            live2dAvailable = "NO",
            live2dProfileId = "haru",
            live2dProfileName = "Haru",
            live2dModel3File = "Haru.model3.json",
            live2dMappedMouthParameter = "ParamMouthOpenY",
            live2dMappedLeftEyeParameter = "ParamEyeLOpen",
            live2dMappedRightEyeParameter = "ParamEyeROpen",
            live2dMappedBreathParameter = "ParamBreath",
            live2dCapabilityIdle = "YES",
            live2dCapabilityPhysics = "YES",
            live2dCapabilityPose = "YES",
            live2dCapabilityExpressions = "NO",
            live2dRuntimeLoaded = "NO",
            live2dCoreLoaded = "NO",
            live2dModelLoaded = "NO",
            live2dModelName = "n/a",
            live2dMouthParameterId = "ParamMouthOpenY",
            live2dMouthParameterValue = null,
            live2dMouthSemanticValue = 0.0,
            live2dMouthProfileScale = 1.0,
            live2dInputMouthOpen = 0.0,
            live2dFallbackIdleEnabled = "NO",
            live2dFallbackHeadX = 0.0,
            live2dFallbackHeadY = 0.0,
            live2dFallbackHeadXMax = 0.0,
            live2dFallbackHeadYMax = 0.0,
            live2dFallbackIdleCycle = "DISABLED",
            live2dPhysicsEarOutputsAvailable = "NO",
            live2dPhysicsEarJiggleX = null,
            live2dPhysicsEarJiggleY = null,
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
            live2dPhysicsEnabled = "NO",
            live2dPhysicsStatus = "UNAVAILABLE",
            live2dPhysicsFile = "n/a",
            live2dPhysicsLoaded = "NO",
            live2dPhysicsUpdateCount = 0L,
            live2dPhysicsLastDeltaMs = 0.0,
            live2dPhysicsInputCount = 0,
            live2dPhysicsOutputCount = 0,
            live2dPhysicsOutputParameterIds = "[]",
            live2dLastPhysicsError = "n/a",
            live2dLifecycleState = "DISABLED",
            live2dFallbackReason = "LIVE2D_DISABLED",
            live2dMouthParameterStatus = "UNAVAILABLE",
            live2dLastError = "n/a",
            stateVideoStatus = "DISABLED",
            stateVideoCurrentState = "UNKNOWN",
            stateVideoCurrentClip = "n/a",
            stateVideoPlayerReady = "NO",
            stateVideoPlayerPlaying = "NO",
            stateVideoLoopEnabled = "NO",
            stateVideoMuted = "YES",
            stateVideoWidth = 0,
            stateVideoHeight = 0,
            stateVideoLastStateSwitchMs = null,
            stateVideoLastVideoError = "n/a",
            lastFrameTimestampMs = null
        )
    }
}

data class StateVideoDiagnosticsSnapshot(
    val status: String,
    val currentState: String,
    val currentClip: String,
    val playerReady: Boolean,
    val playerPlaying: Boolean,
    val loopEnabled: Boolean,
    val muted: Boolean,
    val videoWidth: Int,
    val videoHeight: Int,
    val lastStateSwitchMs: Long?,
    val lastVideoError: String
)

data class Live2DDiagnosticsSnapshot(
    val available: Boolean,
    val profileId: String = "haru",
    val profileName: String = "Haru",
    val model3File: String = "Haru.model3.json",
    val mappedMouthParameter: String = "ParamMouthOpenY",
    val mappedLeftEyeParameter: String = "ParamEyeLOpen",
    val mappedRightEyeParameter: String = "ParamEyeROpen",
    val mappedBreathParameter: String = "ParamBreath",
    val capabilityIdle: Boolean = true,
    val capabilityPhysics: Boolean = true,
    val capabilityPose: Boolean = true,
    val capabilityExpressions: Boolean = false,
    val runtimeLoaded: Boolean = available,
    val coreLoaded: Boolean = available,
    val modelLoaded: Boolean,
    val modelName: String,
    val mouthParameterId: String,
    val mouthParameterValue: Float?,
    val mouthSemanticValue: Float = mouthParameterValue ?: 0f,
    val mouthProfileScale: Float = 1f,
    val inputMouthOpen: Float = mouthParameterValue ?: 0f,
    val fallbackIdleEnabled: Boolean = false,
    val fallbackHeadX: Float = 0f,
    val fallbackHeadY: Float = 0f,
    val fallbackHeadXMax: Float = 0f,
    val fallbackHeadYMax: Float = 0f,
    val fallbackIdleCycle: String = "DISABLED",
    val physicsEarOutputsAvailable: Boolean = false,
    val physicsEarJiggleX: Float? = null,
    val physicsEarJiggleY: Float? = null,
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
    val physicsEnabled: Boolean = false,
    val physicsStatus: String = "UNAVAILABLE",
    val physicsFile: String = "n/a",
    val physicsLoaded: Boolean = false,
    val physicsUpdateCount: Long = 0L,
    val physicsLastDeltaMs: Float = 0f,
    val physicsInputCount: Int = 0,
    val physicsOutputCount: Int = 0,
    val physicsOutputParameterIds: String = "[]",
    val lastPhysicsError: String = "n/a",
    val lifecycleState: String = "DISABLED",
    val fallbackReason: String,
    val mouthParameterStatus: String,
    val lastError: String = "n/a"
)

enum class CharacterMode {
    MINIMAL_MOUTH,
    LIVE2D,
    STATE_VIDEO
}

fun CharacterMode.runtimeTypeLabel(): String = when (this) {
    CharacterMode.STATE_VIDEO -> "STATE_VIDEO"
    CharacterMode.LIVE2D -> "LIVE2D"
    CharacterMode.MINIMAL_MOUTH -> "MINIMAL_MOUTH"
}
