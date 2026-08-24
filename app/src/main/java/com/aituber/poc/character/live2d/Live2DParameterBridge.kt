package com.aituber.poc.character.live2d

import com.aituber.poc.character.CharacterParameterFrame

class Live2DParameterBridge(
    private val modelConfig: Live2DModelConfig,
    private val sink: Live2DParameterSink
) {
    fun apply(frame: CharacterParameterFrame): Live2DParameterResult {
        val value = frame.mouthOpen.coerceIn(0f, 1f)
        val applied = sink.setParameter(modelConfig.mouthParameterId, value)
        val leftEyeValue = frame.eyeLeftOpen?.coerceIn(0f, 1f)
        val rightEyeValue = frame.eyeRightOpen?.coerceIn(0f, 1f)
        val leftEyeStatus = leftEyeValue?.let {
            if (sink.setParameter(modelConfig.leftEyeParameterId, it)) {
                Live2DParameterStatus.APPLIED
            } else {
                Live2DParameterStatus.NOT_FOUND
            }
        } ?: Live2DParameterStatus.UNAVAILABLE
        val rightEyeStatus = rightEyeValue?.let {
            if (sink.setParameter(modelConfig.rightEyeParameterId, it)) {
                Live2DParameterStatus.APPLIED
            } else {
                Live2DParameterStatus.NOT_FOUND
            }
        } ?: Live2DParameterStatus.UNAVAILABLE
        val breathValue = frame.breath?.coerceIn(0f, 1f)
        val breathIntensity = frame.breathIntensity?.coerceIn(0f, 1f) ?: modelConfig.breathIntensity
        val breathAppliedValue = breathValue?.let {
            Live2DBreathParameterMapper.map(
                normalized = it,
                min = 0f,
                max = 1f,
                default = 0.5f,
                intensity = breathIntensity
            )
        }
        val breathStatus = breathAppliedValue?.let {
            if (sink.setParameter(modelConfig.breathParameterId, it)) {
                Live2DParameterStatus.APPLIED
            } else {
                Live2DParameterStatus.NOT_FOUND
            }
        } ?: Live2DParameterStatus.UNAVAILABLE
        return Live2DParameterResult(
            parameterId = modelConfig.mouthParameterId,
            value = value,
            status = if (applied) Live2DParameterStatus.APPLIED else Live2DParameterStatus.NOT_FOUND,
            leftEyeParameterId = modelConfig.leftEyeParameterId,
            leftEyeValue = leftEyeValue,
            leftEyeStatus = leftEyeStatus,
            rightEyeParameterId = modelConfig.rightEyeParameterId,
            rightEyeValue = rightEyeValue,
            rightEyeStatus = rightEyeStatus,
            breathParameterId = modelConfig.breathParameterId,
            breathNormalized = breathValue,
            breathAppliedValue = breathAppliedValue,
            breathStatus = breathStatus
        )
    }
}

object Live2DBreathParameterMapper {
    fun map(
        normalized: Float,
        min: Float,
        max: Float,
        default: Float,
        intensity: Float
    ): Float {
        val safeMin = kotlin.math.min(min, max)
        val safeMax = kotlin.math.max(min, max)
        val safeDefault = default.coerceIn(safeMin, safeMax)
        val safeIntensity = intensity.coerceIn(0f, 1f)
        val safeNormalized = normalized.coerceIn(0f, 1f)
        val lower = (safeDefault - safeMin) * safeIntensity
        val upper = (safeMax - safeDefault) * safeIntensity
        return if (safeNormalized < 0.5f) {
            safeDefault - lower * ((0.5f - safeNormalized) / 0.5f)
        } else {
            safeDefault + upper * ((safeNormalized - 0.5f) / 0.5f)
        }.coerceIn(safeMin, safeMax)
    }
}

interface Live2DParameterSink {
    fun setParameter(parameterId: String, value: Float): Boolean
}

data class Live2DParameterResult(
    val parameterId: String,
    val value: Float,
    val status: Live2DParameterStatus,
    val leftEyeParameterId: String,
    val leftEyeValue: Float?,
    val leftEyeStatus: Live2DParameterStatus,
    val rightEyeParameterId: String,
    val rightEyeValue: Float?,
    val rightEyeStatus: Live2DParameterStatus,
    val breathParameterId: String,
    val breathNormalized: Float?,
    val breathAppliedValue: Float?,
    val breathStatus: Live2DParameterStatus
)

enum class Live2DParameterStatus {
    APPLIED,
    NOT_FOUND,
    UNAVAILABLE
}
