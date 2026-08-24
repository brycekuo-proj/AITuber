package com.aituber.poc.character.live2d

data class Live2DParameterMapping(
    val mouthOpen: String,
    val eyeLeftOpen: String,
    val eyeRightOpen: String,
    val breath: String
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
    val capabilities: Live2DCharacterCapabilities
) {
    fun toModelConfig(): Live2DModelConfig = Live2DModelConfig(
        modelName = displayName,
        model3JsonPath = model3File,
        mouthParameterId = parameterMapping.mouthOpen,
        leftEyeParameterId = parameterMapping.eyeLeftOpen,
        rightEyeParameterId = parameterMapping.eyeRightOpen,
        breathParameterId = parameterMapping.breath
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
            breath = "ParamBreath"
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
