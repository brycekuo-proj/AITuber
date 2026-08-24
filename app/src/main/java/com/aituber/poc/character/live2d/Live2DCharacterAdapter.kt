package com.aituber.poc.character.live2d

import com.aituber.poc.character.CharacterAdapter
import com.aituber.poc.character.CharacterDiagnostics
import com.aituber.poc.character.CharacterParameterFrame
import com.aituber.poc.character.Live2DDiagnosticsSnapshot

class Live2DCharacterAdapter(
    private val modelConfig: Live2DModelConfig = Live2DModelConfig(),
    private val sdkAvailable: Boolean = false,
    private val parameterSink: Live2DParameterSink? = null,
    private val renderFps: Double = 0.0,
    private val nativeView: Live2DOverlayView? = null
) : CharacterAdapter {
    override val characterId = "live2d-character-adapter"

    val available: Boolean
        get() = (sdkAvailable && parameterSink != null) || nativeView?.initialized == true

    override fun render(frame: CharacterParameterFrame) {
        val view = nativeView
        if (view != null && view.initialized) {
            val clampedFrame = frame.clamped()
            view.renderFrame(clampedFrame)
            return
        }

        val sink = parameterSink
        if (!sdkAvailable || sink == null) {
            CharacterDiagnostics.recordLive2D(unavailableSnapshot("SDK_OR_MODEL_NOT_CONFIGURED"))
            return
        }

        val result = Live2DParameterBridge(modelConfig, sink).apply(frame)
        CharacterDiagnostics.recordLive2D(
            Live2DDiagnosticsSnapshot(
                available = true,
                modelLoaded = true,
                modelName = modelConfig.modelName,
                mouthParameterId = result.parameterId,
                mouthParameterValue = result.value,
                renderFps = renderFps,
                leftEyeParameterStatus = result.leftEyeStatus.name,
                rightEyeParameterStatus = result.rightEyeStatus.name,
                leftEyeOpen = result.leftEyeValue,
                rightEyeOpen = result.rightEyeValue,
                fallbackReason = "n/a",
                mouthParameterStatus = result.status.name
            )
        )
    }

    fun diagnosticsSnapshot(): Live2DDiagnosticsSnapshot {
        val view = nativeView
        if (view != null && view.initialized) {
            val native = view.bridge.snapshot()
            return Live2DDiagnosticsSnapshot(
                available = true,
                runtimeLoaded = native.runtimeLoaded,
                coreLoaded = native.coreLoaded,
                modelLoaded = native.modelLoaded,
                modelName = native.modelName,
                mouthParameterId = native.mouthParameterId,
                mouthParameterValue = native.appliedMouthOpen,
                inputMouthOpen = native.inputMouthOpen,
                leftEyeParameterStatus = native.leftEyeParameterStatus,
                rightEyeParameterStatus = native.rightEyeParameterStatus,
                leftEyeOpen = native.appliedLeftEyeOpen,
                rightEyeOpen = native.appliedRightEyeOpen,
                renderFps = native.renderFps,
                nativeFrameCount = native.nativeFrameCount,
                surfaceWidth = native.surfaceWidth,
                surfaceHeight = native.surfaceHeight,
                textureCount = native.textureCount,
                texturesLoaded = native.texturesLoaded,
                lastTexturePath = native.lastTexturePath,
                lastTextureError = native.lastTextureError,
                glTextureIds = native.glTextureIds,
                poseFile = native.poseFile,
                poseLoaded = native.poseLoaded,
                poseActive = native.poseActive,
                lifecycleState = when {
                    native.modelLoaded -> "READY"
                    native.lastError.isNotBlank() -> "FAILED"
                    native.runtimeLoaded -> "INITIALIZING"
                    else -> "WAITING_FOR_SURFACE"
                },
                fallbackReason = native.lastError.ifBlank { "n/a" },
                mouthParameterStatus = if (native.mouthParameterFound) {
                    Live2DParameterStatus.APPLIED.name
                } else {
                    Live2DParameterStatus.NOT_FOUND.name
                },
                lastError = native.lastError.ifBlank { "n/a" }
            )
        }
        return if (available) {
            Live2DDiagnosticsSnapshot(
                available = true,
                modelLoaded = true,
                modelName = modelConfig.modelName,
                mouthParameterId = modelConfig.mouthParameterId,
                mouthParameterValue = null,
                renderFps = renderFps,
                lifecycleState = "READY",
                fallbackReason = "n/a",
                mouthParameterStatus = "READY"
            )
        } else {
            unavailableSnapshot("SDK_OR_MODEL_NOT_CONFIGURED")
        }
    }

    private fun unavailableSnapshot(reason: String) = Live2DDiagnosticsSnapshot(
        available = false,
        runtimeLoaded = false,
        coreLoaded = false,
        modelLoaded = false,
        modelName = modelConfig.modelName,
        mouthParameterId = modelConfig.mouthParameterId,
        mouthParameterValue = null,
        renderFps = 0.0,
        fallbackReason = reason,
        mouthParameterStatus = Live2DParameterStatus.UNAVAILABLE.name,
        lastError = reason
    )
}
