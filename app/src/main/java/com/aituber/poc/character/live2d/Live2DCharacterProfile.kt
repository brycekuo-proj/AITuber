package com.aituber.poc.character.live2d

import kotlin.math.pow

data class Live2DParameterMapping(
    val mouthOpen: String,
    val eyeLeftOpen: String,
    val eyeRightOpen: String,
    val breath: String,
    val headX: String? = null,
    val headY: String? = null,
    val headZ: String? = null
)

data class Live2DMouthTuning(
    val outputMin: Float = 0f,
    val outputMax: Float = 1f,
    val exponent: Float = 1f
) {
    fun map(semantic: Float): Float {
        val normalized = semantic.coerceIn(0f, 1f)
        val curved = normalized.toDouble()
            .let { it.pow(exponent.coerceAtLeast(0.01f).toDouble()) }
            .toFloat()
        return outputMin + (outputMax - outputMin) * curved
    }
}

data class Live2DFallbackHeadIdleConfig(
    val enabled: Boolean = false,
    val headXAmplitude: Float = 0f,
    val headYAmplitude: Float = 0f,
    val headXPeriodMs: Long = 6_200L,
    val headYPeriodMs: Long = 4_700L,
    val testHeadXAmplitude: Float = 0f,
    val testHeadYAmplitude: Float = 0f,
    val testDurationMs: Long = 2_600L
)

data class Live2DCharacterCapabilities(
    val idleMotion: Boolean,
    val physics: Boolean,
    val pose: Boolean,
    val expressions: Boolean
)

data class Live2DCharacterProfile(
    val id: String,
    val displayName: String,
    val assetDir: String,
    val model3File: String,
    val parameterMapping: Live2DParameterMapping,
    val mouthTuning: Live2DMouthTuning = Live2DMouthTuning(),
    val fallbackHeadIdle: Live2DFallbackHeadIdleConfig = Live2DFallbackHeadIdleConfig(),
    val capabilities: Live2DCharacterCapabilities
) {
    fun toModelConfig(): Live2DModelConfig = Live2DModelConfig(
        modelName = displayName,
        model3JsonPath = model3File,
        mouthParameterId = parameterMapping.mouthOpen,
        leftEyeParameterId = parameterMapping.eyeLeftOpen,
        rightEyeParameterId = parameterMapping.eyeRightOpen,
        breathParameterId = parameterMapping.breath,
        mouthTuning = mouthTuning
    )
}

object Live2DCharacterProfiles {
    const val HARU_ID = "haru"
    const val LOAF_DOG_ID = "duokhay-loaf-dog"

    val Haru = Live2DCharacterProfile(
        id = HARU_ID,
        displayName = "Haru",
        assetDir = "live2d/Haru",
        model3File = "Haru.model3.json",
        parameterMapping = Live2DParameterMapping(
            mouthOpen = "ParamMouthOpenY",
            eyeLeftOpen = "ParamEyeLOpen",
            eyeRightOpen = "ParamEyeROpen",
            breath = "ParamBreath"
        ),
        mouthTuning = Live2DMouthTuning(
            outputMin = 0f,
            outputMax = 1f,
            exponent = 1f
        ),
        capabilities = Live2DCharacterCapabilities(
            idleMotion = true,
            physics = true,
            pose = true,
            expressions = false
        )
    )

    val LoafDog = Live2DCharacterProfile(
        id = LOAF_DOG_ID,
        displayName = "Loaf Dog",
        assetDir = "live2d/duokhay-loaf-dog",
        model3File = "bread dog chonk.model3.json",
        parameterMapping = Live2DParameterMapping(
            mouthOpen = "ParamMouthOpen",
            eyeLeftOpen = "ParamEyeLOpen",
            eyeRightOpen = "ParamEyeROpen",
            breath = "ParamBreath",
            headX = "ParamAngleX",
            headY = "ParamAngleY"
        ),
        mouthTuning = Live2DMouthTuning(
            outputMin = 0f,
            outputMax = 1.30f,
            exponent = 1.15f
        ),
        fallbackHeadIdle = Live2DFallbackHeadIdleConfig(
            enabled = true,
            headXAmplitude = 3.6f,
            headYAmplitude = 1.8f,
            testHeadXAmplitude = 10.8f,
            testHeadYAmplitude = 6.0f
        ),
        capabilities = Live2DCharacterCapabilities(
            idleMotion = false,
            physics = true,
            pose = false,
            expressions = false
        )
    )

    val all: List<Live2DCharacterProfile> = listOf(Haru, LoafDog)

    fun byId(id: String?): Live2DCharacterProfile =
        all.firstOrNull { it.id == id } ?: Haru

    fun next(id: String?): Live2DCharacterProfile {
        val currentIndex = all.indexOfFirst { it.id == byId(id).id }
        val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % all.size
        return all[nextIndex]
    }
}
