package com.aituber.poc.character.live2d

data class Live2DNativeSnapshot(
    val runtimeLoaded: Boolean,
    val coreLoaded: Boolean,
    val modelLoaded: Boolean,
    val mouthParameterFound: Boolean,
    val inputMouthOpen: Float,
    val appliedMouthOpen: Float,
    val renderFps: Double,
    val nativeFrameCount: Long,
    val surfaceWidth: Int,
    val surfaceHeight: Int,
    val modelName: String,
    val mouthParameterId: String,
    val lastError: String,
    val textureCount: Int,
    val texturesLoaded: Int,
    val lastTexturePath: String,
    val lastTextureError: String,
    val glTextureIds: String,
    val poseFile: String,
    val poseLoaded: Boolean,
    val poseActive: Boolean
) {
    companion object {
        fun unavailable(reason: String) = Live2DNativeSnapshot(
            runtimeLoaded = false,
            coreLoaded = false,
            modelLoaded = false,
            mouthParameterFound = false,
            inputMouthOpen = 0f,
            appliedMouthOpen = 0f,
            renderFps = 0.0,
            nativeFrameCount = 0L,
            surfaceWidth = 0,
            surfaceHeight = 0,
            modelName = "Haru",
            mouthParameterId = "ParamMouthOpenY",
            lastError = reason,
            textureCount = 0,
            texturesLoaded = 0,
            lastTexturePath = "n/a",
            lastTextureError = reason,
            glTextureIds = "[]",
            poseFile = "n/a",
            poseLoaded = false,
            poseActive = false
        )
    }
}
