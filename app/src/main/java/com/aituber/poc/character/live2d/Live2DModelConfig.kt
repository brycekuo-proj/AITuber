package com.aituber.poc.character.live2d

data class Live2DModelConfig(
    val modelName: String = "Local Live2D Model",
    val model3JsonPath: String = "assets/live2d/model/model.model3.json",
    val mouthParameterId: String = "ParamMouthOpenY",
    val leftEyeParameterId: String = "ParamEyeLOpen",
    val rightEyeParameterId: String = "ParamEyeROpen"
)
