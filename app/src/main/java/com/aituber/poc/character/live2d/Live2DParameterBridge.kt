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
        return Live2DParameterResult(
            parameterId = modelConfig.mouthParameterId,
            value = value,
            status = if (applied) Live2DParameterStatus.APPLIED else Live2DParameterStatus.NOT_FOUND,
            leftEyeParameterId = modelConfig.leftEyeParameterId,
            leftEyeValue = leftEyeValue,
            leftEyeStatus = leftEyeStatus,
            rightEyeParameterId = modelConfig.rightEyeParameterId,
            rightEyeValue = rightEyeValue,
            rightEyeStatus = rightEyeStatus
        )
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
    val rightEyeStatus: Live2DParameterStatus
)

enum class Live2DParameterStatus {
    APPLIED,
    NOT_FOUND,
    UNAVAILABLE
}
