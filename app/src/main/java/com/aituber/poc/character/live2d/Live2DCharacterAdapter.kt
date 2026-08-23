package com.aituber.poc.character.live2d

import com.aituber.poc.character.CharacterAdapter
import com.aituber.poc.character.CharacterDiagnostics
import com.aituber.poc.character.CharacterParameterFrame
import com.aituber.poc.character.Live2DDiagnosticsSnapshot

class Live2DCharacterAdapter(
    private val modelConfig: Live2DModelConfig = Live2DModelConfig(),
    private val sdkAvailable: Boolean = false,
    private val parameterSink: Live2DParameterSink? = null,
    private val renderFps: Double = 0.0
) : CharacterAdapter {
    override val characterId = "live2d-character-adapter"

    val available: Boolean
        get() = sdkAvailable && parameterSink != null

    override fun render(frame: CharacterParameterFrame) {
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
                fallbackReason = "n/a",
                mouthParameterStatus = result.status.name
            )
        )
    }

    fun diagnosticsSnapshot(): Live2DDiagnosticsSnapshot {
        return if (available) {
            Live2DDiagnosticsSnapshot(
                available = true,
                modelLoaded = true,
                modelName = modelConfig.modelName,
                mouthParameterId = modelConfig.mouthParameterId,
                mouthParameterValue = null,
                renderFps = renderFps,
                fallbackReason = "n/a",
                mouthParameterStatus = "READY"
            )
        } else {
            unavailableSnapshot("SDK_OR_MODEL_NOT_CONFIGURED")
        }
    }

    private fun unavailableSnapshot(reason: String) = Live2DDiagnosticsSnapshot(
        available = false,
        modelLoaded = false,
        modelName = modelConfig.modelName,
        mouthParameterId = modelConfig.mouthParameterId,
        mouthParameterValue = null,
        renderFps = 0.0,
        fallbackReason = reason,
        mouthParameterStatus = Live2DParameterStatus.UNAVAILABLE.name
    )
}
